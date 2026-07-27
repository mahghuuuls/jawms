package com.mahghuuuls.jawms.internal.mana;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

public final class ManaBootstrap {

    private static boolean initialized;

    private ManaBootstrap() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;

        ManaCapability.register();
        ManaNetwork.register();
        ManaEvents events = new ManaEvents();
        MinecraftForge.EVENT_BUS.register(events);
        FMLCommonHandler.instance().bus().register(events);
    }
}
