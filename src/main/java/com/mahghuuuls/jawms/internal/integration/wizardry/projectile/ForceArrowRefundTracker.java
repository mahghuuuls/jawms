package com.mahghuuuls.jawms.internal.integration.wizardry.projectile;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import electroblob.wizardry.entity.projectile.EntityForceArrow;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Server-thread pending-to-promoted Force Arrow refund state. */
public final class ForceArrowRefundTracker {

    private static final Map<UUID, Entry> ENTRIES = new HashMap<>();
    private static final Map<UUID, LastProjectile> LAST = new HashMap<>();

    public static synchronized UUID capturePending(
            EntityForceArrow arrow,
            EntityPlayerMP player,
            PerfectionistPolicy policy
    ) {
        UUID projectileId = arrow.getUniqueID();
        ENTRIES.put(projectileId, new Entry(
                projectileId,
                player.getUniqueID(),
                policy,
                new WeakReference<>(arrow),
                State.PENDING,
                0,
                0
        ));
        recordNoMutation(
                player.getUniqueID(), ENTRIES.get(projectileId), Terminal.PENDING, 0, 0
        );
        return projectileId;
    }

    static synchronized void capturePending(
            UUID projectileId, UUID playerId, PerfectionistPolicy policy
    ) {
        ENTRIES.put(projectileId, new Entry(
                projectileId, playerId, policy, new WeakReference<>(null),
                State.PENDING, 0, 0
        ));
    }

    public static synchronized void promote(
            UUID projectileId, EntityPlayerMP player, int paidMana
    ) {
        Entry entry = ENTRIES.get(projectileId);
        if (entry == null || !entry.ownerId.equals(player.getUniqueID())) {
            return;
        }
        EntityForceArrow arrow = entry.arrow.get();
        promote(
                projectileId,
                player.getUniqueID(),
                paidMana,
                arrow != null && !arrow.isDead && arrow.isAddedToWorld()
        );
    }

    static synchronized void promote(
            UUID projectileId, UUID playerId, int paidMana, boolean launched
    ) {
        Entry entry = ENTRIES.get(projectileId);
        if (entry == null || !entry.ownerId.equals(playerId)) {
            return;
        }
        int requested = entry.policy.refundFor(paidMana);
        if (!launched) {
            ENTRIES.remove(projectileId);
            recordNoMutation(
                    entry.ownerId, entry, Terminal.FAILED_LAUNCH, paidMana, requested
            );
            return;
        }
        if (requested <= 0) {
            ENTRIES.remove(projectileId);
            recordNoMutation(
                    entry.ownerId, entry, Terminal.NOT_ELIGIBLE, paidMana, requested
            );
            return;
        }
        entry.state = State.ELIGIBLE;
        entry.paidMana = Math.max(0, paidMana);
        entry.requestedRefund = requested;
        recordNoMutation(
                entry.ownerId, entry, Terminal.PROMOTED, entry.paidMana, requested
        );
    }

    public static synchronized void discardPending(UUID projectileId, Terminal reason) {
        Entry entry = ENTRIES.remove(projectileId);
        if (entry != null) {
            recordNoMutation(entry.ownerId, entry, reason, 0, 0);
        }
    }

    public static void onLivingHit(EntityForceArrow arrow) {
        finish(arrow, Terminal.LIVING_HIT, false);
    }

    public static void onBlockHit(EntityForceArrow arrow) {
        finish(arrow, Terminal.BLOCK, true);
    }

    public static void onExpiry(EntityForceArrow arrow) {
        finish(arrow, Terminal.EXPIRY, true);
    }

