package example.jawmsaddon;

import net.minecraftforge.client.ClientCommandHandler;

/** Client-only fixture wiring, selected by Forge without loading on a server. */
public final class ClientApiFixtureProxy extends CommonApiFixtureProxy {

    @Override
    public void initialize() {
        super.initialize();
        ClientReplacementHudFixture.INSTANCE.install();
        ClientCommandHandler.instance.registerCommand(ClientReplacementHudFixture.INSTANCE);
    }
}
