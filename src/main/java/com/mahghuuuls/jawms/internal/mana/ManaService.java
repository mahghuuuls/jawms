package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentService;
import com.mahghuuuls.jawms.internal.modifier.ManaProfileEvaluation;
import com.mahghuuuls.jawms.internal.modifier.ManaProfileEvaluator;
import electroblob.wizardry.spell.Spell;
import com.mahghuuuls.jawms.internal.casting.CastCostContextManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;

public final class ManaService implements IManaService {

    @Override
    public ManaPublicState getState(EntityPlayer player) {
        ManaState state = requireState(player);
        return state.profileHeld() ? compatibilityView() : snapshot(state);
    }

    @Override
    public int getCurrentMana(EntityPlayer player) {
        return getState(player).getCurrentMana();
    }

    @Override
    public int getMaximumMana(EntityPlayer player) {
        return getState(player).getMaximumMana();
    }

    @Override
    public double getEffectiveRegeneration(EntityPlayer player) {
        return getState(player).getEffectiveRegeneration();
    }

    @Override
    public long getEffectiveRegenerationLockoutTicks(EntityPlayer player) {
        return getState(player).getEffectiveLockoutTicks();
    }

    @Override
    public long getRemainingRegenerationLockoutTicks(EntityPlayer player) {
        return getState(player).getRemainingLockoutTicks();
    }

    @Override
    public boolean isRegenerationEligible(EntityPlayer player) {
        return getState(player).isRegenerationEligible();
    }

    @Override
    public boolean canRegenerateDuringPostCastLockout(EntityPlayer player) {
        return getState(player).canRegenerateDuringPostCastLockout();
    }

    @Override
    public boolean canRegenerateDuringContinuousCasting(EntityPlayer player) {
        return getState(player).canRegenerateDuringContinuousCasting();
    }

    @Override
    public double getApplicableSpellEfficiency(EntityPlayer player, Spell spell) {
        ManaState state = requireState(player);
        requireRegisteredSpell(spell);
        return state.profileHeld() ? 0.0D : state.getSpellEfficiency(spell.getElement());
    }

    @Override
    public double getApplicableSpellCostMultiplier(EntityPlayer player, Spell spell) {
        ManaState state = requireState(player);
        requireRegisteredSpell(spell);
        return state.profileHeld() ? 1.0D : state.getSpellCostMultiplier(spell.getElement());
    }

    private static void requireRegisteredSpell(Spell spell) {
        if (spell == null
                || spell.getRegistryName() == null
                || Spell.registry == null
                || Spell.registry.getValue(spell.getRegistryName()) != spell) {
            throw new IllegalArgumentException("A registered Wizardry spell is required.");
        }
    }

    @Override
    public ManaMutationResult setCurrentMana(
            EntityPlayer player,
            int target,
            ResourceLocation cause
    ) {
        requireNonNegative(target);
        return mutate(player, target, requireCause(cause), (state) -> {
            state.setCurrentMana(target);
            return ManaMutationFailure.NONE;
        });
    }

    @Override
    public ManaMutationResult restoreMana(
            EntityPlayer player,
            int amount,
            ResourceLocation cause
    ) {
        requireNonNegative(amount);
        return mutate(player, amount, requireCause(cause), (state) -> {
            state.restore(amount);
            return ManaMutationFailure.NONE;
        });
    }

    @Override
    public ManaMutationResult consumeMana(
            EntityPlayer player,
            int amount,
            ResourceLocation cause
    ) {
        requireNonNegative(amount);
        return mutate(player, amount, requireCause(cause), (state) ->
                state.tryConsume(amount)
                        ? ManaMutationFailure.NONE
                        : ManaMutationFailure.INSUFFICIENT_MANA
        );
    }

