package com.mahghuuuls.jawms.api;

import net.minecraft.item.ItemStack;

/** Immutable description of one occupied active slot. */
public final class ManaItemContext {

    private final ItemStack stack;
    private final ManaSlotCategory slotCategory;
    private final int slotIndex;

    public ManaItemContext(
            ItemStack stack,
            ManaSlotCategory slotCategory,
            int slotIndex
    ) {
        if (stack == null || stack.isEmpty()) {
            throw new IllegalArgumentException("A non-empty stack is required.");
        }
        if (slotCategory == null) {
            throw new NullPointerException("slotCategory");
        }
        if (slotIndex < 0) {
            throw new IllegalArgumentException("Slot index must be non-negative.");
        }
        if (slotCategory == ManaSlotCategory.ARMOR && slotIndex > 3) {
            throw new IllegalArgumentException("Armor slot index must be between zero and three.");
        }
        if ((slotCategory == ManaSlotCategory.MAIN_HAND
                || slotCategory == ManaSlotCategory.OFF_HAND) && slotIndex != 0) {
            throw new IllegalArgumentException("Hand slot index must be zero.");
        }
        this.stack = stack.copy();
        this.slotCategory = slotCategory;
        this.slotIndex = slotIndex;
    }

    /** Returns a defensive copy; mutating it cannot alter the equipped stack. */
    public ItemStack getStack() {
        return stack.copy();
    }

    public ManaSlotCategory getSlotCategory() {
        return slotCategory;
    }

    /** Armor indices are head=0, chest=1, legs=2, feet=3. Hand indices are zero. */
    public int getSlotIndex() {
        return slotIndex;
    }

    @Override
    public String toString() {
        return slotCategory + "[" + slotIndex + "] " + stack.getItem().getRegistryName()
                + "@" + stack.getMetadata() + "x" + stack.getCount()
                + (stack.hasTagCompound() ? " " + stack.getTagCompound() : "");
    }
}
