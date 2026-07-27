package com.mahghuuuls.jawms.internal.mana;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ManaProvider implements ICapabilitySerializable<NBTTagCompound> {

    private final ManaState state = new ManaState();

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, @Nullable EnumFacing facing) {
        return capability == ManaCapability.INSTANCE;
    }

    @Nullable
    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, @Nullable EnumFacing facing) {
        return capability == ManaCapability.INSTANCE ? ManaCapability.INSTANCE.cast(state) : null;
    }

    @Override
    public NBTTagCompound serializeNBT() {
        return state.serializeIntrinsicState();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        state.deserializeIntrinsicState(nbt);
    }
}
