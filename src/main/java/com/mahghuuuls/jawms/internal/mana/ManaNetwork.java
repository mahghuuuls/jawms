package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.Tags;
import com.mahghuuuls.jawms.internal.casting.ContinuousCastStopMessage;
import com.mahghuuuls.jawms.internal.casting.ContinuousCastReleaseMessage;
import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
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

    private static final java.util.Map<net.minecraft.network.NetHandlerPlayServer, Long> MANAGED_SEQUENCES =
            new java.util.WeakHashMap<>();

    private ManaNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(ParticipantUseMessages.CapabilityHandler.class, ParticipantUseMessages.Capability.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(ParticipantUseMessages.IntentHandler.class, ParticipantUseMessages.Intent.class, 5, Side.SERVER);
        CHANNEL.registerMessage(ParticipantUseMessages.ReleaseHandler.class, ParticipantUseMessages.Release.class, 6, Side.SERVER);
        CHANNEL.registerMessage(ParticipantUseMessages.CloseHandler.class, ParticipantUseMessages.Close.class, 7, Side.CLIENT);
        CHANNEL.registerMessage(ManaStateMessage.Handler.class, ManaStateMessage.class, 0, Side.CLIENT);
        CHANNEL.registerMessage(ManaReadinessMessage.Handler.class, ManaReadinessMessage.class, 8, Side.CLIENT);
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
        if (!(player instanceof EntityPlayerMP)) return;
        EntityPlayerMP recipient = (EntityPlayerMP) player;
        if (recipient.connection == null || state.checkingDeath) return;
        ManaReadiness readiness = state.readiness;
        if (readiness != null && (readiness.isRetired() && !state.recoveringTransfer
                || !readiness.bound && !state.recoveringTransfer
                || recipient.connection.player != recipient
                        && !ManaLifecycle.isReconcilingReplacement(recipient, readiness))) return;
        ManaStateMessage payload = state.profileHeld() ? null : new ManaStateMessage(
                state.getCurrentMana(), state.getMaximumMana(), state.getEffectiveRegeneration(),
                state.getRevision(), state.getGlobalSpellEfficiency(), state.getElementSpellEfficiency(),
                state.getGlobalSpellCostMultiplier(), state.getElementSpellCostMultipliers());
        if (readiness != null) {
            if (!recipient.connection.getNetworkManager().isChannelOpen()) return;
            long sequence = MANAGED_SEQUENCES.merge(recipient.connection, 1L, Math::addExact);
            CHANNEL.sendTo(new ManaReadinessMessage(sequence, readiness.identity, readiness.phase(), payload), recipient);
        } else if (!MANAGED_SEQUENCES.containsKey(recipient.connection)) {
            CHANNEL.sendTo(payload, recipient);
        }
    }

    public static void participantCapability(EntityPlayerMP player, java.util.UUID epoch) {
        CHANNEL.sendTo(new ParticipantUseMessages.Capability(epoch), player);
    }
    public static void participantIntent(ParticipantUseMessages.Intent message) { CHANNEL.sendToServer(message); }
    public static void participantRelease(ParticipantUseMessages.Release message) { CHANNEL.sendToServer(message); }
    public static void participantClose(EntityPlayerMP player, ParticipantUseMessages.Close message) { CHANNEL.sendTo(message, player); }

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
        if (state.profileHeld()) {
            synchronize(player, state);
            return;
        }
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
