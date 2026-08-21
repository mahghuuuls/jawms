package com.mahghuuuls.jawms.internal.casting;

import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import java.util.UUID;

/** Test-only access to the package-owned immutable cast-cost context. */
public final class CastCostContextTestProbe {

    private CastCostContextTestProbe() {
    }

    public static int capture(
            UUID playerId,
            EnumHand hand,
            ResourceLocation spellId,
            int cost
    ) {
        return CastCostContextManager.captureOrGet(
                playerId,
                false,
                hand,
                spellId,
                cost,
                Double.NaN,
                Double.NaN,
                Double.NaN,
                Double.NaN
        );
    }
}
