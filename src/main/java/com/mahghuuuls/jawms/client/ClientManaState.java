package com.mahghuuuls.jawms.client;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ClientManaState {

    private static int currentMana;
    private static int maximumMana;
    private static long revision = Long.MIN_VALUE;
    private static boolean available;

    public static void accept(int current, int maximum, long incomingRevision) {
        if (!available || incomingRevision > revision) {
            currentMana = current;
            maximumMana = maximum;
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

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clear();
    }

    static void clear() {
        currentMana = 0;
        maximumMana = 0;
        revision = Long.MIN_VALUE;
        available = false;
    }
}
