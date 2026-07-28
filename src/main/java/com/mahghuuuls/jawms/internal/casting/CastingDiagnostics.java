package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

public final class CastingDiagnostics {

    private static volatile boolean enabled = Boolean.getBoolean("jawms.debugCasting");

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
