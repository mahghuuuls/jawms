package com.mahghuuuls.jawms.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemManaFlaskTooltipResourceTest {

    @Test
    void synchronizedFlaskDescriptionHasAnUnescapedTranslationKey() throws IOException {
        InputStream input = getClass().getResourceAsStream("/assets/jawms/lang/en_us.lang");
        assertNotNull(input);
        try {
            byte[] bytes = new byte[4096];
            int length = input.read(bytes);
            String language = new String(bytes, 0, length, StandardCharsets.UTF_8);
            assertTrue(language.contains(
                    "item.jawms.mana_flask.desc=Restores up to %s Mana"
            ));
        } finally {
            input.close();
        }
    }
}
