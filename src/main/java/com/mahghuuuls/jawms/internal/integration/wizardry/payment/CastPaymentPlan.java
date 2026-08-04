package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

/** Pure complete affordability result for one cast or continuous portion. */
public final class CastPaymentPlan {

    private final int cost;
    private final int mana;
    private final int hunger;
    private final int usableHunger;
    private final boolean sealConsidered;
    private final boolean affordable;

    private CastPaymentPlan(
            int cost,
            int mana,
            int hunger,
            int usableHunger,
            boolean sealConsidered,
            boolean affordable
    ) {
        this.cost = cost;
        this.mana = mana;
        this.hunger = hunger;
        this.usableHunger = usableHunger;
        this.sealConsidered = sealConsidered;
        this.affordable = affordable;
    }

    public static CastPaymentPlan calculate(
            int cost,
            int availableMana,
            int currentHunger,
            int alreadyReservedHunger,
            DemonicSealPolicy policy,
            boolean continuous
    ) {
        requireNonNegative(cost, "cost");
        requireNonNegative(availableMana, "availableMana");
        requireNonNegative(currentHunger, "currentHunger");
        requireNonNegative(alreadyReservedHunger, "alreadyReservedHunger");
        if (policy == null) {
            throw new NullPointerException("policy");
        }

        int mana = Math.min(cost, availableMana);
        int shortfall = cost - mana;
        boolean sealConsidered = shortfall > 0 && policy.appliesTo(continuous);
        int usableHunger = sealConsidered
                ? Math.max(0, currentHunger - policy.getMinimumHunger()
                        - alreadyReservedHunger)
                : 0;
        int hunger = sealConsidered
                ? ceilDivide(shortfall, policy.getManaPerHunger())
                : 0;
        boolean affordable = shortfall == 0
                || (sealConsidered && hunger <= usableHunger);
        return new CastPaymentPlan(
                cost, mana, hunger, usableHunger, sealConsidered, affordable
        );
    }

    static CastPaymentPlan free(int cost) {
        requireNonNegative(cost, "cost");
        return new CastPaymentPlan(cost, 0, 0, 0, false, true);
    }

    private static int ceilDivide(int value, int divisor) {
        return value == 0 ? 0 : 1 + (value - 1) / divisor;
    }

    private static void requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    public int getCost() { return cost; }
    public int getMana() { return mana; }
    public int getHunger() { return hunger; }
    public int getUsableHunger() { return usableHunger; }
    public boolean wasSealConsidered() { return sealConsidered; }
    public boolean isAffordable() { return affordable; }
}
