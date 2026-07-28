package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.mana.ManaReservation;
import com.mahghuuuls.jawms.internal.mana.ManaNetwork;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.Deque;

public final class WizardryWandBridge {

    private static final ThreadLocal<Deque<CastAttempt>> ATTEMPTS =
            ThreadLocal.withInitial(ArrayDeque::new);

    private WizardryWandBridge() {
    }

    public static int replaceCalculatedCost(
            int wizardryCost,
            Spell spell,
            EntityPlayer caster,
            SpellModifiers modifiers
    ) {
        if (caster.world.isRemote) {
            return JustAnotherWizardryManaSystemMod.proxy.getClientFinalSpellCost(
                    spell,
                    modifiers,
                    wizardryCost
            );
        }
        return SpellCostService.getFinalCost(spell, modifiers);
    }

    public static int getAvailableManaForPrecheck(
            int wizardryWandMana,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        if (caster.world.isRemote
                && JustAnotherWizardryManaSystemMod.proxy
                .isClientContinuousCastBlocked(spell, hand)) {
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
            int required = spell.isContinuous
                    ? ContinuousSpellCost.portionAtTick(
                            synchronizedCost,
                            castingTick
                    )
                    : synchronizedCost;
            if (spell.isContinuous && required > available) {
                JustAnotherWizardryManaSystemMod.proxy.blockClientContinuousCast(
                        spell,
                        hand,
                        true
                );
            }
            return available;
        }

        if (spell.isContinuous
                && ManaService.isContinuousCastRetryBlocked(
                        caster,
                        hand,
                        spell.getRegistryName()
                )) {
            return -1;
        }
        int required = costForAttempt(spell, modifiers, castingTick);
        int available = ManaService.getAvailableMana(caster);
        if (required > available) {
            if (spell.isContinuous) {
                ManaService.exhaustContinuousCastMana(
                        caster,
                        hand,
                        spell.getRegistryName()
                );
            }
            notifyInsufficient(caster, spell, hand, required, available);
            CastingDiagnostics.trace(
                    "precheck_rejected",
                    caster,
                    spell.getRegistryName(),
                    hand,
                    required,
                    available
            );
        }
        return available;
    }

    public static boolean castWithReservation(
            Spell spell,
            World world,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        if (world.isRemote || caster.isCreative()) {
            return spell.cast(world, caster, hand, castingTick, modifiers);
        }

        int cost = costForAttempt(spell, modifiers, castingTick);
        ManaReservation reservation = ManaService.reserveMana(caster, cost);
        if (reservation == null) {
            int available = ManaService.getAvailableMana(caster);
            notifyInsufficient(caster, spell, hand, cost, available);
            CastingDiagnostics.trace(
                    "reservation_rejected",
                    caster,
                    spell.getRegistryName(),
                    hand,
                    cost,
                    available
            );
            if (spell.isContinuous) {
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
                !spell.isContinuous || cost > 0
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
        if (caster.world.isRemote) {
            return;
        }
        Spell spell = WandHelper.getCurrentSpell(stack);
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
        if (caster.world.isRemote || !spell.isContinuous) {
            return;
        }
        boolean manaExhausted = ManaService.requireState(caster)
                .wasContinuousCastManaExhausted();
        ManaService.interruptContinuousCast(caster, hand, spell.getRegistryName());
        ManaNetwork.stopContinuousCastVisual(
                caster,
                hand,
                spell.getRegistryName(),
                manaExhausted
        );
        CastingDiagnostics.trace(
                "continuous_interrupted",
                caster,
                spell.getRegistryName(),
                hand,
                0,
                ManaService.getAvailableMana(caster)
        );
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
        if (caster.world.isRemote || !spell.isContinuous) {
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
            return;
        }
        EnumHand hand = caster.getActiveHand();
        ItemStack stack = caster.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemWand)) {
            ManaService.finishContinuousCast(caster);
            return;
        }
        Spell spell = WandHelper.getCurrentSpell(stack);
        if (!spell.isContinuous || !state.matchesContinuousCast(hand, spell.getRegistryName())) {
            ManaService.finishContinuousCast(caster);
        }
    }

    public static int captureAttemptDepth() {
        return ATTEMPTS.get().size();
    }

    public static void cleanupToDepth(EntityPlayer caster, int depth) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        while (attempts.size() > depth) {
            CastAttempt attempt = attempts.pop();
            ManaService.releaseReservation(attempt.reservation);
            traceAttempt("reservation_cleanup", caster, attempt);
        }
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
    }

    private static void commitTopAttempt(EntityPlayer caster, Spell spell, boolean required) {
        Deque<CastAttempt> attempts = ATTEMPTS.get();
        CastAttempt attempt = attempts.peek();
        if (attempt == null) {
            if (required) {
                throw new IllegalStateException(
                        "Successful Wizardry wand deduction had no JAWMS reservation."
                );
            }
            return;
        }
        ResourceLocation spellId = spell.getRegistryName();
        if (!equals(attempt.spellId, spellId)) {
            throw new IllegalStateException(
                    "JAWMS reservation spell mismatch: expected "
                            + attempt.spellId + " but received " + spellId + "."
            );
        }

        if (spell.isContinuous) {
            ManaService.completeSuccessfulContinuousPortion(
                    caster,
                    attempt.reservation,
                    attempt.hand,
                    attempt.spellId
            );
        } else {
            ManaService.completeSuccessfulWandCast(caster, attempt.reservation);
        }
        attempts.pop();
        traceAttempt("reservation_committed", caster, attempt);
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
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
        ManaService.releaseReservation(expected.reservation);
        traceAttempt(event, caster, expected);
        if (attempts.isEmpty()) {
            ATTEMPTS.remove();
        }
    }

    private static void notifyInsufficient(
            EntityPlayer caster,
            Spell spell,
            EnumHand hand,
            int required,
            int available
    ) {
        if (ManaService.shouldNotifyInsufficient(
                caster,
                caster.world.getTotalWorldTime(),
                required,
                available
        )) {
            caster.sendStatusMessage(
                    new TextComponentTranslation(
                            "message.jawms.insufficient_mana",
                            required,
                            available
                    ),
                    true
            );
        }
    }

    private static boolean equals(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }

    private static int costForAttempt(
            Spell spell,
            SpellModifiers modifiers,
            int castingTick
    ) {
        int cost = SpellCostService.getFinalCost(spell, modifiers);
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
                    attempt.reservation.getAmount(),
                    ManaService.getAvailableMana(caster)
            );
        }
    }

    private static final class CastAttempt {

        private final ManaReservation reservation;
        private final ResourceLocation spellId;
        private final EnumHand hand;
        private final boolean trace;

        private CastAttempt(
                ManaReservation reservation,
                ResourceLocation spellId,
                EnumHand hand,
                boolean trace
        ) {
            this.reservation = reservation;
            this.spellId = spellId;
            this.hand = hand;
            this.trace = trace;
        }
    }
}
