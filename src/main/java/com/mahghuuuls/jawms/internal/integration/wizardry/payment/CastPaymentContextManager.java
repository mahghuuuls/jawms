package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import electroblob.wizardry.item.ItemArtefact;
import electroblob.wizardry.registry.WizardryItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Stores the immutable Seal policy for the lifetime of one physical cast. */
public final class CastPaymentContextManager {

    private static final Map<Key, DemonicSealPolicy> CONTEXTS = new HashMap<>();

    private CastPaymentContextManager() {
    }

    public static synchronized DemonicSealPolicy capture(
            EntityPlayer player,
            EnumHand hand,
            WizardryIntegrationRules rules
    ) {
        return capture(
                player.getUniqueID(), player.world.isRemote, hand,
                rules, isSealActive(player)
        );
    }

    static synchronized DemonicSealPolicy capture(
            UUID playerId,
            boolean remote,
            EnumHand hand,
            WizardryIntegrationRules rules,
            boolean sealActive
    ) {
        Key key = new Key(playerId, remote, hand);
        DemonicSealPolicy policy = DemonicSealPolicy.capture(rules, sealActive);
        CONTEXTS.put(key, policy);
        return policy;
    }

    public static synchronized DemonicSealPolicy captureIfAbsent(
            EntityPlayer player,
            EnumHand hand,
            WizardryIntegrationRules rules
    ) {
        DemonicSealPolicy existing = CONTEXTS.get(key(player, hand));
        if (existing != null) {
            return existing;
        }
        return captureIfAbsent(
                player.getUniqueID(), player.world.isRemote, hand,
                rules, isSealActive(player)
        );
    }

    static synchronized DemonicSealPolicy captureIfAbsent(
            UUID playerId,
            boolean remote,
            EnumHand hand,
            WizardryIntegrationRules rules,
            boolean sealActive
    ) {
        Key key = new Key(playerId, remote, hand);
        DemonicSealPolicy existing = CONTEXTS.get(key);
        return existing == null
                ? capture(playerId, remote, hand, rules, sealActive)
                : existing;
    }

    public static synchronized boolean has(
            EntityPlayer player, EnumHand hand
    ) {
        return CONTEXTS.containsKey(key(player, hand));
    }

    public static synchronized DemonicSealPolicy get(
            EntityPlayer player, EnumHand hand
    ) {
        DemonicSealPolicy policy = CONTEXTS.get(key(player, hand));
        return policy == null ? DemonicSealPolicy.inactive() : policy;
    }

    /** Keeps the enclosing payment policy private while its effect invokes child casts. */
    public static synchronized Runnable suspend(EntityPlayer player, EnumHand hand) {
        Key key = key(player, hand);
        DemonicSealPolicy previous = CONTEXTS.remove(key);
        return () -> {
            synchronized (CastPaymentContextManager.class) {
                if (previous == null) CONTEXTS.remove(key);
                else CONTEXTS.put(key, previous);
            }
        };
    }

    public static synchronized void clear(EntityPlayer player, EnumHand hand) {
        CONTEXTS.remove(key(player, hand));
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
        DemonicSealPolicy policy = CONTEXTS.get(key(player, hand));
        if (policy == null) {
            return "none";
        }
        return "active=" + policy.isActive()
                + ", revision=" + policy.getRulesRevision()
                + ", manaPerHunger=" + policy.getManaPerHunger()
                + ", minimumHunger=" + policy.getMinimumHunger()
                + ", allowContinuous=" + policy.isContinuousAllowed();
    }

    private static boolean isSealActive(EntityPlayer player) {
        return WizardryItems.charm_hunger_casting instanceof ItemArtefact
                && ItemArtefact.isArtefactActive(
                        player, WizardryItems.charm_hunger_casting
                );
    }

    private static Key key(EntityPlayer player, EnumHand hand) {
        if (player == null || hand == null) {
            throw new NullPointerException("player and hand must not be null");
        }
        return new Key(player.getUniqueID(), player.world.isRemote, hand);
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

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof Key)) return false;
            Key other = (Key) value;
            return remote == other.remote
                    && playerId.equals(other.playerId)
                    && hand == other.hand;
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, remote, hand);
        }
    }
}
