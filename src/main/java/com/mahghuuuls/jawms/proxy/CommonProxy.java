package com.mahghuuuls.jawms.proxy;

import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;

public class CommonProxy {

    public void preInit() {
        // Common initialization is performed without referencing client classes.
    }

    public void acceptManaState(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision
    ) {
        // Server proxy intentionally ignores client-bound presentation state.
    }

    public void acceptGameplayRules(GameplayRulesMessage message) {
        // Server proxy intentionally ignores client-bound presentation rules.
    }
}
