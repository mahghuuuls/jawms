package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.util.EnumHand;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread original-wand/source owner. Copies and later equal values never create witnesses. */
public final class ServerWandBindings {
    private static final ThreadLocal<Deque<Map<ItemStack, Source>>> COPIES = ThreadLocal.withInitial(ArrayDeque::new);
    private WeakReference<EntityPlayerMP> player;
    private final UUID epoch;
    private final Binding[] bindings = new Binding[2];
    private long generations, publications;

    ServerWandBindings(EntityPlayerMP player, UUID epoch) {
        this.player = new WeakReference<>(player); this.epoch = epoch;
    }

    long bind(EntityPlayerMP actual, EnumHand hand, ItemStack stack, long intent, long use) {
        if (player.get() != actual) { clear(); player = new WeakReference<>(actual); }
        tick();
        int h = hand.ordinal(), slot = hand == EnumHand.MAIN_HAND ? actual.inventory.currentItem : 40;
        // Re-entry can replace the starting argument before vanilla binds. Give its suppressing close
        // a distinct tombstone identity, never a live binding/source witness or an invented use-as-generation.
        if (actual.getHeldItem(hand) != stack || stack.isEmpty()) return nextGeneration();
        Binding binding = bindings[h];
        if (binding == null) {
            binding = new Binding(new WandBinding(stack, nextGeneration()), slot, actual.world);
            bindings[h] = binding;
        }
        binding.intent = intent; binding.use = use;
        WandInventoryMutations.watch(actual.inventory);
        return binding.identity.generation();
    }

    boolean valid(EnumHand hand, ItemStack stack, long generation) {
        tick();
        Binding b = bindings[hand.ordinal()];
        return b != null && b.identity.generation() == generation && b.identity.valid(stack);
    }

    void tick() {
        EntityPlayerMP p = player.get();
        for (int h = 0; h < bindings.length; h++) {
            Binding b = bindings[h];
            if (b != null && (p == null || b.world.get() != p.world
                    || (h == EnumHand.MAIN_HAND.ordinal() && p.inventory.currentItem != b.slot)
                    || !b.identity.valid(p.inventory.getStackInSlot(b.slot)))) retire(h);
        }
    }

    void retireAll() { for (int i = 0; i < bindings.length; i++) retire(i); }
    void clear() {
        EntityPlayerMP p = player.get();
        if (p != null) WandInventoryMutations.unwatch(p.inventory);
        for (int i = 0; i < bindings.length; i++) {
            if (bindings[i] != null) bindings[i].identity.retire();
            bindings[i] = null;
        }
    }
    private void retire(int hand) {
        Binding b = bindings[hand];
        if (b == null) return;
        bindings[hand] = null; b.identity.retire();
        EntityPlayerMP p = player.get();
        if (p != null && b.intent > 0 && b.use > 0) p.connection.sendPacket(WandPublicationPair.marker(
                new WandPublicationRecord(epoch, nextPublication(), WandPublicationRecord.RETIRE, -1,
                        new WandPublicationRecord.Entry(b.slot, -1, b.identity.generation(), b.intent, b.use))));
        if (p != null && bindings[0] == null && bindings[1] == null) WandInventoryMutations.unwatch(p.inventory);
    }
    private long nextPublication() {
        if (publications == Long.MAX_VALUE) throw new IllegalStateException("Publication sequence exhausted");
        return ++publications;
    }
    private long nextGeneration() {
        if (generations == Long.MAX_VALUE) throw new IllegalStateException("Wand generation exhausted");
        return ++generations;
    }

    Source witness(InventoryPlayer inventory, int slot, ItemStack original, ItemStack snapshot) {
        EntityPlayerMP p = player.get();
        tick();
        if (p == null || p.inventory != inventory || inventory.getStackInSlot(slot) != original) return null;
        for (Binding b : bindings) {
            if (b != null && b.slot == slot && b.identity.valid(original)
                    && WandBinding.semanticEquals(original, snapshot)) return new Source(this, b, original, snapshot);
        }
        return null;
    }
    private boolean valid(Source source) {
        EntityPlayerMP p = player.get();
        if (p == null || source.owner != this || source.binding.world.get() != p.world) return false;
        Binding b = source.binding;
        int h = b.slot == 40 ? EnumHand.OFF_HAND.ordinal() : EnumHand.MAIN_HAND.ordinal();
        return bindings[h] == b && (h != 0 || p.inventory.currentItem == b.slot)
                && p.inventory.getStackInSlot(b.slot) == source.original
                && b.identity.valid(source.original) && WandBinding.semanticEquals(source.original, source.snapshot);
    }
    void send(Packet<?> packet, int kind, int window, Source[] sources, int[] windowSlots) {
        EntityPlayerMP p = player.get();
        java.util.List<WandPublicationRecord.Entry> entries = new java.util.ArrayList<>(2);
        for (int i = 0; i < sources.length; i++) {
            Source source = sources[i];
            if (source != null && valid(source)) {
                Binding b = source.binding;
                entries.add(new WandPublicationRecord.Entry(b.slot, windowSlots[i], source.generation, source.intent, source.use));
            }
        }
        if (p == null) return;
        tick(); // Re-entrant source mutation emits its own retirement even when the packet data is equal.
        if (entries.isEmpty()) p.connection.sendPacket(packet);
        else p.connection.sendPacket(new WandPublicationPair(new WandPublicationRecord(epoch, nextPublication(),
                kind, window, entries.toArray(new WandPublicationRecord.Entry[0])), packet));
    }

