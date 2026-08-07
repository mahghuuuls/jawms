package com.mahghuuuls.jawms.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudLayoutTest {

    @Test
    void allNineAnchorsResolveAgainstScaledScreen() {
        assertPosition(ClientHudSettings.Anchor.TOP_LEFT, 0, 0);
        assertPosition(ClientHudSettings.Anchor.TOP_CENTER, 59, 0);
        assertPosition(ClientHudSettings.Anchor.TOP_RIGHT, 119, 0);
        assertPosition(ClientHudSettings.Anchor.MIDDLE_LEFT, 0, 47);
        assertPosition(ClientHudSettings.Anchor.MIDDLE_CENTER, 59, 47);
        assertPosition(ClientHudSettings.Anchor.MIDDLE_RIGHT, 119, 47);
        assertPosition(ClientHudSettings.Anchor.BOTTOM_LEFT, 0, 95);
        assertPosition(ClientHudSettings.Anchor.BOTTOM_CENTER, 59, 95);
        assertPosition(ClientHudSettings.Anchor.BOTTOM_RIGHT, 119, 95);
    }

    @Test
    void signedOffsetsAndOversizedDimensionsRemainOnScreen() {
        ClientHudSettings settings = settings(
                ClientHudSettings.Anchor.BOTTOM_RIGHT,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                ClientHudSettings.FillDirection.LEFT_TO_RIGHT,
                4096,
                4096
        );

        HudLayout.Rectangle bar = HudLayout.resolve(settings, 320, 180);

        assertEquals(0, bar.getX());
        assertEquals(0, bar.getY());
        assertEquals(320, bar.getWidth());
        assertEquals(180, bar.getHeight());
    }

    @Test
    void verticalDirectionsRotateLengthAndThickness() {
        ClientHudSettings settings = settings(
                ClientHudSettings.Anchor.TOP_LEFT,
                0,
                0,
                ClientHudSettings.FillDirection.BOTTOM_TO_TOP,
                81,
                5
        );

        HudLayout.Rectangle bar = HudLayout.resolve(settings, 200, 100);

        assertEquals(5, bar.getWidth());
        assertEquals(81, bar.getHeight());
    }

    @Test
    void halfFillUsesAllFourDirections() {
        HudLayout.Rectangle horizontal = new HudLayout.Rectangle(10, 20, 82, 5);
        HudLayout.Rectangle vertical = new HudLayout.Rectangle(10, 20, 5, 82);

        assertRectangle(
                HudLayout.fill(
                        horizontal,
                        ClientHudSettings.FillDirection.LEFT_TO_RIGHT,
                        50,
                        100
                ),
                11, 21, 40, 3
        );
        assertRectangle(
                HudLayout.fill(
                        horizontal,
                        ClientHudSettings.FillDirection.RIGHT_TO_LEFT,
                        50,
                        100
                ),
                51, 21, 40, 3
        );
        assertRectangle(
                HudLayout.fill(
                        vertical,
                        ClientHudSettings.FillDirection.TOP_TO_BOTTOM,
                        50,
                        100
                ),
                11, 21, 3, 40
        );
        assertRectangle(
                HudLayout.fill(
                        vertical,
                        ClientHudSettings.FillDirection.BOTTOM_TO_TOP,
                        50,
                        100
                ),
                11, 61, 3, 40
        );
    }

    @Test
    void visibilityHonorsFullZeroHideGuiAndSpectatorRules() {
        assertTrue(HudLayout.shouldRender(true, false, false, true, 49, 50));
        assertTrue(HudLayout.shouldRender(true, false, false, true, 0, 50));
        assertFalse(HudLayout.shouldRender(true, false, false, true, 50, 50));
        assertTrue(HudLayout.shouldRender(true, false, false, false, 50, 50));
        assertFalse(HudLayout.shouldRender(true, true, false, false, 25, 50));
        assertFalse(HudLayout.shouldRender(true, false, true, false, 25, 50));
        assertFalse(HudLayout.shouldRender(false, false, false, false, 25, 50));
    }

    private static void assertPosition(
            ClientHudSettings.Anchor anchor,
            int expectedX,
            int expectedY
    ) {
        HudLayout.Rectangle bar = HudLayout.resolve(
                settings(
                        anchor,
                        0,
                        0,
                        ClientHudSettings.FillDirection.LEFT_TO_RIGHT,
                        81,
                        5
                ),
                200,
                100
        );
        assertEquals(expectedX, bar.getX());
        assertEquals(expectedY, bar.getY());
    }

    private static ClientHudSettings settings(
            ClientHudSettings.Anchor anchor,
            int offsetX,
            int offsetY,
            ClientHudSettings.FillDirection direction,
            int length,
            int thickness
    ) {
        return new ClientHudSettings(
                true,
                true,
                ClientHudSettings.TextDisplay.CURRENT_AND_MAXIMUM,
                1.0F,
                anchor,
                offsetX,
                offsetY,
                direction,
                length,
                thickness
        );
    }

    private static void assertRectangle(
            HudLayout.Rectangle actual,
            int x,
            int y,
            int width,
            int height
    ) {
        assertEquals(x, actual.getX());
        assertEquals(y, actual.getY());
        assertEquals(width, actual.getWidth());
        assertEquals(height, actual.getHeight());
    }
}
