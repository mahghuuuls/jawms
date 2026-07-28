package com.mahghuuuls.jawms.internal.casting;

final class ContinuousSpellCost {

    private ContinuousSpellCost() {
    }

    static int portionAtTick(int perSecondCost, int castingTick) {
        if (perSecondCost < 0) {
            throw new IllegalArgumentException("Continuous spell cost must not be negative.");
        }
        if (castingTick < 0) {
            throw new IllegalArgumentException("Casting tick must not be negative.");
        }
        if (castingTick % 20 == 0) {
            return perSecondCost / 2 + perSecondCost % 2;
        }
        if (castingTick % 10 == 0) {
            return perSecondCost / 2;
        }
        return 0;
    }
}
