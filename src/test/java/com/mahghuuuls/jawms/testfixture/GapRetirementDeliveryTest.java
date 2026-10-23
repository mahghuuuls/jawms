package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.internal.casting.WandPublicationPair;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleIndexedCodec;
import net.minecraftforge.fml.relauncher.Side;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GapRetirementDeliveryTest {
    @Test void switchingToAnotherCaseExplicitlyCancelsTheByteCapsule() throws Exception {
        java.lang.reflect.Field field = ContinuousTransportFixture.class.getDeclaredField("CAPSULE"); field.setAccessible(true);
        GapReplayCapsule capsule = (GapReplayCapsule) field.get(null); capsule.clear();
        ContinuousTransportFixture.Hook hook = new ContinuousTransportFixture.Hook(null, Side.CLIENT);
        EmbeddedChannel channel = new EmbeddedChannel(hook); UUID owner = UUID.randomUUID();
        try {
            capsule.arm(owner, System.nanoTime()); assertTrue(capsule.armed());
            hook.configure(ContinuousTransportFixture.Mode.NORMAL, owner); channel.runPendingTasks(); channel.checkException();
            assertFalse(capsule.armed());
        } finally { capsule.clear(); channel.finishAndReleaseAll(); }
    }
    @Test void originalWholePairAndCloseWaitForActualRetirementThenForwardInOneOrder() {
        Bootstrap.register(); UUID epoch = UUID.randomUUID();
        WandPublicationRecord held = new WandPublicationRecord(epoch, 4, WandPublicationRecord.SLOT, 0,
                new WandPublicationRecord.Entry(0, 36, 1, 1, 1));
        WandPublicationRecord retired = new WandPublicationRecord(epoch, 5, WandPublicationRecord.RETIRE, -1,
                new WandPublicationRecord.Entry(0, -1, 1, 1, 1));
        PacketBuffer metadata = new PacketBuffer(Unpooled.buffer()); held.write(metadata);
        SPacketCustomPayload marker = new SPacketCustomPayload(WandPublicationPair.CHANNEL, metadata);
        SPacketSetSlot slot = new SPacketSetSlot(0, 36, new ItemStack(Items.STICK));
        PacketBuffer close = new PacketBuffer(Unpooled.buffer()); close.writeByte(7);
        new ParticipantUseMessages.Close(epoch, 1, 1, EnumHand.MAIN_HAND, new ResourceLocation("fixture", "stream"),
                ParticipantUseMessages.CloseCause.NORMAL_FINISH, 1).toBytes(close);
        ContinuousTransportFixture.Hook hook = new ContinuousTransportFixture.Hook(null, Side.CLIENT);
        SimpleIndexedCodec codec = new SimpleIndexedCodec(); codec.addDiscriminator(7, ParticipantUseMessages.Close.class);
        List<Object> delivered = new ArrayList<>();
        EmbeddedChannel channel = new EmbeddedChannel(hook, codec, new ChannelInboundHandlerAdapter() {
            @Override public void channelRead(ChannelHandlerContext context, Object packet) {
                delivered.add(packet);
                if (packet instanceof SPacketCustomPayload) PublicationFixture.payload((SPacketCustomPayload) packet).release();
            }
        });
        try {
            hook.mode = ContinuousTransportFixture.Mode.GAP_RETIRED;
            hook.pendingAction = true;
            hook.publications.add(new ContinuousTransportFixture.PublicationHold(marker, slot, held));
            hook.held = new ContinuousTransportFixture.Saved(new FMLProxyPacket(close, "jawms")); close.release();
            hook.queued = 2;
            hook.retiredObserved(retired, false); channel.runPendingTasks();
            assertTrue(delivered.isEmpty()); assertEquals(2, hook.queued());
            hook.retiredObserved(retired, true); channel.runPendingTasks(); channel.checkException();
            assertEquals(3, delivered.size()); assertSame(marker, delivered.get(0)); assertSame(slot, delivered.get(1));
            assertTrue(delivered.get(2) instanceof ParticipantUseMessages.Close);
            assertEquals(0, hook.queued()); assertTrue(hook.publications.isEmpty()); assertNull(hook.held);
            assertTrue(hook.applying.isEmpty(), "discard-only replay must not retain a never-applied old packet");
            assertEquals(ContinuousTransportFixture.Mode.NORMAL, hook.mode);
            hook.retiredObserved(retired, true); channel.runPendingTasks(); assertEquals(3, delivered.size());
        } finally { if (close.refCnt() > 0) close.release(); channel.finishAndReleaseAll(); }
    }
}
