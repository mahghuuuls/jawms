package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ManaHudRenderer {

    private static final int BORDER_COLOR = 0xFFC0C0C0;
    private static final int BACKGROUND_COLOR = 0xA0000000;
    private static final int MANA_COLOR = 0xFF2F7BFF;

    @SubscribeEvent(receiveCanceled = true)
    public void render(RenderGameOverlayEvent.Chat event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        if (minecraft.player == null
                || !snapshot.isAvailable()
                || minecraft.gameSettings.hideGUI
                || minecraft.player.isSpectator()) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        ManaHudRenderEvent hudEvent = new ManaHudRenderEvent(
                snapshot,
                event.getPartialTicks(),
                resolution.getScaledWidth(),
                resolution.getScaledHeight()
        );
        if (MinecraftForge.EVENT_BUS.post(hudEvent)) {
            return;
        }

        ClientHudSettings settings = ClientHudConfigManager.current();
        if (!settings.isEnabled() || !HudLayout.shouldRender(
                true,
                false,
                false,
                settings.isHideWhenFull(),
                snapshot.getCurrentMana(),
                snapshot.getMaximumMana()
        )) {
            return;
        }

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
                snapshot.getCurrentMana(),
                snapshot.getMaximumMana()
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
            drawNumbers(minecraft, resolution, bar, settings, snapshot);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawNumbers(
            Minecraft minecraft,
            ScaledResolution resolution,
            HudLayout.Rectangle bar,
            ClientHudSettings settings,
            ClientManaSnapshot snapshot
    ) {
        String text = settings.getTextDisplay().format(
                snapshot.getCurrentMana(),
                snapshot.getMaximumMana()
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
