package com.mahghuuuls.jawms.proxy;

public class CommonProxy {

    public void preInit() {
        // Common initialization is performed without referencing client classes.
    }

    public void acceptManaState(int currentMana, int maximumMana, long revision) {
        // Server proxy intentionally ignores client-bound presentation state.
    }
}
