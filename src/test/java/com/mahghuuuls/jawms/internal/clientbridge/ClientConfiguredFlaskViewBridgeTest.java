package com.mahghuuuls.jawms.internal.clientbridge;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientConfiguredFlaskViewBridgeTest {

    @Test
    void defaultsToUnavailableAndAllowsExactlyOneNonNullClientBinding() throws Exception {
        Field viewField = ClientConfiguredFlaskViewBridge.class.getDeclaredField("view");
        viewField.setAccessible(true);
        Object original = viewField.get(null);
        try {
            assertFalse(ClientConfiguredFlaskViewBridge.getSnapshot().isAvailable());
            assertThrows(NullPointerException.class,
                    () -> ClientConfiguredFlaskViewBridge.installClientView(null));

            ConfiguredFlaskSnapshot expected =
                    ConfiguredFlaskSnapshot.available(20, 50, 100, 7L);
            ClientConfiguredFlaskViewBridge.installClientView(() -> expected);
            assertSame(expected, ClientConfiguredFlaskViewBridge.getSnapshot());
            assertThrows(IllegalStateException.class,
                    () -> ClientConfiguredFlaskViewBridge.installClientView(
                            ConfiguredFlaskSnapshot::unavailable
                    ));
        } finally {
            viewField.set(null, original);
        }
    }

    @Test
    void aBrokenBindingCannotPublishNull() throws Exception {
        Field viewField = ClientConfiguredFlaskViewBridge.class.getDeclaredField("view");
        viewField.setAccessible(true);
        Object original = viewField.get(null);
        try {
            viewField.set(null, (ClientConfiguredFlaskViewBridge.View) () -> null);
            assertSame(
                    ConfiguredFlaskSnapshot.unavailable(),
                    ClientConfiguredFlaskViewBridge.getSnapshot()
            );
        } finally {
            viewField.set(null, original);
        }
    }
}
