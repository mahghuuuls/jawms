package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.item.ItemManaFlask;
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

class ItemManaFlaskBytecodeContractTest {

    private static final String RIGHT_CLICK =
            "(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;";
    private static final String FINISH =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/EntityLivingBase;)Lnet/minecraft/item/ItemStack;";
    private static final String RESTORE =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;)V";
    private static final String TOOLTIP =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Ljava/util/List;"
                    + "Lnet/minecraft/client/util/ITooltipFlag;)V";

    @Test
    void pinnedWizardryFlaskContainsEveryRequiredBridgeAnchor() throws IOException {
        String resource = "/" + ItemManaFlask.class.getName().replace('.', '/') + ".class";
        InputStream input = ItemManaFlask.class.getResourceAsStream(resource);
        assertNotNull(input);
        ContractVisitor visitor = new ContractVisitor();
        try {
            new ClassReader(input).accept(visitor, 0);
        } finally {
            input.close();
        }
        assertTrue(visitor.rightClickFound);
        assertTrue(visitor.finishFound);
        assertTrue(visitor.restoreFound);
        assertTrue(visitor.tooltipFound);
        assertEquals(1, visitor.rightClickRestoreCalls);
        assertEquals(1, visitor.finishRestoreCalls);
        assertEquals(1, visitor.restoreRechargeCalls);
        assertEquals(1, visitor.restoreShrinkCalls);
        assertEquals(1, visitor.restoreCooldownCalls);
        assertEquals(2, visitor.restoreSoundCalls);
        assertEquals(1, visitor.tooltipCapacityReads);
    }

    private static final class ContractVisitor extends ClassVisitor {

        private boolean rightClickFound;
        private boolean finishFound;
        private boolean restoreFound;
        private boolean tooltipFound;
        private int rightClickRestoreCalls;
        private int finishRestoreCalls;
        private int restoreRechargeCalls;
        private int restoreShrinkCalls;
        private int restoreCooldownCalls;
        private int restoreSoundCalls;
        private int tooltipCapacityReads;

        private ContractVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions
        ) {
            final int kind;
            if ("onItemRightClick".equals(name) && RIGHT_CLICK.equals(descriptor)) {
                rightClickFound = true;
                kind = 1;
            } else if ("onItemUseFinish".equals(name) && FINISH.equals(descriptor)) {
                finishFound = true;
                kind = 2;
            } else if ("findAndChargeItem".equals(name) && RESTORE.equals(descriptor)) {
                restoreFound = true;
                kind = 3;
            } else if ("addInformation".equals(name) && TOOLTIP.equals(descriptor)) {
                tooltipFound = true;
                kind = 4;
            } else {
                return null;
            }
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String invokedName,
                        String invokedDescriptor, boolean isInterface
                ) {
                    if ("findAndChargeItem".equals(invokedName)) {
                        if (kind == 1) rightClickRestoreCalls++;
                        if (kind == 2) finishRestoreCalls++;
                    }
                    if (kind == 3 && "rechargeMana".equals(invokedName)) restoreRechargeCalls++;
                    if (kind == 3 && "shrink".equals(invokedName)) restoreShrinkCalls++;
                    if (kind == 3 && "setCooldown".equals(invokedName)) restoreCooldownCalls++;
                    if (kind == 3 && "playSoundAtPlayer".equals(invokedName)) restoreSoundCalls++;
                }

                @Override
                public void visitFieldInsn(
                        int opcode, String owner, String name, String descriptor
                ) {
                    if (kind == 4 && opcode == Opcodes.GETFIELD
                            && "electroblob/wizardry/item/ItemManaFlask$Size".equals(owner)
                            && "capacity".equals(name) && "I".equals(descriptor)) {
                        tooltipCapacityReads++;
                    }
                }
            };
        }
    }
}
