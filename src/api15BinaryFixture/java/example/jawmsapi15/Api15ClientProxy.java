package example.jawmsapi15;

import net.minecraftforge.common.MinecraftForge;

/** Client wiring kept out of the common old-binary proxy. */
public final class Api15ClientProxy extends Api15CommonProxy {

    @Override
    public void initialize() {
        super.initialize();
        MinecraftForge.EVENT_BUS.register(Api15HudProbe.INSTANCE);
    }
}
