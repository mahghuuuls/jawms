package com.mahghuuuls.jawms;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = JustAnotherWizardryManaSystemMod.DEPENDENCIES
)
public final class JustAnotherWizardryManaSystemMod {

    public static final String DEPENDENCIES =
            "required-after:ebwizardry@[4.3.19,4.3.20);"
                    + "required-after:mixinbooter@[11.0,12.0);"
                    + "after:baubles@[1.5.2,)";

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Initializing {}.", Tags.MOD_NAME);
    }
}
