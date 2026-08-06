package com.mahghuuuls.jawms.client;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PerfectionistTooltipResourceTest {

    @Test
    void tooltipTruthfullyDescribesPaidManaAndMissCondition() throws IOException {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(
                "assets/jawms/lang/en_us.lang"
        );
        assertTrue(stream != null);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(text.contains("tooltip.jawms.perfectionist="));
            assertTrue(text.contains("Refunds %s%% paid Mana when Force Arrow misses"));
        } finally {
            stream.close();
        }
    }
}
