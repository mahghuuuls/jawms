package com.mahghuuuls.jawms.mixin;

import electroblob.wizardry.client.gui.GuiSpellInfo;
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

class GuiSpellInfoBytecodeContractTest {

    @Test
    void pinnedWizardryGuiContainsRequiredCostPresentationAnchors()
            throws IOException {
        String resource = "/" + GuiSpellInfo.class.getName()
                .replace('.', '/') + ".class";
        InputStream input = GuiSpellInfo.class.getResourceAsStream(resource);
        assertNotNull(input);
        ContractVisitor visitor = new ContractVisitor();
        try {
            new ClassReader(input).accept(visitor, 0);
        } finally {
            input.close();
        }

        assertTrue(visitor.foregroundFound);
        assertEquals(6, visitor.translationCalls);
        assertEquals(2, visitor.spellCostCalls);
        assertEquals(1, visitor.spellDescriptionCalls);
    }

    private static final class ContractVisitor extends ClassVisitor {

        private boolean foregroundFound;
        private int translationCalls;
        private int spellCostCalls;
        private int spellDescriptionCalls;

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
            if (!"drawForegroundLayer".equals(name)
                    || !"(IIII)V".equals(descriptor)) {
                return null;
            }
            foregroundFound = true;
            return new MethodVisitor(Opcodes.ASM5) {
                @Override
                public void visitMethodInsn(
                        int opcode,
                        String owner,
                        String invokedName,
                        String invokedDescriptor,
                        boolean isInterface
                ) {
                    if ("net/minecraft/client/resources/I18n".equals(owner)
                            && "format".equals(invokedName)) {
                        translationCalls++;
                    }
                    if ("electroblob/wizardry/spell/Spell".equals(owner)
                            && "getCost".equals(invokedName)) {
                        spellCostCalls++;
                    }
                    if ("electroblob/wizardry/spell/Spell".equals(owner)
                            && "getDescription".equals(invokedName)) {
                        spellDescriptionCalls++;
                    }
                }
            };
        }
    }
}
