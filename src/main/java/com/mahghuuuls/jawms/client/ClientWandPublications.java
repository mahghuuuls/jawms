package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.internal.casting.WandBinding;
import com.mahghuuuls.jawms.internal.casting.WandInventoryMutations;
import com.mahghuuuls.jawms.internal.casting.WandPublicationPair;
import com.mahghuuuls.jawms.internal.casting.WandPublicationReceiver;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import net.minecraft.util.EnumHand;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/** Client representation cache and exact vanilla-application scope; no finish/payment authority. */
public final class ClientWandPublications {
    private static volatile Session session;
    private static final Binding[] bindings = new Binding[2];
    private static final ThreadLocal<Deque<Application>> APPLYING = ThreadLocal.withInitial(ArrayDeque::new);
    private ClientWandPublications() {}

    public static void capability(NetHandlerPlayClient handler, UUID epoch) {
        if (session == null || session.handler != handler) { clear(); session = new Session(handler, epoch); }
    }
    public static WandBinding binding(EntityPlayer player, EnumHand hand) {
        tick();
        int h = hand.ordinal(), slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
        Binding b = bindings[h];
        if (b == null) {
            b = new Binding(player, slot, new WandBinding(player.getHeldItem(hand), 0));
            bindings[h] = b; WandInventoryMutations.watch(player.inventory);
        }
        return b.identity;
    }
    public static boolean owns(EntityPlayer player, EnumHand hand, WandBinding identity) {
        Binding b = bindings[hand.ordinal()];
        return b != null && b.identity == identity && b.player.get() == player && b.world.get() == player.world
                && Minecraft.getMinecraft().player == player
                && (hand != EnumHand.MAIN_HAND || b.slot == player.inventory.currentItem)
                && identity.valid(player.getHeldItem(hand));
    }
    public static void tick() {
        EntityPlayer player = Minecraft.getMinecraft().player;
        for (int h = 0; h < bindings.length; h++) {
            Binding b = bindings[h];
            if (b != null && (b.player.get() != player || player == null || b.world.get() != player.world
                    || (h == 0 && b.slot != player.inventory.currentItem)
                    || !b.identity.valid(player.inventory.getStackInSlot(b.slot)))) retire(h);
        }
    }
    private static void retire(int h) {
        Binding b = bindings[h];
        if (b != null) {
            b.identity.retire(); bindings[h] = null;
            EntityPlayer player = b.player.get();
            boolean retained = false;
            for (Binding other : bindings) if (other != null && other.player.get() == player) retained = true;
            if (player != null && !retained) WandInventoryMutations.unwatch(player.inventory);
        }
    }
    public static void faultBindings() { for (int h = 0; h < bindings.length; h++) retire(h); }
    public static void faultState() {
        Session current = session;
        if (current != null) { current.faultNotified.set(true); current.receiver.fault(); }
        faultBindings();
    }
    public static void clear() {
        for (Binding b : bindings) {
            EntityPlayer player = b == null ? null : b.player.get();
            if (player != null) WandInventoryMutations.unwatch(player.inventory);
        }
        faultBindings();
        Session old = session; session = null;
        if (old != null) old.receiver.clear();
        APPLYING.remove();
    }