    public static void beginCopies() { COPIES.get().push(new IdentityHashMap<>()); }
    public static void endCopies() { COPIES.get().pop().clear(); }
    /** Called at Container's actual live-slot copy, with the returned snapshot. */
    public static Source beforeCopy(Slot slot, ItemStack original) {
        if (COPIES.get().isEmpty() || !(slot.inventory instanceof InventoryPlayer)
                || !(((InventoryPlayer) slot.inventory).player instanceof EntityPlayerMP)) return null;
        InventoryPlayer inventory = (InventoryPlayer) slot.inventory;
        ServerWandBindings owner = ParticipantUseService.wands((EntityPlayerMP) inventory.player);
        return owner == null ? null : owner.witness(inventory, slot.getSlotIndex(), original, original);
    }
    public static void copied(Source before, ItemStack snapshot) {
        if (before == null || COPIES.get().isEmpty() || !before.owner.valid(before)
                || !WandBinding.semanticEquals(before.original, snapshot)) return;
        COPIES.get().peek().put(snapshot, new Source(before, snapshot));
    }
    public static Source copiedSource(ItemStack snapshot) {
        for (Map<ItemStack, Source> scope : COPIES.get()) {
            Source source = scope.get(snapshot);
            if (source != null) return source;
        }
        return null;
    }
    /** Full-window/direct publication receives original live references, before its constructor copies them. */
    public static Source liveSource(EntityPlayerMP player, Container container, int windowSlot, ItemStack live) {
        if (windowSlot < 0 || windowSlot >= container.inventorySlots.size()) return null;
        Slot slot = container.getSlot(windowSlot);
        if (slot.inventory != player.inventory || slot.getStack() != live) return null;
        return directSource(player.inventory, slot.getSlotIndex(), live);
    }
    public static Source directSource(InventoryPlayer inventory, int slot, ItemStack live) {
        if (!(inventory.player instanceof EntityPlayerMP)) return null;
        ServerWandBindings owner = ParticipantUseService.wands((EntityPlayerMP) inventory.player);
        return owner == null ? null : owner.witness(inventory, slot, live, live);
    }
    public static void sendSlot(EntityPlayerMP player, int window, int windowSlot, Source source, Packet<?> packet) {
        ServerWandBindings owner = ParticipantUseService.wands(player);
        if (owner == null || source == null || source.owner != owner) player.connection.sendPacket(packet);
        else owner.send(packet, WandPublicationRecord.SLOT, window, new Source[] {source}, new int[] {windowSlot});
    }
    public static void sendDirect(EntityPlayerMP player, Source source, Packet<?> packet) {
        sendSlot(player, -2, source == null ? 0 : source.binding.slot, source, packet);
    }
    public static void sendWindow(EntityPlayerMP player, int window, Source[] sources, Packet<?> packet) {
        ServerWandBindings owner = ParticipantUseService.wands(player);
        if (owner == null) { player.connection.sendPacket(packet); return; }
        int[] slots = new int[sources.length]; for (int i = 0; i < slots.length; i++) slots[i] = i;
        owner.send(packet, WandPublicationRecord.WINDOW, window, sources, slots);
    }

    public static final class Source {
        private final ServerWandBindings owner; private final Binding binding;
        private final ItemStack original, snapshot;
        private final long generation, intent, use;
        private Source(ServerWandBindings owner, Binding binding, ItemStack original, ItemStack snapshot) {
            this.owner = owner; this.binding = binding; this.original = original; this.snapshot = snapshot;
            generation = binding.identity.generation(); intent = binding.intent; use = binding.use;
        }
        private Source(Source before, ItemStack snapshot) {
            owner = before.owner; binding = before.binding; original = before.original; this.snapshot = snapshot;
            generation = before.generation; intent = before.intent; use = before.use;
        }
    }
    private static final class Binding {
        final WandBinding identity; final int slot; final WeakReference<Object> world;
        long intent, use;
        Binding(WandBinding identity, int slot, Object world) {
            this.identity = identity; this.slot = slot; this.world = new WeakReference<>(world);
        }
    }
}
