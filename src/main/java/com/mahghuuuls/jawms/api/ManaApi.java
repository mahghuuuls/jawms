package com.mahghuuuls.jawms.api;

import com.mahghuuuls.jawms.internal.mana.ManaService;

/**
 * Stable entry point for the JAWMS player-mana API.
 */
public final class ManaApi {

    public static final String API_VERSION = ManaApiVersion.CURRENT;

    private static final IManaService SERVICE = new ManaService();

    private ManaApi() {
    }

    public static IManaService getManaService() {
        return SERVICE;
    }
}
