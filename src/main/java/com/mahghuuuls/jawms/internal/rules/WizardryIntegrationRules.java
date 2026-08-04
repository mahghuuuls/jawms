package com.mahghuuuls.jawms.internal.rules;

/** Immutable accepted rules for JAWMS's built-in Electroblob Wizardry integration. */
public final class WizardryIntegrationRules {

    public enum HeldWandAggregation {
        HIGHEST,
        SUM
    }

    public static final boolean DEFAULT_STORAGE_ENABLED = true;
    public static final double DEFAULT_STORAGE_MANA_INCREASE_PER_LEVEL = 10.0D;
    public static final boolean DEFAULT_CONDENSER_ENABLED = true;
    public static final double DEFAULT_CONDENSER_REGEN_INCREASE_PER_LEVEL = 10.0D;
    public static final boolean DEFAULT_SIPHON_ENABLED = true;
    public static final int DEFAULT_SIPHON_MANA_PER_LEVEL = 3;
    public static final HeldWandAggregation DEFAULT_HELD_WAND_AGGREGATION =
            HeldWandAggregation.HIGHEST;
    public static final boolean DEFAULT_MELEE_ENABLED = true;
    public static final double DEFAULT_MELEE_DAMAGE_PER_LEVEL = 2.0D;
    public static final int DEFAULT_MELEE_MANA_PER_LEVEL = 4;
    public static final boolean DEFAULT_CONDENSING_ENABLED = true;
    public static final double DEFAULT_CONDENSING_REGEN = 0.5D;
    public static final boolean DEFAULT_SIPHONING_ENABLED = true;
    public static final double DEFAULT_SIPHONING_INCREASE = 30.0D;
    public static final boolean DEFAULT_EXTRACTION_ENABLED = true;
    public static final int DEFAULT_EXTRACTION_MANA = 5;
    public static final boolean DEFAULT_PERFECTIONIST_ENABLED = true;
    public static final double DEFAULT_PERFECTIONIST_REFUND = 100.0D;
    public static final boolean DEFAULT_DEMONIC_SEAL_ENABLED = true;
    public static final int DEFAULT_DEMONIC_SEAL_MANA_PER_HUNGER = 5;
    public static final int DEFAULT_DEMONIC_SEAL_MINIMUM_HUNGER = 6;
    public static final boolean DEFAULT_DEMONIC_SEAL_CONTINUOUS = false;
    public static final boolean DEFAULT_FONT_ENABLED = true;
    public static final double DEFAULT_FONT_REGEN_PER_LEVEL = 1.0D;
    public static final boolean DEFAULT_FONT_LOCKOUT_PERMISSION = true;
    public static final boolean DEFAULT_FONT_CONTINUOUS_PERMISSION = false;

    private final long revision;
    private final boolean storageEnabled;
    private final double storageManaIncreasePerLevel;
    private final boolean condenserEnabled;
    private final double condenserRegenerationIncreasePerLevel;
    private final boolean siphonEnabled;
    private final int siphonManaPerLevel;
    private final HeldWandAggregation heldWandAggregation;
    private final boolean meleeEnabled;
    private final double meleeDamagePerLevel;
    private final int meleeManaPerLevel;
    private final boolean condensingEnabled;
    private final double condensingRegenerationPerRing;
    private final boolean siphoningEnabled;
    private final double siphoningIncrease;
    private final boolean extractionEnabled;
    private final int extractionMana;
    private final boolean perfectionistEnabled;
    private final double perfectionistRefund;
    private final boolean demonicSealEnabled;
    private final int demonicSealManaPerHunger;
    private final int demonicSealMinimumHunger;
    private final boolean demonicSealContinuous;
    private final boolean fontEnabled;
    private final double fontRegenerationPerLevel;
    private final boolean fontLockoutPermission;
    private final boolean fontContinuousPermission;

