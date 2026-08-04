package com.mahghuuuls.jawms.internal.rules;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class WizardryIntegrationRulesLoader {

    private static final String WANDS = "wand_upgrades";
    private static final String ARTEFACTS = "artefacts";
    private static final String SEAL = "demonic_seal";
    private static final String FONT = "font_of_mana";

    private WizardryIntegrationRulesLoader() {
    }

    public static WizardryIntegrationRules load(File file, long revision) {
        if (file == null) {
            throw new NullPointerException("file");
        }
        Configuration configuration = new Configuration(file);
        configuration.load();
        List<String> errors = new ArrayList<>();

        boolean storageEnabled = bool(configuration, WANDS, "storageEnabled",
                WizardryIntegrationRules.DEFAULT_STORAGE_ENABLED,
                "Enable Storage's JAWMS maximum-mana replacement.", errors);
        double storageIncrease = decimal(configuration, WANDS,
                "storageManaIncreasePercentPerLevel",
                WizardryIntegrationRules.DEFAULT_STORAGE_MANA_INCREASE_PER_LEVEL,
                "Percentage maximum-Mana increase per installed Storage level.", errors);
        boolean condenserEnabled = bool(configuration, WANDS, "condenserEnabled",
                WizardryIntegrationRules.DEFAULT_CONDENSER_ENABLED,
                "Enable Condenser's JAWMS mana-regen replacement.", errors);
        double condenserIncrease = decimal(configuration, WANDS,
                "condenserRegenIncreasePercentPerLevel",
                WizardryIntegrationRules.DEFAULT_CONDENSER_REGEN_INCREASE_PER_LEVEL,
                "Percentage Mana-regen increase per installed Condenser level.", errors);
        boolean siphonEnabled = bool(configuration, WANDS, "siphonEnabled",
                WizardryIntegrationRules.DEFAULT_SIPHON_ENABLED,
                "Enable Siphon's JAWMS kill restoration.", errors);
        int siphonMana = whole(configuration, WANDS, "siphonManaPerLevel",
                WizardryIntegrationRules.DEFAULT_SIPHON_MANA_PER_LEVEL,
                "Mana restored per installed Siphon level on a qualifying kill.", errors);
        WizardryIntegrationRules.HeldWandAggregation aggregation = aggregation(
                configuration, errors
        );
        boolean meleeEnabled = bool(configuration, WANDS, "meleeEnabled",
                WizardryIntegrationRules.DEFAULT_MELEE_ENABLED,
                "Enable Melee's JAWMS damage-for-mana replacement.", errors);
        double meleeDamage = decimal(configuration, WANDS, "meleeDamagePerLevel",
                WizardryIntegrationRules.DEFAULT_MELEE_DAMAGE_PER_LEVEL,
                "Additional melee damage per installed Melee level.", errors);
        int meleeMana = whole(configuration, WANDS, "meleeManaPerLevel",
                WizardryIntegrationRules.DEFAULT_MELEE_MANA_PER_LEVEL,
                "Mana cost per installed Melee level for an enhanced hit.", errors);

        boolean condensingEnabled = bool(configuration, ARTEFACTS, "condensingEnabled",
                WizardryIntegrationRules.DEFAULT_CONDENSING_ENABLED,
                "Enable Ring of Condensing's JAWMS Mana regen.", errors);
        double condensingRegen = decimal(configuration, ARTEFACTS,
                "condensingRegenPerRing",
                WizardryIntegrationRules.DEFAULT_CONDENSING_REGEN,
                "Flat Mana regen from each active Ring of Condensing.", errors);
        boolean siphoningEnabled = bool(configuration, ARTEFACTS, "siphoningEnabled",
                WizardryIntegrationRules.DEFAULT_SIPHONING_ENABLED,
                "Enable Ring of Siphoning's JAWMS restoration increase.", errors);
        double siphoningIncrease = decimal(configuration, ARTEFACTS, "siphoningIncreasePercent",
                WizardryIntegrationRules.DEFAULT_SIPHONING_INCREASE,
                "Percentage increase applied once to resolved Siphon restoration.", errors);
        boolean extractionEnabled = bool(configuration, ARTEFACTS, "extractionEnabled",
                WizardryIntegrationRules.DEFAULT_EXTRACTION_ENABLED,
                "Enable Ring of Extraction's JAWMS sorcery-kill restoration.", errors);
        int extractionMana = whole(configuration, ARTEFACTS, "extractionMana",
                WizardryIntegrationRules.DEFAULT_EXTRACTION_MANA,
                "Mana restored by a qualifying sorcery-spell kill.", errors);
        boolean perfectionistEnabled = bool(configuration, ARTEFACTS, "perfectionistEnabled",
                WizardryIntegrationRules.DEFAULT_PERFECTIONIST_ENABLED,
                "Enable Perfectionist's JAWMS Force Arrow refund.", errors);
        double perfectionistRefund = percentage(configuration, ARTEFACTS,
                "perfectionistRefundPercent",
                WizardryIntegrationRules.DEFAULT_PERFECTIONIST_REFUND,
                "Percentage of actual committed player Mana refunded by an eligible miss.", errors);

        boolean sealEnabled = bool(configuration, SEAL, "enabled",
                WizardryIntegrationRules.DEFAULT_DEMONIC_SEAL_ENABLED,
                "Allow Demonic Seal to cover wand-cast Mana shortfalls with hunger.", errors);
        int sealConversion = whole(configuration, SEAL, "manaPerHunger",
                WizardryIntegrationRules.DEFAULT_DEMONIC_SEAL_MANA_PER_HUNGER,
                "Mana shortfall covered by one whole hunger point. Must be at least 1 when enabled.", errors);
        if (sealEnabled && sealConversion < 1) {
            errors.add(SEAL + ".manaPerHunger must be at least 1 while enabled");
        }
        int minimumHunger = boundedWhole(configuration, SEAL, "minimumHunger",
                WizardryIntegrationRules.DEFAULT_DEMONIC_SEAL_MINIMUM_HUNGER,
                0, 20, "Minimum hunger retained after payment. Range: 0 through 20.", errors);
        boolean sealContinuous = bool(configuration, SEAL, "allowContinuous",
                WizardryIntegrationRules.DEFAULT_DEMONIC_SEAL_CONTINUOUS,
                "Allow Demonic Seal substitution for successful continuous-spell portions.", errors);

        boolean fontEnabled = bool(configuration, FONT, "enabled",
                WizardryIntegrationRules.DEFAULT_FONT_ENABLED,
                "Enable Font of Mana's JAWMS Mana-regen contribution.", errors);
        double fontRegen = decimal(configuration, FONT, "regenPerLevel",
                WizardryIntegrationRules.DEFAULT_FONT_REGEN_PER_LEVEL,
                "Flat Mana regen per Font effect level (amplifier plus one).", errors);
        boolean fontLockout = bool(configuration, FONT, "regenerateDuringLockout",
                WizardryIntegrationRules.DEFAULT_FONT_LOCKOUT_PERMISSION,
                "Permit regeneration during post-cast lockout while Font is active.", errors);
        boolean fontContinuous = bool(configuration, FONT, "regenerateDuringContinuous",
                WizardryIntegrationRules.DEFAULT_FONT_CONTINUOUS_PERMISSION,
                "Permit regeneration during continuous casting while Font is active.", errors);

        if (!errors.isEmpty()) {
            throw new InvalidIntegrationRulesException(errors);
        }
        if (configuration.hasChanged()) {
            configuration.save();
        }
        return new WizardryIntegrationRules(
                revision,
                storageEnabled, storageIncrease,
                condenserEnabled, condenserIncrease,
                siphonEnabled, siphonMana, aggregation,
                meleeEnabled, meleeDamage, meleeMana,
                condensingEnabled, condensingRegen,
                siphoningEnabled, siphoningIncrease,
                extractionEnabled, extractionMana,
                perfectionistEnabled, perfectionistRefund,
                sealEnabled, sealConversion, minimumHunger, sealContinuous,
                fontEnabled, fontRegen, fontLockout, fontContinuous
        );
    }

    private static WizardryIntegrationRules.HeldWandAggregation aggregation(
            Configuration configuration, List<String> errors
    ) {
        String value = text(configuration, WANDS, "heldWandAggregation",
                WizardryIntegrationRules.DEFAULT_HELD_WAND_AGGREGATION.name(),
                "How held-wand Storage, Condenser, and Siphon levels combine: HIGHEST or SUM.");
        try {
            return WizardryIntegrationRules.HeldWandAggregation.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            errors.add(WANDS + ".heldWandAggregation must be HIGHEST or SUM, found '" + value + "'");
            return WizardryIntegrationRules.DEFAULT_HELD_WAND_AGGREGATION;
        }
    }

    private static boolean bool(Configuration configuration, String category, String key,
                                boolean defaultValue, String comment, List<String> errors) {
        String value = text(configuration, category, key, Boolean.toString(defaultValue), comment);
        if ("true".equalsIgnoreCase(value.trim())) return true;
        if ("false".equalsIgnoreCase(value.trim())) return false;
        errors.add(category + "." + key + " must be true or false, found '" + value + "'");
        return defaultValue;
    }

    private static int whole(Configuration configuration, String category, String key,
                             int defaultValue, String comment, List<String> errors) {
        return boundedWhole(configuration, category, key, defaultValue, 0, Integer.MAX_VALUE,
                comment, errors);
    }

    private static int boundedWhole(Configuration configuration, String category, String key,
                                    int defaultValue, int minimum, int maximum, String comment,
                                    List<String> errors) {
        String value = text(configuration, category, key, Integer.toString(defaultValue), comment);
        try {
            int parsed = Integer.parseInt(value.trim());
            if (parsed < minimum || parsed > maximum) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException exception) {
            errors.add(category + "." + key + " must be a whole number in " + minimum
                    + ".." + maximum + ", found '" + value + "'");
            return defaultValue;
        }
    }

    private static double decimal(Configuration configuration, String category, String key,
                                  double defaultValue, String comment, List<String> errors) {
        String value = text(configuration, category, key, Double.toString(defaultValue), comment);
        try {
            double parsed = Double.parseDouble(value.trim());
            if (!Double.isFinite(parsed) || parsed < 0.0D) throw new NumberFormatException();
            return parsed;
        } catch (RuntimeException exception) {
            errors.add(category + "." + key
                    + " must be finite and non-negative, found '" + value + "'");
            return defaultValue;
        }
    }

    private static double percentage(Configuration configuration, String category, String key,
                                     double defaultValue, String comment, List<String> errors) {
        double value = decimal(configuration, category, key, defaultValue, comment, errors);
        if (value > 100.0D) {
            errors.add(category + "." + key + " must not exceed 100, found '" + value + "'");
            return defaultValue;
        }
        return value;
    }

    private static String text(Configuration configuration, String category, String key,
                               String defaultValue, String comment) {
        Property property = configuration.get(category, key, defaultValue, comment);
        return property.getString();
    }

    public static final class InvalidIntegrationRulesException extends IllegalArgumentException {
        private final List<String> errors;

        private InvalidIntegrationRulesException(List<String> errors) {
            super("Invalid Electroblob Wizardry integration rules: " + String.join("; ", errors));
            this.errors = new ArrayList<>(errors);
        }

        public List<String> getErrors() {
            return new ArrayList<>(errors);
        }
    }
}
