package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.internal.mana.ManaNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ClientContinuousCastGuard {

    private static EnumHand blockedHand;
    private static ResourceLocation blockedSpellId;
    private static boolean suppressFinish;

    public static void block(
            EnumHand hand,
            ResourceLocation spellId,
            boolean shouldSuppressFinish
    ) {
        if (hand == null) {
            throw new NullPointerException("hand");
        }
        if (spellId == null) {
            throw new NullPointerException("spellId");
        }
        blockedHand = hand;
        blockedSpellId = spellId;
        suppressFinish = shouldSuppressFinish;
    }

    public static boolean isBlocked(EnumHand hand, ResourceLocation spellId) {
        return hand == blockedHand && equals(spellId, blockedSpellId);
    }

    public static boolean isFinishSuppressed(EnumHand hand, ResourceLocation spellId) {
        return suppressFinish && isBlocked(hand, spellId);
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && blockedHand != null
                && !Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown()) {
            ManaNetwork.releaseContinuousCastBlock(blockedHand, blockedSpellId);
            clear();
        }
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clear();
    }

    static void clear() {
        blockedHand = null;
        blockedSpellId = null;
        suppressFinish = false;
    }

    private static boolean equals(Object first, Object second) {
        return first == null ? second == null : first.equals(second);
    }
}
