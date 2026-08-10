package com.mahghuuuls.jawms.api;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientHudContext;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiBoundaryTest {

    @Test
    void apiVersionAndSurfaceAreStableAndServerSafe() {
        assertEquals("1.3", ManaApi.API_VERSION);
        assertEquals(1, ManaApiVersion.MAJOR);
        assertEquals(3, ManaApiVersion.MINOR);

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
        assertPublicSignaturesDoNotExposeImplementation(ClientManaApi.class);
        assertPublicSignaturesDoNotExposeImplementation(ClientHudContext.class);
        assertPublicSignaturesDoNotExposeImplementation(ClientManaSnapshot.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaHudRenderEvent.class);
    }

    @Test
    void publishedApiOnePointTwoClientDescriptorsRemainPresent() throws Exception {
        ClientManaSnapshot.class.getMethod(
                "available", int.class, int.class, double.class
        );
        ClientManaSnapshot.class.getMethod("unavailable");
        ClientManaSnapshot.class.getMethod("isAvailable");
        ClientManaSnapshot.class.getMethod("getCurrentMana");
        ClientManaSnapshot.class.getMethod("getMaximumMana");
        ClientManaSnapshot.class.getMethod("getEffectiveRegeneration");

        ManaHudRenderEvent.class.getConstructor(
                ClientManaSnapshot.class, float.class, int.class, int.class
        );
        ManaHudRenderEvent.class.getMethod("getSnapshot");
        ManaHudRenderEvent.class.getMethod("getPartialTicks");
        ManaHudRenderEvent.class.getMethod("getScaledWidth");
        ManaHudRenderEvent.class.getMethod("getScaledHeight");
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
