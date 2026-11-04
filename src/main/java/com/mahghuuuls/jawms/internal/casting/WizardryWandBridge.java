package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.CastAttemptContext;
import com.mahghuuuls.jawms.api.CastAttemptOutcome;
import com.mahghuuuls.jawms.internal.mana.ManaNetwork;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentPlan;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentReservation;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentService;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.DemonicSealPolicy;
import com.mahghuuuls.jawms.internal.integration.wizardry.projectile.ForceArrowRefundTracker;
import com.mahghuuuls.jawms.internal.integration.wizardry.projectile.PerfectionistPolicy;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import electroblob.wizardry.entity.projectile.EntityForceArrow;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public final class WizardryWandBridge {

    private static UUID runEpoch = UUID.randomUUID();
    private static long attemptSequence;

    public static void beginServerRun() {
        runEpoch = UUID.randomUUID();
        attemptSequence = 0L;
        ATTEMPTS.remove();
        ParticipantUseService.clear();
    }

    public static boolean isCastAllowed() {
        return !CastCostCoordinator.isCallbackActive()
                && (CastCostRegistry.instance().isEmpty() || ATTEMPTS.get().size() < 16);
    }

    private static final ThreadLocal<Deque<CastAttempt>> ATTEMPTS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private WizardryWandBridge() {
    }

    public static void beginPhysicalCast(EntityPlayer caster, EnumHand hand) {
        boolean continuingSameUse = caster.isHandActive()
                && caster.getActiveHand() == hand
                && CastPaymentContextManager.has(caster, hand);
        if (!continuingSameUse) {
            CastCostContextManager.clear(caster, hand);
            CastPaymentContextManager.clear(caster, hand);
        }
        CastPaymentContextManager.captureIfAbsent(
                caster, hand, integrationRules(caster)
        );
    }

    public static void endDiscreteCast(EntityPlayer caster, EnumHand hand, Spell spell) {
        if (!spell.isContinuous) {
            CastCostContextManager.clear(caster, hand);
            CastPaymentContextManager.clear(caster, hand);
        }
    }

    public static int replaceCalculatedCost(
            int wizardryCost,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            SpellModifiers modifiers
    ) {
        if (caster.world.isRemote) {
            int calculated = JustAnotherWizardryManaSystemMod.proxy.getClientFinalSpellCost(
                    spell,
                    modifiers,
                    wizardryCost
            );
            return CastCostContextManager.captureOrGet(caster, hand, spell, calculated);
        }
        com.mahghuuuls.jawms.internal.rules.GameplayRules rules =
                com.mahghuuuls.jawms.internal.rules.GameplayRulesManager.current();
        ManaService mana = new ManaService();
        double points = mana.getApplicableSpellEfficiency(caster, spell);
        double profileMultiplier = mana.getApplicableSpellCostMultiplier(caster, spell);
        return CastCostContextManager.captureOrGet(caster, hand, spell,
                SpellCostService.getFinalCost(caster, spell, modifiers),
                SpellCostService.getConfiguredBaseCost(rules, spell),
                SpellCostService.retainedContextualFactor(modifiers.get(SpellModifiers.COST)),
                profileMultiplier, points, rules.getEfficiencyScale());
    }

    public static int getAvailableManaForPrecheck(
            int wizardryWandMana,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        if (!hasUsableManaProfile(caster) || (caster.world.isRemote
                && JustAnotherWizardryManaSystemMod.proxy.isClientContinuousCastBlocked(spell, hand))) {
            return -1;
        }
        if (caster.isCreative()) {
            return Integer.MAX_VALUE;
        }
        if (caster.world.isRemote) {
            int available = JustAnotherWizardryManaSystemMod.proxy.getClientAvailableMana(
                    wizardryWandMana
            );
            int fallbackCost = SpellCostService.getFinalCost(spell, modifiers);
            int synchronizedCost = JustAnotherWizardryManaSystemMod.proxy
                    .getClientFinalSpellCost(spell, modifiers, fallbackCost);
            int capturedCost = CastCostContextManager.captureOrGet(
                    caster, hand, spell, synchronizedCost
            );
            int required = spell.isContinuous
                    ? ContinuousSpellCost.portionAtTick(
                            capturedCost,
                            castingTick
                    )
                    : capturedCost;
            CastPaymentPlan plan = CastPaymentPlan.calculate(
                    required,
                    available,
                    caster.getFoodStats().getFoodLevel(),
                    0,
                    paymentPolicy(caster, hand),
                    spell.isContinuous
            );
            if (spell.isContinuous && !plan.isAffordable()) {
                JustAnotherWizardryManaSystemMod.proxy.blockClientContinuousCast(
                        spell,
                        hand,
                        true
                );
            }
            return plan.isAffordable() ? Math.max(available, required) : available;
        }

        if (spell.isContinuous
                && ManaService.isContinuousCastRetryBlocked(
                        caster,
                        hand,
                        spell.getRegistryName()
                )) {
            return -1;
        }
        int required = costForAttempt(caster, hand, spell, modifiers, castingTick);
        CastPaymentPlan plan = CastPaymentService.preview(
                caster,
                required,
                paymentPolicy(caster, hand),
                spell.isContinuous,
                hand,
                spell.getRegistryName()
        );
        int available = ManaService.getAvailableMana(caster);
        if (!plan.isAffordable()) {
            if (spell.isContinuous) {
                ManaService.exhaustContinuousCastMana(
                        caster,
                        hand,
                        spell.getRegistryName()
                );
            }
            notifyInsufficient(caster, spell, hand, plan);
            CastingDiagnostics.trace(
                    "precheck_rejected",
                    caster,
                    spell.getRegistryName(),
                    hand,
                    required,
                    available
            );
        }
        return plan.isAffordable() ? Math.max(available, required) : available;
    }

    public static boolean castWithReservation(
            ItemStack owningStack, Spell spell, World world, EntityPlayer caster,
            EnumHand hand, int castingTick, SpellModifiers modifiers) {
        if (!hasUsableManaProfile(caster) || (!world.isRemote && !isCastAllowed())) return false;
        if (world.isRemote || CastCostRegistry.instance().isEmpty()) {
            return castLegacyWithReservation(spell, world, caster, hand, castingTick, modifiers);
        }
        ManaState originalState = ManaService.requireState(caster);
        int cost = costForAttempt(caster, hand, spell, modifiers, castingTick);
        CastPaymentReservation reservation = CastPaymentService.reserve(caster,
                caster.isCreative() ? 0 : cost,
                caster.isCreative() ? DemonicSealPolicy.inactive() : paymentPolicy(caster, hand),
                spell.isContinuous, hand, spell.getRegistryName());
        if (reservation == null) {
            if (spell.isContinuous && ParticipantUseService.syntheticCast()) return false;
            if (spell.isContinuous && ParticipantUseService.interrupt(caster)) return false;
            return castLegacyWithReservation(spell, world, caster, hand, castingTick, modifiers);
        }
        CastAttempt attempt = new CastAttempt(reservation, spell.getRegistryName(), hand,
                true, caster, perfectionistPolicy(caster, spell));
        attempt.coordinated = true;
        ATTEMPTS.get().push(attempt);
        if (attemptSequence == Long.MAX_VALUE) {
            throw new IllegalStateException("Cast identity sequence exhausted.");
        }
        String id = runEpoch + "/" + (++attemptSequence);
        ParticipantUseService.Scope use = ParticipantUseService.current();
        if (use != null) use.preparing();
        CastAttemptContext context = new CastAttemptContext(caster, spell, spell.getRegistryName(),
                owningStack, hand, spell.isContinuous, caster.isCreative(), castingTick,
                use == null ? id + "/use" : use.id(), id + "/attempt", CastCostContextManager.acceptedCost(caster, hand, spell),
                cost, reservation.getPaidMana(), reservation.getPaidHunger());
        final int tick = world.getMinecraftServer().getTickCounter();
        final boolean physicalBinding = caster.getHeldItem(hand) == owningStack;
        final ItemStack acceptedStack = owningStack.copy();
        CastCostCoordinator.LocalPayment localPayment = new CastCostCoordinator.LocalPayment() {
            public boolean isValid() {
                try {
                    if (ATTEMPTS.get().size() > 16 || (spell.isContinuous && use != null && !use.valid()) || caster.world != world || !caster.isEntityAlive()
                        || world.getMinecraftServer().getTickCounter() != tick
                        || !WandBinding.semanticEquals(acceptedStack, owningStack)
                        || (!spell.isContinuous && !acceptedStack.areCapsCompatible(owningStack))
                        || (physicalBinding && caster.getHeldItem(hand) != owningStack)) return false;
                    return ManaService.requireState(caster) == originalState
                            && reservation.isCurrentGeneration()
                            && (reservation.isCommitted()
                            || CastPaymentService.isValid(caster, reservation));
                } catch (RuntimeException invalid) { return false; }
            }
            public void commit() { CastPaymentService.commitParticipating(caster, reservation); }
            public void completeAccepted() {
                ManaService.completeParticipatingCast(caster, spell.isContinuous, hand, spell.getRegistryName());
            }
            public void release() { CastPaymentService.release(caster, reservation); }
            public boolean isCommitted() { return reservation.isCommitted(); }
            public int paidMana() { return reservation.actualPaidMana(); }
            public int paidHunger() { return reservation.actualPaidHunger(); }
            public boolean isAmountKnown() { return reservation.isLocalAmountKnown(); }
        };
        CastAttemptOutcome outcome = CastCostCoordinator.execute(CastCostRegistry.instance(), context,
                localPayment, () -> {
                    Runnable restoreCost = CastCostContextManager.suspend(caster, hand);
                    Runnable restorePayment = CastPaymentContextManager.suspend(caster, hand);
                    Throwable primary = null;
                    try {
                        return spell.cast(world, caster, hand, castingTick, modifiers);
                    } catch (RuntimeException | Error fault) {
                        primary = fault;
                        throw fault;
                    } finally {
                        try {
                            if (localPayment.isValid()) {
                                restoreCost.run();
                                restorePayment.run();
                            }
                        } catch (RuntimeException | Error fault) {
                            if (primary == null) throw fault;
                            if (primary != fault) primary.addSuppressed(fault);
                        }
                    }
                }, thrown -> CastingDiagnostics.thrownEffect(thrown, tick, context.getManaPortion(),
                        use == null ? null : use.diagnostics()),
                (receipt, participant) -> CastingDiagnostics.notificationFault(receipt, participant, tick));
        CastingDiagnostics.participation(outcome, tick, context.getManaPortion(),
                use == null ? null : use.diagnostics());
        if (outcome.getStatus() == CastAttemptOutcome.Status.LEGACY) {
            if (use != null) use.outcome(outcome);
            ATTEMPTS.get().pop();
            if (spell.isContinuous && use != null && !use.permitsLegacy()) return false;
            return castLegacyWithReservation(spell, world, caster, hand, castingTick, modifiers);
        }
        if (outcome.isAccepted()) promotePendingForceArrows(caster, attempt);
        if (use != null) use.outcome(outcome);
        return outcome.isAccepted();
    }

    private static boolean castLegacyWithReservation(
            Spell spell,
            World world,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        if (world.isRemote) {
            return spell.cast(world, caster, hand, castingTick, modifiers);
        }

        int cost = costForAttempt(caster, hand, spell, modifiers, castingTick);
        if (caster.isCreative()) {
            boolean successful = spell.cast(world, caster, hand, castingTick, modifiers);
            CastPaymentService.recordCreative(
                    caster,
                    cost,
                    paymentPolicy(caster, hand),
                    spell.isContinuous,
                    hand,
                    spell.getRegistryName(),
                    successful
            );
            return successful;
        }
        CastPaymentReservation reservation = CastPaymentService.reserve(
                caster,
                cost,
                paymentPolicy(caster, hand),
                spell.isContinuous,
                hand,
                spell.getRegistryName()
        );
        if (reservation == null) {
            CastPaymentPlan plan = CastPaymentService.preview(
                    caster,
                    cost,
                    paymentPolicy(caster, hand),
                    spell.isContinuous,
                    hand,
                    spell.getRegistryName()
            );
            int available = ManaService.getAvailableMana(caster);
            notifyInsufficient(caster, spell, hand, plan);
            CastingDiagnostics.trace(
                    "reservation_rejected",
                    caster,
                    spell.getRegistryName(),
                    hand,
                    cost,
                    available
            );
            if (spell.isContinuous) {
                if (ParticipantUseService.syntheticCast() || ParticipantUseService.interrupt(caster)) return false;
                ManaService.exhaustContinuousCastMana(
                        caster,
                        hand,
                        spell.getRegistryName()
                );
                ManaNetwork.stopContinuousCastVisual(
                        caster,
                        hand,
                        spell.getRegistryName(),
                        true
                );
                caster.stopActiveHand();
            }
            return false;
        }

        CastAttempt attempt = new CastAttempt(
                reservation,
                spell.getRegistryName(),
                hand,
                !spell.isContinuous || cost > 0,
                caster,
                perfectionistPolicy(caster, spell)
        );
        ATTEMPTS.get().push(attempt);
        traceAttempt("reservation_created", caster, attempt);
        try {
            boolean successful = spell.cast(world, caster, hand, castingTick, modifiers);
            if (!successful) {
                releaseTopAttempt(caster, attempt, "spell_failed");
            }
            return successful;
        } catch (RuntimeException | Error throwable) {
            releaseTopAttempt(caster, attempt, "spell_threw");
            throw throwable;
        }
    }

    public static void commitFromSuccessfulDeduction(
            ItemWand wand,
            ItemStack stack,
            int wizardryCost,
            EntityLivingBase wielder,
            Spell spell
    ) {
        if (!(wielder instanceof EntityPlayer)) {
            throw new IllegalStateException("Wizardry wand deduction did not have a player caster.");
        }
        EntityPlayer player = (EntityPlayer) wielder;
        if (player.world.isRemote || player.isCreative()) {
            return;
        }
        commitTopAttempt(player, spell, true);
    }

    public static void commitZeroCostIfPending(EntityPlayer caster, Spell spell) {
        if (caster.world.isRemote || caster.isCreative()) {
            return;
        }
        commitTopAttempt(caster, spell, false);
    }

    public static void beginContinuousCast(
            EntityPlayer caster,
            EnumHand hand,
            ItemStack stack,
            ActionResult<ItemStack> result
    ) {
        Spell spell = WandHelper.getCurrentSpell(stack);
        if (result.getType() != EnumActionResult.SUCCESS) {
            CastCostContextManager.clear(caster, hand);
            CastPaymentContextManager.clear(caster, hand);
        }
        if (caster.world.isRemote || !ManaService.canUseManaProfile(caster)) {
            return;
        }
        if (!spell.isContinuous) {
            return;
        }
        if (result.getType() != EnumActionResult.SUCCESS) {
            boolean alreadyBlocked = ManaService.isContinuousCastRetryBlocked(
                    caster,
                    hand,
                    spell.getRegistryName()
            );
            ManaService.blockContinuousCastRetry(
                    caster,
                    hand,
                    spell.getRegistryName()
            );
            if (alreadyBlocked) {
                return;
            }
            if (ParticipantUseService.rejectStart(caster, hand, stack, spell)) return;
            ManaNetwork.stopContinuousCastVisual(
                    caster,
                    hand,
                    spell.getRegistryName(),
                    true
            );
            CastingDiagnostics.trace(
                    "continuous_start_rejected",
                    caster,
                    spell.getRegistryName(),
                    hand,
                    0,
                    ManaService.getAvailableMana(caster)
            );
            return;
        }
        if (!caster.isHandActive() || caster.getActiveHand() != hand) {
            return;
        }
        ManaService.beginContinuousCast(caster, hand, spell.getRegistryName());
        CastingDiagnostics.trace(
                "continuous_started",
                caster,
                spell.getRegistryName(),
                hand,
                0,
                ManaService.getAvailableMana(caster)
        );
    }

    public static void interruptContinuousCast(EntityPlayer caster, Spell spell, EnumHand hand) {
        boolean correlatedStop = !caster.world.isRemote && ParticipantUseService.ownsPhysicalStop(caster);
        CastCostContextManager.clear(caster, hand);
        CastPaymentContextManager.clear(caster, hand);
        if (caster.world.isRemote || !spell.isContinuous || !ManaService.canUseManaProfile(caster)) {
            return;
        }
        boolean manaExhausted = ManaService.requireState(caster)
                .wasContinuousCastManaExhausted();
        ManaService.interruptContinuousCast(caster, hand, spell.getRegistryName());
        // In the negotiated envelope, the enclosing actual stop callback owns NORMAL_FINISH versus
        // NORMAL_NO_FINISH. Do not reset the hand before it or also send an ambiguous legacy stop.
        if (!correlatedStop) ManaNetwork.stopContinuousCastVisual(caster, hand, spell.getRegistryName(), manaExhausted);
        CastingDiagnostics.trace(
                "continuous_interrupted",
                caster,
                spell.getRegistryName(),
                hand,
                0,
                ManaService.getAvailableMana(caster)
        );
    }

    public static boolean hasUsableManaProfile(EntityPlayer caster) {
        return caster.world.isRemote
                ? !JustAnotherWizardryManaSystemMod.proxy.isClientManaProfileHeld()
                : ManaService.canUseManaProfile(caster);
    }

    public static int getManaForContinuousFinish(
            int wizardryWandMana,
            EntityPlayer caster,
            Spell spell,
            EnumHand hand
    ) {
        if (!spell.isContinuous) {
            return wizardryWandMana;
        }
        if (caster.world.isRemote || caster.isCreative()) {
            boolean manaExhausted = caster.world.isRemote
                    && JustAnotherWizardryManaSystemMod.proxy
                    .isClientContinuousFinishSuppressed(spell, hand);
            return ContinuousFinishPolicy.affordability(
                    caster.isCreative(),
                    true,
                    manaExhausted
            );
        }
        ManaState state = ManaService.requireState(caster);
        return ContinuousFinishPolicy.affordability(
                false,
                state.wasContinuousCastSuccessful(),
                state.wasContinuousCastManaExhausted()
        );
    }

    public static void finishContinuousCast(EntityPlayer caster, Spell spell, EnumHand hand) {
        CastCostContextManager.clear(caster, hand);
        CastPaymentContextManager.clear(caster, hand);
        if (caster.world.isRemote || !spell.isContinuous || !ManaService.canUseManaProfile(caster)) {
            return;
        }
        boolean lockoutStarted = ManaService.finishContinuousCast(caster);
        CastingDiagnostics.trace(
                lockoutStarted ? "continuous_finished" : "continuous_failed_start",
                caster,
                spell.getRegistryName(),
                hand,
                0,
                ManaService.getAvailableMana(caster)
        );
    }

    public static void reconcileContinuousCast(EntityPlayer caster) {
        if (caster.world.isRemote) {
            return;
        }
        ManaState state = ManaService.requireState(caster);
        if (!state.isContinuousCastActive()) {
            return;
        }
        if (!caster.isHandActive()) {
            ManaService.finishContinuousCast(caster);
            CastCostContextManager.clearAll(caster);
            CastPaymentContextManager.clearAll(caster);
            return;
        }
        EnumHand hand = caster.getActiveHand();
        ItemStack stack = caster.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemWand)) {
            ManaService.finishContinuousCast(caster);
            CastCostContextManager.clearAll(caster);
            CastPaymentContextManager.clearAll(caster);
            return;
        }
        Spell spell = WandHelper.getCurrentSpell(stack);
        if (!spell.isContinuous || !state.matchesContinuousCast(hand, spell.getRegistryName())) {
            ManaService.finishContinuousCast(caster);
            CastCostContextManager.clearAll(caster);
            CastPaymentContextManager.clearAll(caster);
        }
    }

    public static int captureAttemptDepth() {
        return ATTEMPTS.get().size();
    }

    public static void capturePendingForceArrow(
            EntityForceArrow arrow, EntityLivingBase caster
    ) {
        if (!(caster instanceof EntityPlayerMP) || caster.world.isRemote
                || arrow.getCaster() != caster) {
            return;
        }
        CastAttempt attempt = ATTEMPTS.get().peek();
        EntityPlayerMP player = (EntityPlayerMP) caster;
        if (attempt == null
                || attempt.owner != player
                || !equals(attempt.spellId, Spells.force_arrow.getRegistryName())) {
            return;
        }
        UUID projectileId = ForceArrowRefundTracker.capturePending(
                arrow, player, attempt.perfectionistPolicy
        );
        attempt.pendingForceArrows.add(projectileId);
    }

    public static void cleanupToDepth(EntityPlayer caster, int depth) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        Throwable failure = null;
        try {
            while (attempts.size() > depth) {
                CastAttempt attempt = attempts.pop();
                try {
                    try { discardPendingForceArrows(attempt); }
                    finally { CastPaymentService.release(attempt.owner, attempt.reservation); }
                    if (attempt.coordinated && attempt.reservation.isCurrentGeneration()
                            && attempts.stream().noneMatch(parent -> parent.owner == attempt.owner
                                    && parent.reservation.sharesGeneration(attempt.reservation))) {
                        CastCostCoordinator.runGuarded(() -> CastPaymentService.flushPendingProfileEvaluation(
                                attempt.owner, attempt.reservation));
                    }
                    traceAttempt("reservation_cleanup", attempt.owner, attempt);
                } catch (RuntimeException | Error fault) {
                    if (failure == null) failure = fault;
                    else if (fault != failure) failure.addSuppressed(fault);
                }
            }
        } finally {
            if (attempts.isEmpty()) ATTEMPTS.remove();
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (RuntimeException) failure;
    }

    private static void commitTopAttempt(EntityPlayer caster, Spell spell, boolean required) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        CastAttempt attempt = attempts.peek();
        if (attempt != null && attempt.coordinated) {
            // The enclosing cast may visit both deduction hooks after coordination.
            // Keep the marker until its finally so neither hook can debit the parent.
            return;
        }
        if (attempt == null) {
            if (required) {
                throw new IllegalStateException(
                        "Successful Wizardry wand deduction had no JAWMS reservation."
                );
            }
            return;
        }
        ResourceLocation spellId = spell.getRegistryName();
        if (attempt.owner != caster || !equals(attempt.spellId, spellId)) {
            throw new IllegalStateException(
                    "JAWMS reservation spell mismatch: expected "
                            + attempt.spellId + " but received " + spellId + "."
            );
        }

        if (spell.isContinuous) {
            CastPaymentService.commit(
                    caster, attempt.reservation, true, attempt.hand, attempt.spellId
            );
        } else {
            CastPaymentService.commit(
                    caster, attempt.reservation, false, attempt.hand, attempt.spellId
            );
        }
        promotePendingForceArrows(caster, attempt);
        attempts.pop();
        traceAttempt("reservation_committed", caster, attempt);
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
    }

    private static void promotePendingForceArrows(EntityPlayer caster, CastAttempt attempt) {
        if (caster instanceof EntityPlayerMP) {
            for (UUID projectileId : attempt.pendingForceArrows) {
                ForceArrowRefundTracker.promote(
                        projectileId,
                        (EntityPlayerMP) caster,
                        attempt.reservation.getPaidMana()
                );
            }
        } else {
            discardPendingForceArrows(attempt);
        }
        attempt.pendingForceArrows.clear();
    }

    private static void releaseTopAttempt(
            EntityPlayer caster,
            CastAttempt expected,
            String event
    ) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        if (attempts.peek() != expected) {
            throw new IllegalStateException("JAWMS wand reservation stack was corrupted.");
        }
        attempts.pop();
        discardPendingForceArrows(expected);
        CastPaymentService.release(caster, expected.reservation);
        traceAttempt(event, caster, expected);
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
    }

    private static void notifyInsufficient(
            EntityPlayer caster,
            Spell spell,
            EnumHand hand,
            CastPaymentPlan plan
    ) {
        int required = plan.getCost();
        int available = ManaService.getAvailableMana(caster);
        if (ManaService.shouldNotifyInsufficient(
                caster,
                caster.world.getTotalWorldTime(),
                required,
                available
        )) {
            TextComponentTranslation message = plan.wasSealConsidered()
                    ? new TextComponentTranslation(
                            "message.jawms.insufficient_seal_payment",
                            required,
                            available,
                            plan.getUsableHunger()
                    )
                    : new TextComponentTranslation(
                            "message.jawms.insufficient_mana",
                            required,
                            available
                    );
            caster.sendStatusMessage(message, true);
        }
    }

    private static DemonicSealPolicy paymentPolicy(
            EntityPlayer caster, EnumHand hand
    ) {
        return CastPaymentContextManager.captureIfAbsent(
                caster, hand, integrationRules(caster)
        );
    }

    private static PerfectionistPolicy perfectionistPolicy(
            EntityPlayer caster, Spell spell
    ) {
        WizardryIntegrationRules rules = integrationRules(caster);
        if (spell != Spells.force_arrow || !(caster instanceof EntityPlayerMP)) {
            return PerfectionistPolicy.inactive(rules.getRevision());
        }
        boolean active = WizardryItems.ring_mana_return instanceof ItemArtefact
                && ItemArtefact.isArtefactActive(
                        caster, WizardryItems.ring_mana_return
                );
        return PerfectionistPolicy.capture(rules, active);
    }

    private static void discardPendingForceArrows(CastAttempt attempt) {
        for (UUID projectileId : attempt.pendingForceArrows) {
            ForceArrowRefundTracker.discardPending(
                    projectileId, ForceArrowRefundTracker.Terminal.FAILED_CAST
            );
        }
    }

    private static WizardryIntegrationRules integrationRules(EntityPlayer caster) {
        if (!caster.world.isRemote) {
            return GameplayRulesManager.current().getWizardryIntegrationRules();
        }
        WizardryIntegrationRules rules = JustAnotherWizardryManaSystemMod.proxy
                .getClientWizardryIntegrationRules();
        return rules == null ? WizardryIntegrationRules.defaults(0L) : rules;
    }

    private static boolean equals(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int costForAttempt(
            EntityPlayer caster,
            EnumHand hand,
            Spell spell,
            SpellModifiers modifiers,
            int castingTick
    ) {
        int calculated = caster.world.isRemote
                ? JustAnotherWizardryManaSystemMod.proxy.getClientFinalSpellCost(
                        spell, modifiers, SpellCostService.getFinalCost(spell, modifiers))
                : SpellCostService.getFinalCost(caster, spell, modifiers);
        int cost = CastCostContextManager.captureOrGet(caster, hand, spell, calculated);
        return spell.isContinuous
                ? ContinuousSpellCost.portionAtTick(cost, castingTick)
                : cost;
    }

    private static void traceAttempt(
            String event,
            EntityPlayer caster,
            CastAttempt attempt
    ) {
        if (attempt.trace) {
            CastingDiagnostics.trace(
                    event,
                    caster,
                    attempt.spellId,
                    attempt.hand,
                    attempt.reservation.getPlan().getCost(),
                    ManaService.getAvailableMana(caster)
            );
        }
    }

    private static final class CastAttempt {

        private final CastPaymentReservation reservation;
        private final ResourceLocation spellId;
        private final EnumHand hand;
        private final boolean trace;
        private final EntityPlayer owner;
        private final PerfectionistPolicy perfectionistPolicy;
        private final List<UUID> pendingForceArrows = new ArrayList<>();
        private boolean coordinated;

        private CastAttempt(
                CastPaymentReservation reservation,
                ResourceLocation spellId,
                EnumHand hand,
                boolean trace,
                EntityPlayer owner,
                PerfectionistPolicy perfectionistPolicy
        ) {
            this.reservation = reservation;
            this.spellId = spellId;
            this.hand = hand;
            this.trace = trace;
            this.owner = owner;
            this.perfectionistPolicy = perfectionistPolicy;
        }
    }
}
