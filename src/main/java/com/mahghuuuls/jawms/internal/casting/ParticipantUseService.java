package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.CastAttemptOutcome;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import com.mahghuuuls.jawms.internal.mana.ManaNetwork;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages.CloseCause;

/** Server-thread use/close owner. Transport identity is never resource authorization. */
public final class ParticipantUseService {
    private static final Map<Object, Connection> CONNECTIONS = new IdentityHashMap<>();
    private static final ThreadLocal<Deque<Origin>> ORIGINS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Deque<Scope>> CASTS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final AtomicLong RECEIPTS = new AtomicLong();
    private static UUID run = UUID.randomUUID();
    private static long uses;

    private ParticipantUseService() {}

    public static void clear() {
        for (Connection connection : CONNECTIONS.values()) {
            finishDiagnostics(connection.state.current(), CloseCause.CANCELLED);
            connection.wands.clear();
        }
        CastingDiagnostics.clear();
        CONNECTIONS.clear(); ORIGINS.remove(); CASTS.remove();
        run = UUID.randomUUID(); uses = 0;
    }
    public static long networkReceipt() { return RECEIPTS.incrementAndGet(); }
    private static long nextUse() {
        if (uses == Long.MAX_VALUE) throw new IllegalStateException("Use sequence exhausted");
        return ++uses;
    }
    public static void login(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || CastCostRegistry.instance().isEmpty()) return;
        EntityPlayerMP mp = (EntityPlayerMP) player;
        Connection connection = CONNECTIONS.get(mp.connection);
        if (connection == null) {
            connection = new Connection(mp);
            CONNECTIONS.put(mp.connection, connection);
        }
        ManaNetwork.participantCapability(mp, connection.state.epoch());
    }
    public static void remove(EntityPlayer player) {
        Connection connection = player instanceof EntityPlayerMP ? CONNECTIONS.remove(((EntityPlayerMP) player).connection) : null;
        if (connection != null) {
            close(player, connection, connection.state.current(), CloseCause.CANCELLED, false);
            connection.wands.clear();
        }
    }
    private static Connection connection(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) return null;
        Connection result = CONNECTIONS.get(((EntityPlayerMP) player).connection);
        return result != null && ((net.minecraft.network.NetHandlerPlayServer) result.handler).player == player ? result : null;
    }
    public static ServerWandBindings wands(EntityPlayerMP player) {
        Connection c = connection(player);
        return c == null ? null : c.wands;
    }
    public static void cancelUse(EntityPlayer player) {
        Connection c = player instanceof EntityPlayerMP ? CONNECTIONS.get(((EntityPlayerMP) player).connection) : null;
        if (c != null) {
            c.wands.retireAll();
            close(player, c, c.state.current(), CloseCause.CANCELLED, true);
        }
    }
    public static void intent(EntityPlayerMP player, ParticipantUseMessages.Intent message, long receipt) {
        Connection c = connection(player);
        long previous = c == null ? 0 : c.state.newestIntent();
        ParticipantUseState.Use previousUse = c == null ? null : c.state.current();
        if (c != null && c.state.intent(message.epoch(), message.intent(), message.hand(), message.spell())) {
            if (previousUse != null && previousUse != c.state.current()) {
                finishDiagnostics(previousUse, CloseCause.CANCELLED);
            }
            if (message.intent() > previous) c.traces = 0;
            trace(c, "intent", receipt, c.state.current());
        }
    }
    public static void release(EntityPlayerMP player, ParticipantUseMessages.Release message, long receipt) {
        Connection c = connection(player);
        if (c != null && c.state.release(message.epoch(), message.intent())) {
            ParticipantUseState.Use use = c.state.current();
            if (use != null) ManaService.releaseContinuousCastRetryBlock(player, use.hand, use.spell);
            trace(c, "release", receipt, use);
        }
    }

    /** Origins are only real vanilla wand entry points; direct nested calls remain synthetic. */
    public static void enterOrigin(EntityPlayer player, ItemStack stack, EnumHand hand, boolean start) {
        Connection c = player.world.isRemote ? null : connection(player);
        ParticipantUseState.Use use = c == null ? null : c.state.current();
        ORIGINS.get().push(new Origin(player, stack, hand, c, use));
    }
    public static void bindStarted(EntityPlayer player, EnumHand hand) {
        Origin origin = ORIGINS.get().peek();
        if (origin == null || origin.connection == null || !player.isHandActive()
                || player.getActiveHand() != hand) return;
        Spell spell = WandHelper.getCurrentSpell(origin.stack);
        if (!spell.isContinuous) return;
        origin.use = origin.connection.state.bind(hand, spell.getRegistryName(), origin.stack, nextUse());
        bindWand(player, origin.connection, origin.use, origin.stack);
        trace(origin.connection, "vanilla_bind", 0, origin.use);
    }
    public static void exitOrigin() { ORIGINS.get().pop(); }
    public static Scope enterCast(EntityPlayer player, ItemStack stack, Spell spell, EnumHand hand) {
        Origin origin = CASTS.get().isEmpty() ? ORIGINS.get().peek() : null;
        if (origin != null && (origin.player != player || origin.stack != stack || origin.hand != hand)) origin = null;
        Scope scope = new Scope(player, spell, hand, origin, run + "/use/" + nextUse());
        if (spell.isContinuous && origin != null && origin.connection != null
                && origin.connection.state.matchesOpen(origin.use, hand, spell.getRegistryName(), stack))
            scope.id = run + "/use/" + origin.use.id;
        CASTS.get().push(scope);
        return scope;
    }
    public static Scope current() { return CASTS.get().peek(); }
    public static boolean syntheticCast() { return current() != null && current().origin == null; }
    private static Origin physicalOrigin() {
        Scope scope = current();
        return scope == null ? ORIGINS.get().peek() : scope.origin;
    }
    public static boolean supportedDepth() { return CASTS.get().size() <= 16; }
    public static void exitCast(Scope scope) {
        try {
            if (scope.cause != null && scope.origin != null) {
                Origin o = scope.origin;
                if (o.connection != null && o.use != null) close(scope.player, o.connection, o.use, scope.cause, true);
                else {
                    scope.player.resetActiveHand();
                    scope.player.sendStatusMessage(new TextComponentString("Cast stopped: continuous-use correlation unavailable."), true);
                }
            }
        } finally { CASTS.get().pop(); }
    }
    public static boolean retryBlocked(EntityPlayer player) {
        if (player.world.isRemote) return false;
        Connection c = connection(player);
        return c != null && c.state.retryBlocked();
    }
    public static boolean suppressesFinish(EntityPlayer player, EnumHand hand, Spell spell) {
        Connection c = connection(player);
        return c != null && c.state.suppressesFinish(hand, spell.getRegistryName());
    }

    public static ReleaseFrame beginRelease(EntityPlayer player) {
        Connection c = player.world.isRemote ? null : connection(player);
        if (c == null || c.release != null || !c.state.isOpen(c.state.current())) return null;
        ParticipantUseState.Use use = c.state.current();
        if (!player.isHandActive() || !c.state.matchesOpen(use, player.getActiveHand(),
                WandHelper.getCurrentSpell(player.getActiveItemStack()).getRegistryName(), player.getActiveItemStack())
                || player.getHeldItem(use.hand) != use.stack || !c.wands.valid(use.hand, (ItemStack) use.stack, use.generation)) {
            close(player, c, use, CloseCause.CANCELLED, false);
            return null;
        }
        ReleaseFrame frame = new ReleaseFrame(c, c.state.current());
        c.release = frame;
        return frame;
    }
    public static void finishedBody(EntityPlayer player, ItemStack stack, Spell spell) {
        Connection c = connection(player);
        if (c != null && c.release != null && c.release.callbackActive
                && c.state.matchesOpen(c.release.use, c.release.use.hand, spell.getRegistryName(), stack)
                && player.getHeldItem(c.release.use.hand) == stack
                && c.wands.valid(c.release.use.hand, stack, c.release.use.generation)) c.release.finished = true;
    }
    public static boolean permitsFinish(EntityPlayer player, ItemStack stack, EnumHand hand, Spell spell) {
        Connection c = connection(player);
        if (c == null || !spell.isContinuous) return true;
        if (c.release != null && c.release.callbackActive) return false;
        boolean valid = c.release != null && !c.release.callbackActive && c.release.use == c.state.current()
                && c.state.matchesOpen(c.release.use, hand, spell.getRegistryName(), stack)
                && player.getHeldItem(hand) == stack && c.wands.valid(hand, stack, c.release.use.generation);
        if (valid) c.release.callbackActive = true;
        else close(player, c, c.state.current(), CloseCause.CANCELLED, false);
        return valid;
    }
    public static void endFinish(EntityPlayer player) {
        Connection c = connection(player);
        if (c != null && c.release != null) c.release.callbackActive = false;
    }
    public static void endRelease(EntityPlayer player, ReleaseFrame frame, boolean failed) {
        if (frame == null) return;
        try {
            close(player, frame.connection, frame.use, failed ? CloseCause.FAULT
                    : frame.finished ? CloseCause.NORMAL_FINISH : CloseCause.NORMAL_NO_FINISH, false);
            if (ManaService.requireState(player).isContinuousCastActive()) ManaService.finishContinuousCast(player);
            CastCostContextManager.clear(player, frame.use.hand);
            CastPaymentContextManager.clear(player, frame.use.hand);
        } finally { frame.connection.release = null; }
    }
    private static Origin owningPhysicalOrigin(EntityPlayer player) {
        Origin origin = physicalOrigin();
        if (origin == null || origin.player != player || origin.connection == null || origin.use == null
                || connection(player) != origin.connection || !origin.connection.state.owns(origin.use)) return null;
        return origin;
    }
    /** Ordinary canCast interruption must reach the actual stop callback, not pre-classify its finish. */
    public static boolean ownsPhysicalStop(EntityPlayer player) { return owningPhysicalOrigin(player) != null; }
    public static boolean interrupt(EntityPlayer player) {
        Origin origin = owningPhysicalOrigin(player);
        if (origin == null) return false;
        Scope scope = current();
        if (scope != null) scope.cause = CloseCause.NORMAL_NO_FINISH;
        else close(player, origin.connection, origin.use, CloseCause.NORMAL_NO_FINISH, true);
        return true;
    }
    public static boolean rejectStart(EntityPlayer player, EnumHand hand, ItemStack stack, Spell spell) {
        Origin origin = physicalOrigin();
        if (origin == null || origin.player != player || origin.stack != stack || origin.hand != hand) return false;
        Connection c = origin.connection;
        if (c == null || connection(player) != c) return false;
        ParticipantUseState.Use use = c.state.bind(hand, spell.getRegistryName(), stack, nextUse());
        if (use == null) return false;
        origin.use = use;
        bindWand(player, c, use, stack);
        close(player, c, use, CloseCause.NORMAL_NO_FINISH, false);
        return true;
    }
    public static void tick(EntityPlayer player) {
        Connection c = connection(player);
        if (c != null) c.wands.tick();
        if (c == null || !c.state.isOpen(c.state.current())) return;
        ParticipantUseState.Use use = c.state.current();
        if (!player.isHandActive() || player.getActiveHand() != use.hand || player.getHeldItem(use.hand) != use.stack) {
            close(player, c, use, CloseCause.CANCELLED, false);
        }
    }
    private static void bindWand(EntityPlayer player, Connection c, ParticipantUseState.Use use, ItemStack stack) {
        if (use != null && use.generation == 0) {
            use.generation = c.wands.bind((EntityPlayerMP) player, use.hand, stack, use.intent, use.id);
            if (!c.wands.valid(use.hand, stack, use.generation)) close(player, c, use, CloseCause.INVALIDATED, true);
        }
    }
    private static void close(EntityPlayer player, Connection c, ParticipantUseState.Use use, CloseCause cause, boolean reset) {
        if (!c.state.close(use, cause)) return;
        trace(c, "close_" + cause.name(), 0, use);
        finishDiagnostics(use, cause);
        if (player.world != null && !player.world.isRemote) {
            if (ManaService.requireState(player).isContinuousCastActive()) ManaService.finishContinuousCast(player);
            CastCostContextManager.clear(player, use.hand);
            CastPaymentContextManager.clear(player, use.hand);
            ManaNetwork.participantClose((EntityPlayerMP) player,
                    new ParticipantUseMessages.Close(c.state.epoch(), use.intent, use.id, use.hand, use.spell, cause, use.generation));
            if (reset && player.isHandActive() && player.getActiveHand() == use.hand) player.resetActiveHand();
        }
    }
    private static void finishDiagnostics(ParticipantUseState.Use use, CloseCause cause) {
        if (use == null || use.diagnostics == null) return;
        if (CastingDiagnostics.isEnabled()) use.diagnostics.emit(cause);
        use.diagnostics = null;
    }

    private static void trace(Connection c, String event, long receipt, ParticipantUseState.Use use) {
        if (!CastingDiagnostics.isEnabled() || c.traces++ >= 128) return;
        JustAnotherWizardryManaSystemMod.LOGGER.info("JAWMS use: event={}, epoch={}, intent={}, use={}, receipt={}, thread={}",
                event, c.state.epoch(), use == null ? c.state.newestIntent() : use.intent, use == null ? 0 : use.id,
                receipt, Thread.currentThread().getName());
    }
    private static final class Connection {
        final Object handler;
        final ParticipantUseState state = new ParticipantUseState(UUID.randomUUID());
        final ServerWandBindings wands;
        ReleaseFrame release;
        int traces;
        Connection(EntityPlayerMP player) { handler = player.connection; wands = new ServerWandBindings(player, state.epoch()); }
    }
    private static final class Origin {
        final EntityPlayer player; final ItemStack stack; final EnumHand hand;
        final Connection connection; ParticipantUseState.Use use;
        Origin(EntityPlayer player, ItemStack stack, EnumHand hand, Connection c, ParticipantUseState.Use use) {
            this.player = player; this.stack = stack; this.hand = hand; this.connection = c; this.use = use;
        }
    }
    public static final class ReleaseFrame {
        private final Connection connection; private final ParticipantUseState.Use use;
        private boolean finished, callbackActive;
        private ReleaseFrame(Connection c, ParticipantUseState.Use use) { connection = c; this.use = use; }
    }
    public static final class Scope {
        private final EntityPlayer player; private final Spell spell; private final EnumHand hand;
        private final Origin origin; private String id;
        private CloseCause cause;
        private Scope(EntityPlayer player, Spell spell, EnumHand hand, Origin origin, String id) {
            this.player = player; this.spell = spell; this.hand = hand; this.origin = origin; this.id = id;
        }
        public String id() { return id; }
        public boolean valid() {
            return origin == null || (origin.connection != null && connection(player) == origin.connection
                    && origin.connection.state.matchesOpen(origin.use, hand, spell.getRegistryName(), origin.stack)
                    && origin.connection.wands.valid(hand, origin.stack, origin.use.generation)
                    && player.getHeldItem(hand) == origin.stack);
        }
        public void preparing() { if (spell.isContinuous) cause = CloseCause.FAULT; }
        public boolean permitsLegacy() {
            if (origin == null || origin.use == null || valid()) return true;
            cause = CloseCause.CANCELLED;
            return false;
        }
        CastingDiagnostics.UseSummary diagnostics() {
            if (!CastingDiagnostics.isEnabled() || !spell.isContinuous || origin == null
                    || origin.connection == null || !origin.connection.state.isOpen(origin.use)
                    || !id.equals(run + "/use/" + origin.use.id)) return null;
            if (origin.use.diagnostics == null) origin.use.diagnostics = new CastingDiagnostics.UseSummary();
            return origin.use.diagnostics;
        }

        public void outcome(CastAttemptOutcome outcome) {
            if (!spell.isContinuous) return;
            switch (outcome.getStatus()) {
                case LEGACY: cause = null; break;
                case ACCEPTED: cause = outcome.mustStopAfterSuccess() ? CloseCause.TERMINAL : null; break;
                case REFUSED:
                    cause = outcome.getParticipants().stream().anyMatch(p -> p.getFailure()
                            != com.mahghuuuls.jawms.api.CastParticipantOutcome.Failure.NONE) ? CloseCause.FAULT : CloseCause.REFUSED;
                    break;
                case FALSE_EFFECT: cause = CloseCause.NORMAL_NO_FINISH; break;
                case INVALID_BEFORE_EFFECT: case INVALID_AFTER_EFFECT: cause = CloseCause.INVALIDATED; break;
                default: cause = CloseCause.FAULT;
            }
        }
    }
}
