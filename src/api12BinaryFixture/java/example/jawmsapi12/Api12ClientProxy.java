package example.jawmsapi12;

import net.minecraftforge.common.MinecraftForge;

/** Client wiring kept out of the common old-binary proxy. */
public final class Api12ClientProxy extends Api12CommonProxy {

    @Override
    public void initialize() {
        super.initialize();
        MinecraftForge.EVENT_BUS.register(Api12HudProbe.INSTANCE);
    }
}
