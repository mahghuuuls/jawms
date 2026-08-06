package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.internal.integration.wizardry.effect.WizardryKillManaService;
import com.mahghuuuls.jawms.internal.integration.wizardry.effect.WizardryMeleeManaService;
import com.mahghuuuls.jawms.internal.integration.wizardry.projectile.ForceArrowRefundTracker;
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
        ForceArrowRefundTracker forceArrowRefundTracker = new ForceArrowRefundTracker();
        MinecraftForge.EVENT_BUS.register(events);
        MinecraftForge.EVENT_BUS.register(killManaService);
        MinecraftForge.EVENT_BUS.register(forceArrowRefundTracker);
        FMLCommonHandler.instance().bus().register(events);
        FMLCommonHandler.instance().bus().register(killManaService);
        FMLCommonHandler.instance().bus().register(meleeManaService);
        FMLCommonHandler.instance().bus().register(forceArrowRefundTracker);
    }
}
