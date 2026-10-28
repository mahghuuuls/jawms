package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.client.ClientWandPublications;
import com.mahghuuuls.jawms.internal.casting.WandPublicationPair;
import com.mahghuuuls.jawms.testfixture.client.GapClientObservation;
import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;
import com.mahghuuuls.jawms.testfixture.publication.ClientApplicationObserverMixin;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.network.play.server.*;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.junit.jupiter.api.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the actual injected observer callbacks; it does not substitute for live packet delivery. */
class GapReceiverObservationTest {
    private final List<Endpoint> endpoints = new ArrayList<>();
    private final List<String> observations = new CopyOnWriteArrayList<>();
    private String oldProperty;
    private Logger logger;
    private AbstractAppender appender;

    @BeforeAll static void bootstrap() { net.minecraft.init.Bootstrap.register(); }
    @BeforeEach void begin() throws Exception {
        oldProperty = System.getProperty("jawms.enablePublicationObserver");
        System.setProperty("jawms.enablePublicationObserver", "true");
        clearDiagnostics();
        ClientWandPublications.clear();
        logger = (Logger) LogManager.getLogger("jawms_c007_publication");
        appender = new AbstractAppender("gap-receiver-" + UUID.randomUUID(), null, null) {
            @Override public void append(LogEvent event) { observations.add(event.getMessage().getFormattedMessage()); }
        };
        appender.start(); logger.addAppender(appender);
    }
    @AfterEach void end() throws Exception {
        if (logger != null && appender != null) { logger.removeAppender(appender); appender.stop(); }
        GapClientObservation.endSelection();
        ClientWandPublications.clear();
        for (Endpoint endpoint : endpoints) endpoint.wire.finishAndReleaseAll();
        clearDiagnostics();
        if (oldProperty == null) System.clearProperty("jawms.enablePublicationObserver");
        else System.setProperty("jawms.enablePublicationObserver", oldProperty);
    }

    @Test void idleTrafficCannotSpendEitherBudgetBeforeRelevantRejectedPacketsArrive() throws Exception {
        Endpoint current = endpoint(ContinuousTransportFixture.Mode.GAP_RETIRED);
        ClientWandPublications.capability(current.handler, UUID.randomUUID());
        INetHandler server = mock(net.minecraft.network.NetHandlerPlayServer.class);
        for (int i = 0; i < 4096; i++) {
            Packet<?> packet;
            switch (i % 4) {
                case 0: packet = new SPacketTimeUpdate(i, i, true); break;
                case 1: packet = new SPacketKeepAlive(i); break;
                case 2: packet = new SPacketChat(new TextComponentString("idle")); break;
                default: packet = new CPacketPlayer.Position(i, 64, 0, true); break;
            }
            observe(i % 4 == 3 ? server : current.handler, packet, true);
        }
        PacketBuffer unrelated = new PacketBuffer(Unpooled.buffer().writeByte(1));
        try {
            observe(current.handler, new SPacketCustomPayload("unrelated", unrelated), true);
            assertEquals(1, unrelated.refCnt(), "observation does not consume or release a payload");
            assertEquals(0, unrelated.readerIndex());
        } finally { unrelated.release(); }
        assertEquals(0, objects().size(), "idle packets must not allocate identity ordinals");
        assertEquals(0, diagnostic("lines").getInt(null), "idle packets must not fill the bounded log");
        assertFalse(diagnostic("identityOverflow").getBoolean(null));

        PacketBuffer markerBytes = new PacketBuffer(Unpooled.buffer().writeByte(0));
        SPacketCustomPayload marker = new SPacketCustomPayload(WandPublicationPair.CHANNEL, markerBytes);
        SPacketSetSlot slot = new SPacketSetSlot(0, 36, ItemStack.EMPTY);
        SPacketWindowItems window = new SPacketWindowItems(0, NonNullList.create());
        SPacketHeldItemChange held = new SPacketHeldItemChange(0);
        try {
            observe(current.handler, marker, false);
            observe(current.handler, slot, false);
            observe(current.handler, window, true);
            observe(current.handler, held, true);
            Map<Object, Integer> recorded = objects();
            assertEquals(4, recorded.size(), "rejected relevant packets remain observable");
            assertEquals(4, new HashSet<>(recorded.values()).size());
            assertTrue(recorded.values().stream().allMatch(id -> id > 0));
            assertObservation("gap_receiver_disposition", "packet, " + recorded.get(marker)
                    + ", type, SPacketCustomPayload, forward, false, watermark, 0");
            assertObservation("gap_receiver_disposition", "packet, " + recorded.get(slot)
                    + ", type, SPacketSetSlot, forward, false, watermark, 0");
            assertObservation("gap_receiver_disposition", "packet, " + recorded.get(window)
                    + ", type, SPacketWindowItems, forward, true, watermark, 0");
            assertObservation("gap_receiver_disposition", "packet, " + recorded.get(held)
                    + ", type, SPacketHeldItemChange, forward, true, watermark, 0");
            Integer slotId = recorded.get(slot), heldId = recorded.get(held);
            applyCallback(slot, current.handler);
            GapClientObservation.beginSelection(held);
            assertObservation("main_apply_scope", "packet, " + slotId);
            assertObservation("gap_held_handler", "packet, " + heldId);
            assertEquals(slotId, objects().get(slot), "application callback uses the same packet identity");
            assertEquals(heldId, objects().get(held), "held-handler callback uses the same packet identity");
            assertEquals(1, markerBytes.refCnt());
            assertEquals(0, markerBytes.readerIndex(), "RETURN observation never decodes a consumed marker");
        } finally { GapClientObservation.endSelection(); markerBytes.release(); }
    }

