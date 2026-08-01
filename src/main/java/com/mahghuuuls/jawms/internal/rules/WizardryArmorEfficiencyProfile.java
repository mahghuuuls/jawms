package com.mahghuuuls.jawms.internal.rules;

import electroblob.wizardry.item.ItemWizardArmour;

/** Immutable server assignments replacing Wizardry armor cost reductions. */
public final class WizardryArmorEfficiencyProfile {

    public static final WizardryArmorEfficiencyProfile DEFAULT =
            new WizardryArmorEfficiencyProfile(true, 10.0D, 20.0D, 5.0D, 10.0D, 20.0D);

    private final boolean enabled;
    private final double wizard;
    private final double sage;
    private final double battlemage;
    private final double warlock;
    private final double sageNonmatchingFullSet;

    public WizardryArmorEfficiencyProfile(
            boolean enabled,
            double wizard,
            double sage,
            double battlemage,
            double warlock,
            double sageNonmatchingFullSet
    ) {
        this.enabled = enabled;
        this.wizard = valid(wizard, "wizard");
        this.sage = valid(sage, "sage");
        this.battlemage = valid(battlemage, "battlemage");
        this.warlock = valid(warlock, "warlock");
        this.sageNonmatchingFullSet = valid(
                sageNonmatchingFullSet, "sageNonmatchingFullSet"
        );
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double getPerPiece(ItemWizardArmour.ArmourClass armourClass) {
        switch (armourClass) {
            case WIZARD:
                return wizard;
            case SAGE:
                return sage;
            case BATTLEMAGE:
                return battlemage;
            case WARLOCK:
                return warlock;
            default:
                throw new IllegalArgumentException("Unknown Wizardry armor class: " + armourClass);
        }
    }

    public double getWizard() {
        return wizard;
    }

    public double getSage() {
        return sage;
    }

    public double getBattlemage() {
        return battlemage;
    }

    public double getWarlock() {
        return warlock;
    }

    public double getSageNonmatchingFullSet() {
        return sageNonmatchingFullSet;
    }

    private static double valid(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0D) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
        return value;
    }
}
