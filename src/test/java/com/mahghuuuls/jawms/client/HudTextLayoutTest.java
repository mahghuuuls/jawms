package com.mahghuuuls.jawms.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudTextLayoutTest {

    @Test
    void configuredScaleIsPreservedWhenTextFits() {
        HudTextLayout.Result result = HudTextLayout.resolve(
                20,
                9,
                0.65F,
                new HudLayout.Rectangle(0, 49, 7, 81),
                320,
                180
        );

        assertEquals(0.65F, result.getScale());
        assertEquals(13, result.getWidth());
        assertEquals(6, result.getHeight());
        assertOnScreen(result, 320, 180);
    }

    @Test
    void oversizedScaledTextShrinksToScreenWidth() {
        HudTextLayout.Result result = HudTextLayout.resolve(
                200,
                9,
                3.0F,
                new HudLayout.Rectangle(0, 0, 7, 50),
                100,
                50
        );

        assertEquals(0.5F, result.getScale());
        assertEquals(100, result.getWidth());
        assertOnScreen(result, 100, 50);
    }

    @Test
    void oversizedScaledTextShrinksToScreenHeight() {
        HudTextLayout.Result result = HudTextLayout.resolve(
                10,
                9,
                3.0F,
                new HudLayout.Rectangle(0, 0, 7, 7),
                100,
                10
        );

        assertTrue(result.getScale() < 3.0F);
        assertEquals(10, result.getHeight());
        assertOnScreen(result, 100, 10);
    }

    private static void assertOnScreen(
            HudTextLayout.Result result,
            int screenWidth,
            int screenHeight
    ) {
        assertTrue(result.getX() >= 0);
        assertTrue(result.getY() >= 0);
        assertTrue(result.getX() + result.getWidth() <= screenWidth);
        assertTrue(result.getY() + result.getHeight() <= screenHeight);
    }
}
