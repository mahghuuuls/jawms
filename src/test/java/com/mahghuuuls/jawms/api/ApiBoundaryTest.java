package com.mahghuuuls.jawms.api;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiBoundaryTest {

    @Test
    void apiVersionAndSurfaceAreStableAndServerSafe() {
        assertEquals("1.1", ManaApi.API_VERSION);
        assertEquals(1, ManaApiVersion.MAJOR);
        assertEquals(1, ManaApiVersion.MINOR);

        assertPublicSignaturesDoNotExposeImplementation(ManaApi.class);
        assertPublicSignaturesDoNotExposeImplementation(IManaService.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaPublicState.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaMutationResult.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaStateChangeEvent.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaContribution.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaItemContext.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaTooltips.class);
        assertPublicSignaturesDoNotExposeImplementation(IItemManaModifierProvider.class);
        assertPublicSignaturesDoNotExposeImplementation(IContextualManaModifierProvider.class);
    }

    private static void assertPublicSignaturesDoNotExposeImplementation(Class<?> apiType) {
        for (Method method : apiType.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertAllowedType(method.getReturnType());
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertAllowedType(parameterType);
            }
        }
    }

    private static void assertAllowedType(Class<?> type) {
        String name = type.getName();
        assertFalse(name.startsWith("com.mahghuuuls.jawms.internal."), name);
        assertFalse(name.startsWith("com.mahghuuuls.jawms.client."), name);
        assertFalse(name.startsWith("net.minecraft.client."), name);
    }
}
