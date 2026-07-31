package com.mahghuuuls.jawms.api;

/** Immutable public view of one logical-server player's JAWMS mana state. */
public final class ManaPublicState {

    private final int currentMana;
    private final int maximumMana;
    private final double effectiveRegeneration;
    private final long effectiveLockoutTicks;
    private final long remainingLockoutTicks;
    private final boolean regenerateDuringPostCastLockout;
    private final boolean regenerateDuringContinuousCasting;
    private final boolean continuousCastActive;
    private final boolean regenerationEligible;

    public ManaPublicState(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long effectiveLockoutTicks,
            long remainingLockoutTicks,
            boolean regenerateDuringPostCastLockout,
            boolean regenerateDuringContinuousCasting,
            boolean continuousCastActive,
            boolean regenerationEligible
    ) {
        if (maximumMana < 1 || currentMana < 0 || currentMana > maximumMana) {
            throw new IllegalArgumentException("Mana values are outside their public bounds.");
        }
        if (!Double.isFinite(effectiveRegeneration) || effectiveRegeneration < 0.0D) {
            throw new IllegalArgumentException("Effective regeneration must be finite and non-negative.");
        }
        if (effectiveLockoutTicks < 0L || remainingLockoutTicks < 0L) {
            throw new IllegalArgumentException("Lockout values must be non-negative.");
        }
        this.currentMana = currentMana;
        this.maximumMana = maximumMana;
        this.effectiveRegeneration = effectiveRegeneration;
        this.effectiveLockoutTicks = effectiveLockoutTicks;
        this.remainingLockoutTicks = remainingLockoutTicks;
        this.regenerateDuringPostCastLockout = regenerateDuringPostCastLockout;
        this.regenerateDuringContinuousCasting = regenerateDuringContinuousCasting;
        this.continuousCastActive = continuousCastActive;
        this.regenerationEligible = regenerationEligible;
    }

    public int getCurrentMana() {
        return currentMana;
    }

    public int getMaximumMana() {
        return maximumMana;
    }

    public double getEffectiveRegeneration() {
        return effectiveRegeneration;
    }

    public long getEffectiveLockoutTicks() {
        return effectiveLockoutTicks;
    }

    public long getRemainingLockoutTicks() {
        return remainingLockoutTicks;
    }

    public double getEffectiveLockoutSeconds() {
        return effectiveLockoutTicks / 20.0D;
    }

    public double getRemainingLockoutSeconds() {
        return remainingLockoutTicks / 20.0D;
    }

    public boolean canRegenerateDuringPostCastLockout() {
        return regenerateDuringPostCastLockout;
    }

    public boolean canRegenerateDuringContinuousCasting() {
        return regenerateDuringContinuousCasting;
    }

    public boolean isContinuousCastActive() {
        return continuousCastActive;
    }

    public boolean isRegenerationEligible() {
        return regenerationEligible;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ManaPublicState)) {
            return false;
        }
        ManaPublicState that = (ManaPublicState) other;
        return currentMana == that.currentMana
                && maximumMana == that.maximumMana
                && Double.compare(effectiveRegeneration, that.effectiveRegeneration) == 0
                && effectiveLockoutTicks == that.effectiveLockoutTicks
                && remainingLockoutTicks == that.remainingLockoutTicks
                && regenerateDuringPostCastLockout == that.regenerateDuringPostCastLockout
                && regenerateDuringContinuousCasting == that.regenerateDuringContinuousCasting
                && continuousCastActive == that.continuousCastActive
                && regenerationEligible == that.regenerationEligible;
    }

    @Override
    public int hashCode() {
        int result = currentMana;
        result = 31 * result + maximumMana;
        long regenerationBits = Double.doubleToLongBits(effectiveRegeneration);
        result = 31 * result + (int) (regenerationBits ^ regenerationBits >>> 32);
        result = 31 * result + (int) (effectiveLockoutTicks ^ effectiveLockoutTicks >>> 32);
        result = 31 * result + (int) (remainingLockoutTicks ^ remainingLockoutTicks >>> 32);
        result = 31 * result + (regenerateDuringPostCastLockout ? 1 : 0);
        result = 31 * result + (regenerateDuringContinuousCasting ? 1 : 0);
        result = 31 * result + (continuousCastActive ? 1 : 0);
        result = 31 * result + (regenerationEligible ? 1 : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ManaPublicState{"
                + "current=" + currentMana
                + ", maximum=" + maximumMana
                + ", regeneration=" + effectiveRegeneration
                + ", effectiveLockoutTicks=" + effectiveLockoutTicks
                + ", remainingLockoutTicks=" + remainingLockoutTicks
                + ", postCastPermission=" + regenerateDuringPostCastLockout
                + ", continuousPermission=" + regenerateDuringContinuousCasting
                + ", continuousActive=" + continuousCastActive
                + ", eligible=" + regenerationEligible
                + '}';
    }
}
