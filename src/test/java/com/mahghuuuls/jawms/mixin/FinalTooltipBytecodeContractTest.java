package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.client.ManaItemTooltipHandler;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalTooltipBytecodeContractTest {

    private static final String TOOLTIP_DESCRIPTOR =
            "(Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/client/util/ITooltipFlag;)Ljava/util/List;";

    @Test
    void mixinDelegatesExactlyOnceWithoutOwningPresentationPolicy() throws IOException {
        DelegationVisitor visitor = read(
                ItemStackTooltipClientMixin.class,
                new DelegationVisitor()
        );

        assertTrue(visitor.hookFound);
        assertEquals(1, visitor.assemblerCalls);
        assertEquals(0, visitor.policyCalls);
    }

    @Test
    void ordinaryHandlerNoLongerFormatsAutomaticContributions() throws IOException {
        HandlerVisitor visitor = read(ManaItemTooltipHandler.class, new HandlerVisitor());

        assertTrue(visitor.handlerFound);
        assertEquals(0, visitor.automaticFormatterCalls);
    }

    @Test
    void patchedItemStackPostsForgeEventBeforeReturningTooltip() throws IOException {
        ItemStackVisitor visitor = read(ItemStack.class, new ItemStackVisitor());

        assertTrue(visitor.methodFound);
        assertTrue(visitor.eventObserved);
        assertEquals(1, visitor.returnsAfterEvent);
        assertEquals(0, visitor.returnsBeforeEvent);
    }

    private static <T extends ClassVisitor> T read(Class<?> type, T visitor)
            throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        InputStream input = type.getResourceAsStream(resource);
        assertNotNull(input);
        try {
            new ClassReader(input).accept(visitor, 0);
            return visitor;
        } finally {
            input.close();
        }
    }

    private static final class DelegationVisitor extends ClassVisitor {

        private boolean hookFound;
        private int assemblerCalls;
        private int policyCalls;

        private DelegationVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor,
                String signature, String[] exceptions
        ) {
            if (!"jawms$appendFinalContributionSection".equals(name)) {
                return null;
            }
            hookFound = true;
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String invokedName,
                        String invokedDescriptor, boolean isInterface
                ) {
                    if ("com/mahghuuuls/jawms/client/FinalManaTooltipAssembler".equals(owner)
                            && "append".equals(invokedName)) {
                        assemblerCalls++;
                    }
                    if (owner.startsWith("com/mahghuuuls/jawms/")
                            && !"com/mahghuuuls/jawms/client/FinalManaTooltipAssembler"
                            .equals(owner)) {
                        policyCalls++;
                    }
                }
            };
        }
    }

    private static final class HandlerVisitor extends ClassVisitor {

        private boolean handlerFound;
        private int automaticFormatterCalls;

        private HandlerVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor,
                String signature, String[] exceptions
        ) {
            if (!"tooltip".equals(name)) {
                return null;
            }
            handlerFound = true;
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String invokedName,
                        String invokedDescriptor, boolean isInterface
                ) {
                    if ("com/mahghuuuls/jawms/api/ManaTooltips".equals(owner)
                            && "format".equals(invokedName)) {
                        automaticFormatterCalls++;
                    }
                }
            };
        }
    }

    private static final class ItemStackVisitor extends ClassVisitor {

        private boolean methodFound;
        private boolean eventObserved;
        private int returnsBeforeEvent;
        private int returnsAfterEvent;

        private ItemStackVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor,
                String signature, String[] exceptions
        ) {
            if (!"getTooltip".equals(name) || !TOOLTIP_DESCRIPTOR.equals(descriptor)) {
                return null;
            }
            methodFound = true;
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String invokedName,
                        String invokedDescriptor, boolean isInterface
                ) {
                    if ("net/minecraftforge/event/ForgeEventFactory".equals(owner)
                            && "onItemTooltip".equals(invokedName)) {
                        eventObserved = true;
                    }
                }

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.ARETURN) {
                        if (eventObserved) {
                            returnsAfterEvent++;
                        } else {
                            returnsBeforeEvent++;
                        }
                    }
                }
            };
        }
    }
}