    public WizardryIntegrationRules(
            long revision,
            boolean storageEnabled, double storageManaIncreasePerLevel,
            boolean condenserEnabled, double condenserRegenerationIncreasePerLevel,
            boolean siphonEnabled, int siphonManaPerLevel,
            HeldWandAggregation heldWandAggregation,
            boolean meleeEnabled, double meleeDamagePerLevel, int meleeManaPerLevel,
            boolean condensingEnabled, double condensingRegenerationPerRing,
            boolean siphoningEnabled, double siphoningIncrease,
            boolean extractionEnabled, int extractionMana,
            boolean perfectionistEnabled, double perfectionistRefund,
            boolean demonicSealEnabled, int demonicSealManaPerHunger,
            int demonicSealMinimumHunger, boolean demonicSealContinuous,
            boolean fontEnabled, double fontRegenerationPerLevel,
            boolean fontLockoutPermission, boolean fontContinuousPermission
    ) {
        if (revision < 0L) {
            throw new IllegalArgumentException("Integration revision must be non-negative.");
        }
        requireNonNegativeFinite(storageManaIncreasePerLevel,
                "storageManaIncreasePerLevel");
        requireNonNegativeFinite(condenserRegenerationIncreasePerLevel,
                "condenserRegenerationIncreasePerLevel");
        requireNonNegative(siphonManaPerLevel, "siphonManaPerLevel");
        if (heldWandAggregation == null) {
            throw new NullPointerException("heldWandAggregation");
        }
        requireNonNegativeFinite(meleeDamagePerLevel, "meleeDamagePerLevel");
        requireNonNegative(meleeManaPerLevel, "meleeManaPerLevel");
        requireNonNegativeFinite(condensingRegenerationPerRing,
                "condensingRegenerationPerRing");
        requireNonNegativeFinite(siphoningIncrease, "siphoningIncrease");
        requireNonNegative(extractionMana, "extractionMana");
        requirePercentage(perfectionistRefund, "perfectionistRefund");
        requireNonNegative(demonicSealManaPerHunger, "demonicSealManaPerHunger");
        if (demonicSealEnabled && demonicSealManaPerHunger < 1) {
            throw new IllegalArgumentException(
                    "Enabled Demonic Seal mana per hunger must be at least one."
            );
        }
        if (demonicSealMinimumHunger < 0 || demonicSealMinimumHunger > 20) {
            throw new IllegalArgumentException("Demonic Seal minimum hunger must be 0..20.");
        }
        requireNonNegativeFinite(fontRegenerationPerLevel, "fontRegenerationPerLevel");
        this.revision = revision;
        this.storageEnabled = storageEnabled;
        this.storageManaIncreasePerLevel = storageManaIncreasePerLevel;
        this.condenserEnabled = condenserEnabled;
        this.condenserRegenerationIncreasePerLevel = condenserRegenerationIncreasePerLevel;
        this.siphonEnabled = siphonEnabled;
        this.siphonManaPerLevel = siphonManaPerLevel;
        this.heldWandAggregation = heldWandAggregation;
        this.meleeEnabled = meleeEnabled;
        this.meleeDamagePerLevel = meleeDamagePerLevel;
        this.meleeManaPerLevel = meleeManaPerLevel;
        this.condensingEnabled = condensingEnabled;
        this.condensingRegenerationPerRing = condensingRegenerationPerRing;
        this.siphoningEnabled = siphoningEnabled;
        this.siphoningIncrease = siphoningIncrease;
        this.extractionEnabled = extractionEnabled;
        this.extractionMana = extractionMana;
        this.perfectionistEnabled = perfectionistEnabled;
        this.perfectionistRefund = perfectionistRefund;
        this.demonicSealEnabled = demonicSealEnabled;
        this.demonicSealManaPerHunger = demonicSealManaPerHunger;
        this.demonicSealMinimumHunger = demonicSealMinimumHunger;
        this.demonicSealContinuous = demonicSealContinuous;
        this.fontEnabled = fontEnabled;
        this.fontRegenerationPerLevel = fontRegenerationPerLevel;
        this.fontLockoutPermission = fontLockoutPermission;
        this.fontContinuousPermission = fontContinuousPermission;
    }

