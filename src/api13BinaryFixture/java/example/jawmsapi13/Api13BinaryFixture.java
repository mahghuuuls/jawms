package example.jawmsapi13;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

/** Consumer compiled only against the published JAWMS 0.3.0 / API 1.3 JAR. */
@Mod(
        modid = Api13BinaryFixture.MOD_ID,
        name = "JAWMS API 1.3 Binary Fixture",
        version = "1.3",
        dependencies = "required-after:jawms",
        acceptableRemoteVersions = "*"
)
public final class Api13BinaryFixture {

    public static final String MOD_ID = "jawms_api13_fixture";

    @SidedProxy(
            modId = MOD_ID,
            clientSide = "example.jawmsapi13.Api13ClientProxy",
            serverSide = "example.jawmsapi13.Api13CommonProxy"
    )
    public static Api13CommonProxy proxy;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        proxy.initialize();
    }
}
