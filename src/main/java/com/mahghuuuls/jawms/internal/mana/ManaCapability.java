package com.mahghuuuls.jawms.internal.mana;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;

public final class ManaCapability {

    @CapabilityInject(ManaState.class)
    public static final Capability<ManaState> INSTANCE = null;

    private ManaCapability() {
    }

    public static void register() {
        CapabilityManager.INSTANCE.register(ManaState.class, new Storage(), ManaState::new);
    }

    private static final class Storage implements Capability.IStorage<ManaState> {

        @Override
        public NBTBase writeNBT(Capability<ManaState> capability, ManaState instance, EnumFacing side) {
            return instance.serializeIntrinsicState();
        }

        @Override
        public void readNBT(Capability<ManaState> capability, ManaState instance, EnumFacing side, NBTBase nbt) {
            if (!(nbt instanceof NBTTagCompound)) {
                throw new IllegalArgumentException("JAWMS mana capability requires a compound NBT tag.");
            }
            instance.deserializeIntrinsicState((NBTTagCompound) nbt);
        }
    }
}
