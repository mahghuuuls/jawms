package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.ref.WeakReference;
import java.util.Objects;

/** Original wand identity, distinct from the client object installed by synchronization. */
public final class WandBinding {
    // Forge's getItemDamage/getMetadata delegate to virtual Item callbacks, even for a value read.
    // These exact MC1.12.2 MCP/SRG names read vanilla's equality field without executing addon code.
    private static final java.lang.reflect.Field DAMAGE = net.minecraftforge.fml.relauncher.ReflectionHelper
            .findField(ItemStack.class, new String[] { "itemDamage", "field_77991_e" });
    private final Values semanticSnapshot;
    private WeakReference<ItemStack> representative;
    private long generation;
    private boolean retired;

    public WandBinding(ItemStack original, long generation) {
        if (Objects.requireNonNull(original, "original").isEmpty() || generation < 0)
            throw new IllegalArgumentException("Wand binding");
        this.semanticSnapshot = new Values(original);
        this.representative = new WeakReference<>(original);
        this.generation = generation;
    }

    /** Equality guards cast data; it never grants identity to an equal replacement. */
    public boolean valid(ItemStack actual) {
        if (retired) return false;
        if (representative.get() != actual || !semanticSnapshot.matches(actual)) {
            retire();
            return false;
        }
        return true;
    }

    /** Called only by a validated correlated binding/close or exact publication scope. */
    public boolean adopt(long authoritativeGeneration) {
        if (retired || authoritativeGeneration <= 0
                || (generation != 0 && generation != authoritativeGeneration)) return false;
        generation = authoritativeGeneration;
        return true;
    }

    /** The caller must establish source provenance and exact vanilla packet/write ownership. */
    public boolean publish(ItemStack previous, ItemStack installed, long authoritativeGeneration) {
        if (!valid(previous) || !adopt(authoritativeGeneration)
                || !semanticSnapshot.matches(installed)) {
            retire();
            return false;
        }
        representative = new WeakReference<>(installed);
        return true;
    }

    public ItemStack representative() { return retired ? null : representative.get(); }
    public long generation() { return generation; }
    public boolean retired() { return retired; }
    public void retire() { retired = true; representative.clear(); }

    public static boolean semanticEquals(ItemStack left, ItemStack right) {
        return left != null && right != null && !left.isEmpty() && !right.isEmpty()
                && new Values(left).matches(right);
    }

    private static NBTTagCompound projectData(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null) {
            tag = tag.copy();
            tag.removeTag("cooldown");
            tag.removeTag("maxCooldown");
            tag.removeTag("progression");
            if (tag.getKeySet().isEmpty()) tag = null;
        }
        return tag;
    }
    private static int damage(ItemStack stack) {
        try { return DAMAGE.getInt(stack); }
        catch (IllegalAccessException inaccessible) { throw new IllegalStateException("Cannot read MC1.12.2 wand damage value", inaccessible); }
    }

    /** Item/count/damage and ordinary wand NBT only; never construct stacks or evaluate capabilities. */
    private static final class Values {
        final net.minecraft.item.Item item;
        final int count, damage;
        final NBTTagCompound data;
        Values(ItemStack stack) {
            item = stack.getItem(); count = stack.getCount(); damage = damage(stack);
            data = projectData(stack);
        }
        boolean matches(ItemStack stack) {
            return stack != null && !stack.isEmpty() && item == stack.getItem()
                    && count == stack.getCount() && damage == damage(stack)
                    && Objects.equals(data, projectData(stack));
        }
    }
}
