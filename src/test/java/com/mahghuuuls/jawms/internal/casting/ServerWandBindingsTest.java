package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Slot;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import net.minecraft.util.EnumHand;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Production server binding/source and normal-write adapters; not transformed vanilla/Netty proof. */
class ServerWandBindingsTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @AfterEach void clear() { ParticipantUseService.clear(); }

    @Test void beforeCopyWitnessSurvivesDetachedSnapshotButNotEqualLateLookup() throws Exception {
        Fixture f = new Fixture();
        Slot slot = new Slot(f.player.inventory, 0, 0, 0);
        ServerWandBindings.beginCopies();
        try {
            ServerWandBindings.Source before = ServerWandBindings.beforeCopy(slot, f.stack);
            ItemStack copy = f.stack.copy();
            ServerWandBindings.copied(before, copy);
            assertNotNull(ServerWandBindings.copiedSource(copy));
            assertNull(ServerWandBindings.copiedSource(copy.copy()));
            assertNull(f.owner.witness(f.player.inventory, 0, copy, copy));
            f.send(ServerWandBindings.copiedSource(copy));
            WandPublicationRecord record = f.lastRecord();
            assertEquals(f.generation, record.entries()[0].generation);
            assertEquals(1, record.entries()[0].intent);
            assertEquals(7, record.entries()[0].use);
        } finally { ServerWandBindings.endCopies(); }
        assertNull(ServerWandBindings.copiedSource(f.stack));
    }
    @Test void capturedOldUseCannotBeRelabelledByListenerStartingNewUseOnSameWand() throws Exception {
        Fixture f = new Fixture();
        ServerWandBindings.Source before = f.owner.witness(f.player.inventory, 0, f.stack, f.stack.copy());
        assertEquals(f.generation, f.owner.bind(f.player, EnumHand.MAIN_HAND, f.stack, 2, 11));
        f.send(before);
        WandPublicationRecord record = f.lastRecord();
        assertEquals(f.generation, record.entries()[0].generation);
        assertEquals(1, record.entries()[0].intent); assertEquals(7, record.entries()[0].use);
    }
    @Test void equalReplacementBetweenCopyAndSendRetiresWithoutGrantingSource() throws Exception {
        Fixture f = new Fixture();
        ServerWandBindings.Source source = f.owner.witness(f.player.inventory, 0, f.stack, f.stack.copy());
        f.player.inventory.mainInventory.set(0, f.stack.copy());
        f.send(source);
        ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
        verify(f.player.connection, times(2)).sendPacket(packets.capture());
        assertTrue(packets.getAllValues().get(0) instanceof SPacketCustomPayload);
        SPacketCustomPayload marker = (SPacketCustomPayload) packets.getAllValues().get(0);
        try { assertEquals(WandPublicationRecord.RETIRE, WandPublicationRecord.read(marker.getBufferData()).kind); }
        finally { marker.getBufferData().release(); }
        assertSame(f.packet, packets.getAllValues().get(1));
        assertFalse(f.owner.valid(EnumHand.MAIN_HAND, f.stack, f.generation));
    }
    @Test void copyCallbackReplacementCannotCreateWitnessAndRetirementNeedsNoDirtyPacket() throws Exception {
        Fixture f = new Fixture();
        ServerWandBindings.beginCopies();
        try {
            ServerWandBindings.Source before = ServerWandBindings.beforeCopy(new Slot(f.player.inventory, 0, 0, 0), f.stack);
            ItemStack snapshot = f.stack.copy();
            ItemStack replacement = f.stack.copy();
            f.player.inventory.mainInventory.set(0, replacement);
            WandInventoryMutations.wrote(f.player.inventory.mainInventory, 0, f.stack, replacement);
            ServerWandBindings.copied(before, snapshot);
            assertNull(ServerWandBindings.copiedSource(snapshot));
            assertEquals(WandPublicationRecord.RETIRE, f.lastRecord().kind);
        } finally { ServerWandBindings.endCopies(); }
    }
    @Test void supportedSelectionAwayAndBackIsMonotonicWithinOneTick() throws Exception {
        Fixture f = new Fixture();
        WandInventoryMutations.select(f.player.inventory, 1);
        WandInventoryMutations.select(f.player.inventory, 0);
        assertFalse(f.owner.valid(EnumHand.MAIN_HAND, f.stack, f.generation));
        long next = f.owner.bind(f.player, EnumHand.MAIN_HAND, f.stack, 2, 9);
        assertTrue(next > f.generation);
        assertEquals(WandPublicationRecord.RETIRE, f.lastRecord().kind);
    }
    @Test void routineUpdatesKeepSourceButSemanticChangeDoesNot() throws Exception {
        Fixture f = new Fixture();
        f.stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        f.stack.getTagCompound().setInteger("cooldown", 10);
        f.stack.getTagCompound().setFloat("progression", 4);
        WandInventoryMutations.dataChanged(f.stack);
        assertTrue(f.owner.valid(EnumHand.MAIN_HAND, f.stack, f.generation));
        f.stack.getTagCompound().setInteger("selectedSpell", 1);
        WandInventoryMutations.dataChanged(f.stack);
        f.stack.getTagCompound().removeTag("selectedSpell");
        assertFalse(f.owner.valid(EnumHand.MAIN_HAND, f.stack, f.generation));
        assertEquals(WandPublicationRecord.RETIRE, f.lastRecord().kind);
    }
    @Test void invalidStartingArgumentGetsDistinctUnusableTombstoneNotZeroOrUseId() throws Exception {
        Fixture f = new Fixture();
        ItemStack detached = f.stack.copy();
        long tombstone = f.owner.bind(f.player, EnumHand.MAIN_HAND, detached, 2, 97);
        assertTrue(tombstone > f.generation); assertNotEquals(97, tombstone);
        assertFalse(f.owner.valid(EnumHand.MAIN_HAND, detached, tombstone));
        assertNull(f.owner.witness(f.player.inventory, 0, detached, detached));
        assertTrue(f.owner.valid(EnumHand.MAIN_HAND, f.stack, f.generation));
        verifyNoInteractions(f.player.connection);
    }
    @Test void fullWindowPreservesIndependentMappedMainAndOffhandSourcesAndLeavesOtherSlotsUnproven() throws Exception {
        Fixture f = new Fixture();
        ItemStack offhand = new ItemStack(Items.BLAZE_ROD);
        f.player.inventory.offHandInventory.set(0, offhand);
        long offGeneration = f.owner.bind(f.player, EnumHand.OFF_HAND, offhand, 2, 9);
        InventoryBasic unrelated = new InventoryBasic("unrelated", false, 46);
        Container container = new Container() {
            { for (int i = 0; i < 46; i++) addSlotToContainer(i == 36
                    ? new Slot(f.player.inventory, 0, 0, 0) : i == 45
                    ? new Slot(f.player.inventory, 40, 0, 0) : new Slot(unrelated, i, 0, 0)); }
            @Override public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) { return true; }
        };
        net.minecraft.util.NonNullList<ItemStack> live = container.getInventory();
        ServerWandBindings.Source[] sources = new ServerWandBindings.Source[live.size()];
        for (int i = 0; i < sources.length; i++) sources[i] = ServerWandBindings.liveSource(f.player, container, i, live.get(i));
        assertNull(sources[0]); assertNotNull(sources[36]); assertNotNull(sources[45]);
        assertNull(ServerWandBindings.liveSource(f.player, container, 36, f.stack.copy()));
        SPacketWindowItems packet = new SPacketWindowItems(0, live);
        ServerWandBindings.sendWindow(f.player, 0, sources, packet);
        WandPublicationRecord record = f.lastRecord();
        assertEquals(WandPublicationRecord.WINDOW, record.kind); assertEquals(0, record.window);
        WandPublicationRecord.Entry[] entries = record.entries(); assertEquals(2, entries.length);
        assertEquals(0, entries[0].inventorySlot); assertEquals(36, entries[0].windowSlot);
        assertEquals(f.generation, entries[0].generation); assertEquals(7, entries[0].use);
        assertEquals(40, entries[1].inventorySlot); assertEquals(45, entries[1].windowSlot);
        assertEquals(offGeneration, entries[1].generation); assertEquals(9, entries[1].use);
        ArgumentCaptor<Packet> sent = ArgumentCaptor.forClass(Packet.class);
        verify(f.player.connection).sendPacket(sent.capture());
        assertSame(packet, ((WandPublicationPair) sent.getValue()).original);
    }
    @Test void directPlayerCorrectionUsesActualInventoryIndexForEachTrackedHand() throws Exception {
        for (EnumHand hand : EnumHand.values()) {
            Fixture f = new Fixture();
            ItemStack live = hand == EnumHand.MAIN_HAND ? f.stack : new ItemStack(Items.BLAZE_ROD);
            int index = hand == EnumHand.MAIN_HAND ? 0 : 40;
            if (hand == EnumHand.OFF_HAND) f.player.inventory.offHandInventory.set(0, live);
            long generation = f.owner.bind(f.player, hand, live, 2, 9);
            assertNull(ServerWandBindings.directSource(f.player.inventory, index, live.copy()));
            SPacketSetSlot packet = new SPacketSetSlot(-2, index, live);
            ServerWandBindings.sendDirect(f.player, ServerWandBindings.directSource(f.player.inventory, index, live), packet);
            WandPublicationRecord record = f.lastRecord();
            assertEquals(-2, record.window); assertEquals(index, record.entries()[0].inventorySlot);
            assertEquals(index, record.entries()[0].windowSlot); assertEquals(generation, record.entries()[0].generation);
            assertEquals(9, record.entries()[0].use);
            clear();
        }
    }
    private static final class Fixture {
        final EntityPlayerMP player = mock(EntityPlayerMP.class);
        final ItemStack stack = new ItemStack(Items.STICK);
        final SPacketSetSlot packet = new SPacketSetSlot(0, 36, stack);
        final ServerWandBindings owner;
        final long generation;
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            player.world = mock(WorldServer.class);
            player.inventory = new InventoryPlayer(player);
            player.inventory.mainInventory.set(0, stack);
            player.connection = mock(NetHandlerPlayServer.class); player.connection.player = player;
            when(player.getHeldItem(EnumHand.MAIN_HAND)).thenAnswer(call -> player.inventory.getCurrentItem());
            when(player.getHeldItem(EnumHand.OFF_HAND)).thenAnswer(call -> player.inventory.offHandInventory.get(0));
            Class<?> type = Class.forName(ParticipantUseService.class.getName() + "$Connection");
            Constructor<?> constructor = type.getDeclaredConstructor(EntityPlayerMP.class); constructor.setAccessible(true);
            Object connection = constructor.newInstance(player);
            Field connections = ParticipantUseService.class.getDeclaredField("CONNECTIONS"); connections.setAccessible(true);
            ((Map<Object, Object>) connections.get(null)).put(player.connection, connection);
            owner = ParticipantUseService.wands(player);
            generation = owner.bind(player, EnumHand.MAIN_HAND, stack, 1, 7);
        }
        void send(ServerWandBindings.Source source) {
            owner.send(packet, WandPublicationRecord.SLOT, 0, new ServerWandBindings.Source[] { source }, new int[] {36});
        }
        WandPublicationRecord lastRecord() {
            ArgumentCaptor<Packet> packets = ArgumentCaptor.forClass(Packet.class);
            verify(player.connection, atLeastOnce()).sendPacket(packets.capture());
            Packet<?> last = packets.getValue();
            SPacketCustomPayload marker = last instanceof WandPublicationPair
                    ? (SPacketCustomPayload) ((WandPublicationPair) last).marker : (SPacketCustomPayload) last;
            try { return WandPublicationRecord.read(marker.getBufferData()); }
            finally { marker.getBufferData().release(); }
        }
    }
}