    public static WizardryIntegrationRules defaults(long revision) {
        return new WizardryIntegrationRules(
                revision,
                DEFAULT_STORAGE_ENABLED, DEFAULT_STORAGE_MANA_INCREASE_PER_LEVEL,
                DEFAULT_CONDENSER_ENABLED, DEFAULT_CONDENSER_REGEN_INCREASE_PER_LEVEL,
                DEFAULT_SIPHON_ENABLED, DEFAULT_SIPHON_MANA_PER_LEVEL,
                DEFAULT_HELD_WAND_AGGREGATION,
                DEFAULT_MELEE_ENABLED, DEFAULT_MELEE_DAMAGE_PER_LEVEL,
                DEFAULT_MELEE_MANA_PER_LEVEL,
                DEFAULT_CONDENSING_ENABLED, DEFAULT_CONDENSING_REGEN,
                DEFAULT_SIPHONING_ENABLED, DEFAULT_SIPHONING_INCREASE,
                DEFAULT_EXTRACTION_ENABLED, DEFAULT_EXTRACTION_MANA,
                DEFAULT_PERFECTIONIST_ENABLED, DEFAULT_PERFECTIONIST_REFUND,
                DEFAULT_DEMONIC_SEAL_ENABLED, DEFAULT_DEMONIC_SEAL_MANA_PER_HUNGER,
                DEFAULT_DEMONIC_SEAL_MINIMUM_HUNGER, DEFAULT_DEMONIC_SEAL_CONTINUOUS,
                DEFAULT_FONT_ENABLED, DEFAULT_FONT_REGEN_PER_LEVEL,
                DEFAULT_FONT_LOCKOUT_PERMISSION, DEFAULT_FONT_CONTINUOUS_PERMISSION
        );
    }

    public long getRevision() { return revision; }
    public boolean isStorageEnabled() { return storageEnabled; }
    public double getStorageManaIncreasePerLevel() { return storageManaIncreasePerLevel; }
    public boolean isCondenserEnabled() { return condenserEnabled; }
    public double getCondenserRegenerationIncreasePerLevel() {
        return condenserRegenerationIncreasePerLevel;
    }
    public boolean isSiphonEnabled() { return siphonEnabled; }
    public int getSiphonManaPerLevel() { return siphonManaPerLevel; }
    public HeldWandAggregation getHeldWandAggregation() { return heldWandAggregation; }
    public boolean isMeleeEnabled() { return meleeEnabled; }
    public double getMeleeDamagePerLevel() { return meleeDamagePerLevel; }
    public int getMeleeManaPerLevel() { return meleeManaPerLevel; }
    public boolean isCondensingEnabled() { return condensingEnabled; }
    public double getCondensingRegenerationPerRing() {
        return condensingRegenerationPerRing;
    }
    public boolean isSiphoningEnabled() { return siphoningEnabled; }
    public double getSiphoningIncrease() { return siphoningIncrease; }
    public boolean isExtractionEnabled() { return extractionEnabled; }
    public int getExtractionMana() { return extractionMana; }
    public boolean isPerfectionistEnabled() { return perfectionistEnabled; }
    public double getPerfectionistRefund() { return perfectionistRefund; }
    public boolean isDemonicSealEnabled() { return demonicSealEnabled; }
    public int getDemonicSealManaPerHunger() { return demonicSealManaPerHunger; }
    public int getDemonicSealMinimumHunger() { return demonicSealMinimumHunger; }
    public boolean isDemonicSealContinuous() { return demonicSealContinuous; }
    public boolean isFontEnabled() { return fontEnabled; }
    public double getFontRegenerationPerLevel() { return fontRegenerationPerLevel; }
    public boolean isFontLockoutPermission() { return fontLockoutPermission; }
    public boolean isFontContinuousPermission() { return fontContinuousPermission; }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative.");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }

    private static void requirePercentage(double value, String name) {
        requireNonNegativeFinite(value, name);
        if (value > 100.0D) {
            throw new IllegalArgumentException(name + " must not exceed 100.");
        }
    }
}
