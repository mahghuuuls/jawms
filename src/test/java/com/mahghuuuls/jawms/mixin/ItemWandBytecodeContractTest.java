package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.item.ItemWand;
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

class ItemWandBytecodeContractTest {

    private static final String CAN_CAST_DESCRIPTOR =
            "(Lnet/minecraft/item/ItemStack;"
                    + "Lelectroblob/wizardry/spell/Spell;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;"
                    + "ILelectroblob/wizardry/util/SpellModifiers;)Z";

    @Test
    void pinnedWizardryWandContainsEveryRequiredInjectionAnchor() throws IOException {
        String resource = "/" + ItemWand.class.getName().replace('.', '/') + ".class";
        InputStream input = ItemWand.class.getResourceAsStream(resource);
        assertNotNull(input);
        ContractVisitor visitor = new ContractVisitor();
        try {
            new ClassReader(input).accept(visitor, 0);
        } finally {
            input.close();
        }

        assertTrue(visitor.canCastFound);
        assertEquals(2, visitor.canCastCostStores);
        assertEquals(1, visitor.canCastGetManaCalls);

        assertTrue(visitor.castFound);
        assertEquals(3, visitor.castIntegerSlotEightStores);
        assertEquals(1, visitor.castSpellCalls);
        assertEquals(1, visitor.castConsumeManaCalls);
        assertEquals(4, visitor.castContinuousFieldReads);
    }

    private static final class ContractVisitor extends ClassVisitor {

        private boolean canCastFound;
        private int canCastCostStores;
        private int canCastGetManaCalls;
        private boolean castFound;
        private int castIntegerSlotEightStores;
        private int castSpellCalls;
        private int castConsumeManaCalls;
        private int castContinuousFieldReads;

        private ContractVisitor() {
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
            if ("canCast".equals(name) && CAN_CAST_DESCRIPTOR.equals(descriptor)) {
                canCastFound = true;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitVarInsn(int opcode, int variable) {
                        if (opcode == Opcodes.ISTORE && variable == 7) {
                            canCastCostStores++;
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
                        if ("electroblob/wizardry/item/ItemWand".equals(owner)
                                && "getMana".equals(invokedName)
                                && "(Lnet/minecraft/item/ItemStack;)I".equals(invokedDescriptor)) {
                            canCastGetManaCalls++;
                        }
                    }
                };
            }
            if ("cast".equals(name) && CAN_CAST_DESCRIPTOR.equals(descriptor)) {
                castFound = true;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitVarInsn(int opcode, int variable) {
                        if (opcode == Opcodes.ISTORE && variable == 8) {
                            castIntegerSlotEightStores++;
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
                        if ("electroblob/wizardry/spell/Spell".equals(owner)
                                && "cast".equals(invokedName)) {
                            castSpellCalls++;
                        }
                        if ("electroblob/wizardry/item/ItemWand".equals(owner)
                                && "consumeMana".equals(invokedName)) {
                            castConsumeManaCalls++;
                        }
                    }

                    @Override
                    public void visitFieldInsn(
                            int opcode,
                            String owner,
                            String fieldName,
                            String fieldDescriptor
                    ) {
                        if (opcode == Opcodes.GETFIELD
                                && "electroblob/wizardry/spell/Spell".equals(owner)
                                && "isContinuous".equals(fieldName)
                                && "Z".equals(fieldDescriptor)) {
                            castContinuousFieldReads++;
                        }
                    }
                };
            }
            return null;
        }
    }
}
