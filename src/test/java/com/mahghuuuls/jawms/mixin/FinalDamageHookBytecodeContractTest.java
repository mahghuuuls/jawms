package com.mahghuuuls.jawms.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Label;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FinalDamageHookBytecodeContractTest {

    @Test
    void livingAndPlayerDamageMethodsEachHaveOneFinalDamageCall() throws IOException {
        assertFinalDamageCall(EntityLivingBase.class);
        assertFinalDamageCall(EntityPlayer.class);
    }

    private static void assertFinalDamageCall(Class<?> target) throws IOException {
        String resource = "/" + target.getName().replace('.', '/') + ".class";
        InputStream input = target.getResourceAsStream(resource);
        assertNotNull(input);
        FinalDamageVisitor visitor = new FinalDamageVisitor();
        try {
            new ClassReader(input).accept(visitor, 0);
        } finally {
            input.close();
        }
        assertEquals(1, visitor.damageMethods);
        assertEquals(1, visitor.finalDamageCalls);
        assertTrue(visitor.immediateStoreAndZeroCheck,
                target.getSimpleName() + " must immediately store and zero-check final damage");
        assertEquals(0, visitor.damageRewritesAfterHook,
                target.getSimpleName() + " must not rewrite final damage after the Forge hook");
        assertEquals(0, visitor.extraBranchesBeforeHealth,
                target.getSimpleName() + " must not add another rejection branch before health");
        assertEquals(0, visitor.returnsBeforeHealth,
                target.getSimpleName() + " must not return before applying positive final damage");
        assertTrue(visitor.healthSubtractsFinalDamage,
                target.getSimpleName() + " setHealth must subtract the stored final damage");
        assertTrue(visitor.zeroBranchSkipsPastHealth,
                target.getSimpleName() + " zero-damage branch must be the only path around health application");
    }

    private static final class FinalDamageVisitor extends ClassVisitor {
        private int damageMethods;
        private int finalDamageCalls;
        private boolean immediateStoreAndZeroCheck;
        private int damageRewritesAfterHook;
        private int extraBranchesBeforeHealth;
        private int returnsBeforeHealth;
        private boolean healthSubtractsFinalDamage;
        private boolean zeroBranchSkipsPastHealth;

        private FinalDamageVisitor() {
            super(Opcodes.ASM5);
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions
        ) {
            if (!"(Lnet/minecraft/util/DamageSource;F)V".equals(descriptor)
                    || !("damageEntity".equals(name) || "func_70665_d".equals(name))) {
                return null;
            }
            damageMethods++;
            return new MethodVisitor(Opcodes.ASM5) {
                private static final int BEFORE_HOOK = 0;
                private static final int EXPECT_STORE = 1;
                private static final int EXPECT_LOAD = 2;
                private static final int EXPECT_ZERO = 3;
                private static final int EXPECT_COMPARE = 4;
                private static final int EXPECT_ZERO_BRANCH = 5;
                private static final int BEFORE_HEALTH = 6;
                private static final int AFTER_HEALTH = 7;
                private static final int LAST_DAMAGE_LOAD = -100;
                private static final int LAST_DAMAGE_SUBTRACTION = -101;

                private int state = BEFORE_HOOK;
                private Label zeroExit;
                private int lastMeaningfulOpcode = -1;

                @Override
                public void visitVarInsn(int opcode, int var) {
                    if (state == EXPECT_STORE && opcode == Opcodes.FSTORE && var == 2) {
                        state = EXPECT_LOAD;
                    } else if (state == EXPECT_LOAD && opcode == Opcodes.FLOAD && var == 2) {
                        state = EXPECT_ZERO;
                    } else if (state >= BEFORE_HEALTH && state < AFTER_HEALTH
                            && opcode == Opcodes.FSTORE && var == 2) {
                        damageRewritesAfterHook++;
                    } else if (state >= EXPECT_STORE && state < BEFORE_HEALTH) {
                        state = BEFORE_HOOK;
                    }
                    lastMeaningfulOpcode = opcode == Opcodes.FLOAD && var == 2
                            ? LAST_DAMAGE_LOAD : opcode;
                }

                @Override
                public void visitInsn(int opcode) {
                    if (state == EXPECT_ZERO && opcode == Opcodes.FCONST_0) {
                        state = EXPECT_COMPARE;
                    } else if (state == EXPECT_COMPARE
                            && (opcode == Opcodes.FCMPL || opcode == Opcodes.FCMPG)) {
                        state = EXPECT_ZERO_BRANCH;
                    } else if (state >= EXPECT_STORE && state < BEFORE_HEALTH) {
                        state = BEFORE_HOOK;
                    }
                    if (state == BEFORE_HEALTH && opcode >= Opcodes.IRETURN
                            && opcode <= Opcodes.RETURN) {
                        returnsBeforeHealth++;
                    }
                    lastMeaningfulOpcode = opcode == Opcodes.FSUB
                            && lastMeaningfulOpcode == LAST_DAMAGE_LOAD
                            ? LAST_DAMAGE_SUBTRACTION : opcode;
                }

                @Override
                public void visitJumpInsn(int opcode, Label label) {
                    if (state == EXPECT_ZERO_BRANCH && opcode == Opcodes.IFEQ) {
                        immediateStoreAndZeroCheck = true;
                        zeroExit = label;
                        state = BEFORE_HEALTH;
                    } else if (state == BEFORE_HEALTH) {
                        extraBranchesBeforeHealth++;
                    } else if (state >= EXPECT_STORE && state < BEFORE_HEALTH) {
                        state = BEFORE_HOOK;
                    }
                    lastMeaningfulOpcode = opcode;
                }

                @Override
                public void visitLabel(Label label) {
                    if (label == zeroExit) {
                        zeroBranchSkipsPastHealth = state == AFTER_HEALTH;
                    }
                }

                @Override
                public void visitMethodInsn(
                        int opcode, String owner, String invokedName,
                        String invokedDescriptor, boolean isInterface
                ) {
                    if ("net/minecraftforge/common/ForgeHooks".equals(owner)
                            && "onLivingDamage".equals(invokedName)
                            && "(Lnet/minecraft/entity/EntityLivingBase;"
                            .concat("Lnet/minecraft/util/DamageSource;F)F")
                            .equals(invokedDescriptor)) {
                        finalDamageCalls++;
                        state = EXPECT_STORE;
                    } else if (state == BEFORE_HEALTH
                            && "setHealth".equals(invokedName)
                            && "(F)V".equals(invokedDescriptor)) {
                        healthSubtractsFinalDamage =
                                lastMeaningfulOpcode == LAST_DAMAGE_SUBTRACTION;
                        state = AFTER_HEALTH;
                    }
                    lastMeaningfulOpcode = "setHealth".equals(invokedName)
                            ? lastMeaningfulOpcode : opcode;
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                    if (state >= EXPECT_STORE && state < BEFORE_HEALTH) {
                        state = BEFORE_HOOK;
                    }
                    lastMeaningfulOpcode = opcode;
                }

                @Override
                public void visitIntInsn(int opcode, int operand) {
                    if (state >= EXPECT_STORE && state < BEFORE_HEALTH) {
                        state = BEFORE_HOOK;
                    }
                    lastMeaningfulOpcode = opcode;
                }
            };
        }
    }
}
