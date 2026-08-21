package example.jawmsapi15;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** Consumer compiled only against the released JAWMS 1.0.0 / API 1.5 JAR. */
@Mod(
        modid = Api15BinaryFixture.MOD_ID,
        name = "JAWMS API 1.5 Binary Fixture",
        version = "1.5",
        dependencies = "required-after:jawms",
        acceptableRemoteVersions = "*"
)
public final class Api15BinaryFixture {

    public static final String MOD_ID = "jawms_api15_fixture";

    @SidedProxy(
            modId = MOD_ID,
            clientSide = "example.jawmsapi15.Api15ClientProxy",
            serverSide = "example.jawmsapi15.Api15CommonProxy"
    )
    public static Api15CommonProxy proxy;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        proxy.initialize();
    }
}
