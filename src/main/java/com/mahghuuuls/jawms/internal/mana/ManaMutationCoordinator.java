package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class ManaMutationCoordinator {

    static final int MAX_DEPTH = 16;

    private static final ThreadLocal<Map<UUID, Integer>> DEPTHS =
            ThreadLocal.withInitial(HashMap::new);
    private static final Set<UUID> DEPTH_WARNED_PLAYERS = new HashSet<>();

    private ManaMutationCoordinator() {
    }

    static Scope enter(UUID playerId) {
        if (playerId == null) {
            throw new NullPointerException("playerId");
        }
        Map<UUID, Integer> depths = DEPTHS.get();
        int depth = depths.getOrDefault(playerId, 0);
        if (depth >= MAX_DEPTH) {
            if (DEPTH_WARNED_PLAYERS.add(playerId)) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "Rejected a JAWMS mana mutation for player {} beyond nested depth {}.",
                        playerId,
                        MAX_DEPTH
                );
            }
            throw new IllegalStateException(
                    "JAWMS mana mutation nesting exceeded depth " + MAX_DEPTH + "."
            );
        }
        depths.put(playerId, depth + 1);
        return new Scope(playerId);
    }

    static int currentDepth(UUID playerId) {
        return DEPTHS.get().getOrDefault(playerId, 0);
    }

    static void clear(UUID playerId) {
        DEPTHS.get().remove(playerId);
        DEPTH_WARNED_PLAYERS.remove(playerId);
    }

    static final class Scope implements AutoCloseable {

        private final UUID playerId;
        private boolean active = true;

        private Scope(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            Map<UUID, Integer> depths = DEPTHS.get();
            int depth = depths.getOrDefault(playerId, 0);
            if (depth <= 1) {
                depths.remove(playerId);
            } else {
                depths.put(playerId, depth - 1);
            }
            if (depths.isEmpty()) {
                DEPTHS.remove();
            }
        }
    }
}
