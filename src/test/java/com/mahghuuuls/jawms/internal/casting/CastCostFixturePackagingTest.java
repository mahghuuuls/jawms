package com.mahghuuuls.jawms.internal.casting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import electroblob.wizardry.packet.PacketSpellProperties;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellProperties;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.fml.common.ModContainer;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CastCostFixturePackagingTest {

    @Test
    void singleModOriginLoadsProbePropertiesAndEncodesLoginPayload() throws Exception {
        File origin = new File(System.getProperty("jawms.castCostFixtureJar"));
        try (ZipFile jar = new ZipFile(origin)) {
            assertNotNull(jar.getEntry("example/jawmscast/CastCostFixture.class"));
        }
        ModContainer mod = modAt(origin);
        Spell spell = mock(Spell.class);
        // The final accessor needs its empty backing set without bootstrapping the mod registry.
        Field keys = Spell.class.getDeclaredField("propertyKeys");
        keys.setAccessible(true);
        keys.set(spell, Collections.emptySet());
        Constructor<SpellProperties> parser = SpellProperties.class
                .getDeclaredConstructor(JsonObject.class, Spell.class);
        parser.setAccessible(true);
        AtomicInteger found = new AtomicInteger();
        Map<String, SpellProperties> properties = new HashMap<>();

        assertTrue(CraftingHelper.findFiles(mod, "assets/jawms_cast_fixture/spells", null,
                (root, file) -> {
                    if (!Files.isRegularFile(file)) return true;
                    String name = root.relativize(file).toString();
                    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
                        properties.put(name, parser.newInstance(json, spell));
                        found.incrementAndGet();
                        return true;
                    } catch (Exception failure) {
                        throw new AssertionError("Fixture properties must parse through Wizardry", failure);
                    }
                }, true, true));
        // findFiles returns true even when the requested root is absent.
        assertEquals(5, found.get());
        String[] names = { "probe.json", "stream.json", "slow_stream.json", "free_stream.json", "charging_stream.json" };
        int[] costs = { 12, 4, 1, 0, 4 };
        for (int i = 0; i < names.length; i++) {
            SpellProperties value = properties.get(names[i]);
            assertNotNull(value, names[i]);
            ByteBuf payload = Unpooled.buffer();
            try {
                new PacketSpellProperties.Message(value).toBytes(payload);
                SpellProperties received = new SpellProperties(spell, payload);
                assertEquals(costs[i], received.cost);
                assertEquals(i == 4 ? 200 : 0, received.chargeup);
                assertEquals(20, received.cooldown);
                assertEquals(0, payload.readableBytes());
            } finally { payload.release(); }
        }
    }

    @Test
    void separateClassDirectoryDoesNotExposeResourcesToForgeModSourceScan() {
        File classOrigin = new File("build/classes/java/castCostFixture");
        assertTrue(new File(classOrigin, "example/jawmscast/CastCostFixture.class").isFile());
        AtomicInteger found = new AtomicInteger();
        assertTrue(CraftingHelper.findFiles(modAt(classOrigin), "assets/jawms_cast_fixture/spells", null,
                (root, file) -> { found.incrementAndGet(); return true; }, true, true));
        assertEquals(0, found.get(), "The former split launch origin must reproduce the missing-root boundary");
    }

    private ModContainer modAt(File origin) {
        ModContainer mod = mock(ModContainer.class);
        when(mod.getModId()).thenReturn("jawms_cast_fixture");
        when(mod.getSource()).thenReturn(origin);
        return mod;
    }
}
