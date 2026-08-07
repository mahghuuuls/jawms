package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.internal.clientbridge.ClientManaViewBridge;

public final class ClientManaViewAdapter implements ClientManaViewBridge.View {

    @Override
    public ClientManaSnapshot getSnapshot() {
        return ClientManaState.snapshot();
    }
}
