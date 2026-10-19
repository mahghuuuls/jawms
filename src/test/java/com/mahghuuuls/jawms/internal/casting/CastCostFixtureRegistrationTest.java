package com.mahghuuuls.jawms.internal.casting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import electroblob.wizardry.spell.Spell;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumAction;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Actual code with isolated spell/fixture state and an in-memory registry adapter, not an FML launch. */
class CastCostFixtureRegistrationTest {
    @BeforeAll static void bootstrap() throws Exception {
        Bootstrap.register();
        // This owner extends Minecraft's shared enum; initialize it once, never once per isolated spell registry.
        Class.forName("electroblob.wizardry.item.SpellActions");
    }

    @Test void loadingContinuousSupportBeforeRegistrationDoesNotConstructSpells() throws Exception {
        try (FixtureLoader loader = new FixtureLoader()) {
            Class<?> spell = loader.type("electroblob.wizardry.spell.Spell");
            Field counter = spell.getDeclaredField("nextSpellId");
            counter.setAccessible(true);
            int before = counter.getInt(null);
            loader.type("example.jawmscast.ContinuousCastFixture");
            assertEquals(before, counter.getInt(null), "Loading preInit support must not consume spell network IDs");
        }
    }

    @Test @SuppressWarnings({"rawtypes", "unchecked"})
    void registrationSorterAndCompleteLoginCodecPreserveEveryRegisteredSpellSchema() throws Exception {
        EnumAction[] sharedActions = EnumAction.values();
        try (FixtureLoader loader = new FixtureLoader()) {
            Class<?> spell = loader.type("electroblob.wizardry.spell.Spell");
            // Exercise the actual early class load before the dependency's registration callback.
            loader.type("example.jawmscast.ContinuousCastFixture");
            Map<ResourceLocation, Object> entries = new LinkedHashMap<>();
            IForgeRegistry registry = mock(IForgeRegistry.class);
            when(registry.getValuesCollection()).thenAnswer(call -> new ArrayList<>(entries.values()));
            when(registry.getValue(any(ResourceLocation.class))).thenAnswer(call -> entries.get(call.getArgument(0)));
            doAnswer(call -> {
                Object value = call.getArgument(0);
                ResourceLocation name = (ResourceLocation) spell.getMethod("getRegistryName").invoke(value);
                assertNull(entries.put(name, value), "Duplicate registry name: " + name);
                return null;
            }).when(registry).register(any());
            spell.getField("registry").set(null, registry);
            RegistryEvent.Register event = new RegistryEvent.Register(new ResourceLocation("ebwizardry", "spells"), registry);
            Class<?> builtIns = loader.type("electroblob.wizardry.registry.Spells");
            // Real built-in constructors with different schemas; the complete mod's armour registry needs FML.
            registry.register((IForgeRegistryEntry) loader.type("electroblob.wizardry.spell.None").newInstance());
            registry.register((IForgeRegistryEntry) loader.type("electroblob.wizardry.spell.Light").newInstance());
            assertEquals(2, entries.size());
            // Stand in for FML's ObjectHolder injection, which is not run by this isolated callback test.
            Field none = builtIns.getField("none");
            Field modifiers = Field.class.getDeclaredField("modifiers");
            modifiers.setAccessible(true);
            modifiers.setInt(none, none.getModifiers() & ~Modifier.FINAL);
            none.set(null, entries.get(new ResourceLocation("ebwizardry", "none")));
            Class<?> fixture = loader.type("example.jawmscast.CastCostFixture");
            fixture.getMethod("spells", RegistryEvent.Register.class).invoke(fixture.newInstance(), event);
            assertEquals(7, entries.size(), "Two built-ins and every fixture spell must be registered");
            loader.type("electroblob.wizardry.util.SpellNetworkIDSorter").getMethod("init").invoke(null);
            List<Object> ordered = new ArrayList<>(entries.values());
            ordered.sort(Comparator.comparingInt(value -> networkId(spell, value)));
            assertAll("Actual sorted registry and complete login payload",
                    () -> {
                        for (int i = 0; i < ordered.size(); i++) {
                            assertEquals(i, networkId(spell, ordered.get(i)), "Network IDs must be unique and contiguous");
                            assertSame(ordered.get(i), spell.getMethod("byNetworkID", int.class).invoke(null, i));
                        }
                    },
                    () -> completeLoginRoundTrip(loader, spell, ordered));
        } finally {
            assertArrayEquals(sharedActions, EnumAction.values(), "Isolated registration must not extend shared action enums again");
        }
    }

