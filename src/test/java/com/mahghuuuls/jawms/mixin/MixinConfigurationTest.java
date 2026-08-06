package com.mahghuuuls.jawms.mixin;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import java.lang.reflect.Method;

class MixinConfigurationTest {

    @Test
    void commonMixinIsRequiredAndDeclaresGeneratedProductionRefmap() throws IOException {
        String json = readResource("/mixins.jawms.json");

        assertTrue(json.contains("\"required\": true"));
        assertTrue(json.contains("\"ItemWandMixin\""));
        assertTrue(json.contains("\"ItemWizardArmourMixin\""));
        assertTrue(json.contains("\"ItemArtefactMixin\""));
        assertTrue(json.contains("\"ItemManaFlaskMixin\""));
        assertTrue(json.contains("\"ItemWandClientMixin\""));
        assertTrue(json.contains("\"ItemManaFlaskClientMixin\""));
        assertTrue(json.contains("\"ForceArrowMixin\""));
        assertTrue(json.contains("\"EntityForceArrowMixin\""));
        assertTrue(json.contains("\"defaultRequire\": 1"));
        assertTrue(json.contains("\"refmap\": \"mixins.jawms.refmap.json\""));

        String refmap = readResource("/mixins.jawms.refmap.json");
        assertTrue(refmap.contains("func_77659_a"));
        assertTrue(refmap.contains("func_77624_a"));
        assertTrue(refmap.contains("func_77615_a"));
        assertTrue(refmap.contains("func_184597_cx"));
        assertTrue(refmap.contains("func_135052_a"));
        assertTrue(refmap.contains("func_75116_a"));
        assertTrue(refmap.contains("func_75122_a"));
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

    @Test
    void forceArrowHooksPinCreationTerminalsAndLegacyRecharge() {
        Inject capture = inject(ForceArrowMixin.class, "jawms$capturePendingForceArrow");
        assertEquals(1, capture.require());
        assertEquals(1, capture.expect());
        assertEquals(1, capture.at().length);
        assertEquals("TAIL", capture.at()[0].value());

        Inject living = inject(EntityForceArrowMixin.class, "jawms$consumeRefundOnLivingHit");
        Inject block = inject(EntityForceArrowMixin.class, "jawms$refundOnBlockHit");
        Inject expiry = inject(EntityForceArrowMixin.class, "jawms$refundOnExpiry");
        assertEquals(1, living.at().length);
        assertEquals(1, block.at().length);
        assertEquals("HEAD", living.at()[0].value());
        assertEquals("HEAD", block.at()[0].value());
        assertEquals(1, living.require());
        assertEquals(1, block.require());
        assertEquals(1, expiry.require());
        assertEquals(1, expiry.at().length);
        assertEquals("INVOKE", expiry.at()[0].value());
        assertEquals("Lelectroblob/wizardry/entity/projectile/EntityForceArrow;"
                + "returnManaToCaster()V", expiry.at()[0].target());

        Redirect legacy = redirect(
                EntityForceArrowMixin.class, "jawms$disableLegacyWandManaRefund"
        );
        assertEquals(1, legacy.require());
        assertEquals("Lelectroblob/wizardry/item/IManaStoringItem;"
                + "rechargeMana(Lnet/minecraft/item/ItemStack;I)V",
                legacy.at().target());
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

    private static Inject inject(Class<?> owner, String name) {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                Inject inject = method.getAnnotation(Inject.class);
                assertNotNull(inject);
                return inject;
            }
        }
        throw new AssertionError("Missing inject " + name);
    }

    private String readResource(String path) throws IOException {
        InputStream input = getClass().getResourceAsStream(path);
        assertNotNull(input);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
