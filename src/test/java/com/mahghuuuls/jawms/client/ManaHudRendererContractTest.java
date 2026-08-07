package com.mahghuuuls.jawms.client;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaHudRendererContractTest {

    @Test
    void rendererUsesThePreVanillaChatBoundaryAndReceivesCanceledChatEvents()
            throws NoSuchMethodException {
        Method render = ManaHudRenderer.class.getDeclaredMethod(
                "render",
                RenderGameOverlayEvent.Chat.class
        );
        SubscribeEvent subscription = render.getAnnotation(SubscribeEvent.class);

        assertEquals(void.class, render.getReturnType());
        assertTrue(subscription.receiveCanceled());
    }
}
