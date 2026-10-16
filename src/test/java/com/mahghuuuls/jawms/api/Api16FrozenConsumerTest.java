package com.mahghuuuls.jawms.api;

import org.junit.jupiter.api.Test;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import static org.junit.jupiter.api.Assertions.*;

/** Loads original consumer bytes, not recompiled candidate-dependent source. */
class Api16FrozenConsumerTest {
    @Test void unchangedCommonConsumerAndCommandLinkAgainstCandidateApi() throws Exception {
        URL artifact = Paths.get("src/api16BinaryFixture/artifacts/jawms-api-fixture-1.0-dev.jar")
                .toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(new URL[] { artifact }, getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("example.jawmsaddon.")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = findClass(name);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        }) {
            Class<?> consumer = loader.loadClass("example.jawmsaddon.ApiConsumerFixture");
            Object instance = consumer.getConstructor().newInstance();
            Class<?> command = loader.loadClass("example.jawmsaddon.ApiConsumerFixture$FixtureCommand");
            java.lang.reflect.Constructor<?> constructor = command.getDeclaredConstructor(consumer);
            constructor.setAccessible(true);
            Object fixtureCommand = constructor.newInstance(instance);
            java.lang.reflect.Method name = command.getMethod("getName");
            name.setAccessible(true);
            assertEquals("jawms-api-fixture", name.invoke(fixtureCommand));
            assertSame(loader, consumer.getClassLoader());
            assertNotSame(consumer, example.jawmsaddon.ApiConsumerFixture.class);
            assertEquals(artifact, consumer.getProtectionDomain().getCodeSource().getLocation());
        }
    }
}
