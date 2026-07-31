package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import electroblob.wizardry.spell.Spell;
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
        return snapshot(state, GameplayRulesManager.current().getRegenerationLockoutTicks());
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
        requireState(player);
        if (spell == null
                || spell.getRegistryName() == null
                || Spell.registry == null
                || Spell.registry.getValue(spell.getRegistryName()) != spell) {
            throw new IllegalArgumentException("A registered Wizardry spell is required.");
        }
        return 0.0D;
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
            return true;
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
            return true;
        });
    }

    @Override
    public ManaMutationResult consumeMana(
            EntityPlayer player,
            int amount,
            ResourceLocation cause
    ) {
        requireNonNegative(amount);
        return mutate(player, amount, requireCause(cause), (state) -> state.tryConsume(amount));
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
            return true;
        });
    }

    private static ManaMutationResult mutate(
            EntityPlayer player,
            int requestedAmount,
            ResourceLocation cause,
            StateMutation mutation
    ) {
        ManaState state = requireMutableState(player);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            ManaPublicState oldState = snapshot(
                    state,
                    GameplayRulesManager.current().getRegenerationLockoutTicks()
            );
            boolean successful = mutation.apply(state);
            ManaPublicState newState = snapshot(
                    state,
                    GameplayRulesManager.current().getRegenerationLockoutTicks()
            );
            if (!oldState.equals(newState)) {
                synchronizeDisplayChange(player, oldState, newState, state);
                publishTransition(player, oldState, newState, cause, false);
            }
            return new ManaMutationResult(
                    successful,
                    requestedAmount,
                    newState.getCurrentMana() - oldState.getCurrentMana(),
                    cause,
                    oldState,
                    newState
            );
        }
    }

    public static ManaState requireState(EntityPlayer player) {
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
        if (ManaEvaluationGuard.isEvaluating(player.getUniqueID())) {
            throw new IllegalStateException(
                    "JAWMS mana cannot be mutated during modifier-provider evaluation."
            );
        }
        return state;
    }

    public static int getAvailableMana(EntityPlayer player) {
        return requireState(player).getAvailableMana();
    }

    public static ManaReservation reserveMana(EntityPlayer player, int amount) {
        return requireMutableState(player).reserve(amount);
    }

    public static void releaseReservation(ManaReservation reservation) {
        if (reservation != null && reservation.isActive()) {
            reservation.ownerRelease();
        }
    }

    public static void completeSuccessfulWandCast(EntityPlayer player, ManaReservation reservation) {
        ManaState state = requireMutableState(player);
        if (reservation == null || !reservation.belongsTo(state)) {
            throw new IllegalStateException("Successful wand cast has no matching mana reservation.");
        }
        transition(player, ManaCauses.SPELL_COST, false, () -> state.commit(reservation));
        startRegenerationLockout(player, state);
    }

    public static void completeSuccessfulContinuousPortion(
            EntityPlayer player,
            ManaReservation reservation,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaState state = requireMutableState(player);
        if (reservation == null || !reservation.belongsTo(state)) {
            throw new IllegalStateException(
                    "Successful continuous cast has no matching mana reservation."
            );
        }
        transition(player, ManaCauses.SPELL_COST, false, () -> state.commit(reservation));
        state.markContinuousCastSuccessful(hand, spellId);
    }

    public static void beginContinuousCast(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaState state = requireMutableState(player);
        transition(
                player,
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
        transition(player, ManaCauses.CONTINUOUS_END, wasActive,
                () -> lockoutStarted[0] = state.finishContinuousCast());
        if (lockoutStarted[0]) {
            startRegenerationLockout(player, state);
        }
        return lockoutStarted[0];
    }

    public static void clearTransientLifecycleState(EntityPlayer player) {
        ManaState state = requireMutableState(player);
        boolean force = state.isContinuousCastActive() || state.getRemainingLockoutTicks() > 0L;
        transition(player, ManaCauses.LIFECYCLE, force, state::clearTransientLifecycleState);
        ManaMutationCoordinator.clear(player.getUniqueID());
    }

    public static void copyForClone(EntityPlayer replacementPlayer, ManaState original) {
        if (original == null) {
            throw new NullPointerException("Original mana state must not be null.");
        }
        ManaState replacement = requireMutableState(replacementPlayer);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(replacementPlayer.getUniqueID())) {
            long duration = GameplayRulesManager.current().getRegenerationLockoutTicks();
            ManaPublicState oldState = snapshot(original, duration);
            replacement.copyFrom(original);
            ManaPublicState newState = snapshot(replacement, duration);
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

    public static void tickPlayer(EntityPlayer player, GameplayRules rules) {
        ManaState state = requireMutableState(player);
        ManaPublicState oldState = snapshot(state, rules.getRegenerationLockoutTicks());
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            state.applyEffectiveStats(rules.getBaseMaximum(), rules.getBaseRegeneration());
            boolean lockoutEnded = state.getRemainingLockoutTicks() > 0L;
            boolean gateOpen = state.advanceRegenerationGate();
            lockoutEnded = lockoutEnded && state.getRemainingLockoutTicks() == 0L;
            int restored = gateOpen
                    ? state.regenerateTick(state.getEffectiveRegeneration())
                    : 0;
            ManaPublicState newState = snapshot(state, rules.getRegenerationLockoutTicks());
            if (!oldState.equals(newState)) {
                synchronizeDisplayChange(player, oldState, newState, state);
                ResourceLocation cause = restored > 0
                        ? ManaCauses.REGENERATION
                        : lockoutEnded ? ManaCauses.LOCKOUT_END : ManaCauses.STAT_CLAMP;
                publishTransition(player, oldState, newState, cause, lockoutEnded);
            }
        }
    }

    public static void applyRulesReload(
            EntityPlayer player,
            GameplayRules previous,
            GameplayRules replacement
    ) {
        ManaState state = requireMutableState(player);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            ManaPublicState oldState = snapshot(state, previous.getRegenerationLockoutTicks());
            boolean hadLockout = state.getRemainingLockoutTicks() > 0L;
            state.clampLockoutAfterReload(replacement.getRegenerationLockoutTicks());
            state.applyEffectiveStats(
                    replacement.getBaseMaximum(),
                    replacement.getBaseRegeneration()
            );
            ManaPublicState newState = snapshot(state, replacement.getRegenerationLockoutTicks());
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

    static ManaPublicState snapshot(ManaState state, long effectiveLockoutTicks) {
        return new ManaPublicState(
                state.getCurrentMana(),
                state.getMaximumMana(),
                state.getEffectiveRegeneration(),
                effectiveLockoutTicks,
                state.getRemainingLockoutTicks(),
                state.canRegenerateDuringPostCastLockout(),
                state.canRegenerateDuringContinuousCasting(),
                state.isContinuousCastActive(),
                state.isRegenerationEligible()
        );
    }

    private static void startRegenerationLockout(EntityPlayer player, ManaState state) {
        long duration = GameplayRulesManager.current().getRegenerationLockoutTicks();
        boolean restart = state.getRemainingLockoutTicks() > 0L;
        transition(
                player,
                restart ? ManaCauses.LOCKOUT_RESTART : ManaCauses.LOCKOUT_START,
                duration > 0L,
                () -> state.startRegenerationLockout(duration)
        );
    }

    private static void transition(
            EntityPlayer player,
            ResourceLocation cause,
            boolean forceEvent,
            Runnable mutation
    ) {
        ManaState state = requireMutableState(player);
        try (ManaMutationCoordinator.Scope ignored =
                     ManaMutationCoordinator.enter(player.getUniqueID())) {
            long duration = GameplayRulesManager.current().getRegenerationLockoutTicks();
            ManaPublicState oldState = snapshot(state, duration);
            mutation.run();
            ManaPublicState newState = snapshot(state, duration);
            if (!oldState.equals(newState) || forceEvent) {
                synchronizeDisplayChange(player, oldState, newState, state);
                publishTransition(player, oldState, newState, cause, forceEvent);
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
            throw new NullPointerException("Mutation cause must not be null.");
        }
        return cause;
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Mana amount must not be negative.");
        }
    }

    private interface StateMutation {
        boolean apply(ManaState state);
    }
}
