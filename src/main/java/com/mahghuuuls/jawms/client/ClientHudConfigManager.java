package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.Tags;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class ClientHudConfigManager {

    private static final String CATEGORY = "hud";
    private static volatile ClientHudSettings current = ClientHudSettings.DEFAULT;
    private static Configuration configuration;

    public static synchronized void configure(File configurationDirectory) {
        if (configurationDirectory == null) {
            throw new NullPointerException("configurationDirectory");
        }
        configuration = new Configuration(
                new File(configurationDirectory, Tags.MOD_ID + "-client.cfg")
        );
        reload();
    }

    public static ClientHudSettings current() {
        return current;
    }

    public static synchronized void reload() {
        requireConfigured();
        configuration.load();
        current = read(configuration, current);
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    public static synchronized List<IConfigElement> getConfigElements() {
        requireConfigured();
        return new ConfigElement(configuration.getCategory(CATEGORY)).getChildElements();
    }

    @SubscribeEvent
    public void configChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!Tags.MOD_ID.equals(event.getModID())) {
            return;
        }
        synchronized (ClientHudConfigManager.class) {
            requireConfigured();
            configuration.save();
            current = read(configuration, current);
        }
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Applied JAWMS client HUD configuration without restart."
        );
    }

    static ClientHudSettings read(
            Configuration source,
            ClientHudSettings previous
    ) {
        source.setCategoryLanguageKey(CATEGORY, "config.jawms.hud.category");

        boolean enabled = source.getBoolean(
                "enabled",
                CATEGORY,
                ClientHudSettings.DEFAULT.isEnabled(),
                "Show the built-in JAWMS mana HUD. Synchronization remains active when disabled."
        );
        boolean hideWhenFull = source.getBoolean(
                "hideWhenFull",
                CATEGORY,
                ClientHudSettings.DEFAULT.isHideWhenFull(),
                "Hide the mana HUD while current mana is full."
        );
        ClientHudSettings.TextDisplay textDisplay = textDisplay(source, previous);
        float textScale = source.getFloat(
                "textScale",
                CATEGORY,
                ClientHudSettings.DEFAULT.getTextScale(),
                0.5F,
                3.0F,
                "Scale multiplier for mana text. Range: 0.5 through 3.0."
        );
        ClientHudSettings.Anchor anchor = enumValue(
                source,
                "anchor",
                ClientHudSettings.DEFAULT.getAnchor(),
                previous.getAnchor(),
                ClientHudSettings.Anchor.values(),
                "Screen anchor used before applying signed offsets."
        );
        int offsetX = source.getInt(
                "offsetX",
                CATEGORY,
                ClientHudSettings.DEFAULT.getOffsetX(),
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "Signed horizontal offset from the selected anchor. The resolved bar remains on-screen."
        );
        int offsetY = source.getInt(
                "offsetY",
                CATEGORY,
                ClientHudSettings.DEFAULT.getOffsetY(),
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                "Signed vertical offset from the selected anchor. The resolved bar remains on-screen."
        );
        ClientHudSettings.FillDirection direction = enumValue(
                source,
                "fillDirection",
                ClientHudSettings.DEFAULT.getFillDirection(),
                previous.getFillDirection(),
                ClientHudSettings.FillDirection.values(),
                "Mana fill direction. Vertical directions rotate the configured length."
        );
        int length = source.getInt(
                "length",
                CATEGORY,
                ClientHudSettings.DEFAULT.getLength(),
                3,
                4096,
                "Bar length in scaled GUI pixels. Range: 3 through 4096."
        );
        int thickness = source.getInt(
                "thickness",
                CATEGORY,
                ClientHudSettings.DEFAULT.getThickness(),
                3,
                4096,
                "Bar thickness in scaled GUI pixels. Range: 3 through 4096."
        );
        return new ClientHudSettings(
                enabled,
                hideWhenFull,
                textDisplay,
                textScale,
                anchor,
                offsetX,
                offsetY,
                direction,
                length,
                thickness
        );
    }

    private static ClientHudSettings.TextDisplay textDisplay(
            Configuration source,
            ClientHudSettings previous
    ) {
        if (!source.hasKey(CATEGORY, "textDisplay") && source.hasKey(CATEGORY, "showNumbers")) {
            boolean legacyShowNumbers = source.getBoolean(
                    "showNumbers",
                    CATEGORY,
                    true,
                    "Legacy numeric visibility setting."
            );
            source.getCategory(CATEGORY).remove("showNumbers");
            ClientHudSettings.TextDisplay migrated = legacyShowNumbers
                    ? ClientHudSettings.TextDisplay.CURRENT_AND_MAXIMUM
                    : ClientHudSettings.TextDisplay.NONE;
            return enumValue(
                    source,
                    "textDisplay",
                    migrated,
                    migrated,
                    ClientHudSettings.TextDisplay.values(),
                    "Mana text: none, current mana, maximum mana, or current and maximum mana."
            );
        }
        return enumValue(
                source,
                "textDisplay",
                ClientHudSettings.DEFAULT.getTextDisplay(),
                previous.getTextDisplay(),
                ClientHudSettings.TextDisplay.values(),
                "Mana text: none, current mana, maximum mana, or current and maximum mana."
        );
    }

    private static <E extends Enum<E>> E enumValue(
            Configuration source,
            String key,
            E defaultValue,
            E previousValue,
            E[] values,
            String comment
    ) {
        String[] validValues = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            validValues[index] = values[index].name();
        }
        Property property = source.get(
                CATEGORY,
                key,
                defaultValue.name(),
                comment,
                Property.Type.STRING
        );
        property.setValidValues(validValues);
        String configured = property.getString().trim().toUpperCase(Locale.ROOT);
        try {
            @SuppressWarnings("unchecked")
            Class<E> enumType = (Class<E>) defaultValue.getDeclaringClass();
            return Enum.valueOf(enumType, configured);
        } catch (IllegalArgumentException exception) {
            JustAnotherWizardryManaSystemMod.LOGGER.warn(
                    "Ignored invalid JAWMS client option {}.{}='{}'; keeping {}.",
                    CATEGORY,
                    key,
                    property.getString(),
                    previousValue
            );
            return previousValue;
        }
    }

    private static void requireConfigured() {
        if (configuration == null) {
            throw new IllegalStateException("Client HUD configuration is not initialized.");
        }
    }
}