    /** Network thread: bounded metadata/identity attachment only. Never inspect a player or inventory here. */
    public static boolean receive(INetHandler handler, Packet<?> packet) {
        Session current = session;
        if (packet instanceof SPacketCustomPayload && WandPublicationPair.CHANNEL.equals(((SPacketCustomPayload) packet).getChannelName())) {
            SPacketCustomPayload marker = (SPacketCustomPayload) packet;
            try {
                WandPublicationRecord record = WandPublicationRecord.read(marker.getBufferData());
                if (current != null && current.handler == handler) {
                    boolean accepted = current.receiver.record(record);
                    if (current.receiver.faulted()) fault(current);
                    else if (accepted && record.kind == WandPublicationRecord.RETIRE)
                        Minecraft.getMinecraft().addScheduledTask(() -> { if (session == current) retired(record); });
                }
            } catch (RuntimeException malformed) { if (current != null && current.handler == handler) { current.receiver.fault(); fault(current); } }
            finally { marker.getBufferData().release(); }
            return false;
        }
        if (current == null || current.handler != handler) return true;
        WandPublicationReceiver.Delivery delivery = current.receiver.packet(packet, ClientWandPublications::shape);
        if (delivery == WandPublicationReceiver.Delivery.FAULT) fault(current);
        return delivery != WandPublicationReceiver.Delivery.DISCARD;
    }
    private static void fault(Session current) {
        if (!current.faultNotified.compareAndSet(false, true)) return;
        Minecraft.getMinecraft().addScheduledTask(() -> {
            if (session == current) ClientParticipantUseGuard.protocolFault("inventory publication association failed; reconnect required");
        });
    }
    private static boolean shape(WandPublicationRecord record, Packet<?> packet) {
        if (record.kind == WandPublicationRecord.SLOT && packet instanceof SPacketSetSlot) {
            SPacketSetSlot slot = (SPacketSetSlot) packet;
            return record.window == slot.getWindowId() && record.entries()[0].windowSlot == slot.getSlot();
        }
        if (record.kind == WandPublicationRecord.WINDOW && packet instanceof SPacketWindowItems) {
            SPacketWindowItems window = (SPacketWindowItems) packet;
            if (record.window != window.getWindowId()) return false;
            for (WandPublicationRecord.Entry e : record.entries()) if (e.windowSlot >= window.getItemStacks().size()) return false;
            return true;
        }
        return false;
    }
    private static void retired(WandPublicationRecord record) {
        Session current = session;
        if (current == null || !current.epoch.equals(record.epoch)) return;
        for (WandPublicationRecord.Entry e : record.entries()) {
            int h = e.inventorySlot == 40 ? 1 : 0; Binding b = bindings[h];
            if (b != null && b.slot == e.inventorySlot && (b.identity.generation() == e.generation
                    || b.identity.generation() == 0 && ClientParticipantUseGuard.correlatePublication(record, e, b.identity))) retire(h);
        }
        ClientParticipantUseGuard.contextRetired();
    }
    /** Main thread only, after PacketThreadUtil has completed any re-enqueue. */
    public static void apply(Packet<?> packet, NetHandlerPlayClient handler, Runnable vanilla) {
        Session current = session;
        WandPublicationRecord record = current != null && current.handler == handler ? current.receiver.take(packet) : null;
        Application application = new Application(packet, record);
        APPLYING.get().push(application);
        try { vanilla.run(); } finally { APPLYING.get().pop(); }
    }
    public static void wrote(InventoryPlayer inventory, int index, ItemStack previous, ItemStack installed) {
        for (int h = 0; h < bindings.length; h++) {
            Binding b = bindings[h];
            if (b == null || b.player.get() != inventory.player || b.slot != index) continue;
            if (b.world.get() != inventory.player.world || Minecraft.getMinecraft().player != inventory.player
                    || (h == 0 && b.slot != inventory.currentItem)) { retire(h); continue; }
            if (previous == installed) { if (!b.identity.valid(installed)) retire(h); continue; }
            Application apply = APPLYING.get().peek();
            boolean transferred = false;
            if (apply != null && apply.record != null && session != null && session.epoch.equals(apply.record.epoch)) {
                for (WandPublicationRecord.Entry e : apply.record.entries()) {
                    ItemStack actualPacketStack = apply.packet instanceof SPacketSetSlot ? ((SPacketSetSlot) apply.packet).getStack()
                            : ((SPacketWindowItems) apply.packet).getItemStacks().get(e.windowSlot);
                    if (e.inventorySlot == index && actualPacketStack == installed
                            && ClientParticipantUseGuard.correlatePublication(apply.record, e, b.identity))
                        transferred = b.identity.publish(previous, installed, e.generation);
                }
            }
            if (!transferred) retire(h);
        }
    }
    private static final class Session {
        final NetHandlerPlayClient handler; final UUID epoch;
        final java.util.concurrent.atomic.AtomicBoolean faultNotified = new java.util.concurrent.atomic.AtomicBoolean();
        final WandPublicationReceiver<Packet<?>> receiver = new WandPublicationReceiver<>();
        Session(NetHandlerPlayClient handler, UUID epoch) { this.handler = handler; this.epoch = epoch; receiver.capability(epoch); }
    }
    private static final class Binding {
        final WeakReference<EntityPlayer> player; final WeakReference<Object> world;
        final int slot; final WandBinding identity;
        Binding(EntityPlayer player, int slot, WandBinding identity) {
            this.player = new WeakReference<>(player); this.world = new WeakReference<>(player.world);
            this.slot = slot; this.identity = identity;
        }
    }
    private static final class Application {
        final Packet<?> packet; final WandPublicationRecord record;
        Application(Packet<?> packet, WandPublicationRecord record) { this.packet = packet; this.record = record; }
    }
}
