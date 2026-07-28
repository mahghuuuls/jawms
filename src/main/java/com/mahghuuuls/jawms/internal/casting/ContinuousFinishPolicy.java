package com.mahghuuuls.jawms.internal.casting;

final class ContinuousFinishPolicy {

    private ContinuousFinishPolicy() {
    }

    static int affordability(
            boolean creative,
            boolean successful,
            boolean manaExhausted
    ) {
        if (creative) {
            return Integer.MAX_VALUE;
        }
        return successful && !manaExhausted ? Integer.MAX_VALUE : -1;
    }
}
