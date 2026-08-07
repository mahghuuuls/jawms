package com.mahghuuuls.jawms.internal.clientbridge;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientManaViewBridgeTest {

    @Test
    void defaultsToUnavailableAndAllowsExactlyOneNonNullClientBinding() throws Exception {
        Field viewField = ClientManaViewBridge.class.getDeclaredField("view");
        viewField.setAccessible(true);
        Object original = viewField.get(null);
        try {
            assertFalse(ClientManaViewBridge.getSnapshot().isAvailable());
            assertThrows(NullPointerException.class,
                    () -> ClientManaViewBridge.installClientView(null));

            ClientManaSnapshot expected = ClientManaSnapshot.available(25, 60, 2.4D);
            ClientManaViewBridge.installClientView(() -> expected);
            assertSame(expected, ClientManaViewBridge.getSnapshot());
            assertThrows(IllegalStateException.class,
                    () -> ClientManaViewBridge.installClientView(ClientManaSnapshot::unavailable));
        } finally {
            viewField.set(null, original);
        }
    }

    @Test
    void aBrokenBindingCannotPublishNull() throws Exception {
        Field viewField = ClientManaViewBridge.class.getDeclaredField("view");
        viewField.setAccessible(true);
        Object original = viewField.get(null);
        try {
            viewField.set(null, (ClientManaViewBridge.View) () -> null);
            assertSame(ClientManaSnapshot.unavailable(), ClientManaViewBridge.getSnapshot());
        } finally {
            viewField.set(null, original);
        }
    }
}
