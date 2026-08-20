package com.mahghuuuls.jawms.internal.clientbridge;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;

/** Internal side-neutral binding behind the configured-flask client facade. */
public final class ClientConfiguredFlaskViewBridge {

    private static final View UNAVAILABLE_VIEW = ConfiguredFlaskSnapshot::unavailable;
    private static volatile View view = UNAVAILABLE_VIEW;

    private ClientConfiguredFlaskViewBridge() {
    }

    public static ConfiguredFlaskSnapshot getSnapshot() {
        ConfiguredFlaskSnapshot snapshot = view.getSnapshot();
        return snapshot == null ? ConfiguredFlaskSnapshot.unavailable() : snapshot;
    }

    public static synchronized void installClientView(View clientView) {
        if (clientView == null) {
            throw new NullPointerException("clientView");
        }
        if (view != UNAVAILABLE_VIEW) {
            throw new IllegalStateException("The JAWMS configured-flask client view is already installed.");
        }
        view = clientView;
    }

    public interface View {
        ConfiguredFlaskSnapshot getSnapshot();
    }
}
