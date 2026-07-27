package com.mahghuuuls.jawms.api;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Server-authoritative access to JAWMS player mana.
 *
 * <p>Every method must be called for a logical-server player on the Minecraft
 * server thread. Mutation amounts are whole mana points and must not be
 * negative.</p>
 */
public interface IManaService {

    int getCurrentMana(EntityPlayer player);

    int getMaximumMana(EntityPlayer player);

    /**
     * Restores mana up to the player's effective maximum.
     *
     * @return the number of mana points actually restored
     */
    int restoreMana(EntityPlayer player, int amount);

    /**
     * Atomically consumes mana when the complete amount is available.
     *
     * @return true when the amount was consumed
     */
    boolean tryConsumeMana(EntityPlayer player, int amount);
}
