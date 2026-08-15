package com.mahghuuuls.jawms;

import com.mahghuuuls.jawms.internal.mana.ManaBootstrap;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.rules.JawmsCommand;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import com.mahghuuuls.jawms.internal.modifier.ActiveSlotSources;
import com.mahghuuuls.jawms.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

@Mod(
        modid = Tags.MOD_ID,
        name = Tags.MOD_NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.12.2]",
        dependencies = JustAnotherWizardryManaSystemMod.DEPENDENCIES,
        guiFactory = "com.mahghuuuls.jawms.client.JawmsGuiFactory"
)
public final class JustAnotherWizardryManaSystemMod {

    public static final String DEPENDENCIES =
            "required-after:ebwizardry@[4.3.19,4.3.20);"
                    + "required-after:mixinbooter@[11.8,);"
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
        File configurationDirectory = new File(
                event.getModConfigurationDirectory(), Tags.MOD_ID
        );
        if (!configurationDirectory.isDirectory() && !configurationDirectory.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create JAWMS configuration directory: "
                            + configurationDirectory.getAbsolutePath()
            );
        }
        File integrationsDirectory = new File(configurationDirectory, "integrations");
        if (!integrationsDirectory.isDirectory() && !integrationsDirectory.mkdirs()) {
            throw new IllegalStateException(
                    "Could not create JAWMS integrations directory: "
                            + integrationsDirectory.getAbsolutePath()
            );
        }
        GameplayRulesManager.configure(
                new File(configurationDirectory, Tags.MOD_ID + ".cfg"),
                new File(integrationsDirectory, "ebwizardry.cfg")
        );
        ActiveSlotSources.initialize();
        ManaBootstrap.initialize();
        initializeDevelopmentFixture();
        proxy.preInit(configurationDirectory);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        GameplayRulesManager.initialize();
    }

    @Mod.EventHandler
    public void loadComplete(FMLLoadCompleteEvent event) {
        ManaProviderRegistry.instance().freeze();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new JawmsCommand());
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

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        ManaProviderRegistry.instance().clearRuntimeState();
    }

    private static void initializeDevelopmentFixture() {
        if (!Boolean.getBoolean("jawms.enableTestCommands")) {
            return;
        }
        try {
            Class<?> fixtureClass = Class.forName(
                    "com.mahghuuuls.jawms.testfixture.ManaTestFixture"
            );
            Method registrationMethod = fixtureClass.getMethod("preInitialize");
            registrationMethod.invoke(null);
        } catch (ClassNotFoundException exception) {
            LOGGER.warn("JAWMS development fixture initialization was requested but is unavailable.");
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException(
                    "Could not initialize the JAWMS development fixture.", exception
            );
        }
    }
}
