package com.mahghuuuls.jawms.bootstrap;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Registers native targets before ordinary mod construction, without initializing gameplay. */
@IFMLLoadingPlugin.Name("JAWMS")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions("com.mahghuuuls.jawms.bootstrap.")
public final class JawmsEarlyMixinLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.jawms.early.json");
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // MixinBooter owns configuration queueing and transformation.
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