    private static int networkId(Class<?> spell, Object value) {
        try { return (Integer) spell.getMethod("networkID").invoke(value); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static void completeLoginRoundTrip(FixtureLoader loader, Class<?> spell, List<Object> ordered) throws Exception {
        Class<?> properties = loader.type("electroblob.wizardry.util.SpellProperties");
        Constructor<?> parse = properties.getDeclaredConstructor(JsonObject.class, spell);
        parse.setAccessible(true);
        Object values = java.lang.reflect.Array.newInstance(properties, ordered.size());
        File wizardry = new File(Spell.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (ZipFile baseJar = new ZipFile(wizardry);
             ZipFile fixtureJar = new ZipFile(System.getProperty("jawms.castCostFixtureJar"))) {
            for (int i = 0; i < ordered.size(); i++) {
                Object value = ordered.get(i);
                ResourceLocation name = (ResourceLocation) spell.getMethod("getRegistryName").invoke(value);
                ZipFile jar = name.getNamespace().equals("ebwizardry") ? baseJar : fixtureJar;
                String path = "assets/" + name.getNamespace() + "/spells/" + name.getPath() + ".json";
                assertNotNull(jar.getEntry(path), path);
                try (Reader reader = new InputStreamReader(jar.getInputStream(jar.getEntry(path)), StandardCharsets.UTF_8)) {
                    java.lang.reflect.Array.set(values, i, parse.newInstance(new JsonParser().parse(reader).getAsJsonObject(), value));
                }
            }
        }
        Class<?> message = loader.type("electroblob.wizardry.packet.PacketSpellProperties$Message");
        ByteBuf payload = Unpooled.buffer();
        try {
            message.getMethod("toBytes", ByteBuf.class).invoke(message.getConstructor(values.getClass()).newInstance(values), payload);
            Object received = message.newInstance();
            message.getMethod("fromBytes", ByteBuf.class).invoke(received, payload);
            assertEquals(0, payload.readableBytes());
            Field array = message.getDeclaredField("propertiesArray");
            array.setAccessible(true);
            Object decoded = array.get(received);
            assertEquals(ordered.size(), java.lang.reflect.Array.getLength(decoded));
            for (int i = 0; i < ordered.size(); i++) {
                Object expected = java.lang.reflect.Array.get(values, i);
                Object actual = java.lang.reflect.Array.get(decoded, i);
                for (Field field : properties.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true);
                    assertEquals(field.get(expected), field.get(actual), "Spell " + i + " field " + field.getName());
                }
            }
        } finally { payload.release(); }
    }

    private static final class FixtureLoader extends URLClassLoader {
        FixtureLoader() throws Exception {
            super(new URL[] { new File(System.getProperty("jawms.castCostFixtureJar")).toURI().toURL(),
                    Spell.class.getProtectionDomain().getCodeSource().getLocation() }, CastCostFixtureRegistrationTest.class.getClassLoader());
            // FML normally supplies this during preInit; do not execute a mod launch in the test.
            type("electroblob.wizardry.Wizardry").getField("logger").set(null,
                    org.apache.logging.log4j.LogManager.getLogger("fixture-registration-test"));
        }
        Class<?> type(String name) throws ClassNotFoundException { return Class.forName(name, true, this); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.equals("electroblob.wizardry.item.SpellActions")) return super.loadClass(name, resolve);
            if (!name.startsWith("electroblob.wizardry.") && !name.startsWith("example.jawmscast.")) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
