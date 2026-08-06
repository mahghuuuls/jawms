package com.mahghuuuls.jawms.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardryMeleeTooltipResourceTest {

    @Test
    void meleeTooltipDescribesConfiguredPerLevelAndInstalledBehavior() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "assets/jawms/lang/en_us.lang"
        );
        assertTrue(stream != null);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        stream.close();
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);

        assertTrue(text.contains("tooltip.jawms.melee_per_level="));
        assertTrue(text.contains("%s Melee damage per level"));
        assertTrue(text.contains("%s Mana per enhanced hit per level"));
        assertTrue(text.contains("tooltip.jawms.melee_installed="));
        assertTrue(text.contains("%s Melee damage for %s Mana per enhanced hit"));
    }
}
