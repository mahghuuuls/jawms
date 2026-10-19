package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import java.util.IdentityHashMap;
import java.util.Map;

/** Exact normal inventory-list writes and selection transitions, scoped to live tracked inventories. */
public final class WandInventoryMutations {
    private static final Map<Object, Watch> WATCHED = new IdentityHashMap<>();
    private WandInventoryMutations() {}
    public static synchronized void watch(InventoryPlayer inventory) {
        Watch watch = new Watch(inventory);
        WATCHED.put(inventory.mainInventory, watch); WATCHED.put(inventory.offHandInventory, watch);
    }
    public static synchronized void unwatch(InventoryPlayer inventory) {
        WATCHED.remove(inventory.mainInventory); WATCHED.remove(inventory.offHandInventory);
    }
    public static void wrote(Object list, int index, Object previous, Object installed) {
        Watch watch;
        synchronized (WandInventoryMutations.class) { watch = WATCHED.get(list); }
        if (watch == null || watch.thread != Thread.currentThread()
                || !(previous instanceof ItemStack) || !(installed instanceof ItemStack)) return;
        InventoryPlayer inventory = watch.inventory;
        if (inventory.player instanceof EntityPlayerMP) {
            ServerWandBindings owner = ParticipantUseService.wands((EntityPlayerMP) inventory.player);
            if (owner != null) owner.tick();
        } else JustAnotherWizardryManaSystemMod.proxy.wandInventoryWrite(inventory,
                list == inventory.offHandInventory ? 40 + index : index, (ItemStack) previous, (ItemStack) installed);
    }
    public static void select(InventoryPlayer inventory, int selected) {
        int previous = inventory.currentItem;
        inventory.currentItem = selected;
        if (previous == selected) return;
        if (inventory.player instanceof EntityPlayerMP) {
            ServerWandBindings owner = ParticipantUseService.wands((EntityPlayerMP) inventory.player);
            if (owner != null) owner.tick();
        } else JustAnotherWizardryManaSystemMod.proxy.wandSelectionChanged(inventory);
    }
    public static void dataChanged(ItemStack stack) {
        java.util.List<Watch> watches;
        synchronized (WandInventoryMutations.class) {
            watches = new java.util.ArrayList<>(new java.util.HashSet<>(WATCHED.values()));
        }
        for (Watch watch : watches) {
            InventoryPlayer inventory = watch.inventory;
            if (watch.thread != Thread.currentThread() || (inventory.getCurrentItem() != stack
                    && inventory.offHandInventory.get(0) != stack)) continue;
            if (inventory.player instanceof EntityPlayerMP) {
                ServerWandBindings owner = ParticipantUseService.wands((EntityPlayerMP) inventory.player);
                if (owner != null) owner.tick();
            } else JustAnotherWizardryManaSystemMod.proxy.wandSelectionChanged(inventory);
        }
    }
    private static final class Watch {
        final InventoryPlayer inventory; final Thread thread;
        Watch(InventoryPlayer inventory) { this.inventory = inventory; thread = Thread.currentThread(); }
    }
}
