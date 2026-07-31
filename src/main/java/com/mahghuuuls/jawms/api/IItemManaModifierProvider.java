package com.mahghuuuls.jawms.api;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Side-effect-free logical-server modifier evaluation for one active stack.
 * Implementations may query JAWMS state, but must not mutate mana or invalidate
 * the modifier cache during this callback. Current/transient query values are
 * live; derived statistics are the player's last committed profile.
 */
@FunctionalInterface
public interface IItemManaModifierProvider {

    /**
     * Return a complete immutable contribution. Returning {@code null} or
     * throwing a runtime exception invalidates this provider result only; JAWMS
     * caches that failure for the rest of the evaluation tick.
     */
    ManaContribution getContribution(EntityPlayer player, ManaItemContext context);
}