    @Test void foreignRetiredAndNonGapConnectionsDoNotBorrowAnotherHooksMode() throws Exception {
        Endpoint old = endpoint(ContinuousTransportFixture.Mode.GAP_RETIRED);
        Endpoint current = endpoint(ContinuousTransportFixture.Mode.NORMAL);
        ClientWandPublications.capability(current.handler, UUID.randomUUID());
        for (int i = 0; i < 300; i++) {
            observe(old.handler, new SPacketSetSlot(0, 36, ItemStack.EMPTY), false);
            observe(current.handler, new SPacketHeldItemChange(0), true);
            observe(mock(INetHandler.class), new SPacketWindowItems(0, NonNullList.create()), true);
        }
        assertEquals(0, objects().size(), "only the current handler's own armed gap hook may observe");
        assertEquals(0, diagnostic("lines").getInt(null));
        current.hook.configure(ContinuousTransportFixture.Mode.PAIR_FAULT, UUID.randomUUID());
        current.wire.runPendingTasks(); current.wire.checkException();
        SPacketHeldItemChange relevant = new SPacketHeldItemChange(0);
        observe(current.handler, relevant, true);
        assertTrue(objects().get(relevant) > 0);
        ClientWandPublications.clear();
        int count = objects().size(), lines = diagnostic("lines").getInt(null);
        observe(current.handler, new SPacketHeldItemChange(1), true);
        assertEquals(count, objects().size(), "a cleared session is not a current observation owner");
        assertEquals(lines, diagnostic("lines").getInt(null));
    }

    @Test void genuineRelevantPressureStillReportsCapacityWithoutResettingIdentities() throws Exception {
        Endpoint current = endpoint(ContinuousTransportFixture.Mode.GAP_RETIRED);
        ClientWandPublications.capability(current.handler, UUID.randomUUID());
        SPacketHeldItemChange first = new SPacketHeldItemChange(0);
        observe(current.handler, first, true);
        Integer original = objects().get(first);
        for (int i = 0; i < 300; i++) observe(current.handler, new SPacketHeldItemChange(i % 9), true);
        assertEquals(256, objects().size());
        assertTrue(diagnostic("identityOverflow").getBoolean(null), "relevant evidence still fails explicitly at the approved bound");
        assertEquals(original, objects().get(first), "no hidden reset or ordinal reuse to conceal capacity");
    }

    private Endpoint endpoint(ContinuousTransportFixture.Mode mode) throws Exception {
        NetworkManager manager = mock(NetworkManager.class);
        NetHandlerPlayClient handler = mock(NetHandlerPlayClient.class);
        when(handler.getNetworkManager()).thenReturn(manager);
        when(manager.getNetHandler()).thenReturn(handler);
        EmbeddedChannel wire = new EmbeddedChannel();
        wire.pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter());
        when(manager.channel()).thenReturn(wire);
        java.lang.reflect.Constructor<ContinuousTransportFixture> constructor = ContinuousTransportFixture.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance().client(new FMLNetworkEvent.ClientConnectedToServerEvent(manager, "MODDED"));
        wire.runPendingTasks(); wire.checkException();
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(manager);
        assertNotNull(hook);
        hook.configure(mode, UUID.randomUUID()); wire.runPendingTasks(); wire.checkException();
        Endpoint result = new Endpoint(handler, hook, wire); endpoints.add(result); return result;
    }
    private void observe(INetHandler handler, Packet<?> packet, boolean forward) throws Exception {
        Method method = ClientApplicationObserverMixin.class.getDeclaredMethod("gapReceive", INetHandler.class, Packet.class, CallbackInfoReturnable.class);
        method.setAccessible(true);
        CallbackInfoReturnable<Boolean> callback = new CallbackInfoReturnable<>("receive", false, forward);
        method.invoke(null, handler, packet, callback);
        assertEquals(forward, callback.getReturnValue(), "observer must preserve the real receiver decision");
        assertFalse(callback.isCancelled());
    }
    private void applyCallback(Packet<?> packet, NetHandlerPlayClient handler) throws Exception {
        Method method = ClientApplicationObserverMixin.class.getDeclaredMethod("observedApply", Packet.class, NetHandlerPlayClient.class, Runnable.class, CallbackInfo.class);
        method.setAccessible(true);
        method.invoke(null, packet, handler, (Runnable) () -> {}, new CallbackInfo("apply", false));
    }
    private void assertObservation(String event, String values) {
        String expected = "PUBLICATION_OBSERVER event=" + event + " values=[" + values + "] thread=";
        assertTrue(observations.stream().anyMatch(line -> line.startsWith(expected)),
                "missing emitted evidence for the expected role and packet token: " + expected);
    }
    private static Field diagnostic(String name) throws Exception {
        Field field = PublicationFixture.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    @SuppressWarnings("unchecked") private static Map<Object, Integer> objects() throws Exception { return (Map<Object, Integer>) diagnostic("OBJECTS").get(null); }
    private static void clearDiagnostics() throws Exception { objects().clear(); diagnostic("lines").setInt(null, 0); diagnostic("identityOverflow").setBoolean(null, false); }
    private static final class Endpoint {
        final NetHandlerPlayClient handler; final ContinuousTransportFixture.Hook hook; final EmbeddedChannel wire;
        Endpoint(NetHandlerPlayClient handler, ContinuousTransportFixture.Hook hook, EmbeddedChannel wire) { this.handler=handler; this.hook=hook; this.wire=wire; }
    }
}
