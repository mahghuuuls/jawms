package com.mahghuuuls.jawms.testfixture.automation;

import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.*;
import java.io.InputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class InputPilotHarnessTest {
    @Test void delayedReadyNeedsLiveAckAndRttCannotExtendOriginalServerLease() {
        PilotLease lease = new PilotLease(0);
        assertFalse(lease.authorized(1));
        assertFalse(lease.acknowledge(1, 100, 50), "already-expired queued authorization must not arm input");
        assertFalse(lease.authorized(100));
        PilotLease queued = new PilotLease(0);
        assertFalse(queued.acknowledge(1, PilotLease.FRESH_NANOS + 1, PilotSequence.LIMIT_NANOS));
        PilotLease valid = new PilotLease(0);
        assertTrue(valid.acknowledge(10, 20, 100)); assertTrue(valid.authorized(21));
        assertTrue(valid.acknowledge(22, 23, 1000));
        assertFalse(valid.authorized(111), "newer messages cannot extend original remaining90ns");
    }
    @Test void lossOfLivePeerFreshnessExpiresEvenBeforeWholePilotDeadline() {
        PilotLease lease = new PilotLease(0);
        assertTrue(lease.acknowledge(1, 2, PilotSequence.LIMIT_NANOS));
        assertTrue(lease.authorized(PilotLease.FRESH_NANOS));
        assertTrue(lease.expired(PilotLease.FRESH_NANOS + 2));
        assertFalse(lease.authorized(PilotLease.FRESH_NANOS + 2));
        assertFalse(lease.acknowledge(PilotLease.FRESH_NANOS + 2, PilotLease.FRESH_NANOS + 3, PilotSequence.LIMIT_NANOS), "late ACK cannot revive expired freshness");
    }
    @Test void pilotHooksResolveActualVanillaMethodsAndForgeListenerIsAccessible() throws Exception {
        Map<String, String> targets = new LinkedHashMap<>();
        targets.put("net/minecraft/client/Minecraft", "processKeyBinds()V");
        targets.put("net/minecraft/client/multiplayer/PlayerControllerMP", "processRightClick(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/EnumActionResult;");
        for (Map.Entry<String, String> target : targets.entrySet()) {
            List<String> methods = new ArrayList<>();
            try (InputStream input = getClass().getResourceAsStream("/" + target.getKey() + ".class")) {
                assertNotNull(input);
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM5) {
                    @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                        methods.add(name + desc); return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
            assertTrue(methods.contains(target.getValue()), target.toString());
        }
        Class<?> listener = Class.forName("com.mahghuuuls.jawms.testfixture.client.InputPilotDriver$InputPilotDriverListener", false, getClass().getClassLoader());
        assertTrue(java.lang.reflect.Modifier.isPublic(listener.getModifiers()), "Forge's generated handler must be able to call the listener");
        assertTrue(java.lang.reflect.Modifier.isPublic(listener.getMethod("tick", net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent.class).getModifiers()));
    }
    @Test @SuppressWarnings("unchecked") void independentClientSnapshotIsACopyAndMissingBaselineNeverMeansZero() throws Exception {
        net.minecraft.init.Bootstrap.register();
        net.minecraft.entity.player.EntityPlayer player = org.mockito.Mockito.mock(net.minecraft.entity.player.EntityPlayer.class);
        player.world = org.mockito.Mockito.mock(net.minecraft.world.World.class);
        java.lang.reflect.Field remote = net.minecraft.world.World.class.getDeclaredField("isRemote");
        remote.setAccessible(true); remote.setBoolean(player.world, true);
        UUID id = UUID.randomUUID(); org.mockito.Mockito.when(player.getUniqueID()).thenReturn(id);
        Class<?> fixture = Class.forName("example.jawmscast.ContinuousCastFixture");
        java.lang.reflect.Field registry = fixture.getDeclaredField("CLIENT"); registry.setAccessible(true);
        Map<UUID, Object> counts = (Map<UUID, Object>) registry.get(null);
        java.lang.reflect.Method snapshot = fixture.getMethod("snapshot", net.minecraft.entity.player.EntityPlayer.class);
        assertThrows(java.lang.reflect.InvocationTargetException.class, () -> snapshot.invoke(null, player));
        Class<?> counter = Class.forName("example.jawmscast.ContinuousCastFixture$Counts");
        java.lang.reflect.Constructor<?> constructor = counter.getDeclaredConstructor(); constructor.setAccessible(true);
        Object value = constructor.newInstance(); counts.put(id, value);
        try {
            Map<String, String> observed = (Map<String, String>) snapshot.invoke(null, player);
            assertEquals("0", observed.get("effects"));
            java.lang.reflect.Field effects = counter.getDeclaredField("effects"); effects.setAccessible(true); effects.setInt(value, 5);
            assertEquals("0", observed.get("effects"), "Prior snapshot must not alias mutable counters");
            assertThrows(UnsupportedOperationException.class, () -> observed.put("effects", "0"));
            assertEquals("5", ((Map<String, String>) snapshot.invoke(null, player)).get("effects"));
        } finally { counts.remove(id); }
    }
    @Test void normalWaitsForActualEffectsSettlementAndALaterCleanServerSample() {
        PilotSequence s = new PilotSequence(PilotSequence.Kind.NORMAL, 0);
        s.advance(1, true, false, 0, 0, false, false, true, true);
        assertFalse(s.key()); assertEquals(PilotSequence.Phase.READY, s.phase());
        s.advance(2, true, true, 0, 0, false, false, true, true); assertTrue(s.key());
        s.advance(3, true, false, 1, 2, false, false, false, false); assertTrue(s.key(), "unsettled effect is not readiness to release");
        s.advance(4, true, false, 2, 2, false, false, true, false); assertFalse(s.key());
        s.advance(5, true, false, 2, 2, false, false, true, true);
        s.advance(6, true, false, 2, 2, false, false, true, true); assertEquals(PilotSequence.Phase.FINAL, s.phase());
        s.advance(7, true, false, 3, 2, false, false, true, true); assertEquals(PilotSequence.Phase.COMPLETE, s.phase());
    }
    @Test void terminalRemainsHeldUntilTheRealRetryBarrierIsObserved() {
        PilotSequence s = new PilotSequence(PilotSequence.Kind.TERMINAL, 0);
        s.advance(1, true, true, 0, 0, false, false, true, true);
        s.advance(2, true, false, 1, 1, false, false, true, false); assertTrue(s.key());
        s.advance(3, true, false, 2, 1, false, true, true, false); assertFalse(s.key());
    }
    @Test void delayedCaseRequiresAPendingCaptureAndRejectedSecondPressNotATimedSleep() {
        for (PilotSequence.Kind kind : Arrays.asList(PilotSequence.Kind.DELAYED_NORMAL, PilotSequence.Kind.DELAYED_TERMINAL)) {
            PilotSequence s = new PilotSequence(kind, 0);
            s.advance(1, true, true, 0, 0, false, false, true, true);
            s.advance(2, true, false, 1, 2, false, false, true, false); assertFalse(s.key());
            s.advance(3, true, false, 2, 2, false, false, true, true); assertEquals(PilotSequence.Phase.RELEASE, s.phase());
            s.advance(4, true, false, 3, 2, true, false, true, false); assertTrue(s.key());
            s.advance(5, true, false, 4, 2, false, false, true, false); assertTrue(s.key(), "permission alone cannot consume the rejected-input step");
            s.advance(6, true, false, 5, 2, false, true, true, false); assertFalse(s.key());
            s.advance(7, true, false, 6, 2, false, false, true, true); assertEquals(PilotSequence.Phase.COMPLETE, s.phase());
        }
    }
    @Test void lostScopeOrFixedWholeRunDeadlineReleasesInputAndCannotBeRearmedByTraffic() {
        for (boolean scope : new boolean[] { true, false }) {
            PilotSequence s = new PilotSequence(PilotSequence.Kind.NORMAL, 0);
            s.advance(1, true, true, 0, 0, false, false, true, true); assertTrue(s.key());
            s.advance(PilotSequence.LIMIT_NANOS, scope, true, 99, 99, false, false, true, true);
            assertEquals(PilotSequence.Phase.ABORTED, s.phase()); assertFalse(s.key());
            s.advance(2, true, true, 100, 99, false, false, true, true); assertFalse(s.key());
        }
    }
    @Test void explicitScopeAndOperatorPermissionAreNotImplicitLoginAutomation() {
        assertEquals(4, new InputPilotHarness.PilotCommand().getRequiredPermissionLevel());
        assertFalse(InputPilotHarness.disposable("New World")); assertFalse(InputPilotHarness.disposable("MpServer"));
        assertTrue(InputPilotHarness.disposable("JAWMS-C007-MV02-I-P-Retry3"));
        assertFalse(InputPilotHarness.disposable("JAWMS-C007-MV02-" + String.join("", Collections.nCopies(100, "x"))));
        String old = System.getProperty("jawms.enableInputPilot");
        try { System.clearProperty("jawms.enableInputPilot"); assertFalse(InputPilotHarness.enabled()); }
        finally { if (old != null) System.setProperty("jawms.enableInputPilot", old); }
    }
    @Test void actualControlCodecRoundTripsBoundedFieldsAndRejectsTrailingOrOversizedMaps() {
        InputPilotHarness.Message original = new InputPilotHarness.Message(0, UUID.randomUUID(), 2, "NORMAL", "off",
                "JAWMS-C007-MV02-D-P", Collections.singletonMap("effects", "2"));
        PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
        try {
            original.toBytes(buffer); InputPilotHarness.Message copy = new InputPilotHarness.Message(); copy.fromBytes(buffer);
            assertEquals(original.run, copy.run); assertEquals(original.values, copy.values); assertEquals("off", copy.hand);
            buffer.clear(); original.toBytes(buffer); buffer.writeByte(1);
            assertThrows(IllegalArgumentException.class, () -> new InputPilotHarness.Message().fromBytes(buffer));
            buffer.clear(); original.values.clear();
            for (int i = 0; i < 33; i++) original.values.put("key" + i, "0");
            assertThrows(IllegalArgumentException.class, () -> original.toBytes(buffer));
        } finally { buffer.release(); }
    }
    @Test void compiledDriverDoesNotCallCastingControllerOrProductionMutators() throws Exception {
        List<String> calls = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/com/mahghuuuls/jawms/testfixture/client/InputPilotDriver.class")) {
            assertNotNull(in);
            new ClassReader(in).accept(new ClassVisitor(Opcodes.ASM5) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                            calls.add(owner + "." + method);
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(calls.contains("net/minecraft/client/settings/KeyBinding.setKeyBindState"));
        assertTrue(calls.contains("net/minecraft/client/settings/KeyBinding.onTick"));
        for (String call : calls) {
            assertFalse(call.startsWith("net/minecraft/client/multiplayer/PlayerControllerMP."), call);
            assertFalse(call.startsWith("electroblob/wizardry/item/ItemWand."), call);
            assertFalse(call.startsWith("com/mahghuuuls/jawms/client/ClientParticipantUseGuard."), call);
            if (call.startsWith("com/mahghuuuls/jawms/internal/casting/ClientUseState."))
                assertTrue(Arrays.asList("epoch", "intent", "use", "pending", "blocked", "faulted", "enabled").contains(call.substring(call.lastIndexOf('.') + 1)), call);
        }
        // Bytecode inspection proves the intended route/no bypass, not transformed runtime input equivalence.
    }
}
