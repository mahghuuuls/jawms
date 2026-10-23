package com.mahghuuuls.jawms.testfixture.publication;

import com.mahghuuuls.jawms.internal.casting.ClientUseState;
import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ClientUseState.class, remap = false)
public abstract class GapCloseObserverMixin {
    @Inject(method = "accept", at = @At("RETURN"))
    private void observed(ParticipantUseMessages.Close message, CallbackInfoReturnable<Boolean> callback) {
        if (!ContinuousTransportFixture.gapObserving()) return;
        ClientUseState state = (ClientUseState) (Object) this;
        PublicationFixture.log("gap_actual_close_accept", "incomingEpoch", message.epoch(), "currentEpoch", state.epoch(),
                "incomingIntent", message.intent(), "currentIntent", state.intent(), "incomingUse", message.use(),
                "currentUse", state.use(), "incomingHand", message.hand(), "currentHand", PublicationFixture.field(state, "hand"),
                "incomingSpell", message.spell(), "currentSpell", PublicationFixture.field(state, "spell"),
                "accepted", callback.getReturnValue(), "pending", state.pending(), "blocked", state.blocked());
    }
}
