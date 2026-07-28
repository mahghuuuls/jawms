package com.mahghuuuls.jawms.proxy;

import com.mahghuuuls.jawms.client.ClientManaState;
import com.mahghuuuls.jawms.client.ClientGameplayRules;
import com.mahghuuuls.jawms.client.ManaHudRenderer;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new ManaHudRenderer());
        FMLCommonHandler.instance().bus().register(new ClientManaState());
        FMLCommonHandler.instance().bus().register(new ClientGameplayRules());
    }

    @Override
    public void acceptManaState(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision
    ) {
        ClientManaState.accept(currentMana, maximumMana, effectiveRegeneration, revision);
    }

    @Override
    public void acceptGameplayRules(GameplayRulesMessage message) {
        ClientGameplayRules.accept(message);
    }
}
