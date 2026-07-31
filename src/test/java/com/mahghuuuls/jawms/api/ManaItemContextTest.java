package com.mahghuuuls.jawms.api;

import net.minecraft.item.ItemStack;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ManaItemContextTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void copiesInputAndEveryReturnedStack() {
        ItemStack source = new ItemStack(Items.STICK, 2);
        ManaItemContext context = new ManaItemContext(source, ManaSlotCategory.ARMOR, 0);
        source.setCount(7);
        ItemStack first = context.getStack();
        assertNotSame(source, first);
        first.setCount(9);
        assertEquals(2, context.getStack().getCount());
    }

    @Test
    void validatesCategorySpecificIndices() {
        ItemStack stack = new ItemStack(Items.STICK);
        assertThrows(IllegalArgumentException.class, () ->
                new ManaItemContext(stack, ManaSlotCategory.ARMOR, 4));
        assertThrows(IllegalArgumentException.class, () ->
                new ManaItemContext(stack, ManaSlotCategory.OFF_HAND, 1));
    }

}
