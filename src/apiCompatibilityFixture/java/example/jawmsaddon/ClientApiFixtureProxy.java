package example.jawmsaddon;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

/** Client-only fixture wiring, selected by Forge without loading on a server. */
public final class ClientApiFixtureProxy extends CommonApiFixtureProxy {

    @Override
    public void initialize() {
        super.initialize();
        MinecraftForge.EVENT_BUS.register(ClientReplacementHudFixture.INSTANCE);
        ClientCommandHandler.instance.registerCommand(ClientReplacementHudFixture.INSTANCE);
    }
}
