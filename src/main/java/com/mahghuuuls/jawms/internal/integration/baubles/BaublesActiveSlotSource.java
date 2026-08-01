package com.mahghuuuls.jawms.internal.integration.baubles;

import baubles.api.BaublesApi;
import baubles.api.cap.IBaublesItemHandler;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import com.mahghuuuls.jawms.internal.modifier.ActiveSlotConsumer;
import com.mahghuuuls.jawms.internal.modifier.ActiveSlotSource;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Typed Baubles integration; loaded only after the common bootstrap confirms Baubles is present. */
public final class BaublesActiveSlotSource implements ActiveSlotSource {

    @Override
    public void collect(EntityPlayer player, ActiveSlotConsumer consumer) {
        collectHandler(BaublesApi.getBaublesHandler(player), consumer);
    }

    static void collectHandler(IBaublesItemHandler handler, ActiveSlotConsumer consumer) {
        if (handler == null) {
            throw new IllegalStateException("Baubles returned no item handler for the player.");
        }
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (stack != null && !stack.isEmpty()) {
                consumer.accept(stack, ManaSlotCategory.BAUBLES, slot);
            }
        }
    }
}
