package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

public final class GameplayRulesLoader {

    private static final String MANA = "mana";
    private static final String SPELLS = "spells";
    private static final String FLASKS = "flasks";
    private static final String ITEMS = "item_modifiers";
    private static final String ARMOR = "wizardry_armor_defaults";
    private static final String PROVIDERS = "modifier_providers";

    private GameplayRulesLoader() {
    }

    public static GameplayRules load(
            File file,
            GameplayRules previous,
            long revision,
            List<SpellCostCompiler.SpellDefinition> spells,
            Consumer<String> warning
    ) {
        Configuration configuration = new Configuration(file);
        configuration.load();

        int baseMaximum = positiveInt(
                string(configuration, MANA, "baseMaximum", Integer.toString(GameplayRules.DEFAULT_BASE_MAXIMUM),
                        "Base player mana before equipped modifiers. Range: 1 through 2147483647."),
                previous.getBaseMaximum(),
                MANA + ".baseMaximum",
                warning
        );
        double baseRegeneration = nonNegativeDouble(
                string(configuration, MANA, "baseRegeneration",
                        Double.toString(GameplayRules.DEFAULT_BASE_REGENERATION),
                        "Base mana regenerated per second before equipped modifiers."),
                previous.getBaseRegeneration(),
                MANA + ".baseRegeneration",
                warning
        );
        double previousLockoutSeconds = previous.getRegenerationLockoutSeconds();
        double lockoutSeconds = nonNegativeDouble(
                string(configuration, MANA, "regenerationLockoutSeconds",
                        Double.toString(GameplayRules.DEFAULT_LOCKOUT_SECONDS),
                        "Seconds regeneration remains paused after a successful survival wand cast. Zero disables post-cast lockout."),
                previousLockoutSeconds,
                MANA + ".regenerationLockoutSeconds",
                warning
        );

        String formula = string(
                configuration,
                SPELLS,
                "formula",
                GameplayRules.DEFAULT_COST_FORMULA,
                "Spell cost expression. Variables: original_cost and tier. Per-spell overrides win."
        ).trim();
        Map<ResourceLocation, Integer> overrides = parseOverrides(
                configuration.get(
                        SPELLS,
                        "overrides",
                        new String[0],
                        "One per line: namespace:spell=non-negative-whole-cost"
                ).getStringList(),
                warning
        );

        int smallFlask = nonNegativeInt(
                string(configuration, FLASKS, "small", Integer.toString(GameplayRules.DEFAULT_SMALL_FLASK),
                        "Small mana flask restoration budget."),
                previous.getSmallFlask(),
                FLASKS + ".small",
                warning
        );
        int mediumFlask = nonNegativeInt(
                string(configuration, FLASKS, "medium", Integer.toString(GameplayRules.DEFAULT_MEDIUM_FLASK),
                        "Medium mana flask restoration budget."),
                previous.getMediumFlask(),
                FLASKS + ".medium",
                warning
        );
        int largeFlask = nonNegativeInt(
                string(configuration, FLASKS, "large", Integer.toString(GameplayRules.DEFAULT_LARGE_FLASK),
                        "Large mana flask restoration budget."),
                previous.getLargeFlask(),
                FLASKS + ".large",
                warning
        );

        List<ConfiguredItemRule> itemRules = parseItemRules(
                configuration.get(
                        ITEMS,
                        "rules",
                        new String[0],
                        "One per line: namespace:item[@metadata-or-*]=field:value;field:value. "
                                + "Fields: flat_maximum, maximum_increase, maximum_reduction, "
                                + "flat_regeneration, regeneration_increase, regeneration_reduction, "
                                + "flat_lockout, lockout_increase, lockout_reduction, "
                                + "global_efficiency, efficiency_<element>, "
                                + "regenerate_during_lockout, regenerate_during_continuous. "
                                + "Legacy maximum,flat_regeneration,signed_regeneration_percent is migrated."
                ).getStringList(),
                warning
        );
        WizardryArmorProfile profile = loadArmorProfile(configuration, previous.getArmorProfile(), warning);
        Set<ResourceLocation> disabledProviderIds = parseProviderIds(
                configuration.get(
                        PROVIDERS,
                        "disabledProviderIds",
                        new String[0],
                        "Globally unique namespace:path IDs of addon modifier providers to disable."
                ).getStringList(),
                warning
        );

        Map<ResourceLocation, Double> spellCosts = SpellCostCompiler.compile(
                formula,
                overrides,
                spells,
                warning
        );

        if (configuration.hasChanged()) {
            configuration.save();
        }

        return new GameplayRules(
                revision,
                baseMaximum,
                baseRegeneration,
                lockoutSeconds,
                formula,
                smallFlask,
                mediumFlask,
                largeFlask,
                spellCosts,
                itemRules,
                profile,
                disabledProviderIds
        );
    }