    private static synchronized void finish(
            EntityForceArrow arrow, Terminal terminal, boolean refund
    ) {
        Resolution resolution = resolve(
                arrow.getUniqueID(), terminal, refund, arrow.world.isRemote
        );
        if (resolution == null || !resolution.shouldRefund()) {
            return;
        }
        if (!(arrow.getCaster() instanceof EntityPlayerMP)) {
            recordNoMutation(
                    resolution.entry.ownerId, resolution.entry, Terminal.CASTER_MISSING,
                    resolution.entry.paidMana, resolution.entry.requestedRefund
            );
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) arrow.getCaster();
        if (!resolution.entry.ownerId.equals(player.getUniqueID()) || player.world.isRemote) {
            recordNoMutation(
                    resolution.entry.ownerId, resolution.entry, Terminal.CASTER_MISSING,
                    resolution.entry.paidMana, resolution.entry.requestedRefund
            );
            return;
        }
        IManaService mana = ManaApi.getManaService();
        ManaPublicState before = mana.getState(player);
        ManaMutationResult result = mana.restoreMana(
                player, resolution.entry.requestedRefund,
                ManaCauses.WIZARDRY_PERFECTIONIST
        );
        ManaPublicState after = mana.getState(player);
        record(resolution.entry.ownerId, resolution.entry, terminal,
                resolution.entry.paidMana,
                resolution.entry.requestedRefund, result.getActualAmount(),
                before.getCurrentMana(), after.getCurrentMana(),
                before.getRemainingLockoutTicks(), after.getRemainingLockoutTicks());
    }

    static synchronized Resolution resolve(
            UUID projectileId, Terminal terminal, boolean refund
    ) {
        Entry entry = ENTRIES.remove(projectileId);
        if (entry == null) {
            return null;
        }
        boolean shouldRefund = entry.state == State.ELIGIBLE && refund;
        if (!shouldRefund) {
            recordNoMutation(
                    entry.ownerId, entry, terminal,
                    entry.paidMana, entry.requestedRefund
            );
        }
        return new Resolution(entry, shouldRefund);
    }

    static synchronized Resolution resolve(
            UUID projectileId, Terminal terminal, boolean refund, boolean remote
    ) {
        return remote ? null : resolve(projectileId, terminal, refund);
    }

    public static synchronized void clearPlayer(EntityPlayer player) {
        if (player != null) {
            clearPlayer(player.getUniqueID());
        }
    }

    static synchronized void clearPlayer(UUID playerId) {
        ENTRIES.values().removeIf(entry -> entry.ownerId.equals(playerId));
        LAST.remove(playerId);
    }

