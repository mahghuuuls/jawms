package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

public final class CastingDiagnostics {

    private static volatile boolean enabled = Boolean.getBoolean("jawms.debugCasting");
    private static final java.util.Map<String, long[]> FAULT_WINDOWS =
            new java.util.LinkedHashMap<String, long[]>(16, 0.75F, true) {
                @Override protected boolean removeEldestEntry(java.util.Map.Entry<String, long[]> entry) {
                    return size() > 256;
                }
            };

    private CastingDiagnostics() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setDevelopmentEnabled(boolean value) {
        enabled = value;
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "JAWMS casting diagnostics {}.",
                value ? "enabled" : "disabled"
        );
    }

    static synchronized void participation(com.mahghuuuls.jawms.api.CastAttemptOutcome outcome, long tick) {
        for (com.mahghuuuls.jawms.api.CastParticipantOutcome participant : outcome.getParticipants()) {
            if (participant.getFailure()
                    == com.mahghuuuls.jawms.api.CastParticipantOutcome.Failure.NONE) continue;
            String key = participant.getParticipantId() + "/" + participant.getFailure();
            long[] window = FAULT_WINDOWS.get(key);
            if (window != null && tick >= window[0] && tick - window[0] < 200L) {
                window[1]++;
                continue;
            }
            JustAnotherWizardryManaSystemMod.LOGGER.warn(
                    "JAWMS participant fault: participant={}, failure={}, attempt={}, status={}, suppressed={}.",
                    participant.getParticipantId(), participant.getFailure(), outcome.getAttemptId(),
                    outcome.getStatus(), window == null ? 0L : window[1]);
            FAULT_WINDOWS.put(key, new long[] { tick, 0L });
        }
        if (enabled) {
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS participation: attempt={}, spell={}, hand={}, status={}, mana={}, hunger={}.",
                    outcome.getAttemptId(), outcome.getSpellId(), outcome.getHand(), outcome.getStatus(),
                    outcome.getPaidMana(), outcome.getPaidHunger());
        }
    }

    static void trace(
            String event,
            EntityPlayer player,
            ResourceLocation spellId,
            EnumHand hand,
            int cost,
            int available
    ) {
        if (!enabled) {
            return;
        }
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "JAWMS cast event: event={}, player={}, spell={}, hand={}, cost={}, available={}.",
                event,
                player.getUniqueID(),
                spellId,
                hand,
                cost,
                available
        );
    }
}
