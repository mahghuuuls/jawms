package com.mahghuuuls.jawms;

import com.mahghuuuls.jawms.internal.mana.ManaBootstrap;
import com.mahghuuuls.jawms.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = JustAnotherWizardryManaSystemMod.DEPENDENCIES
)
public final class JustAnotherWizardryManaSystemMod {

    public static final String DEPENDENCIES =
            "required-after:ebwizardry@[4.3.19,4.3.20);"
                    + "required-after:mixinbooter@[11.11,12.0);"
                    + "after:baubles@[1.5.2,)";

    public static final Logger LOGGER = LogManager.getLogger(Tags.MOD_NAME);

    @SidedProxy(
            modId = Tags.MOD_ID,
            clientSide = "com.mahghuuuls.jawms.proxy.ClientProxy",
            serverSide = "com.mahghuuuls.jawms.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Initializing {}.", Tags.MOD_NAME);
        ManaBootstrap.initialize();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (!Boolean.getBoolean("jawms.enableTestCommands")) {
            return;
        }

        try {
            Class<?> fixtureClass = Class.forName("com.mahghuuuls.jawms.testfixture.ManaTestFixture");
            Method registrationMethod = fixtureClass.getMethod("register", FMLServerStartingEvent.class);
            registrationMethod.invoke(null, event);
            LOGGER.warn("JAWMS development test commands are enabled. They are not included in the production jar.");
        } catch (ClassNotFoundException exception) {
            LOGGER.warn("JAWMS test commands were requested but the development fixture is unavailable.");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Could not register the JAWMS development test fixture.", exception);
        }
    }
}