    public static synchronized void clearWorld(World world) {
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            EntityForceArrow arrow = entry.arrow.get();
            if (arrow == null || arrow.world == world) {
                iterator.remove();
                LAST.remove(entry.ownerId);
            }
        }
    }

    public static synchronized void clearStale(World world) {
        Iterator<Entry> iterator = ENTRIES.values().iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            EntityForceArrow arrow = entry.arrow.get();
            if (arrow == null || (arrow.world == world
                    && (arrow.isDead || (entry.state == State.ELIGIBLE
                    && !arrow.isAddedToWorld())))) {
                iterator.remove();
                recordNoMutation(
                        entry.ownerId, entry, Terminal.REMOVED,
                        entry.paidMana, entry.requestedRefund
                );
            }
        }
    }

    public static synchronized String describe(EntityPlayerMP player) {
        int pending = 0;
        int eligible = 0;
        for (Entry entry : ENTRIES.values()) {
            if (entry.ownerId.equals(player.getUniqueID())) {
                if (entry.state == State.PENDING) pending++;
                if (entry.state == State.ELIGIBLE) eligible++;
            }
        }
        LastProjectile last = LAST.get(player.getUniqueID());
        return "JAWMS projectile pending=" + pending + ", eligible=" + eligible
                + ", last=" + (last == null ? "none" : last.describe()) + ".";
    }

    public static synchronized void clearLast(EntityPlayerMP player) {
        LAST.remove(player.getUniqueID());
    }

    static synchronized int size() { return ENTRIES.size(); }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        clearPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        clearPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        clearPlayer(event.player);
    }

    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayer) {
            clearPlayer((EntityPlayer) event.getEntityLiving());
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        clearWorld(event.getWorld());
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.world.isRemote) {
            clearStale(event.world);
        }
    }

    private static void record(
            UUID ownerId, Entry entry, Terminal terminal,
            int paidMana, int requested, int actual,
            int manaBefore, int manaAfter, long lockoutBefore, long lockoutAfter
    ) {
        if (Boolean.getBoolean("jawms.enableTestCommands")) {
            LAST.put(ownerId, new LastProjectile(
                    entry.projectileId, entry.policy, terminal,
                    paidMana, requested, actual,
                    manaBefore, manaAfter, lockoutBefore, lockoutAfter
            ));
        }
    }

    private static void recordNoMutation(
            UUID ownerId, Entry entry, Terminal terminal,
            int paidMana, int requested
    ) {
        record(ownerId, entry, terminal, paidMana, requested, 0,
                0, 0, 0, 0);
    }

    enum State { PENDING, ELIGIBLE }

    static final class Resolution {
        private final Entry entry;
        private final boolean refund;

        private Resolution(Entry entry, boolean refund) {
            this.entry = entry;
            this.refund = refund;
        }

        boolean shouldRefund() { return refund; }
        int getPaidMana() { return entry.paidMana; }
        int getRequestedRefund() { return entry.requestedRefund; }
        UUID getOwnerId() { return entry.ownerId; }
    }

    public enum Terminal {
        PENDING,
        PROMOTED,
        BLOCK,
        EXPIRY,
        LIVING_HIT,
        FAILED_CAST,
        FAILED_LAUNCH,
        NOT_ELIGIBLE,
        CASTER_MISSING,
        REMOVED
    }

    private static final class Entry {
        private final UUID projectileId;
        private final UUID ownerId;
        private final PerfectionistPolicy policy;
        private final WeakReference<EntityForceArrow> arrow;
        private State state;
        private int paidMana;
        private int requestedRefund;

        private Entry(
                UUID projectileId, UUID ownerId, PerfectionistPolicy policy,
                WeakReference<EntityForceArrow> arrow, State state,
                int paidMana, int requestedRefund
        ) {
            this.projectileId = projectileId;
            this.ownerId = ownerId;
            this.policy = policy;
            this.arrow = arrow;
            this.state = state;
            this.paidMana = paidMana;
            this.requestedRefund = requestedRefund;
        }
    }

    private static final class LastProjectile {
        private final UUID projectileId;
        private final PerfectionistPolicy policy;
        private final Terminal terminal;
        private final int paidMana;
        private final int requested;
        private final int actual;
        private final int manaBefore;
        private final int manaAfter;
        private final long lockoutBefore;
        private final long lockoutAfter;

        private LastProjectile(
                UUID projectileId, PerfectionistPolicy policy, Terminal terminal,
                int paidMana, int requested, int actual,
                int manaBefore, int manaAfter, long lockoutBefore, long lockoutAfter
        ) {
            this.projectileId = projectileId;
            this.policy = policy;
            this.terminal = terminal;
            this.paidMana = paidMana;
            this.requested = requested;
            this.actual = actual;
            this.manaBefore = manaBefore;
            this.manaAfter = manaAfter;
            this.lockoutBefore = lockoutBefore;
            this.lockoutAfter = lockoutAfter;
        }

        private String describe() {
            return "projectile=" + projectileId
                    + ", terminal=" + terminal
                    + ", active=" + policy.isActive()
                    + ", rulesRevision=" + policy.getRulesRevision()
                    + ", refundPercent=" + policy.getRefundPercent()
                    + ", paidMana=" + paidMana
                    + ", refund=" + requested + "/" + actual
                    + ", mana=" + manaBefore + "->" + manaAfter
                    + ", lockout=" + lockoutBefore + "->" + lockoutAfter;
        }
    }
}
