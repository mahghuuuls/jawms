package com.mahghuuuls.jawms.internal.integration.baubles;

import baubles.api.cap.IBaublesItemHandler;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BaublesActiveSlotSourceTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void enumeratesEveryOccupiedHandlerSlotWithStableIndex() {
        IBaublesItemHandler handler = mock(IBaublesItemHandler.class);
        when(handler.getSlots()).thenReturn(7);
        when(handler.getStackInSlot(0)).thenReturn(ItemStack.EMPTY);
        when(handler.getStackInSlot(1)).thenReturn(new ItemStack(Items.STICK, 64));
        when(handler.getStackInSlot(4)).thenReturn(new ItemStack(Items.STICK));
        List<String> contexts = new ArrayList<>();

        BaublesActiveSlotSource.collectHandler(handler, (stack, category, index) ->
                contexts.add(category + ":" + index + ":" + stack.getCount())
        );

        assertEquals(java.util.Arrays.asList("BAUBLES:1:64", "BAUBLES:4:1"), contexts);
    }
}
