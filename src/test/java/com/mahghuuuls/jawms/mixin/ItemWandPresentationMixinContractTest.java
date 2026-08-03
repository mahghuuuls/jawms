package com.mahghuuuls.jawms.mixin;

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

class ItemWandPresentationMixinContractTest {

    @Test
    void presentationOverridesReturnFalseWithoutMutatingWandState() throws IOException {
        String resource = "/" + ItemWandMixin.class.getName().replace('.', '/') + ".class";
        InputStream input = ItemWandMixin.class.getResourceAsStream(resource);
        assertNotNull(input);
        PresentationVisitor visitor = new PresentationVisitor();
        try {
            new ClassReader(input).accept(visitor, 0);
        } finally {
            input.close();
        }

        assertTrue(visitor.durabilityMethodFound);
        assertTrue(visitor.workbenchMethodFound);
        assertEquals(2, visitor.falseReturns);
        assertEquals(0, visitor.mutationCalls);
    }

    private static final class PresentationVisitor extends ClassVisitor {

        private boolean durabilityMethodFound;
        private boolean workbenchMethodFound;
        private int falseReturns;
        private int mutationCalls;

        private PresentationVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(
                int access,
                String name,
                String descriptor,
                String signature,
                String[] exceptions
        ) {
            boolean presentationMethod = false;
            if ("showDurabilityBar".equals(name)
                    && "(Lnet/minecraft/item/ItemStack;)Z".equals(descriptor)) {
                durabilityMethodFound = true;
                presentationMethod = true;
            }
            if ("showManaInWorkbench".equals(name)
                    && "(Lnet/minecraft/entity/player/EntityPlayer;"
                    .concat("Lnet/minecraft/item/ItemStack;)Z").equals(descriptor)) {
                workbenchMethodFound = true;
                presentationMethod = true;
            }
            if (!presentationMethod) {
                return null;
            }
            return new MethodVisitor(Opcodes.ASM5) {
                private boolean sawFalse;

                @Override
                public void visitInsn(int opcode) {
                    if (opcode == Opcodes.ICONST_0) {
                        sawFalse = true;
                    } else if (opcode == Opcodes.IRETURN && sawFalse) {
                        falseReturns++;
                    }
                }

                @Override
                public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String invokedName,
                        String invokedDescriptor,
                        boolean isInterface
                ) {
                    if ("setMana".equals(invokedName)
                            || "setDamage".equals(invokedName)
                            || "setTagCompound".equals(invokedName)) {
                        mutationCalls++;
                    }
                }
            };
        }
    }
}
