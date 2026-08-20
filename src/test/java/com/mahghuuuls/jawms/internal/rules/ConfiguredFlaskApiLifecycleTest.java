package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.ManaApi;
import electroblob.wizardry.item.ItemManaFlask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ConfiguredFlaskApiLifecycleTest {

    @AfterEach
    void stopAuthority() {
        GameplayRulesManager.serverStopped();
    }

    @Test
    void serverFacadeIsAvailableOnlyWhileServerAuthorityIsActive() {
        assertSame(ConfiguredFlaskSnapshot.unavailable(), ManaApi.getConfiguredFlaskSnapshot());

        GameplayRulesManager.serverStarted();
        GameplayRules current = GameplayRulesManager.current();
        ConfiguredFlaskSnapshot expected = ConfiguredFlaskSnapshot.available(
                current.getSmallFlask(), current.getMediumFlask(),
                current.getLargeFlask(), current.getRevision()
        );
        assertEquals(
                expected,
                ManaApi.getConfiguredFlaskSnapshot()
        );

        int originalSmall = ItemManaFlask.Size.SMALL.capacity;
        try {
            ItemManaFlask.Size.SMALL.capacity = 999;
            assertEquals(
                    expected,
                    ManaApi.getConfiguredFlaskSnapshot()
            );
        } finally {
            ItemManaFlask.Size.SMALL.capacity = originalSmall;
        }

        AtomicReference<ConfiguredFlaskSnapshot> offThread = new AtomicReference<>();
        Thread clientOrWorker = new Thread(
                () -> offThread.set(ManaApi.getConfiguredFlaskSnapshot()),
                "simulated-client-or-worker"
        );
        clientOrWorker.start();
        try {
            clientOrWorker.join();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
        assertSame(ConfiguredFlaskSnapshot.unavailable(), offThread.get());

        GameplayRulesManager.serverStopped();
        assertSame(ConfiguredFlaskSnapshot.unavailable(), ManaApi.getConfiguredFlaskSnapshot());
    }
}
