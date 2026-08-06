package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.entity.projectile.EntityForceArrow;
import electroblob.wizardry.spell.ForceArrow;
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

class ForceArrowBytecodeContractTest {

    @Test
    void pinnedForceArrowSpellContainsThePendingCaptureBoundary() throws IOException {
        SpellVisitor visitor = read(ForceArrow.class, new SpellVisitor());
        assertTrue(visitor.extrasFound);
        assertEquals(1, visitor.legacyManaWrites);
    }

    @Test
    void pinnedForceArrowEntityContainsEveryTerminalAndLegacyRefundBoundary()
            throws IOException {
        EntityVisitor visitor = read(EntityForceArrow.class, new EntityVisitor());
        assertTrue(visitor.entityHitFound);
        assertTrue(visitor.blockHitFound);
        assertTrue(visitor.updateFound);
        assertEquals(1, visitor.expiryRefundCalls);
        assertTrue(visitor.legacyRefundFound);
        assertEquals(1, visitor.legacyRechargeCalls);
    }

    private static <T extends ClassVisitor> T read(Class<?> type, T visitor) throws IOException {
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

    private static final class SpellVisitor extends ClassVisitor {
        private boolean extrasFound;
        private int legacyManaWrites;

        private SpellVisitor() { super(Opcodes.ASM5); }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if (!"addArrowExtras".equals(name)) return null;
            extrasFound = true;
            return calls((owner, called, calledDescriptor) -> {
                if ("electroblob/wizardry/entity/projectile/EntityForceArrow".equals(owner)
                        && "setMana".equals(called)
                        && "(I)V".equals(calledDescriptor)) {
                    legacyManaWrites++;
                }
            });
        }
    }

    private static final class EntityVisitor extends ClassVisitor {
        private boolean entityHitFound;
        private boolean blockHitFound;
        private boolean updateFound;
        private int expiryRefundCalls;
        private boolean legacyRefundFound;
        private int legacyRechargeCalls;

        private EntityVisitor() { super(Opcodes.ASM5); }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            if ("onEntityHit".equals(name)) entityHitFound = true;
            if ("onBlockHit".equals(name)) blockHitFound = true;
            if ("onUpdate".equals(name) && "()V".equals(descriptor)) {
                updateFound = true;
                return calls((owner, called, calledDescriptor) -> {
                    if ("electroblob/wizardry/entity/projectile/EntityForceArrow".equals(owner)
                            && "returnManaToCaster".equals(called)
                            && "()V".equals(calledDescriptor)) {
                        expiryRefundCalls++;
                    }
                });
            }
            if ("returnManaToCaster".equals(name) && "()V".equals(descriptor)) {
                legacyRefundFound = true;
                return calls((owner, called, calledDescriptor) -> {
                    if ("electroblob/wizardry/item/IManaStoringItem".equals(owner)
                            && "rechargeMana".equals(called)
                            && "(Lnet/minecraft/item/ItemStack;I)V".equals(calledDescriptor)) {
                        legacyRechargeCalls++;
                    }
                });
            }
            return null;
        }
    }

    private static MethodVisitor calls(MethodCall callback) {
        return new MethodVisitor(Opcodes.ASM5) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                        String descriptor, boolean isInterface) {
                callback.accept(owner, name, descriptor);
            }
        };
    }

    private interface MethodCall {
        void accept(String owner, String name, String descriptor);
    }
}
