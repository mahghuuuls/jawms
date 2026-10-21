package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.client.InputPilotDriver;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Development-only key input source; vanilla still dispatches every action and release. */
@Mixin(Minecraft.class)
public abstract class PilotKeyProcessingMixin {
    @Inject(method = "processKeyBinds", at = @At("HEAD"), require = 1, expect = 1)
    private void pilot$keys(CallbackInfo callback) { InputPilotDriver.beforeKeys(); }
}
