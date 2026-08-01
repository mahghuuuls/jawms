package com.mahghuuuls.jawms.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.lang.reflect.Method;

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
        assertTrue(json.contains("\"ItemWizardArmourMixin\""));
        assertTrue(json.contains("\"ItemArtefactMixin\""));
        assertTrue(json.contains("\"defaultRequire\": 1"));
        assertFalse(json.contains("\"refmap\""));
    }

    @Test
    void armorAndHungerRedirectsPinExactWizardryCalls() {
        Redirect matching = redirect(ItemWizardArmourMixin.class, "jawms$removeMatchingArmorCost");
        Redirect sage = redirect(ItemWizardArmourMixin.class, "jawms$removeSageArmorCost");
        String costTarget = "Lelectroblob/wizardry/util/SpellModifiers;"
                + "set(Ljava/lang/String;FZ)Lelectroblob/wizardry/util/SpellModifiers;";
        assertEquals("applySpellModifiers", matching.method()[0]);
        assertEquals(costTarget, matching.at().target());
        assertEquals(0, matching.at().ordinal());
        assertEquals(1, matching.require());
        assertEquals(costTarget, sage.at().target());
        assertEquals(1, sage.at().ordinal());
        assertEquals(1, sage.require());

        Redirect hunger = redirect(ItemArtefactMixin.class, "jawms$disableHungerCastingFallback");
        assertEquals("onSpellCastPreEvent", hunger.method()[0]);
        assertEquals("Lnet/minecraft/util/FoodStats;addStats(IF)V", hunger.at().target());
        assertEquals(1, hunger.require());
    }

    private static Redirect redirect(Class<?> owner, String name) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                Redirect redirect = method.getAnnotation(Redirect.class);
                assertNotNull(redirect);
                return redirect;
            }
        }
        throw new AssertionError("Missing redirect " + name);
    }
}
