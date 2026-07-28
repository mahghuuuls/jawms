package com.mahghuuuls.jawms.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MixinConfigurationTest {

    @Test
    void commonMixinIsRequiredAndDoesNotClaimANonexistentRefmap() throws IOException {
        InputStream input = getClass().getResourceAsStream("/mixins.jawms.json");
        assertNotNull(input);
        String json;
        try {
            byte[] bytes = new byte[4096];
            int length = input.read(bytes);
            json = new String(bytes, 0, length, StandardCharsets.UTF_8);
        } finally {
            input.close();
        }

        assertTrue(json.contains("\"required\": true"));
        assertTrue(json.contains("\"ItemWandMixin\""));
        assertTrue(json.contains("\"defaultRequire\": 1"));
        assertFalse(json.contains("\"refmap\""));
    }
}
