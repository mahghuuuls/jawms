package com.mahghuuuls.jawms.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ManaHudRenderer {

    private static final int BORDER_COLOR = 0xFFC0C0C0;
    private static final int BACKGROUND_COLOR = 0xA0000000;
    private static final int MANA_COLOR = 0xFF2F7BFF;

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        ClientHudSettings settings = ClientHudConfigManager.current();
        if (minecraft.player == null || !HudLayout.shouldRender(
                ClientManaState.isAvailable(),
                minecraft.gameSettings.hideGUI,
                minecraft.player.isSpectator(),
                settings.isHideWhenFull(),
                ClientManaState.getCurrentMana(),
                ClientManaState.getMaximumMana()
        )) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        HudLayout.Rectangle bar = HudLayout.resolve(
                settings,
                resolution.getScaledWidth(),
                resolution.getScaledHeight()
        );
        Gui.drawRect(
                bar.getX(),
                bar.getY(),
                bar.getX() + bar.getWidth(),
                bar.getY() + bar.getHeight(),
                BORDER_COLOR
        );
        Gui.drawRect(
                bar.getX() + 1,
                bar.getY() + 1,
                bar.getX() + bar.getWidth() - 1,
                bar.getY() + bar.getHeight() - 1,
                BACKGROUND_COLOR
        );

        HudLayout.Rectangle fill = HudLayout.fill(
                bar,
                settings.getFillDirection(),
                ClientManaState.getCurrentMana(),
                ClientManaState.getMaximumMana()
        );
        if (fill.getWidth() > 0 && fill.getHeight() > 0) {
            Gui.drawRect(
                    fill.getX(),
                    fill.getY(),
                    fill.getX() + fill.getWidth(),
                    fill.getY() + fill.getHeight(),
                    MANA_COLOR
            );
        }

        if (settings.getTextDisplay() != ClientHudSettings.TextDisplay.NONE) {
            drawNumbers(minecraft, resolution, bar, settings);
        }
    }

    private static void drawNumbers(
            Minecraft minecraft,
            ScaledResolution resolution,
            HudLayout.Rectangle bar,
            ClientHudSettings settings
    ) {
        String text = settings.getTextDisplay().format(
                ClientManaState.getCurrentMana(),
                ClientManaState.getMaximumMana()
        );
        HudTextLayout.Result textLayout = HudTextLayout.resolve(
                minecraft.fontRenderer.getStringWidth(text),
                9,
                settings.getTextScale(),
                bar,
                resolution.getScaledWidth(),
                resolution.getScaledHeight()
        );
        float scale = textLayout.getScale();
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, 1.0F);
        minecraft.fontRenderer.drawStringWithShadow(
                text,
                textLayout.getX() / scale,
                textLayout.getY() / scale,
                0xFFFFFFFF
        );
        GlStateManager.popMatrix();
    }
}