    private static Set<ResourceLocation> parseProviderIds(
            String[] entries,
            Consumer<String> warning
    ) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            try {
                ResourceLocation id = fullIdentifier(entry);
                if (!ids.add(id)) {
                    warning.accept("Ignored duplicate modifier_providers.disabledProviderIds entry '"
                            + id + "'.");
                }
            } catch (RuntimeException exception) {
                warning.accept("Ignored invalid modifier_providers.disabledProviderIds entry '"
                        + raw + "': " + exception.getMessage());
            }
        }
        return ids;
    }

    private static WizardryArmorProfile loadArmorProfile(
            Configuration configuration,
            WizardryArmorProfile previous,
            Consumer<String> warning
    ) {
        Property enabledProperty;
        if (configuration.hasKey(ARMOR, "enabled")) {
            enabledProperty = configuration.getCategory(ARMOR).get("enabled");
        } else {
            enabledProperty = configuration.get(
                    ARMOR,
                    "enabled",
                    WizardryArmorProfile.DEFAULT.isEnabled(),
                    "Apply the slot defaults to Electroblob's Wizardry ItemWizardArmour entries."
            );
        }
        boolean enabled = booleanValue(
                enabledProperty.getString(),
                previous.isEnabled(),
                ARMOR + ".enabled",
                warning
        );
        return new WizardryArmorProfile(
                enabled,
                signedInt(configuration, "helmetMaximum", 15, previous.getHelmetMaximum(), warning),
                signedDouble(configuration, "helmetFlatRegeneration", 0.5D,
                        previous.getHelmetRegeneration(), warning),
                signedInt(configuration, "chestMaximum", 15, previous.getChestMaximum(), warning),
                signedDouble(configuration, "chestFlatRegeneration", 0.5D,
                        previous.getChestRegeneration(), warning),
                signedInt(configuration, "legsMaximum", 10, previous.getLegsMaximum(), warning),
                signedDouble(configuration, "legsFlatRegeneration", 0.5D,
                        previous.getLegsRegeneration(), warning),
                signedInt(configuration, "bootsMaximum", 10, previous.getBootsMaximum(), warning),
                signedDouble(configuration, "bootsFlatRegeneration", 0.5D,
                        previous.getBootsRegeneration(), warning)
        );
    }

    private static boolean booleanValue(
            String value,
            boolean previous,
            String key,
            Consumer<String> warning
    ) {
        String normalized = value.trim();
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        warning.accept("Invalid " + key + "='" + value + "'; keeping " + previous + ".");
        return previous;
    }

    private static int signedInt(
            Configuration configuration,
            String key,
            int defaultValue,
            int previous,
            Consumer<String> warning
    ) {
        return integer(
                string(configuration, ARMOR, key, Integer.toString(defaultValue),
                        "Signed maximum-mana contribution for this armor slot."),
                previous,
                ARMOR + "." + key,
                warning
        );
    }

    private static double signedDouble(
            Configuration configuration,
            String key,
            double defaultValue,
            double previous,
            Consumer<String> warning
    ) {
        return finiteDouble(
                string(configuration, ARMOR, key, Double.toString(defaultValue),
                        "Signed flat mana-per-second contribution for this armor slot."),
                previous,
                ARMOR + "." + key,
                warning
        );
    }

    private static Map<ResourceLocation, Integer> parseOverrides(String[] entries, Consumer<String> warning) {
        Map<ResourceLocation, Integer> overrides = new LinkedHashMap<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                warning.accept("Ignored invalid spells.override entry '" + raw
                        + "'; expected namespace:spell=cost.");
                continue;
            }
            try {
                ResourceLocation id = fullIdentifier(entry.substring(0, separator).trim());
                int cost = parseNonNegativeInt(entry.substring(separator + 1).trim());
                if (overrides.containsKey(id)) {
                    warning.accept("Ignored duplicate spells.override entry for '" + id + "'.");
                    continue;
                }
                overrides.put(id, cost);
            } catch (RuntimeException exception) {
                warning.accept("Ignored invalid spells.override entry '" + raw + "': "
                        + exception.getMessage());
            }
        }
        return overrides;
    }

    private static List<ConfiguredItemRule> parseItemRules(String[] entries, Consumer<String> warning) {
        Map<String, ConfiguredItemRule> rules = new LinkedHashMap<>();
        for (String raw : entries) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf('=');
            if (separator <= 0 || separator == entry.length() - 1) {
                warning.accept("Ignored invalid item_modifiers.rules entry '" + raw
                        + "'; expected namespace:item[@metadata-or-*]=field:value;field:value.");
                continue;
            }
            try {
                String selector = entry.substring(0, separator).trim();
                int metadata = ConfiguredItemRule.WILDCARD_METADATA;
                int metadataSeparator = selector.lastIndexOf('@');
                if (metadataSeparator >= 0) {
                    String metadataText = selector.substring(metadataSeparator + 1).trim();
                    selector = selector.substring(0, metadataSeparator).trim();
                    if (!"*".equals(metadataText)) {
                        metadata = Integer.parseInt(metadataText);
                    }
                }
                ResourceLocation itemId = fullIdentifier(selector);
                if (!Item.REGISTRY.containsKey(itemId)) {
                    throw new IllegalArgumentException("unknown item '" + itemId + "'");
                }
                String contributionText = entry.substring(separator + 1).trim();
                ConfiguredItemRule rule = new ConfiguredItemRule(
                        itemId,
                        metadata,
                        parseContribution(contributionText)
                );
                if (rules.containsKey(rule.key())) {
                    warning.accept("Ignored duplicate item_modifiers.rules entry for '" + rule.key() + "'.");
                    continue;
                }
                rules.put(rule.key(), rule);
            } catch (RuntimeException exception) {
                warning.accept("Ignored invalid item_modifiers.rules entry '" + raw + "': "
                        + exception.getMessage());
            }
        }
        return new ArrayList<>(rules.values());
    }

    static ManaContribution parseContribution(String value) {
        if (value.indexOf(';') < 0 && value.indexOf(':') < 0) {
            String[] legacy = value.split(",", -1);
            if (legacy.length != 3) {
                throw new IllegalArgumentException(
                        "expected field:value pairs or three legacy comma-separated values"
                );
            }
            double signedRegenerationPercentage = parseFinite(legacy[2].trim());
            return ManaContribution.builder()
                    .flatMaximumMana(Integer.parseInt(legacy[0].trim()))
                    .flatRegeneration(parseFinite(legacy[1].trim()))
                    .regenerationIncrease(Math.max(0.0D, signedRegenerationPercentage))
                    .regenerationReduction(Math.max(0.0D, -signedRegenerationPercentage))
                    .build();
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (String rawField : value.split(";", -1)) {
            String field = rawField.trim();
            if (field.isEmpty()) {
                continue;
            }
            int separator = field.indexOf(':');
            if (separator <= 0 || separator == field.length() - 1) {
                throw new IllegalArgumentException("invalid contribution field '" + rawField + "'");
            }
            String name = field.substring(0, separator).trim().toLowerCase();
            String fieldValue = field.substring(separator + 1).trim();
            if (fields.put(name, fieldValue) != null) {
                throw new IllegalArgumentException("duplicate contribution field '" + name + "'");
            }
        }

        rejectMixedLegacy(fields, "maximum_percent", "maximum_increase", "maximum_reduction");
        rejectMixedLegacy(
                fields,
                "regeneration_percent",
                "regeneration_increase",
                "regeneration_reduction"
        );
        rejectMixedLegacy(fields, "lockout_percent", "lockout_increase", "lockout_reduction");

        ManaContribution.Builder builder = ManaContribution.builder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            String name = field.getKey();
            String fieldValue = field.getValue();
            switch (name) {
                case "flat_maximum":
                    builder.flatMaximumMana(Integer.parseInt(fieldValue));
                    break;
                case "maximum_increase":
                    builder.maximumManaIncrease(nonNegative(fieldValue, name));
                    break;
                case "maximum_reduction":
                    builder.maximumManaReduction(reduction(fieldValue, name));
                    break;
                case "maximum_percent":
                    applyLegacyMaximum(builder, parseFinite(fieldValue));
                    break;
                case "flat_regeneration":
                    builder.flatRegeneration(parseFinite(fieldValue));
                    break;
                case "regeneration_increase":
                    builder.regenerationIncrease(nonNegative(fieldValue, name));
                    break;
                case "regeneration_reduction":
                    builder.regenerationReduction(reduction(fieldValue, name));
                    break;
                case "regeneration_percent":
                    applyLegacyRegeneration(builder, parseFinite(fieldValue));
                    break;
                case "flat_lockout":
                    builder.flatLockoutSeconds(parseFinite(fieldValue));
                    break;
                case "lockout_increase":
                    builder.lockoutIncrease(nonNegative(fieldValue, name));
                    break;
                case "lockout_reduction":
                    builder.lockoutReduction(reduction(fieldValue, name));
                    break;
                case "lockout_percent":
                    applyLegacyLockout(builder, parseFinite(fieldValue));
                    break;
                case "global_efficiency":
                    builder.globalSpellEfficiency(nonNegative(fieldValue, name));
                    break;
                case "regenerate_during_lockout":
                    builder.regenerateDuringPostCastLockout(strictBoolean(fieldValue, name));
                    break;
                case "regenerate_during_continuous":
                    builder.regenerateDuringContinuousCasting(strictBoolean(fieldValue, name));
                    break;
                default:
                    if (name.startsWith("efficiency_")) {
                        Element element = Element.fromName(name.substring("efficiency_".length()));
                        builder.elementSpellEfficiency(
                                element,
                                nonNegative(fieldValue, name)
                        );
                    } else {
                        throw new IllegalArgumentException("unknown contribution field '" + name + "'");
                    }
            }
        }
        return builder.build();
    }

    private static void rejectMixedLegacy(
            Map<String, String> fields,
            String legacy,
            String increase,
            String reduction
    ) {
        if (fields.containsKey(legacy)
                && (fields.containsKey(increase) || fields.containsKey(reduction))) {
            throw new IllegalArgumentException(
                    "legacy field '" + legacy + "' cannot be mixed with replacement fields"
            );
        }
    }

    private static void applyLegacyMaximum(ManaContribution.Builder builder, double value) {
        if (value >= 0.0D) {
            builder.maximumManaIncrease(value);
        } else {
            builder.maximumManaReduction(-value);
        }
    }

    private static void applyLegacyRegeneration(ManaContribution.Builder builder, double value) {
        if (value >= 0.0D) {
            builder.regenerationIncrease(value);
        } else {
            builder.regenerationReduction(-value);
        }
    }

    private static void applyLegacyLockout(ManaContribution.Builder builder, double value) {
        if (value >= 0.0D) {
            builder.lockoutIncrease(value);
        } else {
            builder.lockoutReduction(-value);
        }
    }

    private static double nonNegative(String value, String name) {
        double parsed = parseFinite(value);
        if (parsed < 0.0D) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return parsed;
    }

    private static double reduction(String value, String name) {
        double parsed = nonNegative(value, name);
        if (parsed > 100.0D) {
            throw new IllegalArgumentException(name + " must not exceed 100");
        }
        return parsed;
    }

    private static boolean strictBoolean(String value, String name) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(name + " must be true or false");
    }

    private static String string(
            Configuration configuration,
            String category,
            String key,
            String defaultValue,
            String comment
    ) {
        Property property = configuration.get(category, key, defaultValue, comment);
        return property.getString();
    }

    private static int positiveInt(String value, int previous, String path, Consumer<String> warning) {
        int parsed = integer(value, previous, path, warning);
        if (parsed < 1) {
            warning.accept("Invalid " + path + "='" + value + "'; keeping " + previous + ".");
            return previous;
        }
        return parsed;
    }

    private static int nonNegativeInt(String value, int previous, String path, Consumer<String> warning) {
        try {
            return parseNonNegativeInt(value);
        } catch (RuntimeException exception) {
            warning.accept("Invalid " + path + "='" + value + "'; keeping " + previous + ".");
            return previous;
        }
    }

    private static int integer(String value, int previous, String path, Consumer<String> warning) {
        try {
            return Integer.parseInt(value.trim());
        } catch (RuntimeException exception) {
            warning.accept("Invalid " + path + "='" + value + "'; keeping " + previous + ".");
            return previous;
        }
    }

    private static double nonNegativeDouble(
            String value,
            double previous,
            String path,
            Consumer<String> warning
    ) {
        double parsed = finiteDouble(value, previous, path, warning);
        if (parsed < 0.0D) {
            warning.accept("Invalid " + path + "='" + value + "'; keeping " + previous + ".");
            return previous;
        }
        return parsed;
    }

    private static double finiteDouble(
            String value,
            double previous,
            String path,
            Consumer<String> warning
    ) {
        try {
            return parseFinite(value);
        } catch (RuntimeException exception) {
            warning.accept("Invalid " + path + "='" + value + "'; keeping " + previous + ".");
            return previous;
        }
    }

    private static int parseNonNegativeInt(String value) {
        int parsed = Integer.parseInt(value);
        if (parsed < 0) {
            throw new IllegalArgumentException("value must be non-negative");
        }
        return parsed;
    }

    private static double parseFinite(String value) {
        double parsed = Double.parseDouble(value);
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("value must be finite");
        }
        return parsed;
    }

    private static ResourceLocation fullIdentifier(String value) {
        int namespaceSeparator = value.indexOf(':');
        if (namespaceSeparator <= 0 || namespaceSeparator == value.length() - 1) {
            throw new IllegalArgumentException("a full namespace:path registry identifier is required");
        }
        return new ResourceLocation(value);
    }
}
