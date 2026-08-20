package example.jawmsapi14;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** Consumer compiled only against the published JAWMS 0.4.0 / API 1.4 JAR. */
@Mod(
        modid = Api14BinaryFixture.MOD_ID,
        name = "JAWMS API 1.4 Binary Fixture",
        version = "1.4",
        dependencies = "required-after:jawms",
        acceptableRemoteVersions = "*"
)
public final class Api14BinaryFixture {

    public static final String MOD_ID = "jawms_api14_fixture";

    @SidedProxy(
            modId = MOD_ID,
            clientSide = "example.jawmsapi14.Api14ClientProxy",
            serverSide = "example.jawmsapi14.Api14CommonProxy"
    )
    public static Api14CommonProxy proxy;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        proxy.initialize();
    }
}
