package com.mahghuuuls.jawms.mixin;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Exact pinned classpath anchors only; Mixin transformation and connection/application remain runtime checks. */
class WandPublicationBytecodeContractTest {
    private static final String MC = "net/minecraft/";
    @Test void singleAndBulkSnapshotsHaveTheApprovedCopyAndSendAnchors() throws Exception {
        assertEquals(1, calls(MC + "inventory/Container", "detectAndSendChanges", MC + "item/ItemStack", "copy"));
        assertEquals(1, calls(MC + "entity/player/EntityPlayerMP", "sendSlotContents", MC + "network/NetHandlerPlayServer", "sendPacket"));
        assertEquals(1, calls(MC + "entity/player/EntityPlayerMP", "sendAllContents", MC + "network/play/server/SPacketWindowItems", "<init>"));
        assertEquals(2, calls(MC + "entity/player/EntityPlayerMP", "sendAllContents", MC + "network/NetHandlerPlayServer", "sendPacket"));
        assertEquals(1, calls(MC + "network/play/server/SPacketSetSlot", "<init>", MC + "item/ItemStack", "copy"));
        assertEquals(1, calls(MC + "network/play/server/SPacketWindowItems", "<init>", MC + "item/ItemStack", "copy"));
    }
    @Test void bothDirectCorrectionSitesAndNormalSelectionWritesExist() throws Exception {
        assertEquals(1, calls(MC + "entity/player/InventoryPlayer", "placeItemBackInInventory", MC + "network/play/server/SPacketSetSlot", "<init>"));
        assertEquals(2, calls(MC + "network/NetHandlerPlayServer", "processCustomPayload", MC + "network/play/server/SPacketSetSlot", "<init>"));
        assertEquals(3, calls(MC + "network/NetHandlerPlayServer", "processCustomPayload", MC + "network/NetHandlerPlayServer", "sendPacket"));
        assertEquals(1, writes(MC + "network/NetHandlerPlayServer", "processHeldItemChange"));
        assertEquals(1, writes(MC + "client/network/NetHandlerPlayClient", "handleHeldItemChange"));
        assertEquals(1, writes(MC + "client/Minecraft", "processKeyBinds"));
        for (String method : new String[] { "pickItem", "changeCurrentItem", "setPickedItemStack", "copyInventory" })
            assertTrue(writes(MC + "entity/player/InventoryPlayer", method) > 0, method);
        assertEquals(1, calls(MC + "util/NonNullList", "set", "java/util/List", "set"));
    }
    @Test void receiveAndMainThreadSchedulingAnchorsHaveExactTypedDescriptors() throws Exception {
        assertTrue(methods(MC + "network/NetworkManager").contains("channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V"));
        assertEquals(1, calls(MC + "client/network/NetHandlerPlayClient", "handleSetSlot", MC + "network/PacketThreadUtil", "checkThreadAndEnqueue"));
        assertEquals(1, calls(MC + "client/network/NetHandlerPlayClient", "handleWindowItems", MC + "network/PacketThreadUtil", "checkThreadAndEnqueue"));
        assertTrue(methods(MC + "network/NetworkManager").stream().anyMatch(m -> m.startsWith("dispatchPacket(")));
    }
    @Test void exactWizardryMutationHooksHaveTheirExpectedSignatures() throws Exception {
        List<String> methods = methods("electroblob/wizardry/util/WandHelper");
        assertTrue(methods.contains("selectSpell(Lnet/minecraft/item/ItemStack;I)Z"));
        assertTrue(methods.contains("selectNextSpell(Lnet/minecraft/item/ItemStack;)V"));
        assertTrue(methods.contains("selectPreviousSpell(Lnet/minecraft/item/ItemStack;)V"));
        assertTrue(methods.contains("setSpells(Lnet/minecraft/item/ItemStack;[Lelectroblob/wizardry/spell/Spell;)V"));
        assertTrue(methods.contains("applyUpgrade(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/Item;)V"));
    }
    @Test void commonProductionAndFixtureServerPathsDoNotLinkClientOnlyPacketMethods() throws Exception {
        for (String type : new String[] {
                "com/mahghuuuls/jawms/internal/casting/ServerWandBindings",
                "com/mahghuuuls/jawms/internal/casting/WandPublicationPair",
                "com/mahghuuuls/jawms/testfixture/ContinuousTransportFixture$Hook",
                "com/mahghuuuls/jawms/testfixture/ContinuousTransportFixture$SignalHandler",
                "com/mahghuuuls/jawms/testfixture/observation/PublicationFixture" }) {
            read(type, new ClassVisitor(Opcodes.ASM5) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean iface) {
                            assertFalse(owner.startsWith(MC + "client/"), type + "." + name);
                            if (owner.equals(MC + "network/play/server/SPacketCustomPayload"))
                                assertFalse(method.equals("getChannelName") || method.equals("getBufferData"), type + "." + name);
                            if (owner.equals(MC + "network/play/client/CPacketCustomPayload") && method.equals("<init>"))
                                assertEquals("()V", descriptor, type + "." + name);
                        }
                    };
                }
            });
        }
    }
    private static List<String> methods(String type) throws Exception {
        List<String> names = new ArrayList<>();
        read(type, new ClassVisitor(Opcodes.ASM5) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                names.add(name + desc); return null;
            }
        });
        return names;
    }
    private static int writes(String type, String method) throws Exception { return count(type, method, null, null, true); }
    private static int calls(String type, String method, String owner, String name) throws Exception { return count(type, method, owner, name, false); }
    private static int count(String type, String method, String owner, String target, boolean fields) throws Exception {
        int[] count = {0};
        read(type, new ClassVisitor(Opcodes.ASM5) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exceptions) {
                if (!name.equals(method)) return null;
                return new MethodVisitor(Opcodes.ASM5) {
                    @Override public void visitMethodInsn(int opcode, String calledOwner, String called, String desc, boolean iface) {
                        if (!fields && calledOwner.equals(owner) && called.equals(target)) count[0]++;
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                        if (fields && opcode == Opcodes.PUTFIELD && owner.equals(MC + "entity/player/InventoryPlayer") && name.equals("currentItem")) count[0]++;
                    }
                };
            }
        });
        return count[0];
    }
    private static void read(String type, ClassVisitor visitor) throws Exception {
        try (InputStream input = WandPublicationBytecodeContractTest.class.getResourceAsStream("/" + type + ".class")) {
            assertNotNull(input, type); new ClassReader(input).accept(visitor, 0);
        }
    }
}
