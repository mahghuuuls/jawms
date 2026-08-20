package example.jawmsapi14;

import net.minecraftforge.common.MinecraftForge;

/** Client wiring kept out of the common old-binary proxy. */
public final class Api14ClientProxy extends Api14CommonProxy {

    @Override
    public void initialize() {
        super.initialize();
        MinecraftForge.EVENT_BUS.register(Api14HudProbe.INSTANCE);
    }
}
