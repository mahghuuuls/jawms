package com.mahghuuuls.jawms.bootstrap;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Collections;
import java.util.List;

/** Discovered by its direct interface after normal mod targets become available. */
public final class JawmsLateMixinLoader implements ILateMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("mixins.jawms.late.json");
    }
}
