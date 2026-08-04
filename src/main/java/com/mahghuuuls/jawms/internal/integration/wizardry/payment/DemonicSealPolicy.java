package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;

/** Immutable Demonic Seal policy captured at the start of one physical cast. */
public final class DemonicSealPolicy {

    private static final DemonicSealPolicy INACTIVE =
            new DemonicSealPolicy(false, 0L, 1, 0, false);

    private final boolean active;
    private final long rulesRevision;
    private final int manaPerHunger;
    private final int minimumHunger;
    private final boolean allowContinuous;

    public DemonicSealPolicy(
            boolean active,
            long rulesRevision,
            int manaPerHunger,
            int minimumHunger,
            boolean allowContinuous
    ) {
        if (manaPerHunger < 1) {
            throw new IllegalArgumentException("manaPerHunger must be at least one");
        }
        if (minimumHunger < 0 || minimumHunger > 20) {
            throw new IllegalArgumentException("minimumHunger must be 0..20");
        }
        this.active = active;
        this.rulesRevision = rulesRevision;
        this.manaPerHunger = manaPerHunger;
        this.minimumHunger = minimumHunger;
        this.allowContinuous = allowContinuous;
    }

    public static DemonicSealPolicy inactive() {
        return INACTIVE;
    }

    public static DemonicSealPolicy capture(
            WizardryIntegrationRules rules, boolean artefactActive
    ) {
        if (rules == null || !rules.isDemonicSealEnabled() || !artefactActive) {
            return INACTIVE;
        }
        return new DemonicSealPolicy(
                true,
                rules.getRevision(),
                rules.getDemonicSealManaPerHunger(),
                rules.getDemonicSealMinimumHunger(),
                rules.isDemonicSealContinuous()
        );
    }

    public boolean appliesTo(boolean continuous) {
        return active && (!continuous || allowContinuous);
    }

    public boolean isActive() {
        return active;
    }

    public long getRulesRevision() {
        return rulesRevision;
    }

    public int getManaPerHunger() {
        return manaPerHunger;
    }

    public int getMinimumHunger() {
        return minimumHunger;
    }

    public boolean isContinuousAllowed() {
        return allowContinuous;
    }
}
