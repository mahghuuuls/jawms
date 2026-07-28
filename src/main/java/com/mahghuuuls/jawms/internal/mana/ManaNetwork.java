package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.Tags;
import com.mahghuuuls.jawms.internal.casting.ContinuousCastStopMessage;
import com.mahghuuuls.jawms.internal.casting.ContinuousCastReleaseMessage;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public final class ManaNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(Tags.MOD_ID);

    private ManaNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(ManaStateMessage.Handler.class, ManaStateMessage.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(GameplayRulesMessage.Handler.class, GameplayRulesMessage.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(
                ContinuousCastStopMessage.Handler.class,
                ContinuousCastStopMessage.class,
                2,
                Side.CLIENT
        );
        CHANNEL.registerMessage(
                ContinuousCastReleaseMessage.Handler.class,
                ContinuousCastReleaseMessage.class,
                3,
                Side.SERVER
        );
    }

    public static void synchronize(EntityPlayer player, ManaState state) {
        if (player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(
                    new ManaStateMessage(
                            state.getCurrentMana(),
                            state.getMaximumMana(),
                            state.getEffectiveRegeneration(),
                            state.getRevision()
                    ),
                    (EntityPlayerMP) player
            );
        }
    }

    public static void synchronizeRules(EntityPlayer player, GameplayRules rules) {
        if (player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(new GameplayRulesMessage(rules), (EntityPlayerMP) player);
        }
    }

    public static void stopContinuousCastVisual(
            EntityPlayer player,
            EnumHand hand,
            ResourceLocation spellId,
            boolean resetImmediately
    ) {
        if (player instanceof EntityPlayerMP) {
            CHANNEL.sendTo(
                    new ContinuousCastStopMessage(hand, spellId, resetImmediately),
                    (EntityPlayerMP) player
            );
        }
    }

    public static void releaseContinuousCastBlock(
            EnumHand hand,
            ResourceLocation spellId
    ) {
        CHANNEL.sendToServer(new ContinuousCastReleaseMessage(hand, spellId));
    }

    public static void synchronizeLifecycle(EntityPlayer player, String reason) {
        ManaState state = ManaService.requireState(player);
        state.advanceRevision();
        synchronize(player, state);
        com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Synchronized player mana after {}: player={}, current={}, maximum={}, regeneration={}, revision={}.",
                reason,
                player.getUniqueID(),
                state.getCurrentMana(),
                state.getMaximumMana(),
                state.getEffectiveRegeneration(),
                state.getRevision()
        );
    }
}
