package com.mahghuuuls.jawms.client;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ClientManaState {

    private static int currentMana;
    private static int maximumMana;
    private static double effectiveRegeneration;
    private static long revision = Long.MIN_VALUE;
    private static boolean available;

    public static void accept(
            int current,
            int maximum,
            double regeneration,
            long incomingRevision
    ) {
        if (!available || incomingRevision > revision) {
            currentMana = current;
            maximumMana = maximum;
            effectiveRegeneration = regeneration;
            revision = incomingRevision;
            available = true;
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static int getCurrentMana() {
        return currentMana;
    }

    public static int getMaximumMana() {
        return maximumMana;
    }

    public static double getEffectiveRegeneration() {
        return effectiveRegeneration;
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clear();
    }

    static void clear() {
        currentMana = 0;
        maximumMana = 0;
        effectiveRegeneration = 0.0D;
        revision = Long.MIN_VALUE;
        available = false;
    }
}
