package com.mahghuuuls.jawms.api.client;

import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplacementHudFixtureContractTest {

    @Test
    void replacementAndCompetingClaimantsUseTheApprovedForgePriorities()
            throws Exception {
        Class<?> controller = Class.forName(
                "example.jawmsaddon.ClientReplacementHudFixture"
        );
        assertSubscription(
                controller.getMethod("renderReplacement", ManaHudRenderEvent.class),
                EventPriority.LOWEST,
                false
        );
        assertSubscription(
                controller.getMethod("claimEarlier", ManaHudRenderEvent.class),
                EventPriority.HIGH,
                false
        );
        assertSubscription(
                controller.getMethod("cancelParentChat", RenderGameOverlayEvent.Chat.class),
                EventPriority.HIGHEST,
                false
        );

        Class<?> equalClaimant = Class.forName(
                "example.jawmsaddon.ClientReplacementHudFixture$EqualClaimant"
        );
        assertSubscription(
                equalClaimant.getMethod("claim", ManaHudRenderEvent.class),
                EventPriority.LOWEST,
                false
        );
    }

    @Test
    void fixtureRendererRestoresEveryStateItChangesThroughGlStateManager()
            throws IOException {
        MethodCalls render = inspect(
                "example/jawmsaddon/CacheSafeReplacementHud.class",
                "render"
        );
        assertTrue(render.hasFinallyHandler);
        assertTrue(render.calls.contains("example/jawmsaddon/CacheSafeReplacementHud$RenderState.restore"));

        MethodCalls restore = inspect(
                "example/jawmsaddon/CacheSafeReplacementHud$RenderState.class",
                "restore"
        );
        MethodCalls capture = inspect(
                "example/jawmsaddon/CacheSafeReplacementHud$RenderState.class",
                "<init>"
        );
        MethodCalls enter = inspect(
                "example/jawmsaddon/CacheSafeReplacementHud$RenderState.class",
                "enterFixtureState"
        );
        assertTrue(capture.calls.contains("net/minecraft/client/renderer/GlStateManager.setActiveTexture"));
        assertTrue(capture.floatBufferCapacities.contains(16));
        assertFalse(capture.floatBufferCapacities.contains(4));
        assertTrue(enter.calls.contains("net/minecraft/client/renderer/GlStateManager.setActiveTexture"));
        assertTrue(restore.calls.contains("net/minecraft/client/renderer/GlStateManager.setActiveTexture"));
        assertTrue(restore.calls.contains("net/minecraft/client/renderer/GlStateManager.popMatrix"));
        assertTrue(restore.calls.contains("net/minecraft/client/renderer/GlStateManager.alphaFunc"));
        assertTrue(restore.calls.contains("net/minecraft/client/renderer/GlStateManager.tryBlendFuncSeparate"));
        assertTrue(restore.calls.contains("net/minecraft/client/renderer/GlStateManager.bindTexture"));
        assertTrue(restore.calls.contains("net/minecraft/client/renderer/GlStateManager.color"));
        assertTrue(restore.calls.contains("example/jawmsaddon/CacheSafeReplacementHud$RenderState.setAlpha"));
        assertTrue(restore.calls.contains("example/jawmsaddon/CacheSafeReplacementHud$RenderState.setBlend"));
        assertTrue(restore.calls.contains("example/jawmsaddon/CacheSafeReplacementHud$RenderState.setDepth"));
        assertTrue(restore.calls.contains("example/jawmsaddon/CacheSafeReplacementHud$RenderState.setLighting"));
        assertTrue(restore.calls.contains("example/jawmsaddon/CacheSafeReplacementHud$RenderState.setTexture"));
        assertFalse(restore.calls.contains("org/lwjgl/opengl/GL11.glPopAttrib"));
    }

    private static void assertSubscription(
            Method method,
            EventPriority priority,
            boolean receiveCanceled
    ) {
        SubscribeEvent subscription = method.getAnnotation(SubscribeEvent.class);
        assertNotNull(subscription);
        assertEquals(priority, subscription.priority());
        assertEquals(receiveCanceled, subscription.receiveCanceled());
    }

    private static MethodCalls inspect(String resource, String methodName) throws IOException {
        InputStream stream = ReplacementHudFixtureContractTest.class
                .getClassLoader().getResourceAsStream(resource);
        assertNotNull(stream, resource);
        try {
            MethodCalls result = new MethodCalls();
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override
                public MethodVisitor visitMethod(
                        int access,
                        String name,
                        String descriptor,
                        String signature,
                        String[] exceptions
                ) {
                    if (!methodName.equals(name)) {
                        return null;
                    }
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitTryCatchBlock(
                                org.objectweb.asm.Label start,
                                org.objectweb.asm.Label end,
                                org.objectweb.asm.Label handler,
                                String type
                        ) {
                            if (type == null) {
                                result.hasFinallyHandler = true;
                            }
                        }

                        @Override
                        public void visitMethodInsn(
                                int opcode,
                                String owner,
                                String name,
                                String descriptor,
                                boolean isInterface
                        ) {
                            result.calls.add(owner + "." + name);
                            if ("org/lwjgl/BufferUtils".equals(owner)
                                    && "createFloatBuffer".equals(name)
                                    && "(I)Ljava/nio/FloatBuffer;".equals(descriptor)
                                    && result.lastIntegerConstant != null) {
                                result.floatBufferCapacities.add(result.lastIntegerConstant);
                            }
                            result.lastIntegerConstant = null;
                        }

                        @Override
                        public void visitIntInsn(int opcode, int operand) {
                            if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                                result.lastIntegerConstant = operand;
                            }
                        }

                        @Override
                        public void visitInsn(int opcode) {
                            if (opcode >= Opcodes.ICONST_M1 && opcode <= Opcodes.ICONST_5) {
                                result.lastIntegerConstant = opcode - Opcodes.ICONST_0;
                            }
                        }
                    };
                }
            }, 0);
            return result;
        } finally {
            stream.close();
        }
    }

    private static final class MethodCalls {
        private final Set<String> calls = new HashSet<>();
        private final Set<Integer> floatBufferCapacities = new HashSet<>();
        private Integer lastIntegerConstant;
        private boolean hasFinallyHandler;
    }
}