    @Override
    public ManaMutationResult drainMana(
            EntityPlayer player,
            int maximumAmount,
            ResourceLocation cause
    ) {
        requireNonNegative(maximumAmount);
        return mutate(player, maximumAmount, requireCause(cause), (state) -> {
            state.drainUpTo(maximumAmount);
            return ManaMutationFailure.NONE;
        });
    }

    @Override
    public long startRegenerationLockout(
            EntityPlayer player,
            ResourceLocation cause
    ) {
        ResourceLocation checkedCause = requireCause(cause);
        ManaState state = requireServerState(player);
        if (state.profileHeld()) return 0;
        requireMutationAllowed(player);
        long duration = state.getEffectiveLockoutTicks();
        boolean wasActive = state.getRemainingLockoutTicks() > 0L;
        transition(
                player,
                state,
                checkedCause,
                duration > 0L || wasActive,
                () -> state.startRegenerationLockout(duration)
        );
        return duration;
    }

    private static ManaMutationResult mutate(
            EntityPlayer player,
            int requestedAmount,
            ResourceLocation cause,
            StateMutation mutation
    ) {
        ManaState state = requireState(player);
        if (state.profileHeld()) {
            ManaPublicState view = compatibilityView();
            return new ManaMutationResult(ManaMutationFailure.PROFILE_NOT_READY,
                    requestedAmount, 0, cause, view, view);
        }
        requireMutationAllowed(player);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            ManaPublicState oldState = snapshot(state);
            ManaMutationFailure failure = mutation.apply(state);
            ManaPublicState newState = snapshot(state);
            if (!oldState.equals(newState)) {
                synchronizeDisplayChange(player, oldState, newState, state);
                publishTransition(player, oldState, newState, cause, false);
            }
            return new ManaMutationResult(
                    failure,
                    requestedAmount,
                    newState.getCurrentMana() - oldState.getCurrentMana(),
                    cause,
                    oldState,
                    newState
            );
        }
    }

    public static ManaState requireState(EntityPlayer player) {
        ManaState state = requireServerState(player);
        if (!state.profileHeld() && state.hasPendingLoadedMana()
                && !ManaEvaluationGuard.isEvaluating(player.getUniqueID())) {
            ManaProfileEvaluation evaluation = evaluateCurrentProfile(player, state, GameplayRulesManager.current());
            if (evaluation != null) state.applyProfileEvaluation(evaluation);
        }
        return state;
    }

    /** Validates refresh authority without applying pending profile state. */
    public static void requireModifierRefreshContext(EntityPlayer player) {
        requireServerState(player);
    }

    /** Returns the retained accepted explanation without evaluating providers. */
    public static ManaProfileExplanation explainAcceptedProfile(EntityPlayer player) {
        ManaState state = requireServerState(player);
        if (state.profileHeld()) {
            throw new IllegalStateException("No authoritative JAWMS mana-profile evaluation has been accepted yet.");
        }
        return state.explainAcceptedProfile();
    }

    static ManaState requireServerState(EntityPlayer player) {
        if (player == null) {
            throw new NullPointerException("Player must not be null.");
        }
        if (!(player instanceof EntityPlayerMP)
                || !(player.world instanceof WorldServer)
                || player.world.isRemote) {
            throw new IllegalStateException("JAWMS mana API operations require a logical-server player.");
        }
        if (!((WorldServer) player.world).isCallingFromMinecraftThread()) {
            throw new IllegalStateException(
                    "JAWMS mana API operations must run on the Minecraft server thread."
            );
        }
        ManaState state = player.getCapability(ManaCapability.INSTANCE, null);
        if (state == null) {
            throw new IllegalStateException("The player does not have the JAWMS mana capability.");
        }
        return state;
    }

    private static ManaState requireMutableState(EntityPlayer player) {
        ManaState state = requireState(player);
        requireMutationAllowed(player);
        return state;
    }

    private static ManaPublicState compatibilityView() {
        return new ManaPublicState(0, GameplayRulesManager.current().getBaseMaximum(), 0.0D,
                0L, 0L, false, false, false, false);
    }

    public static ManaProfileReadiness getProfileReadiness(EntityPlayer player) {
        ManaState state = requireServerState(player);
        if (state.checkingDeath) return ManaProfileReadiness.PENDING;
        ManaReadiness readiness = state.readiness;
        return readiness == null || (!readiness.held() && !readiness.hasEligibleProvider())
                ? ManaProfileReadiness.LEGACY : readiness.phase();
    }

    public static boolean canUseManaProfile(EntityPlayer player) {
        return !requireServerState(player).profileHeld();
    }

    static void configureReadiness(EntityPlayer player, ManaState state, GameplayRules rules) {
        if (!(player instanceof EntityPlayerMP) || !(player.world instanceof WorldServer)
                || player instanceof net.minecraftforge.common.util.FakePlayer) return;
        Set<ResourceLocation> eligible = ManaProviderRegistry.instance().eligibleReadinessProviders(rules);
        ManaLifecycle.applyTransferSourcePolicy(state, eligible);
        if (state.readiness != null) {
            state.readiness.applyPolicy(eligible);
        } else if (!eligible.isEmpty()) {
            state.readiness = new ManaReadiness(eligible, player.world.getMinecraftServer().getTickCounter(),
                    state.hasAcceptedProfile());
        }
    }

    public static void bindLogin(EntityPlayerMP player) {
        ManaState state = requireServerState(player);
        configureReadiness(player, state, GameplayRulesManager.current());
        if (state.readiness != null) state.readiness.bound = true;
    }

    public static void finishLogin(EntityPlayerMP player) {
        ManaReadiness readiness = requireServerState(player).readiness;
        if (readiness != null) {
            readiness.world = player.getServerWorld();
            readiness.complete = true;
        }
    }

    public static void retireLogout(EntityPlayerMP player) {
        ManaLifecycle.retire(player);
    }

    private static Object publicationContext(EntityPlayer player, ManaState state) {
        return ManaLifecycle.publicationWorld((EntityPlayerMP) player, state.readiness);
    }

    public static Optional<ManaProfileGeneration> getReadinessGeneration(EntityPlayer player,
            ResourceLocation id, Supplier<ManaProfileGeneration> create) {
        ManaState state = requireServerState(player);
        return state.readiness == null || state.checkingDeath ? Optional.empty()
                : state.readiness.generation(id, publicationContext(player, state), create);
    }

    public static ManaContribution readinessContribution(EntityPlayer player, ResourceLocation id) {
        ManaState state = requireServerState(player);
        return state.readiness == null ? null : state.readiness.contribution(id);
    }

    public static ManaContributionPublicationResult publishReadiness(EntityPlayer player, ResourceLocation id,
            ManaProfileGeneration generation, ManaContribution contribution, boolean initial, long revision) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(contribution, "contribution");
        if (!initial && revision < 1) throw new IllegalArgumentException("Expected a positive revision.");
        ManaState state = requireServerState(player);
        if (state.readiness == null) return ManaReadiness.rejection(ManaContributionPublicationStatus.INAPPLICABLE);
        if (state.checkingDeath || ManaEvaluationGuard.isEvaluating(player.getUniqueID())
                || ManaMutationCoordinator.currentDepth(player.getUniqueID()) > 0) {
            return ManaReadiness.rejection(ManaContributionPublicationStatus.BUSY);
        }
        ManaContributionPublicationResult result = state.readiness.publish(id, publicationContext(player, state),
                generation, contribution, initial, revision);
        if (result.getRevision().isPresent() && !state.profileHeld()) {
            try (ManaMutationCoordinator.Scope ignored = ManaMutationCoordinator.enter(player.getUniqueID())) {
                ManaPublicState before = snapshot(state);
                ManaProfileEvaluation evaluation = evaluateCurrentProfile(player, state, GameplayRulesManager.current());
                if (evaluation == null) return result;
                boolean profileChanged = state.applyProfileEvaluation(evaluation);
                ManaPublicState after = snapshot(state);
                if (!before.equals(after)) {
                    synchronizeDisplayChange(player, before, after, state);
                    publishTransition(player, before, after, ManaCauses.STAT_CLAMP, false);
                } else if (profileChanged) {
                    ManaNetwork.synchronize(player, state);
                }
            }
        }
        return result;
    }

    private static void tickHeldProfile(EntityPlayerMP player, ManaState state, GameplayRules rules) {
        if (state.checkingDeath) return;
        if (state.recoveringTransfer) ManaLifecycle.recoverTransfer(player, state, rules);
        ManaReadiness readiness = state.readiness;
        if (readiness.isRetired()) return;
        int tick = player.getServer().getTickCounter();
        List<ResourceLocation> missing = readiness.missing();
        if (missing.isEmpty() && ManaLifecycle.canAccept(player, readiness)) {
            ManaProfileEvaluation evaluation = evaluateCurrentProfile(player, state, rules);
            if (evaluation == null || !readiness.missing().isEmpty() || !ManaLifecycle.canAccept(player, readiness)) return;
            boolean recovering = readiness.phase() == ManaProfileReadiness.UNAVAILABLE;
            Integer input = state.acceptFirstProfile(evaluation);
            if (Boolean.getBoolean("jawms.debugManaReadiness")) {
                JustAnotherWizardryManaSystemMod.LOGGER.info(
                        "MANA_PROFILE_ACCEPTED player={} generation={} lifecycle={} path=PLAYER_END_TICK input={} current={} maximum={} rulesRevision={} recovery={}",
                        player.getUniqueID(), readiness.identity, readiness.lifecycle, input, state.getCurrentMana(),
                        state.getMaximumMana(), rules.getRevision(), recovering);
            }
            if (recovering) {
                JustAnotherWizardryManaSystemMod.LOGGER.info("MANA_PROFILE_RECOVERED player={} generation={}",
                        player.getUniqueID(), readiness.identity);
            }
            ManaNetwork.synchronize(player, state);
        } else if (readiness.deadlineElapsed(tick)) {
            readiness.unavailable();
            JustAnotherWizardryManaSystemMod.LOGGER.warn(
                    "MANA_PROFILE_UNAVAILABLE player={} generation={} lifecycle={} missingProviders={}",
                    player.getUniqueID(), readiness.identity, readiness.lifecycle, missing);
            ManaNetwork.synchronize(player, state);
        }
    }

    private static void requireMutationAllowed(EntityPlayer player) {
        if (ManaEvaluationGuard.isEvaluating(player.getUniqueID())) {
            throw new IllegalStateException(
                    "JAWMS mana cannot be mutated during modifier-provider evaluation."
            );
        }
    }

    public static int getAvailableMana(EntityPlayer player) {
        ManaState state = requireState(player);
        return state.profileHeld() ? 0 : state.getAvailableMana();
    }

    public static ManaReservation reserveMana(EntityPlayer player, int amount) {
        ManaState state = requireMutableState(player);
        return state.profileHeld() ? null : state.reserve(amount);
    }

    public static void releaseReservation(ManaReservation reservation) {
        if (reservation != null && reservation.isActive()) {
            reservation.ownerRelease();
        }
    }

    public static void completeSuccessfulWandCast(EntityPlayer player, ManaReservation reservation) {
        ManaState state = requireMutableState(player);
        if (state.profileHeld() || reservation == null || !reservation.belongsTo(state)) {
            throw new IllegalStateException("Successful wand cast has no matching mana reservation.");
        }
        transition(player, state, ManaCauses.SPELL_COST, false,
                () -> state.commit(reservation));
        startRegenerationLockout(player, state);
    }

    public static void completeSuccessfulContinuousPortion(
            EntityPlayer player,
            ManaReservation reservation,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaState state = requireMutableState(player);
        if (state.profileHeld() || reservation == null || !reservation.belongsTo(state)) {
            throw new IllegalStateException(
                    "Successful continuous cast has no matching mana reservation."
            );
        }
        transition(player, state, ManaCauses.SPELL_COST, false,
                () -> state.commit(reservation));
        state.markContinuousCastSuccessful(hand, spellId);
    }

    public static void completeSuccessfulCastPayment(
            EntityPlayer player,
            ManaReservation reservation,
            int hunger,
            int minimumHunger,
            boolean continuous,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireNonNegative(hunger);
        requireNonNegative(minimumHunger);
        if (minimumHunger > 20) {
            throw new IllegalArgumentException("Minimum hunger must not exceed 20.");
        }
        ManaState state = requireMutableState(player);
        if (state.profileHeld() || reservation == null
                || !reservation.belongsTo(state)
                || !reservation.isActive()) {
            throw new IllegalStateException(
                    "Successful cast has no matching active mana reservation."
            );
        }
        int currentHunger = player.getFoodStats().getFoodLevel();
        if (currentHunger - hunger < minimumHunger) {
            throw new IllegalStateException(
                    "Successful cast payment would cross its captured hunger floor."
            );
        }
        transition(player, state, ManaCauses.SPELL_COST, false, () -> {
            state.commit(reservation);
            if (hunger > 0) {
                player.getFoodStats().addStats(-hunger, 0.0F);
            }
        });
        if (continuous) {
            state.markContinuousCastSuccessful(hand, spellId);
        } else {
            startRegenerationLockout(player, state);
        }
    }

    /** Participating debit: resolve the local receipt inside mutation, before observers. */
    public static void commitParticipatingCastPayment(EntityPlayer player,
            ManaReservation reservation, int hunger, int minimumHunger, Runnable finalizeReceipt) {
        requireNonNegative(hunger);
        requireNonNegative(minimumHunger);
        ManaState state = requireMutableState(player);
        if (!isReservationValid(player, reservation)
                || (hunger > 0 && player.getFoodStats().getFoodLevel() - hunger < minimumHunger)) {
            throw new IllegalStateException("Participating payment is no longer valid.");
        }
        transition(player, state, ManaCauses.SPELL_COST, false, true, () -> {
            state.commit(reservation);
            if (hunger > 0) player.getFoodStats().addStats(-hunger, 0.0F);
            finalizeReceipt.run();
        });
    }

    public static boolean isReservationValid(EntityPlayer player, ManaReservation reservation) {
        ManaState state = requireServerState(player);
        return !state.profileHeld() && reservation != null && reservation.belongsTo(state) && reservation.isActive()
                && state.hasPayableReservation(reservation);
    }

    /** Success markers belong to aggregate acceptance, not merely the first debit. */
    public static void completeParticipatingCast(EntityPlayer player, boolean continuous,
            EnumHand hand, ResourceLocation spellId) {
        ManaState state = requireMutableState(player);
        if (state.profileHeld()) return;
        if (continuous && !player.isCreative()) state.markContinuousCastSuccessful(hand, spellId);
        else if (!player.isCreative()) startRegenerationLockout(player, state, true);
    }

    public static void beginContinuousCast(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaState state = requireMutableState(player);
        transition(
                player,
                state,
                ManaCauses.CONTINUOUS_START,
                false,
                () -> state.beginContinuousCast(hand, spellId)
        );
    }

    public static void interruptContinuousCast(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireMutableState(player).interruptContinuousCast(hand, spellId);
    }

    public static void exhaustContinuousCastMana(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireMutableState(player).exhaustContinuousCastMana(hand, spellId);
    }

    public static void blockContinuousCastRetry(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireMutableState(player).blockContinuousCastRetry(hand, spellId);
    }

    public static boolean isContinuousCastRetryBlocked(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        return requireState(player).isContinuousCastRetryBlocked(hand, spellId);
    }

    public static void releaseContinuousCastRetryBlock(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireMutableState(player).releaseContinuousCastRetryBlock(hand, spellId);
    }

    public static boolean finishContinuousCast(EntityPlayer player) {
        ManaState state = requireMutableState(player);
        boolean wasActive = state.isContinuousCastActive();
        boolean[] lockoutStarted = new boolean[1];
        transition(player, state, ManaCauses.CONTINUOUS_END, wasActive,
                () -> lockoutStarted[0] = state.finishContinuousCast());
        if (lockoutStarted[0]) {
            startRegenerationLockout(player, state);
        }
        return lockoutStarted[0];
    }

    public static void clearTransientLifecycleState(EntityPlayer player) {
        ManaState state = requireMutableState(player);
        boolean force = state.isContinuousCastActive() || state.getRemainingLockoutTicks() > 0L;
        try {
            if (state.profileHeld()) {
                state.clearTransientLifecycleState();
            } else {
                transition(player, state, ManaCauses.LIFECYCLE, force, state::clearTransientLifecycleState);
            }
        } finally {
            clearLifecycleContexts(player);
        }
    }

    static void clearLifecycleContexts(EntityPlayer player) {
        ManaMutationCoordinator.clear(player.getUniqueID());
        CastCostContextManager.clearAll(player);
        CastPaymentContextManager.clearAll(player);
        CastPaymentService.clear(player);
    }

    public static void copyForClone(EntityPlayer replacementPlayer, ManaState original) {
        if (original == null) {
            throw new NullPointerException("Original mana state must not be null.");
        }
        ManaState replacement = requireServerState(replacementPlayer);
        if (replacement.readiness != null || original.readiness != null) {
            if (replacement.readiness == null) {
                ManaLifecycle.armReplacement((EntityPlayerMP) replacementPlayer, replacement);
            }
            replacement.copyIntrinsicMana(original);
            return;
        }
        replacement = requireMutableState(replacementPlayer);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(replacementPlayer.getUniqueID())) {
            ManaPublicState oldState = snapshot(original);
            replacement.copyFrom(original);
            ManaPublicState newState = snapshot(replacement);
            if (!oldState.equals(newState)) {
                boolean transientStateReset = oldState.getRemainingLockoutTicks() > 0L
                        || oldState.isContinuousCastActive();
                synchronizeDisplayChange(replacementPlayer, oldState, newState, replacement);
                publishTransition(
                        replacementPlayer,
                        oldState,
                        newState,
                        ManaCauses.LIFECYCLE,
                        transientStateReset
                );
            }
        }
    }

    /** Accept a profile deferred by a protected cast at its cleanup boundary. */
    public static void flushPendingProfileEvaluation(EntityPlayer player, ManaReservation reservation) {
        ManaState state;
        try {
            state = requireServerState(player);
        } catch (IllegalStateException invalidLifecycle) {
            return;
        }
        if (state.profileHeld() || !reservation.matchesGeneration(state) || !state.hasPendingProfileEvaluation()) return;
        transition(player, state, ManaCauses.STAT_CLAMP, false, state::applyPendingProfileEvaluation);
    }

    public static void tickPlayer(EntityPlayer player, GameplayRules rules) {
        ManaState state = requireMutableState(player);
        if (state.profileHeld()) {
            tickHeldProfile((EntityPlayerMP) player, state, rules);
            return;
        }
        ManaPublicState oldState = snapshot(state);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            ManaProfileEvaluation evaluation = evaluateCurrentProfile(player, state, rules);
            if (evaluation == null) return;
            boolean profileChanged = state.applyProfileEvaluation(evaluation);
            boolean lockoutEnded = state.getRemainingLockoutTicks() > 0L;
            boolean gateOpen = state.advanceRegenerationGate();
            lockoutEnded = lockoutEnded && state.getRemainingLockoutTicks() == 0L;
            int restored = gateOpen
                    ? state.regenerateTick(state.getEffectiveRegeneration())
                    : 0;
            ManaPublicState newState = snapshot(state);
            if (!oldState.equals(newState)) {
                synchronizeDisplayChange(player, oldState, newState, state);
                ResourceLocation cause = restored > 0
                        ? ManaCauses.REGENERATION
                        : lockoutEnded ? ManaCauses.LOCKOUT_END : ManaCauses.STAT_CLAMP;
                publishTransition(player, oldState, newState, cause, lockoutEnded);
            } else if (profileChanged) {
                ManaNetwork.synchronize(player, state);
            }
        }
    }

    public static void applyRulesReload(
            EntityPlayer player,
            GameplayRules previous,
            GameplayRules replacement
    ) {
        ManaState state = requireServerState(player);
        boolean wasLegacy = state.readiness == null;
        configureReadiness(player, state, replacement);
        if (state.checkingDeath || state.transfer != null && !state.transfer.mutated) {
            state.deferProfileReload();
            return;
        }
        if (wasLegacy && state.readiness != null
                && ManaLifecycle.isCurrent((EntityPlayerMP) player, (WorldServer) player.world)) {
            state.readiness.bound = true;
            state.readiness.complete = true;
            state.readiness.world = (WorldServer) player.world;
        }
        if (state.profileHeld()) {
            ManaNetwork.synchronize(player, state);
            return;
        }
        state = requireMutableState(player);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            ManaPublicState oldState = snapshot(state);
            boolean hadLockout = state.getRemainingLockoutTicks() > 0L;
            ManaProfileEvaluation replacementEvaluation = evaluateCurrentProfile(player, state, replacement);
            if (replacementEvaluation == null) return;
            boolean profileChanged = state.applyReloadProfileEvaluation(replacementEvaluation);
            ManaPublicState newState = snapshot(state);
            boolean lockoutEnded = hadLockout && newState.getRemainingLockoutTicks() == 0L;
            if (!oldState.equals(newState)) {
                synchronizeDisplayChange(player, oldState, newState, state);
                publishTransition(
                        player,
                        oldState,
                        newState,
                        ManaCauses.RULES_UPDATE,
                        lockoutEnded
                );
            } else if (profileChanged) {
                ManaNetwork.synchronize(player, state);
            }
        }
    }

    public static boolean shouldNotifyInsufficient(
            EntityPlayer player,
            long currentTick,
            int required,
            int available
    ) {
        return requireState(player).shouldNotifyInsufficient(
                currentTick,
                required,
                available
        );
    }

    static ManaPublicState snapshot(ManaState state) {
        return new ManaPublicState(
                state.getCurrentMana(),
                state.getMaximumMana(),
                state.getEffectiveRegeneration(),
                state.getEffectiveLockoutTicks(),
                state.getRemainingLockoutTicks(),
                state.canRegenerateDuringPostCastLockout(),
                state.canRegenerateDuringContinuousCasting(),
                state.isContinuousCastActive(),
                state.isRegenerationEligible()
        );
    }

    private static void startRegenerationLockout(EntityPlayer player, ManaState state) {
        startRegenerationLockout(player, state, false);
    }

    private static void startRegenerationLockout(EntityPlayer player, ManaState state, boolean propagateObserverFault) {
        long duration = state.getEffectiveLockoutTicks();
        boolean restart = state.getRemainingLockoutTicks() > 0L;
        transition(
                player,
                state,
                restart ? ManaCauses.LOCKOUT_RESTART : ManaCauses.LOCKOUT_START,
                duration > 0L, propagateObserverFault,
                () -> state.startRegenerationLockout(duration)
        );
    }

    private static void transition(
            EntityPlayer player,
            ManaState state,
            ResourceLocation cause,
            boolean forceEvent,
            Runnable mutation
    ) {
        transition(player, state, cause, forceEvent, false, mutation);
    }

    private static void transition(EntityPlayer player, ManaState state, ResourceLocation cause,
            boolean forceEvent, boolean propagateObserverFault, Runnable mutation) {
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            ManaPublicState oldState = snapshot(state);
            mutation.run();
            ManaPublicState newState = snapshot(state);
            if (!oldState.equals(newState) || forceEvent) {
                synchronizeDisplayChange(player, oldState, newState, state);
                publishTransition(player, oldState, newState, cause, forceEvent, propagateObserverFault);
            }
        }
    }

    private static void publishTransition(
            EntityPlayer player,
            ManaPublicState oldState,
            ManaPublicState newState,
            ResourceLocation cause,
            boolean forceEvent
    ) {
        publishTransition(player, oldState, newState, cause, forceEvent, false);
    }

    private static void publishTransition(EntityPlayer player, ManaPublicState oldState,
            ManaPublicState newState, ResourceLocation cause, boolean forceEvent, boolean propagateObserverFault) {
        if (!forceEvent && !hasMeaningfulChange(oldState, newState)) {
            return;
        }
        try {
            MinecraftForge.EVENT_BUS.post(
                    new ManaStateChangeEvent(player, oldState, newState, cause)
            );
        } catch (RuntimeException exception) {
            JustAnotherWizardryManaSystemMod.LOGGER.error(
                    "A JAWMS mana-state listener failed after committed transition: "
                            + "player={}, cause={}, old={}, new={}.",
                    player.getUniqueID(),
                    cause,
                    oldState,
                    newState,
                    exception
            );
            if (propagateObserverFault) throw exception;
        }
    }

    private static boolean hasMeaningfulChange(
            ManaPublicState oldState,
            ManaPublicState newState
    ) {
        return oldState.getCurrentMana() != newState.getCurrentMana()
                || oldState.getMaximumMana() != newState.getMaximumMana()
                || Double.compare(
                        oldState.getEffectiveRegeneration(),
                        newState.getEffectiveRegeneration()
                ) != 0
                || oldState.getEffectiveLockoutTicks() != newState.getEffectiveLockoutTicks()
                || oldState.canRegenerateDuringPostCastLockout()
                != newState.canRegenerateDuringPostCastLockout()
                || oldState.canRegenerateDuringContinuousCasting()
                != newState.canRegenerateDuringContinuousCasting()
                || oldState.isContinuousCastActive() != newState.isContinuousCastActive()
                || oldState.isRegenerationEligible() != newState.isRegenerationEligible();
    }

    private static void synchronizeDisplayChange(
            EntityPlayer player,
            ManaPublicState oldState,
            ManaPublicState newState,
            ManaState state
    ) {
        if (oldState.getCurrentMana() != newState.getCurrentMana()
                || oldState.getMaximumMana() != newState.getMaximumMana()
                || Double.compare(
                        oldState.getEffectiveRegeneration(),
                        newState.getEffectiveRegeneration()
                ) != 0) {
            ManaNetwork.synchronize(player, state);
        }
    }

    private static ResourceLocation requireCause(ResourceLocation cause) {
        if (cause == null) {
            throw new NullPointerException("Operation cause must not be null.");
        }
        return cause;
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Mana amount must not be negative.");
        }
    }

    private static ManaProfileEvaluation evaluateCurrentProfile(
            EntityPlayer player, ManaState state, GameplayRules rules
    ) {
        ManaReadiness readiness = state.readiness;
        GameplayRules activeRules = GameplayRulesManager.current();
        ManaProfileEvaluation evaluation = ManaProfileEvaluator.evaluate(player, rules);
        // A provider can synchronously invoke a native lifecycle transition during evaluation.
        if (state.readiness != readiness || state.checkingDeath) return null;
        if (readiness != null && (readiness.isRetired() || activeRules != GameplayRulesManager.current()
                || !ManaLifecycle.isCurrent((EntityPlayerMP) player, readiness.world))) return null;
        return evaluation;
    }

    private interface StateMutation {
        ManaMutationFailure apply(ManaState state);
    }
}
