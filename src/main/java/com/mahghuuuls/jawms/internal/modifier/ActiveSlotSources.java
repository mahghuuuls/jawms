package com.mahghuuuls.jawms.internal.modifier;

import net.minecraftforge.fml.common.Loader;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/** Runtime-guarded bootstrap for optional active-slot integrations. */
public final class ActiveSlotSources {

    private static final String BAUBLES_SOURCE =
            "com.mahghuuuls.jawms.internal.integration.baubles.BaublesActiveSlotSource";

    private static volatile List<ActiveSlotSource> sources = Collections.emptyList();

    private ActiveSlotSources() {
    }

    public static void initialize() {
        sources = createSources(
                Loader.isModLoaded("baubles"),
                () -> instantiate(BAUBLES_SOURCE)
        );
    }

    static List<ActiveSlotSource> current() {
        return sources;
    }

    static List<ActiveSlotSource> createSources(
            boolean baublesLoaded,
            Supplier<?> baublesFactory
    ) {
        if (!baublesLoaded) {
            return Collections.emptyList();
        }
        Object candidate = baublesFactory.get();
        if (!(candidate instanceof ActiveSlotSource)) {
            throw new IllegalStateException(
                    "The optional Baubles slot adapter does not implement ActiveSlotSource."
            );
        }
        return Collections.singletonList((ActiveSlotSource) candidate);
    }

    private static Object instantiate(String className) {
        try {
            Class<?> type = Class.forName(className, true, ActiveSlotSources.class.getClassLoader());
            Constructor<?> constructor = type.getDeclaredConstructor();
            return constructor.newInstance();
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "Baubles is installed, but JAWMS could not initialize its slot adapter.",
                    exception
            );
        }
    }
}
