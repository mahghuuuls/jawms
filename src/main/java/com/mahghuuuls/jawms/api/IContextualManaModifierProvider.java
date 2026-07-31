package com.mahghuuuls.jawms.api;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Side-effect-free logical-server player-wide modifier evaluation. The query,
 * mutation, failure, and cache rules are the same as for
 * {@link IItemManaModifierProvider}.
 */
@FunctionalInterface
public interface IContextualManaModifierProvider {

    /** Return a complete immutable contribution. Returning {@code null} is invalid. */
    ManaContribution getContribution(EntityPlayer player);
}
