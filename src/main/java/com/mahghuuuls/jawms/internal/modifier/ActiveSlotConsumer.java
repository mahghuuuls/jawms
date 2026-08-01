package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaSlotCategory;
import net.minecraft.item.ItemStack;

@FunctionalInterface
public interface ActiveSlotConsumer {

    void accept(ItemStack stack, ManaSlotCategory category, int slotIndex);
}
