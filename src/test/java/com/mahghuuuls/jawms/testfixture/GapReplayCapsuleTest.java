package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Standard packet byte retention/lifecycle, not a live transformed connection claim. */
class GapReplayCapsuleTest {
    private final UUID owner = UUID.randomUUID(), oldEpoch = UUID.randomUUID();
    private static Item wand;
    @BeforeAll static void bootstrap() {
        Bootstrap.register(); wand = net.minecraft.init.Items.STICK;
    }
    private byte[] marker(long serial) {
        PacketBuffer bytes = new PacketBuffer(Unpooled.buffer());
        try {
            new WandPublicationRecord(oldEpoch, serial, WandPublicationRecord.SLOT, 0,
                    new WandPublicationRecord.Entry(0, 36, 1, 1, 1)).write(bytes);
            return copy(bytes);
        } finally { bytes.release(); }
    }
    private byte[] close(UUID epoch) {
        PacketBuffer bytes = new PacketBuffer(Unpooled.buffer());
        try {
            bytes.writeByte(7); new ParticipantUseMessages.Close(epoch, 1, 1, EnumHand.MAIN_HAND,
                    new ResourceLocation("jawms_cast_fixture", "stream"), ParticipantUseMessages.CloseCause.NORMAL_FINISH, 1).toBytes(bytes);
            return copy(bytes);
        } finally { bytes.release(); }
    }
    private byte[] release() {
        PacketBuffer bytes = new PacketBuffer(Unpooled.buffer());
        try { bytes.writeByte(6); new ParticipantUseMessages.Release(oldEpoch, 1).toBytes(bytes); return copy(bytes); }
        finally { bytes.release(); }
    }
    private byte[] body() { return GapReplayCapsule.encode(new SPacketSetSlot(0, 36, new ItemStack(wand))); }
    private static byte[] copy(PacketBuffer bytes) { byte[] result = new byte[bytes.readableBytes()]; bytes.getBytes(0, result); return result; }
    private GapReplayCapsule seed() {
        GapReplayCapsule capsule = new GapReplayCapsule(); capsule.arm(owner, 0);
        capsule.pair(owner, marker(9), body(), 1); capsule.frame(owner, release(), false, 2);
        capsule.frame(owner, close(oldEpoch), true, 3); return capsule;
    }
    @Test void genuinePacketBytesRoundTripAndConsumeExactlyOnceWithoutMutableAliases() {
        GapReplayCapsule capsule = seed(); String digest = capsule.inspect(owner, 4).digest();
        byte[] external = capsule.inspect(owner, 4).close(); external[0] = 0;
        capsule.disconnect(owner, 5);
        GapReplayCapsule.Snapshot snapshot = capsule.consume(owner, UUID.randomUUID(), 1, 1, 2, 6);
        assertEquals(digest, snapshot.digest()); assertEquals(9, snapshot.record.serial);
        assertEquals(oldEpoch, snapshot.closed.epoch()); assertEquals(36, snapshot.vanilla().getSlot());
        assertSame(wand, snapshot.vanilla().getStack().getItem());
        assertArrayEquals(close(oldEpoch), snapshot.close()); assertArrayEquals(release(), snapshot.release());
        PacketBuffer metadata = snapshot.marker(); try { assertEquals(oldEpoch, WandPublicationRecord.read(metadata).epoch); } finally { metadata.release(); }
        assertFalse(capsule.armed()); assertThrows(IllegalStateException.class, () -> capsule.inspect(owner, 7));
    }
    @Test void tupleCollisionAndNonstaleSerialAreRealReadinessConditions() {
        GapReplayCapsule capsule = seed(); capsule.disconnect(owner, 4);
        assertThrows(IllegalStateException.class, () -> capsule.consume(owner, oldEpoch, 1, 1, 0, 5));
        assertThrows(IllegalStateException.class, () -> capsule.consume(owner, UUID.randomUUID(), 2, 1, 0, 5));
        assertThrows(IllegalStateException.class, () -> capsule.consume(owner, UUID.randomUUID(), 1, 2, 0, 5));
        for (long watermark : new long[] { 9, 10 }) assertThrows(IllegalStateException.class,
                () -> capsule.consume(owner, UUID.randomUUID(), 1, 1, watermark, 5));
        assertTrue(capsule.armed()); assertNotNull(capsule.consume(owner, UUID.randomUUID(), 1, 0, 8, 6));
    }
    @Test void incompleteSeedCanBeInspectedButCannotSurviveDisconnect() {
        GapReplayCapsule capsule = new GapReplayCapsule(); capsule.arm(owner, 0);
        assertTrue(capsule.status(owner, 1).contains("incomplete"));
        assertThrows(IllegalStateException.class, () -> capsule.disconnect(owner, 2)); assertFalse(capsule.armed());
    }
    @Test void wrongOwnerExpirySecondDisconnectAndExplicitClearCancelRetention() {
        GapReplayCapsule capsule = seed();
        assertThrows(IllegalStateException.class, () -> capsule.inspect(UUID.randomUUID(), 4)); assertFalse(capsule.armed());
        GapReplayCapsule expired = seed(); assertThrows(IllegalStateException.class,
                () -> expired.inspect(owner, GapReplayCapsule.LIFETIME)); assertFalse(expired.armed());
        GapReplayCapsule twice = seed(); twice.disconnect(owner, 4);
        assertThrows(IllegalStateException.class, () -> twice.disconnect(owner, 5)); assertFalse(twice.armed());
        GapReplayCapsule cleared = seed(); cleared.clear(); assertFalse(cleared.armed());
    }
    @Test void incompleteMismatchedAndOversizedFramesCannotBeReplayMembers() {
        GapReplayCapsule capsule = new GapReplayCapsule(); capsule.arm(owner, 0);
        assertThrows(IllegalStateException.class, () -> capsule.pair(owner, marker(1), new byte[65536], 1));
        assertThrows(IllegalStateException.class, () -> capsule.frame(owner, new byte[513], true, 1));
        assertThrows(IllegalArgumentException.class, () -> capsule.pair(owner, marker(1),
                GapReplayCapsule.encode(new SPacketSetSlot(0, 37, new ItemStack(wand))), 1));
        capsule.pair(owner, marker(1), body(), 1); capsule.frame(owner, release(), false, 2);
        capsule.frame(owner, close(UUID.randomUUID()), true, 3);
        assertThrows(IllegalArgumentException.class, () -> capsule.inspect(owner, 4));
    }
    @Test void closeFreezesPreclosePublicationAndCapsuleCannotBeSilentlyRearmed() {
        GapReplayCapsule capsule = seed(); capsule.pair(owner, marker(99), body(), 4);
        assertEquals(9, capsule.inspect(owner, 5).record.serial);
        assertThrows(IllegalStateException.class, () -> capsule.arm(UUID.randomUUID(), 5));
        assertFalse(capsule.armed(), "another owner cancels rather than inheriting the old case");
    }
    @Test void retentionHasOnlyBoundedBytesAndScalarIdentityNotLiveRuntimeReferences() {
        for (Field field : GapReplayCapsule.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            assertTrue(field.getType().isPrimitive() || field.getType() == UUID.class || field.getType() == byte[].class, field.toString());
        }
    }
}
