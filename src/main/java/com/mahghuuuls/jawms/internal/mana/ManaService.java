package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;

public final class ManaService implements IManaService {

    @Override
    public int getCurrentMana(EntityPlayer player) {
        return requireState(player).getCurrentMana();
    }

    @Override
    public int getMaximumMana(EntityPlayer player) {
        return requireState(player).getMaximumMana();
    }

    @Override
    public int restoreMana(EntityPlayer player, int amount) {
        ManaState state = requireState(player);
        int restored = state.restore(amount);
        if (restored > 0) {
            ManaNetwork.synchronize(player, state);
        }
        return restored;
    }

    @Override
    public boolean tryConsumeMana(EntityPlayer player, int amount) {
        ManaState state = requireState(player);
        boolean consumed = state.tryConsume(amount);
        if (consumed && amount > 0) {
            ManaNetwork.synchronize(player, state);
        }
        return consumed;
    }

    public static ManaState requireState(EntityPlayer player) {
        if (player == null) {
            throw new NullPointerException("Player must not be null.");
        }
        if (!(player.world instanceof WorldServer) || player.world.isRemote) {
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

    public static int getAvailableMana(EntityPlayer player) {
        return requireState(player).getAvailableMana();
    }

    public static ManaReservation reserveMana(EntityPlayer player, int amount) {
        return requireState(player).reserve(amount);
    }

    public static void releaseReservation(ManaReservation reservation) {
        if (reservation != null && reservation.isActive()) {
            reservation.ownerRelease();
        }
    }

    public static void completeSuccessfulWandCast(EntityPlayer player, ManaReservation reservation) {
        ManaState state = requireState(player);
        if (reservation == null || !reservation.belongsTo(state)) {
            throw new IllegalStateException("Successful wand cast has no matching mana reservation.");
        }
        state.commit(reservation);
        state.startRegenerationLockout(
                GameplayRulesManager.current().getRegenerationLockoutTicks()
        );
        if (reservation.getAmount() > 0) {
            ManaNetwork.synchronize(player, state);
        }
    }

    public static void completeSuccessfulContinuousPortion(
            EntityPlayer player,
            ManaReservation reservation,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaState state = requireState(player);
        if (reservation == null || !reservation.belongsTo(state)) {
            throw new IllegalStateException(
                    "Successful continuous cast has no matching mana reservation."
            );
        }
        state.commit(reservation);
        state.markContinuousCastSuccessful(hand, spellId);
        if (reservation.getAmount() > 0) {
            ManaNetwork.synchronize(player, state);
        }
    }

    public static void beginContinuousCast(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireState(player).beginContinuousCast(hand, spellId);
    }

    public static void interruptContinuousCast(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaState state = requireState(player);
        state.interruptContinuousCast(hand, spellId);
    }

    public static void exhaustContinuousCastMana(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        ManaState state = requireState(player);
        state.exhaustContinuousCastMana(hand, spellId);
    }

    public static void blockContinuousCastRetry(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId
    ) {
        requireState(player).blockContinuousCastRetry(hand, spellId);
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
        requireState(player).releaseContinuousCastRetryBlock(hand, spellId);
    }

    public static boolean finishContinuousCast(EntityPlayer player) {
        return requireState(player).finishContinuousCast(
                GameplayRulesManager.current().getRegenerationLockoutTicks()
        );
    }

    public static void clearTransientLifecycleState(EntityPlayer player) {
        requireState(player).clearTransientLifecycleState();
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
}
