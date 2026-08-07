package com.mahghuuuls.jawms.client;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.relauncher.FMLInjectionData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientHudConfigManagerTest {

    @BeforeAll
    static void initializeForgeConfigurationBasePath()
            throws ReflectiveOperationException {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, new File(".").getAbsoluteFile());
    }

    @Test
    void freshConfigurationProducesApprovedDefaults() throws IOException {
        Configuration configuration = configuration("fresh.cfg");
        ClientHudSettings settings = ClientHudConfigManager.read(
                configuration,
                ClientHudSettings.DEFAULT
        );

        assertTrue(settings.isEnabled());
        assertEquals(ClientHudSettings.TextDisplay.CURRENT, settings.getTextDisplay());
        assertEquals(0.65F, settings.getTextScale());
        assertEquals(ClientHudSettings.Anchor.BOTTOM_LEFT, settings.getAnchor());
        assertEquals(0, settings.getOffsetX());
        assertEquals(-50, settings.getOffsetY());
        assertEquals(
                ClientHudSettings.FillDirection.BOTTOM_TO_TOP,
                settings.getFillDirection()
        );
        assertEquals(81, settings.getLength());
        assertEquals(7, settings.getThickness());
    }

    @Test
    void configureGeneratesInspectableClientFileWithApprovedDefaults()
            throws IOException {
        File directory = Files.createTempDirectory("jawms-client-config-").toFile();
        directory.deleteOnExit();

        ClientHudConfigManager.configure(directory);

        File generated = new File(directory, "jawms-client.cfg");
        generated.deleteOnExit();
        assertTrue(generated.isFile());
        String text = new String(
                Files.readAllBytes(generated.toPath()),
                StandardCharsets.UTF_8
        );
        assertTrue(text.contains("B:enabled=true"));
        assertTrue(text.contains("B:hideWhenFull=true"));
        assertTrue(text.contains("S:textDisplay=CURRENT"));
        assertTrue(text.contains("textScale=0.65"));
        assertTrue(text.contains("S:anchor=BOTTOM_LEFT"));
        assertTrue(text.contains("I:offsetY=-50"));
        assertTrue(text.contains("S:fillDirection=BOTTOM_TO_TOP"));
        assertTrue(text.contains("I:length=81"));
        assertTrue(text.contains("I:thickness=7"));
    }

    @Test
    void validValuesApplyAndInvalidEnumKeepsPreviousValue() throws IOException {
        Configuration configuration = configuration("changed.cfg");
        configuration.get("hud", "enabled", true).set(false);
        configuration.get("hud", "hideWhenFull", true).set(false);
        configuration.get("hud", "showNumbers", true).set(false);
        configuration.get("hud", "anchor", "invalid").set("invalid");
        configuration.get("hud", "offsetX", 0).set(-42);
        configuration.get("hud", "offsetY", -50).set(73);
        configuration.get("hud", "fillDirection", "LEFT_TO_RIGHT")
                .set("BOTTOM_TO_TOP");
        configuration.get("hud", "length", 81).set(120);
        configuration.get("hud", "thickness", 5).set(9);
        configuration.get("hud", "textScale", 1.0D).set(1.5D);

        ClientHudSettings previous = new ClientHudSettings(
                true,
                true,
                ClientHudSettings.TextDisplay.CURRENT,
                1.0F,
                ClientHudSettings.Anchor.MIDDLE_RIGHT,
                0,
                0,
                ClientHudSettings.FillDirection.RIGHT_TO_LEFT,
                81,
                5
        );
        ClientHudSettings settings = ClientHudConfigManager.read(
                configuration,
                previous
        );

        assertFalse(settings.isEnabled());
        assertFalse(settings.isHideWhenFull());
        assertEquals(ClientHudSettings.TextDisplay.NONE, settings.getTextDisplay());
        assertTrue(configuration.hasKey("hud", "textDisplay"));
        assertFalse(configuration.hasKey("hud", "showNumbers"));
        assertEquals(1.5F, settings.getTextScale());
        assertEquals(ClientHudSettings.Anchor.MIDDLE_RIGHT, settings.getAnchor());
        assertEquals(-42, settings.getOffsetX());
        assertEquals(73, settings.getOffsetY());
        assertEquals(
                ClientHudSettings.FillDirection.BOTTOM_TO_TOP,
                settings.getFillDirection()
        );
        assertEquals(120, settings.getLength());
        assertEquals(9, settings.getThickness());
    }

    @Test
    void textDisplaySupportsAllPlayerFacingForms() {
        assertEquals("", ClientHudSettings.TextDisplay.NONE.format(25, 50));
        assertEquals("25", ClientHudSettings.TextDisplay.CURRENT.format(25, 50));
        assertEquals("50", ClientHudSettings.TextDisplay.MAXIMUM.format(25, 50));
        assertEquals(
                "25 / 50",
                ClientHudSettings.TextDisplay.CURRENT_AND_MAXIMUM.format(25, 50)
        );
    }

    private Configuration configuration(String filename) throws IOException {
        File file = File.createTempFile("jawms-", "-" + filename);
        if (!file.delete()) {
            throw new IOException("Could not prepare temporary config path.");
        }
        file.deleteOnExit();
        Configuration configuration = new Configuration(file);
        configuration.load();
        return configuration;
    }
}
