package com.mahghuuuls.jawms.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public final class ManaHudRenderer {

    private static final int BAR_LENGTH = 81;
    private static final int BAR_THICKNESS = 5;
    private static final int INNER_LENGTH = BAR_LENGTH - 2;
    private static final int BORDER_COLOR = 0xFFC0C0C0;
    private static final int BACKGROUND_COLOR = 0xA0000000;
    private static final int MANA_COLOR = 0xFF2F7BFF;

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL || !ClientManaState.isAvailable()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null
                || minecraft.gameSettings.hideGUI
                || minecraft.player.isSpectator()
                || ClientManaState.getCurrentMana() >= ClientManaState.getMaximumMana()) {
            return;
        }

        ScaledResolution resolution = new ScaledResolution(minecraft);
        int x = Math.max(0, (resolution.getScaledWidth() - BAR_LENGTH) / 2);
        int y = Math.max(10, resolution.getScaledHeight() - 55);
        x = Math.min(x, Math.max(0, resolution.getScaledWidth() - BAR_LENGTH));
        y = Math.min(y, Math.max(10, resolution.getScaledHeight() - BAR_THICKNESS));

        Gui.drawRect(x, y, x + BAR_LENGTH, y + BAR_THICKNESS, BORDER_COLOR);
        Gui.drawRect(x + 1, y + 1, x + BAR_LENGTH - 1, y + BAR_THICKNESS - 1, BACKGROUND_COLOR);

        int filled = (int) ((long) INNER_LENGTH * ClientManaState.getCurrentMana()
                / ClientManaState.getMaximumMana());
        if (filled > 0) {
            Gui.drawRect(x + 1, y + 1, x + 1 + filled, y + BAR_THICKNESS - 1, MANA_COLOR);
        }

        String text = ClientManaState.getCurrentMana() + " / " + ClientManaState.getMaximumMana();
        int textX = x + (BAR_LENGTH - minecraft.fontRenderer.getStringWidth(text)) / 2;
        minecraft.fontRenderer.drawStringWithShadow(text, textX, y - 10, 0xFFFFFFFF);
    }
}
