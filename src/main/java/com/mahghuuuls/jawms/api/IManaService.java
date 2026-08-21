package com.mahghuuuls.jawms.api;

import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * Server-authoritative access to JAWMS player mana.
 *
 * <p>Every method must be called for a logical-server player on the Minecraft
 * server thread. Mutation amounts are whole mana points and must not be
 * negative. Every mutation requires a namespaced cause, applies to creative
 * players, and synchronizes automatically. Ordinary mana mutations do not
 * start cast lockout; use {@link #startRegenerationLockout(EntityPlayer,
 * ResourceLocation)} when an ability needs that behavior explicitly.</p>
 */
public interface IManaService {

    /** Returns one immutable snapshot of all scalar and gate state. */
    ManaPublicState getState(EntityPlayer player);

    int getCurrentMana(EntityPlayer player);

    int getMaximumMana(EntityPlayer player);

    double getEffectiveRegeneration(EntityPlayer player);

    /** Returns the lockout duration a successful cast would start now. */
    long getEffectiveRegenerationLockoutTicks(EntityPlayer player);

    /** Returns the countdown sampled by the most recent successful cast. */
    long getRemainingRegenerationLockoutTicks(EntityPlayer player);

    default double getEffectiveRegenerationLockoutSeconds(EntityPlayer player) {
        return getEffectiveRegenerationLockoutTicks(player) / 20.0D;
    }

    default double getRemainingRegenerationLockoutSeconds(EntityPlayer player) {
        return getRemainingRegenerationLockoutTicks(player) / 20.0D;
    }

    boolean isRegenerationEligible(EntityPlayer player);

    boolean canRegenerateDuringPostCastLockout(EntityPlayer player);

    boolean canRegenerateDuringContinuousCasting(EntityPlayer player);

    /**
     * Starts or restarts the player's regeneration lockout using the currently
     * accepted effective duration and returns that duration in ticks.
     *
     * <p>This operation does not evaluate modifier providers, simulate a
     * spell, reserve or consume mana, change hunger, or alter continuous-cast
     * state. A zero duration clears an active lockout and is otherwise a
     * no-op. The supplied cause is used for any resulting state-change event.</p>
     *
     * <p>The default exists so implementations compiled against older JAWMS
     * API versions remain binary compatible. Such implementations must opt in
     * before this operation is available.</p>
     *
     * @throws UnsupportedOperationException if an alternate service
     *         implementation does not support explicit lockout
     */
    default long startRegenerationLockout(
            EntityPlayer player,
            ResourceLocation cause
    ) {
        throw new UnsupportedOperationException(
                "This mana service does not support explicit regeneration lockout."
        );
    }

    /**
     * Returns the efficiency points applicable to this registered spell.
     * The spell's element is part of the result.
     */
    double getApplicableSpellEfficiency(EntityPlayer player, Spell spell);

    /**
     * Returns the resolved multiplicative mana-cost factor applicable to this
     * registered spell. Values below {@code 1} reduce cost; values above it
     * increase cost.
     */
    double getApplicableSpellCostMultiplier(EntityPlayer player, Spell spell);

    /**
     * Sets current mana, clamping the target to the effective maximum. A
     * target below mana reserved by an in-progress cast is rejected so that
     * the already-authorized cast can still commit its exact cost.
     */
    ManaMutationResult setCurrentMana(
            EntityPlayer player,
            int target,
            ResourceLocation cause
    );

    /** Restores up to {@code amount}; the result reports the actual restoration. */
    ManaMutationResult restoreMana(
            EntityPlayer player,
            int amount,
            ResourceLocation cause
    );

    /**
     * Consumes exactly {@code amount}. An unaffordable request returns an
     * unsuccessful result and changes nothing.
     */
    ManaMutationResult consumeMana(
            EntityPlayer player,
            int amount,
            ResourceLocation cause
    );

    /** Drains up to {@code maximumAmount}; the result reports the actual drain. */
    ManaMutationResult drainMana(
            EntityPlayer player,
            int maximumAmount,
            ResourceLocation cause
    );
}
