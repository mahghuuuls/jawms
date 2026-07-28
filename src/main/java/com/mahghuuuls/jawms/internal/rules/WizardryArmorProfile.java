package com.mahghuuuls.jawms.internal.rules;

public final class WizardryArmorProfile {

    public static final WizardryArmorProfile DEFAULT = new WizardryArmorProfile(
            true,
            15,
            0.5D,
            15,
            0.5D,
            10,
            0.5D,
            10,
            0.5D
    );

    private final boolean enabled;
    private final int helmetMaximum;
    private final double helmetRegeneration;
    private final int chestMaximum;
    private final double chestRegeneration;
    private final int legsMaximum;
    private final double legsRegeneration;
    private final int bootsMaximum;
    private final double bootsRegeneration;

    public WizardryArmorProfile(
            boolean enabled,
            int helmetMaximum,
            double helmetRegeneration,
            int chestMaximum,
            double chestRegeneration,
            int legsMaximum,
            double legsRegeneration,
            int bootsMaximum,
            double bootsRegeneration
    ) {
        requireFinite(helmetRegeneration);
        requireFinite(chestRegeneration);
        requireFinite(legsRegeneration);
        requireFinite(bootsRegeneration);
        this.enabled = enabled;
        this.helmetMaximum = helmetMaximum;
        this.helmetRegeneration = helmetRegeneration;
        this.chestMaximum = chestMaximum;
        this.chestRegeneration = chestRegeneration;
        this.legsMaximum = legsMaximum;
        this.legsRegeneration = legsRegeneration;
        this.bootsMaximum = bootsMaximum;
        this.bootsRegeneration = bootsRegeneration;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getHelmetMaximum() {
        return helmetMaximum;
    }

    public double getHelmetRegeneration() {
        return helmetRegeneration;
    }

    public int getChestMaximum() {
        return chestMaximum;
    }

    public double getChestRegeneration() {
        return chestRegeneration;
    }

    public int getLegsMaximum() {
        return legsMaximum;
    }

    public double getLegsRegeneration() {
        return legsRegeneration;
    }

    public int getBootsMaximum() {
        return bootsMaximum;
    }

    public double getBootsRegeneration() {
        return bootsRegeneration;
    }

    private static void requireFinite(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Armor regeneration values must be finite.");
        }
    }
}
