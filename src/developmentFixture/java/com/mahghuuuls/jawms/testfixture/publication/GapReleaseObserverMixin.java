package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.UUID;

@Mixin(targets = "com.mahghuuuls.jawms.internal.casting.ParticipantUseState", remap = false)
public abstract class GapReleaseObserverMixin {
    @Inject(method = "release", at = @At("HEAD"))
    private void before(UUID epoch, long intent, CallbackInfoReturnable<Boolean> callback) { record("before", epoch, intent, null); }
    @Inject(method = "release", at = @At("RETURN"))
    private void after(UUID epoch, long intent, CallbackInfoReturnable<Boolean> callback) { record("after", epoch, intent, callback.getReturnValue()); }
    private void record(String phase, UUID epoch, long intent, Boolean accepted) {
        if (!ContinuousTransportFixture.gapObserving()) return;
        PublicationFixture.log("gap_actual_release", "phase", phase, "incomingEpoch", epoch,
                "currentEpoch", PublicationFixture.field(this, "epoch"), "intent", intent,
                "newestIntent", PublicationFixture.field(this, "newestIntent"),
                "releasedThrough", PublicationFixture.field(this, "releasedThrough"), "accepted", accepted);
    }
}
