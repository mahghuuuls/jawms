package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.Tags;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.config.GuiConfig;

public final class JawmsClientConfigScreen extends GuiConfig {

    public JawmsClientConfigScreen(GuiScreen parentScreen) {
        super(
                parentScreen,
                ClientHudConfigManager.getConfigElements(),
                Tags.MOD_ID,
                "hud",
                false,
                false,
                Tags.MOD_NAME + " - HUD"
        );
    }
}
