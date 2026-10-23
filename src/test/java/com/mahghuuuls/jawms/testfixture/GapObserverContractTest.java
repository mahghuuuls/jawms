package com.mahghuuuls.jawms.testfixture;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual compiled selectors and passivity, not a running Mixin/vanilla write. */
class GapObserverContractTest {
    private static final String ROOT = "com/mahghuuuls/jawms/";
    private ClassNode type(String name) throws Exception {
        try (InputStream stream = getClass().getResourceAsStream("/" + ROOT + name + ".class")) {
            assertNotNull(stream); ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    @Test void oneWizardryStartCallbackRequestsEightPublicationsWithoutCountingPostAsEffects() throws Exception {
        net.minecraft.init.Bootstrap.register();
        net.minecraft.item.ItemStack wand = new net.minecraft.item.ItemStack(net.minecraft.init.Items.STICK);
        electroblob.wizardry.util.WandHelper.setCooldowns(wand, new int[] {0});
        electroblob.wizardry.util.WandHelper.setMaxCooldowns(wand, new int[] {10});
        net.minecraft.nbt.NBTTagCompound before = wand.getTagCompound().copy();
        List<Integer> publications = new ArrayList<>();
        ContinuousTransportFixture.seedPublications(wand, () -> {
            assertEquals(0, electroblob.wizardry.util.WandHelper.getCurrentCooldown(wand));
            publications.add(electroblob.wizardry.util.WandHelper.getMaxCooldowns(wand)[0]);
        });
        assertEquals(Arrays.asList(11,12,13,14,15,16,17,10), publications);
        assertEquals(before, wand.getTagCompound(), "all wand data restored, active cooldown never changed");
        MethodNode callback = type("testfixture/ContinuousTransportFixture").methods.stream()
                .filter(m -> m.name.equals("effect")).findFirst().get();
        assertTrue(callback.desc.contains("SpellCastEvent$Post"));
        long calls = Arrays.stream(callback.instructions.toArray()).filter(i -> i instanceof MethodInsnNode
                && ((MethodInsnNode) i).name.equals("seedPublications")).count();
        assertEquals(1, calls, "one actual start callback, explicit bounded publication loop");
        assertThrows(IllegalStateException.class, () -> ContinuousTransportFixture.seedPublications(wand, () -> { throw new IllegalStateException("failed publication"); }));
        assertEquals(before, wand.getTagCompound(), "failed publication still restores display data");
    }
    @Test void postWriteProbeMatchesTheActualRedirectedAssignmentNotTheOriginalHandler() throws Exception {
        ClassNode source = type("internal/casting/WandInventoryMutations"); int writes = 0;
        for (MethodNode method : source.methods) if (method.name.equals("select"))
            for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) instruction;
                if (field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("currentItem")) writes++;
            }
        assertEquals(1, writes);
        ClassNode probe = type("testfixture/publication/GapSelectionWriteObserverMixin");
        String annotations = probe.methods.toString();
        boolean matched = false;
        for (MethodNode method : probe.methods) for (AnnotationNode annotation : method.visibleAnnotations == null
                ? Collections.<AnnotationNode>emptyList() : method.visibleAnnotations) {
            if (!annotation.desc.endsWith("/Inject;")) continue;
            String value = flatten(annotation.values);
            matched = value.contains("select") && value.contains("currentItem:I") && value.contains("AFTER") && value.contains("181");
        }
        assertTrue(matched, annotations);
    }
    private String flatten(Object value) {
        if (value instanceof AnnotationNode) return flatten(((AnnotationNode) value).values);
        if (value instanceof List) { StringBuilder out = new StringBuilder(); for (Object item : (List<?>) value) out.append(flatten(item)); return out.toString(); }
        if (value instanceof String[]) return Arrays.toString((String[]) value);
        return String.valueOf(value);
    }
    @Test void probesCannotWriteProductionFieldsOrInvokeStateAuthority() throws Exception {
        for (String name : new String[] { "GapCloseObserverMixin", "GapReleaseObserverMixin", "GapSelectionWriteObserverMixin" }) {
            ClassNode node = type("testfixture/publication/" + name);
            for (MethodNode method : node.methods) for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode) assertNotEquals(Opcodes.PUTFIELD, instruction.getOpcode());
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    assertFalse(call.name.equals("setReturnValue") || call.name.equals("cancel"));
                    if (call.owner.startsWith(ROOT + "internal/casting/"))
                        assertFalse(Arrays.asList("input", "release", "accept", "valid", "adopt", "publish", "tick").contains(call.name));
                }
            }
        }
    }
    @Test void heldHandlerAlwaysCallsOriginalOnceAndCleansItsScopeOnExceptionalExit() throws Exception {
        MethodNode wrapper = type("testfixture/publication/GapHeldHandlerObserverMixin").methods.stream()
                .filter(m -> m.name.equals("observed")).findFirst().get();
        assertFalse(wrapper.tryCatchBlocks.isEmpty());
        int originalCalls = 0, cleanups = 0;
        for (AbstractInsnNode instruction : wrapper.instructions.toArray()) if (instruction instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.name.equals("call") && call.owner.endsWith("Operation")) originalCalls++;
            if (call.name.equals("endSelection")) cleanups++;
        }
        assertEquals(2, originalCalls, "one unarmed branch and one scoped branch, never both");
        assertEquals(2, cleanups, "normal and exception finally paths");
    }
}
