package com.mahghuuuls.jawms.internal.modifier;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ActiveSlotSourcesTest {

    @Test
    void absentBaublesDoesNotResolveOrInstantiateAdapter() {
        AtomicInteger calls = new AtomicInteger();

        List<ActiveSlotSource> sources = ActiveSlotSources.createSources(false, () -> {
            calls.incrementAndGet();
            throw new AssertionError("Absent Baubles must not resolve its adapter.");
        });

        assertEquals(0, calls.get());
        assertEquals(0, sources.size());
    }

    @Test
    void presentBaublesInstallsTypedSource() {
        ActiveSlotSource source = (player, consumer) -> {
        };

        List<ActiveSlotSource> sources = ActiveSlotSources.createSources(true, () -> source);

        assertEquals(1, sources.size());
        assertSame(source, sources.get(0));
    }
}
