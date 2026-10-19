package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleIndexedCodec;
import net.minecraft.network.play.client.CPacketCustomPayload;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.local.LocalAddress;
import io.netty.channel.local.LocalChannel;
import io.netty.channel.local.LocalServerChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ContinuousTransportFixtureTest {
    @BeforeAll static void bootstrap() { net.minecraft.init.Bootstrap.register(); }
    @Test @SuppressWarnings("unchecked") void withheldCapabilityConsumesTheNeverEncodedOriginalPayload() throws Exception {
        UUID owner = UUID.randomUUID();
        net.minecraft.entity.player.EntityPlayerMP player = org.mockito.Mockito.mock(net.minecraft.entity.player.EntityPlayerMP.class);
        org.mockito.Mockito.when(player.getUniqueID()).thenReturn(owner);
        net.minecraft.network.NetHandlerPlayServer handler = org.mockito.Mockito.mock(net.minecraft.network.NetHandlerPlayServer.class);
        handler.player = player;
        net.minecraft.network.NetworkManager manager = org.mockito.Mockito.mock(net.minecraft.network.NetworkManager.class);
        org.mockito.Mockito.when(manager.getNetHandler()).thenReturn(handler);
        java.lang.reflect.Field field = ContinuousTransportFixture.class.getDeclaredField("WITHOUT_CAPABILITY"); field.setAccessible(true);
        Set<UUID> without = (Set<UUID>) field.get(null); without.add(owner);
        ContinuousTransportFixture.Hook hook = new ContinuousTransportFixture.Hook(manager, Side.SERVER); hook.owner = owner;
        EmbeddedChannel wire = new EmbeddedChannel(hook);
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer()); payload.writeByte(4);
        new ParticipantUseMessages.Capability(UUID.randomUUID()).toBytes(payload);
        try {
            assertFalse(wire.writeOutbound(new FMLProxyPacket(payload, "jawms")));
            wire.checkException(); assertNull(wire.readOutbound());
            assertEquals(0, payload.refCnt(), "withheld frames are neither encoded nor transferred to a local peer");
        } finally { without.remove(owner); if (payload.refCnt() > 0) payload.release(); wire.finishAndReleaseAll(); }
    }
    @Test void gateTransfersOriginalAndReplayOwnershipThroughRealForgeCodec() {
        for (boolean blockedFirst : new boolean[] { false, true }) gateOrder(blockedFirst);
    }
    private void gateOrder(boolean blockedFirst) {
        UUID epoch = UUID.randomUUID();
        ContinuousTransportFixture.Hook hook = new ContinuousTransportFixture.Hook(null, Side.CLIENT);
        List<byte[]> received = new ArrayList<>();
        SimpleIndexedCodec codec = new SimpleIndexedCodec(); codec.addDiscriminator(7, ParticipantUseMessages.Close.class);
        EmbeddedChannel wire = new EmbeddedChannel(hook, codec, new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext context, Object message) {
                ParticipantUseMessages.Close close = (ParticipantUseMessages.Close) message;
                assertTrue(close.isValid());
                PacketBuffer bytes = new PacketBuffer(Unpooled.buffer()); bytes.writeByte(7); close.toBytes(bytes);
                try { byte[] value = new byte[bytes.readableBytes()]; bytes.readBytes(value); received.add(value); }
                finally { bytes.release(); }
            }
        });
        PacketBuffer payload = new PacketBuffer(Unpooled.buffer()); payload.writeByte(7);
        new ParticipantUseMessages.Close(epoch, 8, 12, EnumHand.MAIN_HAND,
                new ResourceLocation("fixture", "stream"), ParticipantUseMessages.CloseCause.TERMINAL, 17).toBytes(payload);
        byte[] expected = new byte[payload.readableBytes()]; payload.getBytes(0, expected);
        try {
            hook.configure(ContinuousTransportFixture.Mode.GATE, UUID.randomUUID()); wire.runPendingTasks();
            hook.readiness.pending(epoch, 8, EnumHand.MAIN_HAND, 0);
            if (blockedFirst) { hook.blockedGestureObserved(epoch, 8); wire.runPendingTasks(); }
            assertFalse(wire.writeInbound(new FMLProxyPacket(payload, "jawms")));
            assertEquals(0, payload.refCnt(), "Intercepted original is consumed, not retained beside byte history");
            if (blockedFirst) {
                assertEquals(0, hook.queued(), "the already observed press must release on the first close arrival");
                assertEquals(2, received.size(), "a second press must not hide a lost blocked-first signal");
            } else {
                assertEquals(1, hook.queued()); assertTrue(received.isEmpty());
                hook.blockedGestureObserved(epoch, 8); wire.runPendingTasks();
            }
            assertEquals(0, hook.queued()); assertEquals(2, received.size());
            assertArrayEquals(expected, received.get(0)); assertArrayEquals(expected, received.get(1));
            hook.blockedGestureObserved(epoch, 8); wire.runPendingTasks(); assertEquals(2, received.size());
            hook.reset(); wire.runPendingTasks(); assertNull(hook.lastClose); assertNull(hook.lastRelease);
        } finally { if (payload.refCnt() > 0) payload.release(); wire.finishAndReleaseAll(); }
    }
    @Test void outgoingReplaySurvivesRealLocalChannelWriteCompletionUntilPeerForgeDecode() throws Exception {
        EventLoopGroup serverLoops = new DefaultEventLoopGroup(1), clientLoops = new DefaultEventLoopGroup(1);
        CountDownLatch written = new CountDownLatch(1), permitDecode = new CountDownLatch(1), decoded = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        ContinuousTransportFixture.Hook hook = new ContinuousTransportFixture.Hook(null, Side.CLIENT);
        Channel server = null, client = null;
        PacketBuffer source = new PacketBuffer(Unpooled.buffer()); source.writeByte(6);
        new ParticipantUseMessages.Release(UUID.randomUUID(), 3).toBytes(source);
        ContinuousTransportFixture.Saved saved = new ContinuousTransportFixture.Saved(new FMLProxyPacket(source, "jawms")); source.release();
        try {
            server = new ServerBootstrap().group(serverLoops).channel(LocalServerChannel.class)
                    .childHandler(new ChannelInitializer<LocalChannel>() {
                        @Override protected void initChannel(LocalChannel channel) {
                            SimpleIndexedCodec codec = new SimpleIndexedCodec(); codec.addDiscriminator(6, ParticipantUseMessages.Release.class);
                            channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                                @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
                                    if (!permitDecode.await(5, TimeUnit.SECONDS)) throw new AssertionError("Peer decode was never released");
                                    context.fireChannelRead(new FMLProxyPacket((CPacketCustomPayload) message));
                                }
                            }, codec, new ChannelInboundHandlerAdapter() {
                                @Override public void channelRead(ChannelHandlerContext context, Object message) {
                                    ParticipantUseMessages.Release release = (ParticipantUseMessages.Release) message;
                                    if (!release.isValid() || release.intent() != 3) error.set(new AssertionError("Replay body corrupted"));
                                    decoded.countDown();
                                }
                                @Override public void exceptionCaught(ChannelHandlerContext context, Throwable failure) {
                                    error.set(failure); decoded.countDown();
                                }
                            });
                        }
                    }).bind(new LocalAddress("jawms-replay-" + UUID.randomUUID())).sync().channel();
            client = new Bootstrap().group(clientLoops).channel(LocalChannel.class)
                    .handler(new ChannelInitializer<LocalChannel>() {
                        @Override protected void initChannel(LocalChannel channel) {
                            channel.pipeline().addLast(new ChannelOutboundHandlerAdapter() {
                                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
                                    promise.addListener(future -> written.countDown());
                                    context.write(((FMLProxyPacket) message).toC17Packet(), promise);
                                }
                            }, hook);
                        }
                    }).connect(server.localAddress()).sync().channel();
            hook.context.executor().execute(() -> hook.deliver(saved, false));
            assertTrue(written.await(5, TimeUnit.SECONDS));
            // Let every writer completion listener run before the receiver may touch the shared nested buffer.
            client.eventLoop().submit(() -> {}).sync();
            permitDecode.countDown(); assertTrue(decoded.await(5, TimeUnit.SECONDS));
            assertNull(error.get(), "Local packet wrappers do not retain their nested payload on write completion");
        } finally {
            permitDecode.countDown();
            if (client != null) client.close().syncUninterruptibly(); if (server != null) server.close().syncUninterruptibly();
            clientLoops.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
            serverLoops.shutdownGracefully(0, 1, TimeUnit.SECONDS).syncUninterruptibly();
        }
    }
    @Test void healthyPairGateRetainsBothOriginalObjectsUntilDrainAndDisconnectReleasesUnforwardedMarker() {
        String old = System.getProperty("jawms.enablePublicationObserver"); System.setProperty("jawms.enablePublicationObserver", "true");
        try {
            for (boolean disconnect : new boolean[] { false, true }) {
                ContinuousTransportFixture.Hook hook = new ContinuousTransportFixture.Hook(null, Side.CLIENT);
                List<Object> received = new ArrayList<>();
                EmbeddedChannel wire = new EmbeddedChannel(hook, new ChannelInboundHandlerAdapter() {
                    @Override public void channelRead(ChannelHandlerContext context, Object message) {
                        received.add(message);
                        if (message instanceof net.minecraft.network.play.server.SPacketCustomPayload)
                            ((net.minecraft.network.play.server.SPacketCustomPayload) message).getBufferData().release();
                    }
                });
                com.mahghuuuls.jawms.internal.casting.WandPublicationRecord record = new com.mahghuuuls.jawms.internal.casting.WandPublicationRecord(
                        UUID.randomUUID(), 1, com.mahghuuuls.jawms.internal.casting.WandPublicationRecord.SLOT, 0,
                        new com.mahghuuuls.jawms.internal.casting.WandPublicationRecord.Entry(0, 36, 3, 4, 5));
                net.minecraft.network.play.server.SPacketCustomPayload marker = com.mahghuuuls.jawms.internal.casting.WandPublicationPair.marker(record);
                io.netty.buffer.ByteBuf buffer = marker.getBufferData();
                net.minecraft.network.play.server.SPacketSetSlot slot = new net.minecraft.network.play.server.SPacketSetSlot(0, 36,
                        new net.minecraft.item.ItemStack(net.minecraft.init.Items.STICK));
                try {
                    hook.configure(ContinuousTransportFixture.Mode.PAIR_FIRST, UUID.randomUUID()); wire.runPendingTasks();
                    assertFalse(wire.writeInbound(marker, slot));
                    assertTrue(received.isEmpty()); assertEquals(1, hook.queued()); assertEquals(1, buffer.refCnt());
                    if (!disconnect) {
                        hook.reset(); wire.runPendingTasks();
                        assertEquals(Arrays.asList(marker, slot), received); assertEquals(0, hook.queued());
                        assertTrue(hook.applying.isEmpty()); assertEquals(0, buffer.refCnt());
                    }
                } finally { wire.finishAndReleaseAll(); }
                assertEquals(0, buffer.refCnt());
            }
        } finally { if (old == null) System.clearProperty("jawms.enablePublicationObserver"); else System.setProperty("jawms.enablePublicationObserver", old); }
    }
}
