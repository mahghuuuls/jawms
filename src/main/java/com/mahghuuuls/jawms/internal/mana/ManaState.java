package com.mahghuuuls.jawms.internal.mana;

import net.minecraft.nbt.NBTTagCompound;

public final class ManaState {

    public static final int DEFAULT_MAXIMUM = 1000;
    public static final double DEFAULT_REGENERATION_PER_SECOND = 1.0D;

    private int currentMana = DEFAULT_MAXIMUM;
    private double regenerationRemainder;
    private long revision;

    public int getCurrentMana() {
        return currentMana;
    }

    public int getMaximumMana() {
        return DEFAULT_MAXIMUM;
    }

    public long getRevision() {
        return revision;
    }

    public int restore(int amount) {
        requireNonNegative(amount);
        int restored = Math.min(amount, getMaximumMana() - currentMana);
        if (restored > 0) {
            currentMana += restored;
            revision++;
        }
        return restored;
    }

    public boolean tryConsume(int amount) {
        requireNonNegative(amount);
        if (amount > currentMana) {
            return false;
        }
        if (amount > 0) {
            currentMana -= amount;
            revision++;
        }
        return true;
    }

    public int regenerateTick(double manaPerSecond) {
        if (!Double.isFinite(manaPerSecond) || manaPerSecond < 0.0D) {
            throw new IllegalArgumentException("Regeneration rate must be finite and non-negative.");
        }
        if (currentMana >= getMaximumMana() || manaPerSecond == 0.0D) {
            regenerationRemainder = 0.0D;
            return 0;
        }

        double accumulated = regenerationRemainder + manaPerSecond / 20.0D;
        int wholeMana = (int) Math.floor(accumulated + 1.0E-9D);
        regenerationRemainder = accumulated - wholeMana;
        if (wholeMana <= 0) {
            return 0;
        }

        int restored = Math.min(wholeMana, getMaximumMana() - currentMana);
        currentMana += restored;
        if (currentMana >= getMaximumMana()) {
            regenerationRemainder = 0.0D;
        }
        revision++;
        return restored;
    }

    public void advanceRevision() {
        revision++;
    }

    public void copyFrom(ManaState original) {
        currentMana = clamp(original.currentMana);
        regenerationRemainder = 0.0D;
        revision = original.revision + 1L;
    }

    public NBTTagCompound serializeIntrinsicState() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("currentMana", currentMana);
        return tag;
    }

    public void deserializeIntrinsicState(NBTTagCompound tag) {
        if (tag.hasKey("currentMana", 3)) {
            currentMana = clamp(tag.getInteger("currentMana"));
        } else {
            currentMana = getMaximumMana();
        }
        regenerationRemainder = 0.0D;
        revision++;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(value, getMaximumMana()));
    }

    private static void requireNonNegative(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Mana amount must not be negative.");
        }
    }
}
