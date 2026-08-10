package com.mahghuuuls.jawms.api.client;

import com.mahghuuuls.jawms.internal.clientbridge.ClientManaViewBridge;

/** Stable read-only entry point for synchronized local-player mana state. */
public final class ClientManaApi {

    private ClientManaApi() {
    }

    /**
     * Returns one immutable snapshot. It is unavailable before initial client
     * synchronization, after disconnect, and on a dedicated server. A known
     * revision identifies accepted state only within the current connection.
     */
    public static ClientManaSnapshot getSnapshot() {
        return ClientManaViewBridge.getSnapshot();
    }
}
