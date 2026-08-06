package com.mahghuuuls.jawms.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardryKillTooltipResourceTest {

    @Test
    void killEffectsHaveTruthfulReplacementText() throws IOException {
        InputStream input = getClass().getResourceAsStream("/assets/jawms/lang/en_us.lang");
        assertNotNull(input);
        try {
            byte[] bytes = new byte[8192];
            int length = input.read(bytes);
            String language = new String(bytes, 0, length, StandardCharsets.UTF_8);
            assertTrue(language.contains(
                    "tooltip.jawms.siphon_per_level=+%s Mana on kill per level"
            ));
            assertTrue(language.contains(
                    "tooltip.jawms.siphoning=+%s%% Siphon Mana"
            ));
            assertTrue(language.contains(
                    "tooltip.jawms.extraction=+%s Mana on sorcery kill"
            ));
        } finally {
            input.close();
        }
    }
}
