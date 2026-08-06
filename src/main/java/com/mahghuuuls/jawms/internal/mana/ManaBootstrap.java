package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.internal.integration.wizardry.effect.WizardryKillManaService;
import com.mahghuuuls.jawms.internal.integration.wizardry.effect.WizardryMeleeManaService;
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
        WizardryKillManaService killManaService = new WizardryKillManaService();
        WizardryMeleeManaService meleeManaService = new WizardryMeleeManaService();
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(killManaService);
        FMLCommonHandler.instance().bus().register(events);
        FMLCommonHandler.instance().bus().register(killManaService);
        FMLCommonHandler.instance().bus().register(meleeManaService);
    }
}
