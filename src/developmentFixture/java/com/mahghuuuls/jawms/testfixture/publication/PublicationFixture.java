package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.util.EnumHand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Opt-in source-checkout observer. Never grants provenance, validity, permission, or payment. */
public final class PublicationFixture {
    private static final Logger LOG = LogManager.getLogger("jawms_c007_publication");
    private static final Map<Object, InventoryPlayer> INVENTORIES = new IdentityHashMap<>();
    private static final Map<InventoryPlayer, Thread> THREADS = new IdentityHashMap<>();
    private static final Map<Object, Integer> OBJECTS = new IdentityHashMap<>();
    private static int lines;
    private static boolean identityOverflow;
    private PublicationFixture() {}
    public static boolean enabled() { return Boolean.getBoolean("jawms.enablePublicationObserver"); }
    public static synchronized void watch(EntityPlayer player) {
        if (!enabled()) return;
        INVENTORIES.entrySet().removeIf(e -> e.getValue().player.world.isRemote == player.world.isRemote);
        INVENTORIES.put(player.inventory.mainInventory, player.inventory);
        INVENTORIES.put(player.inventory.offHandInventory, player.inventory);
        THREADS.keySet().removeIf(i -> !INVENTORIES.containsValue(i));
        THREADS.put(player.inventory, Thread.currentThread());
    }
    public static synchronized void reset(UUID player, boolean client) {
        INVENTORIES.entrySet().removeIf(e -> e.getValue().player.world.isRemote == client
                && e.getValue().player.getUniqueID().equals(player));
        THREADS.keySet().removeIf(i -> !INVENTORIES.containsValue(i));
        // Each endpoint/campaign reset starts a new explicitly labelled bounded observation ordinal space.
        OBJECTS.clear(); lines = 0; identityOverflow = false;
    }
    private static synchronized int id(Object object) {
        if (object == null) return 0;
        Integer result = OBJECTS.get(object);
        if (result != null) return result;
        if (OBJECTS.size() == 256) {
            if (!identityOverflow) { identityOverflow = true; log("identity_capacity", "capacity", 256, "verdict", "UNREACHED"); }
            return -1;
        }
        int value = OBJECTS.size() + 1; OBJECTS.put(object, value); return value;
    }
    public static synchronized void log(String event, Object... values) {
        if (!enabled()) return;
        if (++lines > 2048) {
            if (lines == 2049) LOG.error("PUBLICATION_OBSERVER overflow; remaining card evidence is incomplete");
            return;
        }
        LOG.info("PUBLICATION_OBSERVER event={} values={} thread={}", event, java.util.Arrays.toString(values), Thread.currentThread().getName());
    }
    public static void source(String event, Object source, ItemStack original, ItemStack snapshot) {
        if (!enabled() || source == null) return;
        java.lang.ref.WeakReference<?> player = (java.lang.ref.WeakReference<?>) field(field(source, "owner"), "player");
        synchronized (PublicationFixture.class) {
            if (!(player.get() instanceof EntityPlayer) || !THREADS.containsKey(((EntityPlayer) player.get()).inventory)) return;
        }
        log(event, "source", id(source), "original", id(original), "snapshot", id(snapshot),
                "generation", field(source, "generation"), "intent", field(source, "intent"), "use", field(source, "use"),
                "equal", original != null && snapshot != null && ItemStack.areItemStacksEqual(original, snapshot));
    }
    public static Object field(Object target, String name) {
        if (target == null) return null;
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Observer field missing: " + name, failure); }
    }
    public static void sources(Object[] sources, Object packet, int window) {
        if (!enabled()) return;
        log("source_send", "packet", id(packet), "window", window);
        for (Object source : sources) if (source != null)
            source("source_send_entry", source, (ItemStack) field(source, "original"), (ItemStack) field(source, "snapshot"));
    }
    public static void copied(Object source, ItemStack snapshot) {
        if (source != null) source("copy_return", source, (ItemStack) field(source, "original"), snapshot);
    }
    public static void copyToSend(EntityPlayerMP player, Container container, int slot, ItemStack snapshot) {
        if (!enabled() || slot < 0 || slot >= container.inventorySlots.size()) return;
        synchronized (PublicationFixture.class) { if (!THREADS.containsKey(player.inventory)) return; }
        ItemStack live = container.getSlot(slot).getStack();
        log("listener_after_copy", "side", "SERVER", "slot", slot, "live", id(live), "snapshot", id(snapshot));
        ContinuousTransportFixture.sourceCallback(player, container, slot, live, snapshot);
    }
    public static void write(Object list, int index, ItemStack previous, ItemStack installed) {
        InventoryPlayer inventory;
        synchronized (PublicationFixture.class) { inventory = INVENTORIES.get(list); }
        if (inventory == null || !enabled()) return;
        log("actual_inventory_write", "side", inventory.player.world.isRemote ? "CLIENT" : "SERVER",
                "slot", list == inventory.offHandInventory ? 40 + index : index,
                "previous", id(previous), "installed", id(installed), "sameReference", previous == installed,
                "selectedSpell", selected(installed), "data", signature(installed));
    }
    public static void cooldown(ItemStack stack, int value) {
        if (!enabled()) return;
        boolean held = false;
        synchronized (PublicationFixture.class) {
            for (InventoryPlayer inventory : new java.util.HashSet<>(INVENTORIES.values())) {
                if (THREADS.get(inventory) != Thread.currentThread()) continue;
                for (EnumHand hand : EnumHand.values()) if (inventory.player.getHeldItem(hand) == stack) {
                    held = true;
                    log("actual_cooldown_target", "side", inventory.player.world.isRemote ? "CLIENT" : "SERVER",
                            "stack", id(stack), "hand", hand, "selectedSpell", selected(stack), "value", value);
                }
            }
            if (!held && OBJECTS.containsKey(stack)) log("detached_cooldown_target", "stack", id(stack), "value", value, "selectedSpell", selected(stack));
        }
    }
    public static void capture(EntityPlayer player, ItemStack original, Object frame) {
        if (!enabled()) return;
        log("original_capture", "side", "CLIENT", "frame", id(frame), "stack", id(original),
                "snapshot", id(field(frame, "snapshot")), "selectedSpell", selected(original), "data", signature(original),
                "intent", field(frame, "intent"), "use", field(frame, "use"));
    }
    public static void packet(String event, Object packet) { if (enabled()) log(event, "packet", id(packet)); }
    public static boolean beforePacketCopy(ItemStack stack) {
        if (!enabled()) return false;
        synchronized (PublicationFixture.class) {
            for (InventoryPlayer inventory : THREADS.keySet()) if (THREADS.get(inventory) == Thread.currentThread()
                    && (inventory.getCurrentItem() == stack || inventory.offHandInventory.get(0) == stack)) {
                log("packet_copy_enter", "original", id(stack)); return true;
            }
        }
        return false;
    }
    public static void afterPacketCopy(ItemStack original, ItemStack snapshot) {
        log("packet_copy_return", "original", id(original), "snapshot", id(snapshot), "equal", ItemStack.areItemStacksEqual(original, snapshot));
    }
    public static void release(EntityPlayer player, ItemStack stack, int timeLeft) {
        synchronized (PublicationFixture.class) {
            if (!enabled() || !THREADS.containsKey(player.inventory)) return;
        }
        log("original_release_arguments", "side", player.world.isRemote ? "CLIENT" : "SERVER", "stack", id(stack),
                "timeLeft", timeLeft, "creative", player.isCreative(), "hand", player.getActiveHand(), "data", signature(stack));
    }
    public static void constructed(electroblob.wizardry.event.SpellCastEvent.Finish event) {
        if (!enabled() || !(event.getCaster() instanceof EntityPlayer)) return;
        EntityPlayer player = (EntityPlayer) event.getCaster();
        synchronized (PublicationFixture.class) { if (!THREADS.containsKey(player.inventory)) return; }
        log("original_finish_constructor", "side", player.world.isRemote ? "CLIENT" : "SERVER", "event", id(event),
                "count", event.getCount(), "modifierSignature", event.getModifiers().getModifiers().hashCode(),
                "cooldownFactor", event.getModifiers().get(electroblob.wizardry.registry.WizardryItems.cooldown_upgrade));
    }
    // MCP fields are intentional: this helper exists only on the approved named development classpaths.
    // SPacketCustomPayload's public getters are client-stripped and cannot be called on a dedicated server.
    public static String channel(SPacketCustomPayload packet) { return (String) field(packet, "channel"); }
    public static PacketBuffer payload(SPacketCustomPayload packet) { return (PacketBuffer) field(packet, "data"); }
    private static int signature(ItemStack stack) { return stack.getTagCompound() == null ? 0 : stack.getTagCompound().hashCode(); }
    private static int selected(ItemStack stack) { return stack.getTagCompound() == null ? 0 : stack.getTagCompound().getInteger("selectedSpell"); }
}
