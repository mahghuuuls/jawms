package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.item.ItemArtefact;
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

class ItemArtefactBytecodeContractTest {

    @Test
    void pinnedHungerCastingBranchContainsBothRequiredAnchors() throws IOException {
        String resource = "/" + ItemArtefact.class.getName().replace('.', '/') + ".class";
        InputStream input = ItemArtefact.class.getResourceAsStream(resource);
        assertNotNull(input);
        Visitor visitor = new Visitor();
        try {
            new ClassReader(input).accept(visitor, 0);
        } finally {
            input.close();
        }
        assertTrue(visitor.methodFound);
        assertEquals(1, visitor.getManaCalls);
        assertEquals(1, visitor.foodLevelCalls);
        assertEquals(1, visitor.foodPaymentCalls);
        assertTrue(visitor.extractionLambdaFound);
        assertEquals(1, visitor.extractionRechargeCalls);
    }

    private static final class Visitor extends ClassVisitor {
        private boolean methodFound;
        private int getManaCalls;
        private int foodLevelCalls;
        private int foodPaymentCalls;
        private boolean extractionLambdaFound;
        private int extractionRechargeCalls;

        private Visitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions
        ) {
            if ("lambda$onLivingHurtEvent$26".equals(name)
                    && "(Lnet/minecraft/world/World;Lnet/minecraft/item/ItemStack;)V"
                    .equals(descriptor)) {
                extractionLambdaFound = true;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        if ("electroblob/wizardry/item/IManaStoringItem".equals(owner)
                                && "rechargeMana".equals(invokedName)
                                && "(Lnet/minecraft/item/ItemStack;I)V"
                                .equals(invokedDescriptor)) {
                            extractionRechargeCalls++;
                        }
                    }
                };
            }
            if (!"onSpellCastPreEvent".equals(name)
                    || !"(Lelectroblob/wizardry/event/SpellCastEvent$Pre;)V".equals(descriptor)) {
                return null;
            }
            methodFound = true;
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String invokedName,
                        String invokedDescriptor,
                        boolean isInterface
                ) {
                    if ("electroblob/wizardry/item/IManaStoringItem".equals(owner)
                            && "getMana".equals(invokedName)) {
                        getManaCalls++;
                    }
                    if ("net/minecraft/util/FoodStats".equals(owner)
                            && "getFoodLevel".equals(invokedName)
                            && "()I".equals(invokedDescriptor)) {
                        foodLevelCalls++;
                    }
                    if ("net/minecraft/util/FoodStats".equals(owner)
                            && "addStats".equals(invokedName)
                            && "(IF)V".equals(invokedDescriptor)) {
                        foodPaymentCalls++;
                    }
                }
            };
        }
    }
}
