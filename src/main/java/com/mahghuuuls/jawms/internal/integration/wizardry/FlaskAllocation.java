package com.mahghuuuls.jawms.internal.integration.wizardry;

import java.util.List;

/** Pure allocation policy shared by runtime behavior and focused tests. */
final class FlaskAllocation {

    private FlaskAllocation() {
    }

    static Plan plan(int budget, int playerMissing, List<ArmorState> armor) {
        if (budget < 0 || playerMissing < 0 || armor == null) {
            throw new IllegalArgumentException("Flask allocation inputs must be non-negative and non-null.");
        }
        int playerAmount = Math.min(budget, playerMissing);
        int remaining = budget - playerAmount;
        int armorIndex = -1;
        double leastFull = Double.POSITIVE_INFINITY;
        for (int index = 0; index < armor.size(); index++) {
            ArmorState state = armor.get(index);
            if (state == null || state.capacity <= 0 || state.mana >= state.capacity) {
                continue;
            }
            double fullness = state.mana / (double) state.capacity;
            if (fullness < leastFull) {
                leastFull = fullness;
                armorIndex = index;
            }
        }
        int armorAmount = armorIndex < 0
                ? 0
                : Math.min(remaining, armor.get(armorIndex).capacity - armor.get(armorIndex).mana);
        return new Plan(playerAmount, armorIndex, armorAmount);
    }

    static final class ArmorState {

        final int mana;
        final int capacity;

        ArmorState(int mana, int capacity) {
            if (mana < 0 || capacity < 0 || mana > capacity) {
                throw new IllegalArgumentException("Armor mana must be within its capacity.");
            }
            this.mana = mana;
            this.capacity = capacity;
        }
    }

    static final class Plan {

        final int playerAmount;
        final int armorIndex;
        final int armorAmount;

        Plan(int playerAmount, int armorIndex, int armorAmount) {
            this.playerAmount = playerAmount;
            this.armorIndex = armorIndex;
            this.armorAmount = armorAmount;
        }

        boolean hasDestination() {
            return playerAmount > 0 || armorAmount > 0;
        }
    }
}
