package com.mahghuuuls.jawms.api;

import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class CastCostValuesTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @Test void contextFreezesActualStackAndAcceptedCostWithoutConsultingProviders() {
        ItemStack stack = new ItemStack(Items.STICK, 2);
        CastAttemptContext.AcceptedCost cost = new CastAttemptContext.AcceptedCost(
                9, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        CastAttemptContext context = new CastAttemptContext(mock(EntityPlayerMP.class), mock(Spell.class),
                new ResourceLocation("addon:spell"), stack, EnumHand.OFF_HAND, false, false, 4,
                "opaque-use", "opaque-attempt", cost, 9, 7, 1);
        stack.setCount(8);
        context.getStack().setCount(64);
        assertEquals(2, context.getStack().getCount());
        assertSame(cost, context.getAcceptedCost());
        assertFalse(cost.isFormulaAvailable());
        assertEquals(9, cost.getTotalOrPerSecondCost());
        assertEquals(7, context.getPlannedMana());
        assertEquals(1, context.getPlannedHunger());
    }
    @Test void invalidPreparationAndMalformedReceiptCannotBeConstructed() {
        assertThrows(NullPointerException.class, () -> CastCostPreparation.accepted(null));
        assertThrows(IllegalArgumentException.class, () -> CastCostPreparation.refused("unsafe\nlog"));
        assertThrows(IllegalArgumentException.class, () -> CastCostSettlement.paid(0, false));
        assertThrows(IllegalArgumentException.class, () -> CastCostSettlement.paid(-1, false));
        assertThrows(IllegalArgumentException.class, () -> CastCostSettlement.paid(Double.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> CastCostSettlement.paid(Double.POSITIVE_INFINITY, false));
    }
    @Test void freeAndInvalidationAreNotAliasesAndTerminalUsesActualReceipt() {
        CastCostSettlement free = CastCostSettlement.free(true);
        assertEquals(CastCostSettlement.Status.FREE, free.getStatus());
        assertTrue(free.isAmountKnown());
        assertEquals(0, free.getAmount());
        assertTrue(free.mustStopAfterSuccess());
        assertFalse(CastCostSettlement.invalidated().isAmountKnown());
        assertTrue(Double.isNaN(CastCostSettlement.invalidated().getAmount()));
        assertFalse(CastCostSettlement.invalidated().mustStopAfterSuccess());
    }
}
