package com.mahghuuuls.jawms.internal.modifier;

import java.util.Collections;

public final class ManaTestEvaluations {
    private ManaTestEvaluations() {
    }

    public static ManaProfileEvaluation withMaximum(int maximum) {
        return new ManaProfileEvaluation(0, 0, maximum, 0, 0,
                ManaProfileResolver.calculate(maximum, 0, 0, Collections.emptyList()),
                Collections.emptyList());
    }
}
