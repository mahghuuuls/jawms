package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.api.IManaService;
import net.minecraft.entity.player.EntityPlayer;
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
}
