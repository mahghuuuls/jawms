package com.mahghuuuls.jawms.internal.clientbridge;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;

/** Internal side-neutral binding behind the public client facade. */
public final class ClientManaViewBridge {

    private static final View UNAVAILABLE_VIEW = ClientManaSnapshot::unavailable;
    private static volatile View view = UNAVAILABLE_VIEW;

    private ClientManaViewBridge() {
    }

    public static ClientManaSnapshot getSnapshot() {
        ClientManaSnapshot snapshot = view.getSnapshot();
        return snapshot == null ? ClientManaSnapshot.unavailable() : snapshot;
    }

    public static synchronized void installClientView(View clientView) {
        if (clientView == null) {
            throw new NullPointerException("clientView");
        }
        if (view != UNAVAILABLE_VIEW) {
            throw new IllegalStateException("The JAWMS client mana view is already installed.");
        }
        view = clientView;
    }

    public interface View {
        ClientManaSnapshot getSnapshot();
    }
}
