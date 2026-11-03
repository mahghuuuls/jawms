package com.mahghuuuls.jawms.internal.casting;

import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Keeps one immutable per-second cost for the lifetime of a physical wand cast. */
public final class CastCostContextManager {

    private static final Map<Key, Context> CONTEXTS = new HashMap<>();

    private CastCostContextManager() {}

    public static synchronized int captureOrGet(
            EntityPlayer player, EnumHand hand, Spell spell, int calculatedCost
    ) {
        return captureOrGet(player, hand, spell, calculatedCost,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public static synchronized int captureOrGet(
            EntityPlayer player, EnumHand hand, Spell spell, int calculatedCost,
            double configuredBase, double contextualFactor, double profileMultiplier,
            double efficiencyPoints, double scale
    ) {
        return captureOrGet(player.getUniqueID(), player.world.isRemote, hand,
                spell.getRegistryName(), calculatedCost, configuredBase,
                contextualFactor, profileMultiplier, efficiencyPoints, scale);
    }

    static synchronized int captureOrGet(
            UUID playerId, boolean remote, EnumHand hand, ResourceLocation spellId,
            int calculatedCost, double configuredBase, double contextualFactor,
            double efficiencyPoints, double scale
    ) {
        return captureOrGet(playerId, remote, hand, spellId, calculatedCost,
                configuredBase, contextualFactor, 1.0D, efficiencyPoints, scale);
    }

    static synchronized int captureOrGet(
            UUID playerId, boolean remote, EnumHand hand, ResourceLocation spellId,
            int calculatedCost, double configuredBase, double contextualFactor,
            double profileMultiplier, double efficiencyPoints, double scale
    ) {
        Key key = new Key(playerId, remote, hand);
        Context existing = CONTEXTS.get(key);
        if (existing != null && Objects.equals(existing.spellId, spellId)) {
            if (!Double.isFinite(existing.configuredBase) && Double.isFinite(configuredBase)) {
                CONTEXTS.put(key, new Context(
                        spellId, existing.perSecondCost, configuredBase, contextualFactor,
                        profileMultiplier, efficiencyPoints, scale
                ));
            }
            return existing.perSecondCost;
        }
        CONTEXTS.put(key, new Context(
                spellId, calculatedCost, configuredBase, contextualFactor,
                profileMultiplier, efficiencyPoints, scale
        ));
        return calculatedCost;
    }

    /** Maps the retained record without running formulas or modifier providers. */
    public static synchronized com.mahghuuuls.jawms.api.CastAttemptContext.AcceptedCost acceptedCost(
            EntityPlayer player, EnumHand hand, Spell spell) {
        Context context = CONTEXTS.get(new Key(player.getUniqueID(), player.world.isRemote, hand));
        if (context == null || !Objects.equals(context.spellId, spell.getRegistryName())) {
            throw new IllegalStateException("No accepted cast cost for the owning spell.");
        }
        return new com.mahghuuuls.jawms.api.CastAttemptContext.AcceptedCost(
                context.perSecondCost, context.configuredBase, context.contextualFactor,
                context.profileMultiplier, context.efficiencyPoints, context.scale);
    }

    /** A nested spell body must not read or overwrite its parent's accepted inputs. */
    static synchronized Runnable suspend(EntityPlayer player, EnumHand hand) {
        Key key = new Key(player.getUniqueID(), player.world.isRemote, hand);
        Context previous = CONTEXTS.remove(key);
        return () -> {
            synchronized (CastCostContextManager.class) {
                if (previous == null) CONTEXTS.remove(key);
                else CONTEXTS.put(key, previous);
            }
        };
    }

    public static synchronized void clear(EntityPlayer player, EnumHand hand) {
        clear(player.getUniqueID(), player.world.isRemote, hand);
    }

    static synchronized void clear(UUID playerId, boolean remote, EnumHand hand) {
        CONTEXTS.remove(new Key(playerId, remote, hand));
    }

    public static synchronized void clearAll(EntityPlayer player) {
        UUID id = player.getUniqueID();
        boolean remote = player.world.isRemote;
        CONTEXTS.keySet().removeIf(key -> key.playerId.equals(id) && key.remote == remote);
    }

    public static synchronized void clearClientSide() {
        CONTEXTS.keySet().removeIf(key -> key.remote);
    }

    public static synchronized String describe(EntityPlayer player, EnumHand hand) {
        Context context = CONTEXTS.get(new Key(
                player.getUniqueID(), player.world.isRemote, hand
        ));
        if (context == null) {
            return "none";
        }
        return "spell=" + context.spellId
                + ", base=" + context.configuredBase
                + ", factor=" + context.contextualFactor
                + ", profileMultiplier=" + context.profileMultiplier
                + ", points=" + context.efficiencyPoints
                + ", scale=" + context.scale
                + ", perSecondCost=" + context.perSecondCost;
    }

    private static final class Context {
        private final ResourceLocation spellId;
        private final int perSecondCost;
        private final double configuredBase;
        private final double contextualFactor;
        private final double profileMultiplier;
        private final double efficiencyPoints;
        private final double scale;
        private Context(ResourceLocation spellId, int perSecondCost, double configuredBase,
                        double contextualFactor, double profileMultiplier,
                        double efficiencyPoints, double scale) {
            this.spellId = spellId;
            this.perSecondCost = perSecondCost;
            this.configuredBase = configuredBase;
            this.contextualFactor = contextualFactor;
            this.profileMultiplier = profileMultiplier;
            this.efficiencyPoints = efficiencyPoints;
            this.scale = scale;
        }
    }

    private static final class Key {
        private final UUID playerId;
        private final boolean remote;
        private final EnumHand hand;
        private Key(UUID playerId, boolean remote, EnumHand hand) {
            this.playerId = playerId;
            this.remote = remote;
            this.hand = hand;
        }
        @Override public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Key)) return false;
            Key other = (Key) value;
            return remote == other.remote && playerId.equals(other.playerId) && hand == other.hand;
        }
        @Override public int hashCode() { return Objects.hash(playerId, remote, hand); }
    }
}
