package com.mahghuuuls.jawms.devtools;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/** Optional public-API consumer for controlled addon-development environments. */
@Mod(
        modid = JawmsDeveloperToolsMod.MOD_ID,
        name = "JAWMS Developer Tools",
        version = JawmsDeveloperToolsMod.VERSION,
        dependencies = "required-after:jawms@[1.3.0,)",
        acceptableRemoteVersions = "*"
)
public final class JawmsDeveloperToolsMod {

    public static final String MOD_ID = "jawms_devtools";
    public static final String VERSION = "1.4.0";

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new JawmsDevCommand());
    }
}
