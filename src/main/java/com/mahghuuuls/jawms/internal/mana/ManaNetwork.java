package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.Tags;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ManaNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);

    private ManaNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(ManaStateMessage.Handler.class, ManaStateMessage.class, 0, Side.CLIENT);
    }

    public static void synchronize(EntityPlayer player, ManaState state) {
        if (player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(
                    new ManaStateMessage(state.getCurrentMana(), state.getMaximumMana(), state.getRevision()),
                    (EntityPlayerMP) player
            );
        }
    }

    public static void synchronizeLifecycle(EntityPlayer player, String reason) {
        ManaState state = ManaService.requireState(player);
        state.advanceRevision();
        synchronize(player, state);
        com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Synchronized player mana after {}: player={}, current={}, maximum={}, revision={}.",
                reason,
                player.getUniqueID(),
                state.getCurrentMana(),
                state.getMaximumMana(),
                state.getRevision()
        );
    }
}
