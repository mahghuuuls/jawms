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
    private static final String RIGHT_CLICK_DESCRIPTOR =
            "(Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;";
    private static final String USING_TICK_DESCRIPTOR =
            "(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/entity/EntityLivingBase;I)V";
    private static final String STOPPED_USING_DESCRIPTOR =
            "(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/EntityLivingBase;I)V";
    private static final String ADD_INFORMATION_DESCRIPTOR =
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;"
                    + "Ljava/util/List;Lnet/minecraft/client/util/ITooltipFlag;)V";

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
        assertTrue(visitor.effectPosition < visitor.postPosition,
                "Coordination must be able to reject before Wizardry success notifications");
        assertTrue(visitor.postPosition < visitor.debitPosition,
                "Legacy ordering must remain Post before late deduction");
        assertEquals(4, visitor.rightClickReturns);
        assertEquals(1, visitor.usingTickStopActiveHandCalls);
        assertEquals(1, visitor.stoppedUsingGetManaCalls);
        assertTrue(visitor.addInformationFound);
        assertEquals(1, visitor.tooltipGetManaCalls);
        assertEquals(1, visitor.tooltipGetManaCapacityCalls);
        assertEquals(1, visitor.tooltipProgressionCalls);
        assertTrue(visitor.getManaFound);
        assertEquals(1, visitor.getManaDamageReads);
        assertTrue(visitor.setManaFound);
        assertEquals(1, visitor.setManaDamageWrites);
        assertEquals(1, visitor.attributeMeleeLevelCalls);
        assertEquals(1, visitor.hitMeleeLevelCalls);
        assertEquals(1, visitor.hitManaConsumptionCalls);
        assertEquals(1, visitor.creativeMeleeLevelCalls);
        assertEquals(1, visitor.attackMeleeLevelCalls);
    }

    private static final class ContractVisitor extends ClassVisitor {

        private boolean canCastFound;
        private int canCastCostStores;
        private int canCastGetManaCalls;
        private boolean castFound;
        private int castIntegerSlotEightStores;
        private int castSpellCalls;
        private int castConsumeManaCalls;
        private int castCallPosition, effectPosition, postPosition, debitPosition;
        private int castContinuousFieldReads;
        private int rightClickReturns;
        private int usingTickStopActiveHandCalls;
        private int stoppedUsingGetManaCalls;
        private boolean addInformationFound;
        private int tooltipGetManaCalls;
        private int tooltipGetManaCapacityCalls;
        private int tooltipProgressionCalls;
        private boolean getManaFound;
        private int getManaDamageReads;
        private boolean setManaFound;
        private int setManaDamageWrites;
        private int attributeMeleeLevelCalls;
        private int hitMeleeLevelCalls;
        private int hitManaConsumptionCalls;
        private int creativeMeleeLevelCalls;
        private int attackMeleeLevelCalls;

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
                            effectPosition = ++castCallPosition;
                        }
                        if ("electroblob/wizardry/event/SpellCastEvent$Post".equals(owner)
                                && "<init>".equals(invokedName)) postPosition = ++castCallPosition;
                        if ("electroblob/wizardry/item/ItemWand".equals(owner)
                                && "consumeMana".equals(invokedName)) {
                            castConsumeManaCalls++;
                            debitPosition = ++castCallPosition;
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
            if ("onItemRightClick".equals(name) && RIGHT_CLICK_DESCRIPTOR.equals(descriptor)) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            rightClickReturns++;
                        }
                    }
                };
            }
            if ("onUsingTick".equals(name) && USING_TICK_DESCRIPTOR.equals(descriptor)) {
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        if ("net/minecraft/entity/player/EntityPlayer".equals(owner)
                                && "stopActiveHand".equals(invokedName)
                                && "()V".equals(invokedDescriptor)) {
                            usingTickStopActiveHandCalls++;
                        }
                    }
                };
            }
            if ("onPlayerStoppedUsing".equals(name)
                    && STOPPED_USING_DESCRIPTOR.equals(descriptor)) {
                return new MethodVisitor(Opcodes.ASM5) {
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
                            stoppedUsingGetManaCalls++;
                        }
                    }
                };
            }
            if ("addInformation".equals(name)
                    && ADD_INFORMATION_DESCRIPTOR.equals(descriptor)) {
                addInformationFound = true;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(
                            int opcode,
                            String owner,
                            String invokedName,
                            String invokedDescriptor,
                            boolean isInterface
                    ) {
                        if ("electroblob/wizardry/item/ItemWand".equals(owner)
                                && "getMana".equals(invokedName)) {
                            tooltipGetManaCalls++;
                        }
                        if ("electroblob/wizardry/item/ItemWand".equals(owner)
                                && "getManaCapacity".equals(invokedName)) {
                            tooltipGetManaCapacityCalls++;
                        }
                        if ("electroblob/wizardry/util/WandHelper".equals(owner)
                                && "getProgression".equals(invokedName)) {
                            tooltipProgressionCalls++;
                        }
                    }
                };
            }
            if ("getMana".equals(name)
                    && "(Lnet/minecraft/item/ItemStack;)I".equals(descriptor)) {
                getManaFound = true;
                return calls((owner, invokedName, invokedDescriptor) -> {
                    if ("electroblob/wizardry/item/ItemWand".equals(owner)
                            && "getDamage".equals(invokedName)) {
                        getManaDamageReads++;
                    }
                });
            }
            if ("setMana".equals(name)
                    && "(Lnet/minecraft/item/ItemStack;I)V".equals(descriptor)) {
                setManaFound = true;
                return calls((owner, invokedName, invokedDescriptor) -> {
                    if ("net/minecraft/item/Item".equals(owner)
                            && "setDamage".equals(invokedName)) {
                        setManaDamageWrites++;
                    }
                });
            }
            if ("getAttributeModifiers".equals(name)) {
                return upgradeCalls(() -> attributeMeleeLevelCalls++);
            }
            if ("hitEntity".equals(name)) {
                return calls((owner, invokedName, invokedDescriptor) -> {
                    if ("electroblob/wizardry/util/WandHelper".equals(owner)
                            && "getUpgradeLevel".equals(invokedName)) {
                        hitMeleeLevelCalls++;
                    }
                    if ("electroblob/wizardry/item/ItemWand".equals(owner)
                            && "consumeMana".equals(invokedName)) {
                        hitManaConsumptionCalls++;
                    }
                });
            }
            if ("canDestroyBlockInCreative".equals(name)) {
                return upgradeCalls(() -> creativeMeleeLevelCalls++);
            }
            if ("onAttackEntityEvent".equals(name)) {
                return upgradeCalls(() -> attackMeleeLevelCalls++);
            }
            return null;
        }

        private static MethodVisitor upgradeCalls(Runnable match) {
            return calls((owner, invokedName, invokedDescriptor) -> {
                if ("electroblob/wizardry/util/WandHelper".equals(owner)
                        && "getUpgradeLevel".equals(invokedName)) {
                    match.run();
                }
            });
        }

        private static MethodVisitor calls(MethodCall call) {
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String invokedName,
                        String invokedDescriptor,
                        boolean isInterface
                ) {
                    call.accept(owner, invokedName, invokedDescriptor);
                }
            };
        }

        private interface MethodCall {
            void accept(String owner, String name, String descriptor);
        }
    }
}
