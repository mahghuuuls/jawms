package com.mahghuuuls.jawms.proxy;

import com.mahghuuuls.jawms.client.ClientManaState;
import com.mahghuuuls.jawms.client.ManaHudRenderer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new ManaHudRenderer());
        FMLCommonHandler.instance().bus().register(new ClientManaState());
    }

    @Override
    public void acceptManaState(int currentMana, int maximumMana, long revision) {
        ClientManaState.accept(currentMana, maximumMana, revision);
    }
}
