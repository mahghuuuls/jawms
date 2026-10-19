package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Production identity/semantic policy only; runtime publication association is separate proof. */
class WandBindingTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    private static ItemStack stack() {
        ItemStack result = new ItemStack(Items.STICK);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setIntArray("spells", new int[] { 1, 1 });
        tag.setInteger("selectedSpell", 0);
        result.setTagCompound(tag);
        return result;
    }

    @Test void routineInPlaceChangesAreNotIdentityChanges() {
        ItemStack original = stack();
        WandBinding binding = new WandBinding(original, 1);
        original.getTagCompound().setIntArray("cooldown", new int[] { 20, 0 });
        original.getTagCompound().setIntArray("maxCooldown", new int[] { 20, 0 });
        original.getTagCompound().setInteger("progression", 15);
        assertTrue(binding.valid(original));
    }

    @Test void verifiedPublicationTransfersOnlyTheRepresentative() {
        ItemStack original = stack(), publication = original.copy();
        WandBinding binding = new WandBinding(original, 0);
        publication.getTagCompound().setIntArray("cooldown", new int[] { 20, 0 });
        assertTrue(binding.publish(original, publication, 7));
        assertSame(publication, binding.representative());
        assertEquals(7, binding.generation());
        assertTrue(binding.valid(publication));
        assertFalse(binding.adopt(8));
    }

    @Test void byteEqualReplacementIsNotAdoptedByValidityAndNeverRevives() {
        ItemStack original = stack();
        WandBinding binding = new WandBinding(original, 1);
        assertTrue(WandBinding.semanticEquals(original, original.copy()));
        assertFalse(binding.valid(original.copy()));
        assertTrue(binding.retired());
        assertFalse(binding.valid(original));
        assertFalse(binding.publish(original, original.copy(), 1));
        assertNull(binding.representative());
    }

    @Test void selectedSlotChangesEvenWhenBothSlotsContainTheSameSpell() {
        ItemStack original = stack();
        WandBinding binding = new WandBinding(original, 1);
        original.getTagCompound().setInteger("selectedSpell", 1);
        assertFalse(binding.valid(original));
        original.getTagCompound().setInteger("selectedSpell", 0);
        assertFalse(binding.valid(original));
    }

    @Test void upgradesAndUnknownTagsAndItemCountDamageRemainSemanticGuards() {
        for (String key : new String[] { "upgrades", "spells", "addon_data" }) {
            ItemStack original = stack(), changed = original.copy();
            changed.getTagCompound().setInteger(key, 12);
            assertFalse(new WandBinding(original, 1).publish(original, changed, 1), key);
        }
        ItemStack original = stack(), changed = original.copy();
        changed.setCount(2);
        assertFalse(WandBinding.semanticEquals(original, changed));
        changed = original.copy(); changed.setItemDamage(1);
        assertFalse(WandBinding.semanticEquals(original, changed));
        assertFalse(WandBinding.semanticEquals(original, new ItemStack(Items.APPLE)));
    }

    @Test void wrongGenerationOrStaleRepresentativeCannotTransfer() {
        ItemStack original = stack(), first = original.copy(), second = original.copy();
        WandBinding binding = new WandBinding(original, 9);
        assertTrue(binding.publish(original, first, 9));
        assertFalse(binding.publish(original, second, 9));
        assertTrue(binding.retired());
        assertFalse(new WandBinding(original, 9).publish(original, first, 10));
    }

    @Test void routineTagCreationAndExplicitRetirementDoNotNeedNBTInequality() {
        ItemStack original = new ItemStack(Items.STICK), published = original.copy();
        NBTTagCompound routine = new NBTTagCompound(); routine.setInteger("progression", 2);
        published.setTagCompound(routine);
        WandBinding binding = new WandBinding(original, 1);
        assertTrue(binding.publish(original, published, 1));
        binding.retire();
        assertFalse(binding.valid(published));
        assertFalse(binding.adopt(1));
    }

    @Test void semanticValidationDoesNotConstructStacksOrInvokeCapabilityAttachment() {
        java.util.concurrent.atomic.AtomicInteger attachments = new java.util.concurrent.atomic.AtomicInteger();
        net.minecraft.item.Item item = new net.minecraft.item.Item() {
            @Override public net.minecraftforge.common.capabilities.ICapabilityProvider initCapabilities(
                    ItemStack stack, NBTTagCompound nbt) { attachments.incrementAndGet(); return null; }
        };
        ItemStack original = new ItemStack(item), installed = original.copy();
        attachments.set(0);
        WandBinding binding = new WandBinding(original, 1);
        assertTrue(binding.valid(original));
        assertTrue(binding.publish(original, installed, 1));
        assertTrue(binding.valid(installed));
        assertEquals(0, attachments.get(), "pure semantic guards must not construct capability-bearing stacks");
    }
    @Test void semanticDamageGuardDoesNotInvokeCustomItemValueCallbacks() {
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        net.minecraft.item.Item item = new net.minecraft.item.Item() {
            @Override public int getDamage(ItemStack stack) { reads.incrementAndGet(); return super.getDamage(stack); }
            @Override public int getMetadata(ItemStack stack) { reads.incrementAndGet(); return super.getMetadata(stack); }
        };
        ItemStack original = new ItemStack(item), installed = original.copy();
        reads.set(0);
        WandBinding binding = new WandBinding(original, 1);
        assertTrue(binding.valid(original));
        assertTrue(WandBinding.semanticEquals(original, installed));
        assertTrue(binding.publish(original, installed, 1));
        assertEquals(0, reads.get(), "raw value guards cannot invoke subclass callbacks");
    }
}
