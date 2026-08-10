package example.jawmsapi12;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** Consumer compiled only against the published JAWMS 0.1.0 / API 1.2 JAR. */
@Mod(
        modid = Api12BinaryFixture.MOD_ID,
        name = "JAWMS API 1.2 Binary Fixture",
        version = "1.2",
        dependencies = "required-after:jawms",
        acceptableRemoteVersions = "*"
)
public final class Api12BinaryFixture {

    public static final String MOD_ID = "jawms_api12_fixture";

    @SidedProxy(
            modId = MOD_ID,
            clientSide = "example.jawmsapi12.Api12ClientProxy",
            serverSide = "example.jawmsapi12.Api12CommonProxy"
    )
    public static Api12CommonProxy proxy;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        proxy.initialize();
    }
}
