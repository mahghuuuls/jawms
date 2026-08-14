package example.jawmsapi13;

import net.minecraftforge.common.MinecraftForge;

/** Client wiring kept out of the common old-binary proxy. */
public final class Api13ClientProxy extends Api13CommonProxy {

    @Override
    public void initialize() {
        super.initialize();
        MinecraftForge.EVENT_BUS.register(Api13HudProbe.INSTANCE);
    }
}
