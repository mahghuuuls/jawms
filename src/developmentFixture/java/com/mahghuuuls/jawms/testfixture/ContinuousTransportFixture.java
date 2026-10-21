package com.mahghuuuls.jawms.testfixture;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.network.play.client.CPacketPlayerTryUseItem;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.network.Packet;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.init.Items;
import electroblob.wizardry.util.WandHelper;
import electroblob.wizardry.registry.WizardryItems;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import com.mahghuuuls.jawms.internal.casting.WandPublicationPair;
import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;
import net.minecraft.server.MinecraftServer;
import electroblob.wizardry.event.SpellCastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.*;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Non-shipping real-channel observer. Never creates a protocol payload or calls a production handler. */
public final class ContinuousTransportFixture {
    static final Logger LOG = LogManager.getLogger("jawms_c007_transport");
    private static final String NAME = "jawms_c007_observer";
    private static final Map<NetworkManager, Hook> HOOKS = new ConcurrentHashMap<>();
    private static final Set<UUID> WITHOUT_CAPABILITY = ConcurrentHashMap.newKeySet();
    private static final AtomicLong ORDER = new AtomicLong();
    private static final Map<UUID, Saved[]> HISTORY = new ConcurrentHashMap<>();
    private static SimpleNetworkWrapper controls;
    private static boolean initialized;
    public enum Mode { NORMAL, GATE, STALE, STALL, PUB_FIRST, PAIR_FIRST, BULK, DIRECT,
        REPLACE, WHEEL, SPELL, UPGRADE, SOURCE_REENTRY, CALLBACK, PAIR_FAULT, LATE, STALE_PAIR, CHANGE }
    private ContinuousTransportFixture() {}

    static void initialize() {
        if (initialized) return;
        initialized = true;
        // FML's bus is an alias of the Forge event bus in 1.12.2.
        MinecraftForge.EVENT_BUS.register(new ContinuousTransportFixture());
        controls = NetworkRegistry.INSTANCE.newSimpleChannel("jawms_c007_control");
        controls.registerMessage(ControlHandler.class, Control.class, 0, Side.CLIENT);
        controls.registerMessage(SignalHandler.class, Signal.class, 1, Side.SERVER);
        com.mahghuuuls.jawms.testfixture.automation.InputPilotHarness.initialize();
        if (FMLCommonHandler.instance().getSide().isClient()) {
            try { Class.forName("com.mahghuuuls.jawms.testfixture.client.ContinuousTransportObserver").getMethod("install").invoke(null); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Client fixture observer unavailable", failure); }
        }
    }
    static void register(FMLServerStartingEvent event) {
        event.registerServerCommand(new TransportCommand());
        com.mahghuuuls.jawms.testfixture.automation.InputPilotHarness.register(event);
    }
    @SubscribeEvent public void effect(SpellCastEvent.Post event) {
        if (event.getCaster().world.isRemote || !(event.getCaster() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getCaster();
        Hook hook = HOOKS.get(player.connection.netManager);
        if (hook != null && hook.mode == Mode.STALE && hook.latestIntent > hook.armAfter && !hook.effectObserved) {
            hook.effectObserved = true;
            controls.sendTo(new Control(7, player.getUniqueID()), player);
            LOG.info("USE_TRANSPORT newer_use_effect intent={} thread={}", hook.latestIntent, Thread.currentThread().getName());
        }
    }
    @SubscribeEvent public void client(FMLNetworkEvent.ClientConnectedToServerEvent event) { install(event.getManager(), Side.CLIENT); }
    @SubscribeEvent public void server(FMLNetworkEvent.ServerConnectionFromClientEvent event) { install(event.getManager(), Side.SERVER); }
    private static void install(NetworkManager manager, Side side) {
        Channel channel = manager.channel();
        channel.eventLoop().execute(() -> {
            if (!channel.isActive()) return;
            ChannelHandler installed = channel.pipeline().get(NAME);
            if (installed == null) {
                installed = new Hook(manager, side);
                channel.pipeline().addBefore("packet_handler", NAME, installed);
            }
            if (!(installed instanceof Hook)) throw new IllegalStateException("Fixture observer name already owned by another handler; stop validation");
            Hook hook = (Hook) installed;
            if (hook.manager != manager || hook.side != side || hook.context == null || hook.context != channel.pipeline().context(hook))
                throw new IllegalStateException("Fixture observer attachment identity mismatch; stop validation");
            // Publish only the actual attached handler; duplicate notifications reuse it.
            HOOKS.put(manager, hook);
            LOG.info("USE_TRANSPORT install side={} pipeline={}", side, channel.pipeline().names());
        });
    }
    public static Hook clientHook(NetworkManager manager) { return HOOKS.get(manager); }
    public static void sourceCallback(EntityPlayerMP player, Container container, int slot, ItemStack live, ItemStack snapshot) {
        Hook hook = HOOKS.get(player.connection.netManager);
        if (hook == null || hook.mode != Mode.SOURCE_REENTRY || !hook.pendingAction || hook.mutated
                || container.getSlot(slot).inventory != player.inventory || player.getHeldItemMainhand() != live) return;
        hook.mutated = true;
        ItemStack replacement = live.copy();
        PublicationFixture.log("copy_to_send_reentry", "equal", ItemStack.areItemStacksEqual(live, replacement), "distinct", live != replacement);
        player.inventory.setInventorySlotContents(container.getSlot(slot).getSlotIndex(), replacement);
    }
    private static Mode mode(int code) {
        return code >= 0 && code <= 3 ? Mode.values()[code]
                : code >= 16 && code < 16 + Mode.values().length ? Mode.values()[code - 16] : null;
    }
    static final class Saved {
        final byte[] bytes;
        Saved(FMLProxyPacket packet) {
            bytes = new byte[packet.payload().readableBytes()];
            packet.payload().getBytes(packet.payload().readerIndex(), bytes);
        }
        FMLProxyPacket packet(Hook hook, boolean incoming) {
            FMLProxyPacket result = new FMLProxyPacket(new PacketBuffer(Unpooled.wrappedBuffer(bytes.clone())), "jawms");
            result.setTarget(incoming ? hook.side : hook.side == Side.CLIENT ? Side.SERVER : Side.CLIENT);
            if (hook.manager != null) result.setDispatcher(NetworkDispatcher.get(hook.manager));
            return result;
        }
    }
    public static final class Hook extends ChannelDuplexHandler {
        final NetworkManager manager;
        final Side side;
        volatile Mode mode = Mode.NORMAL;
        volatile int queued;
        ChannelHandlerContext context;
        Saved held, priorClose, lastClose, lastRelease, previousRelease;
        volatile UUID owner;
        boolean staleDelivered;
        volatile long latestIntent, armAfter;
        volatile boolean effectObserved;
        final List<PublicationHold> publications = new ArrayList<>();
        final Map<Packet<?>, WandPublicationRecord> applying = Collections.synchronizedMap(new IdentityHashMap<>());
        SPacketCustomPayload marker;
        WandPublicationRecord metadata;
        PublicationReadiness readiness = readiness(Mode.NORMAL);
        volatile boolean pendingAction, mutated;
        volatile long signalledIntent;
        long heldIntent, heldSerial;
        Hook(NetworkManager manager, Side side) { this.manager = manager; this.side = side; }
        @Override public void handlerAdded(ChannelHandlerContext context) { this.context = context; }
        void configure(Mode mode, UUID owner) {
            context.executor().execute(() -> {
                if (held != null || marker != null || !publications.isEmpty()) throw new IllegalStateException("Cannot reconfigure with held frames; drain/reset explicitly");
                this.mode = mode; this.owner = owner; staleDelivered = false; effectObserved = false; armAfter = latestIntent;
                pendingAction = mutated = false; signalledIntent = heldIntent = heldSerial = 0;
                readiness = readiness(mode);
                Saved[] saved = side == Side.CLIENT ? HISTORY.get(owner) : null;
                if (saved != null && lastClose == null) { lastClose = saved[0]; lastRelease = saved[1]; }
                LOG.info("USE_TRANSPORT armed mode={} side={} order={}", mode, side, ORDER.incrementAndGet());
            });
        }
        @Override public void channelRead(ChannelHandlerContext context, Object message) throws Exception {
            if (side == Side.CLIENT && PublicationFixture.enabled() && owner != null) {
                if (message instanceof SPacketCustomPayload && WandPublicationPair.CHANNEL.equals(PublicationFixture.channel((SPacketCustomPayload) message))) {
                    SPacketCustomPayload incoming = (SPacketCustomPayload) message;
                    WandPublicationRecord record = WandPublicationRecord.read(PublicationFixture.payload(incoming).duplicate());
                    PublicationFixture.log("marker_receive", "serial", record.serial, "kind", record.kind, "epoch", record.epoch);
                    if (record.kind == WandPublicationRecord.RETIRE) {
                        context.fireChannelRead(message);
                        if (EnumSet.of(Mode.REPLACE, Mode.SPELL, Mode.UPGRADE, Mode.SOURCE_REENTRY).contains(mode)) {
                            readiness.publication(record); releaseIfReady();
                        }
                        return;
                    }
                    if (marker != null) throw new IllegalStateException("Observer encountered orphan metadata; card unreached");
                    marker = incoming; metadata = record;
                    return;
                }
                if (marker != null) {
                    SPacketCustomPayload savedMarker = marker; WandPublicationRecord savedMetadata = metadata;
                    marker = null; metadata = null;
                    if (!(message instanceof SPacketSetSlot || message instanceof SPacketWindowItems)) {
                        PublicationFixture.log("observer_bad_pair", "verdict", "UNREACHED");
                        context.fireChannelRead(savedMarker); context.fireChannelRead(message); return;
                    }
                    Packet<?> packet = (Packet<?>) message;
                    PublicationFixture.packet("paired_decode", packet);
                    boolean hold = mode == Mode.PAIR_FIRST || mode == Mode.LATE
                            || mode == Mode.STALE_PAIR && (publications.isEmpty() || latestIntent <= heldIntent);
                    if (hold) {
                        if (publications.size() == 256) throw new IllegalStateException("Observer pair queue full; card unreached");
                        publications.add(new PublicationHold(savedMarker, packet, savedMetadata));
                        if (heldIntent == 0) { heldIntent = savedMetadata.entries()[0].intent; heldSerial = savedMetadata.serial; }
                        queued = (held == null ? 0 : 1) + publications.size(); return;
                    }
                    if (mode == Mode.PAIR_FAULT && signalledIntent > 0 && !mutated) {
                        mutated = true; context.fireChannelRead(savedMarker);
                        PublicationFixture.log("actual_partner_suppressed", "serial", savedMetadata.serial);
                        return; // Only P11: next real unrelated server packet detects this missing partner.
                    }
                    associate(packet, savedMetadata);
                    context.fireChannelRead(savedMarker); context.fireChannelRead(packet); return;
                }
            }
            if (message instanceof CPacketPlayerTryUseItem || message instanceof CPacketPlayerDigging
                    && ((CPacketPlayerDigging) message).getAction() == CPacketPlayerDigging.Action.RELEASE_USE_ITEM)
                log("vanilla_receive", -1);
            if (message instanceof FMLProxyPacket) {
                FMLProxyPacket packet = (FMLProxyPacket) message;
                int kind = kind(packet);
                if (kind >= 4) log("frame_receive", kind);
                if (kind == 5 && packet.payload().readableBytes() >= 26)
                    latestIntent = packet.payload().getLong(packet.payload().readerIndex() + 18);
                if (kind == 7 && side == Side.CLIENT) {
                    priorClose = lastClose; lastClose = new Saved(packet);
                    remember();
                    if (holdsClose()) {
                        if (held != null) throw new IllegalStateException("Unexpected second close while fixture holds first");
                        held = new Saved(packet); packet.payload().release();
                        io.netty.buffer.ByteBuf closeData = Unpooled.wrappedBuffer(held.bytes, 1, held.bytes.length - 1);
                        try { com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages.Close actualClose = new com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages.Close();
                            actualClose.fromBytes(closeData); readiness.close(actualClose);
                        } finally { closeData.release(); }
                        queued = 1 + publications.size(); log("close_held", kind); releaseIfReady(); return;
                    }
                }
                context.fireChannelRead(message);
                if (kind == 7 && mode == Mode.PAIR_FIRST) afterMain(() -> context.executor().execute(this::drainPublications));
                if (kind >= 4) log("frame_dispatched", kind);
                if (kind == 7 && mode == Mode.STALE && priorClose != null && !staleDelivered) replayStale();
                return;
            }
            context.fireChannelRead(message);
        }
        @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
            if (owner != null && message instanceof SPacketCustomPayload && WandPublicationPair.CHANNEL.equals(PublicationFixture.channel((SPacketCustomPayload) message))) {
                WandPublicationRecord record = WandPublicationRecord.read(PublicationFixture.payload((SPacketCustomPayload) message).duplicate());
                PublicationFixture.log("marker_send", "epoch", record.epoch, "serial", record.serial, "kind", record.kind,
                        "generation", record.entries()[0].generation, "intent", record.entries()[0].intent, "use", record.entries()[0].use);
            }
            if (owner != null && (message instanceof SPacketSetSlot || message instanceof SPacketWindowItems)) PublicationFixture.packet("vanilla_send", message);
            if (message instanceof FMLProxyPacket) {
                FMLProxyPacket packet = (FMLProxyPacket) message;
                int kind = kind(packet);
                if (kind >= 4) log("frame_send", kind);
                if (kind == 6 && side == Side.CLIENT) { previousRelease = lastRelease; lastRelease = new Saved(packet); remember(); }
                if (kind == 5 && packet.payload().readableBytes() >= 26)
                    latestIntent = packet.payload().getLong(packet.payload().readerIndex() + 18);
                if (kind == 4 && side == Side.SERVER && manager != null && manager.getNetHandler() instanceof NetHandlerPlayServer
                        && WITHOUT_CAPABILITY.contains(((NetHandlerPlayServer) manager.getNetHandler()).player.getUniqueID())) {
                    log("capability_withheld", kind); packet.payload().release(); promise.setSuccess(); return;
                }
            }
            context.write(message, promise);
        }
        public void blockedGestureObserved(UUID epoch, long intent) {
            context.executor().execute(() -> {
                readiness.blocked(epoch, intent);
                releaseIfReady();
            });
        }
        void replayStale() {
            staleDelivered = true;
            deliver(priorClose, true);
            Saved release = lastRelease;
            if (release != null && sequence(release) >= sequence(lastClose)) release = previousRelease;
            if (release != null) deliver(release, false);
            log("old_close_and_release_replayed_after_new_close", 7);
        }
        void replayAfterEffect() {
            context.executor().execute(() -> {
                if (mode != Mode.STALE || lastClose == null) return;
                deliver(lastClose, true);
                if (lastRelease != null) deliver(lastRelease, false);
                log("old_frames_replayed_after_real_newer_effect", 7);
            });
        }
        private static long sequence(Saved saved) {
            return saved == null || saved.bytes.length < 26 ? 0 : java.nio.ByteBuffer.wrap(saved.bytes, 18, 8).getLong();
        }
        private void remember() {
            if (side == Side.CLIENT && owner != null) {
                HISTORY.keySet().removeIf(id -> !id.equals(owner)); // One named local operator; byte snapshots only.
                HISTORY.put(owner, new Saved[] { lastClose, lastRelease });
            }
        }
        public int queued() { return queued; }
        public Mode mode() { return mode; }
        private boolean holdsClose() {
            return mode != Mode.NORMAL && mode != Mode.STALE && mode != Mode.PAIR_FIRST
                    && mode != Mode.LATE && mode != Mode.STALE_PAIR;
        }
        private void afterMain(Runnable action) {
            FMLCommonHandler.instance().getWorldThread(manager.getNetHandler()).addScheduledTask(action);
        }
        public void pendingObserved(UUID epoch, long intent, net.minecraft.util.EnumHand hand, int slot) {
            if (intent == signalledIntent || owner == null) return;
            signalledIntent = intent;
            context.executor().execute(() -> {
                readiness.pending(epoch, intent, hand, slot);
                controls.sendToServer(new Signal(owner, intent));
                PublicationFixture.log("real_pending_capture", "epoch", epoch, "intent", intent, "slot", slot, "mode", mode);
            });
        }
        public void contextMutationObserved(UUID epoch, long intent) {
            context.executor().execute(() -> { readiness.context(epoch, intent); releaseIfReady(); });
        }
        public void newerBound(long intent) {
            context.executor().execute(() -> {
                if (mode == Mode.LATE && heldIntent > 0 && intent > heldIntent) drainPublications();
            });
        }
        public void publicationApplied(WandPublicationRecord record, boolean wasPending) {
            context.executor().execute(() -> {
                // The client observer has independently confirmed the decoded stack is the installed object.
                if (wasPending) readiness.publication(record);
                PublicationFixture.log("actual_paired_install", "serial", record.serial, "intent", record.entries()[0].intent, "pending", wasPending);
                if (mode == Mode.STALE_PAIR && heldIntent > 0 && latestIntent > heldIntent && record.serial > heldSerial) drainPublications();
                releaseIfReady();
            });
        }
        private void releaseIfReady() {
            if (!readiness.ready() || held == null) return;
            Saved close = held; held = null; queued = publications.size();
            deliver(close, true);
            if (mode == Mode.GATE) { deliver(close, true); log("held_close_and_duplicate_delivered_after_real_blocked_press", 7); }
            PublicationFixture.log("real_close_after_boundary", "mode", mode);
        }
        private void drainPublications() {
            Mode deliveryMode = mode;
            List<PublicationHold> heldPairs = new ArrayList<>(publications); publications.clear();
            for (PublicationHold pair : heldPairs) {
                if (deliveryMode != Mode.STALE_PAIR) associate(pair.packet, pair.record);
                context.fireChannelRead(pair.marker); context.fireChannelRead(pair.packet);
                PublicationFixture.log("real_pair_released", "serial", pair.record.serial);
            }
            if (deliveryMode == Mode.PAIR_FIRST || deliveryMode == Mode.LATE || deliveryMode == Mode.STALE_PAIR) mode = Mode.NORMAL;
            queued = held == null ? 0 : 1;
        }
        public WandPublicationRecord application(Packet<?> packet) {
            // Identity map is synchronized: decoding and application happen on different threads.
            return applying.remove(packet);
        }
        private void associate(Packet<?> packet, WandPublicationRecord record) {
            if (applying.size() >= 256) PublicationFixture.log("observer_application_capacity", "capacity", 256, "verdict", "UNREACHED");
            else applying.put(packet, record);
        }
        private static PublicationReadiness readiness(Mode mode) {
            PublicationReadiness.Boundary boundary;
            switch (mode) {
                case GATE: boundary = PublicationReadiness.Boundary.BLOCKED_ONLY; break;
                case PUB_FIRST: case CALLBACK: boundary = PublicationReadiness.Boundary.SLOT; break;
                case BULK: boundary = PublicationReadiness.Boundary.WINDOW; break;
                case DIRECT: boundary = PublicationReadiness.Boundary.DIRECT; break;
                case REPLACE: case SPELL: case UPGRADE: case SOURCE_REENTRY: boundary = PublicationReadiness.Boundary.RETIREMENT; break;
                case WHEEL: case CHANGE: case PAIR_FAULT: boundary = PublicationReadiness.Boundary.CONTEXT; break;
                default: boundary = PublicationReadiness.Boundary.NONE;
            }
            return new PublicationReadiness(boundary, mode == Mode.GATE || mode == Mode.PUB_FIRST || mode == Mode.BULK || mode == Mode.DIRECT);
        }
        void deliver(Saved saved, boolean incoming) {
            FMLProxyPacket packet = saved.packet(this, incoming);
            if (incoming) {
                // The real Forge codec consumes this reference. Saved history owns bytes, not a retained buffer.
                context.fireChannelRead(packet);
            } else {
                boolean local = context.channel() instanceof io.netty.channel.local.LocalChannel;
                context.writeAndFlush(packet).addListener(future -> {
                    // Local packet transport transfers the nested buffer to peer decoding; promise completion
                    // precedes that decode. Remote encoding copied bytes, so the sender still owns its source.
                    if (!local || !future.isSuccess()) packet.payload().release();
                });
            }
        }
        public void inspect() {
            LOG.info("USE_TRANSPORT inspect side={} mode={} queued={} priorClose={} lastClose={} lastRelease={} latestIntent={} order={}",
                    side, mode, queued, priorClose != null, lastClose != null, lastRelease != null, latestIntent, ORDER.incrementAndGet());
        }
        public void reset() {
            context.executor().execute(() -> {
                // Deliver an actual held close before disarming; reset never calls a guard or invents permission.
                Saved frame = held; held = null; queued = 0; mode = Mode.NORMAL;
                if (frame != null) deliver(frame, true);
                drainPublications(); applying.clear();
                if (marker != null) { context.fireChannelRead(marker); marker = null; metadata = null; }
                priorClose = lastClose = lastRelease = previousRelease = null; staleDelivered = false;
                if (owner != null) HISTORY.remove(owner);
                if (owner != null) PublicationFixture.reset(owner, side == Side.CLIENT);
                inspect();
            });
        }
        @Override public void channelInactive(ChannelHandlerContext context) throws Exception {
            retire();
            context.fireChannelInactive();
        }
        @Override public void handlerRemoved(ChannelHandlerContext context) { retire(); }
        private void retire() {
            if (manager != null) HOOKS.remove(manager, this);
            held = priorClose = lastClose = lastRelease = previousRelease = null; queued = 0;
            for (PublicationHold pair : publications) PublicationFixture.payload(pair.marker).release(); publications.clear(); applying.clear();
            if (marker != null) PublicationFixture.payload(marker).release(); marker = null; metadata = null;
            if (owner != null) PublicationFixture.reset(owner, side == Side.CLIENT);
        }
        private static int kind(FMLProxyPacket packet) {
            if (!"jawms".equals(packet.channel()) || !packet.payload().isReadable() || packet.payload().readableBytes() > 512) return -1;
            int kind = packet.payload().getUnsignedByte(packet.payload().readerIndex());
            return kind >= 4 && kind <= 7 ? kind : -1;
        }
        private void log(String event, int kind) {
            LOG.info("USE_TRANSPORT event={} side={} kind={} order={} thread={}", event, side, kind, ORDER.incrementAndGet(), Thread.currentThread().getName());
        }
    }
    static final class PublicationHold {
        final SPacketCustomPayload marker; final Packet<?> packet; final WandPublicationRecord record;
        PublicationHold(SPacketCustomPayload marker, Packet<?> packet, WandPublicationRecord record) {
            this.marker = marker; this.packet = packet; this.record = record;
        }
    }
    public static final class TransportCommand extends CommandBase {
        @Override public String getName() { return "jawms-use-transport"; }
        @Override public String getUsage(ICommandSender sender) { return "/jawms-use-transport <mode>|capability-off|inspect|reset"; }
        @Override public int getRequiredPermissionLevel() { return 4; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP) || args.length != 1) throw new CommandException(getUsage(sender));
            EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
            String action = args[0].toLowerCase(Locale.ROOT);
            int code;
            if ("capability-off".equals(action)) { WITHOUT_CAPABILITY.add(player.getUniqueID()); code = 4; }
            else if ("inspect".equals(action)) code = 5;
            else if ("reset".equals(action)) { WITHOUT_CAPABILITY.remove(player.getUniqueID()); code = 6; }
            else try { int ordinal = Mode.valueOf(action.toUpperCase(Locale.ROOT).replace('-', '_')).ordinal(); code = ordinal <= 3 ? ordinal : 16 + ordinal; }
            catch (IllegalArgumentException invalid) { throw new CommandException(getUsage(sender)); }
            Hook serverHook = HOOKS.get(player.connection.netManager);
            if (serverHook == null) throw new CommandException("Real channel observer missing; stop validation");
            if (mode(code) != null) {
                if (code >= 16 && !PublicationFixture.enabled()) throw new CommandException("Publication observer must be explicitly enabled with its development Mixin configuration");
                PublicationFixture.reset(player.getUniqueID(), false); PublicationFixture.watch(player);
                if (mode(code) == Mode.DIRECT) {
                    for (int slot = 0; slot < 9; slot++) if (slot != player.inventory.currentItem)
                        player.inventory.setInventorySlotContents(slot, new ItemStack(Items.STICK));
                }
                serverHook.configure(mode(code), player.getUniqueID());
            }
            if (code == 6) serverHook.reset();
            serverHook.inspect(); controls.sendTo(new Control(code, player.getUniqueID()), player);
        }
    }
    public static final class Control implements IMessage {
        int code;
        UUID owner;
        public Control() {}
        Control(int code, UUID owner) { this.code = code; this.owner = owner; }
        public void toBytes(ByteBuf out) { out.writeByte(code); out.writeLong(owner.getMostSignificantBits()); out.writeLong(owner.getLeastSignificantBits()); }
        public void fromBytes(ByteBuf in) { code = in.readUnsignedByte(); owner = new UUID(in.readLong(), in.readLong()); }
    }
    public static final class ControlHandler implements IMessageHandler<Control, IMessage> {
        public IMessage onMessage(Control message, MessageContext context) {
            if (message.code < 0 || message.code > 7 && mode(message.code) == null) return null;
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                for (Hook hook : HOOKS.values()) if (hook.side == Side.CLIENT && hook.manager.getNetHandler() == context.netHandler) {
                    if (mode(message.code) != null) {
                        com.mahghuuuls.jawms.testfixture.client.ContinuousTransportObserver.rearm();
                        hook.configure(mode(message.code), message.owner);
                    }
                    else if (message.code == 7) hook.replayAfterEffect();
                    else if (message.code == 6) {
                        com.mahghuuuls.jawms.testfixture.client.ContinuousTransportObserver.rearm(); hook.reset();
                    } else hook.inspect();
                }
            });
            return null;
        }
    }
    public static final class Signal implements IMessage {
        UUID owner; long intent;
        public Signal() {}
        Signal(UUID owner, long intent) { this.owner = owner; this.intent = intent; }
        public void toBytes(ByteBuf out) { out.writeLong(owner.getMostSignificantBits()).writeLong(owner.getLeastSignificantBits()).writeLong(intent); }
        public void fromBytes(ByteBuf in) { owner = new UUID(in.readLong(), in.readLong()); intent = in.readLong(); }
    }
    public static final class SignalHandler implements IMessageHandler<Signal, IMessage> {
        public IMessage onMessage(Signal message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Hook hook = HOOKS.get(player.connection.netManager);
                if (!PublicationFixture.enabled() || hook == null || !player.getUniqueID().equals(message.owner)
                        || !message.owner.equals(hook.owner) || hook.latestIntent != message.intent || hook.pendingAction) return;
                hook.pendingAction = true;
                ItemStack original = player.getHeldItemMainhand();
                if (original.isEmpty()) original = player.getHeldItemOffhand();
                PublicationFixture.log("pending_signal_server", "mode", hook.mode, "intent", message.intent);
                if (hook.mode == Mode.BULK) player.sendAllContents(player.inventoryContainer, player.inventoryContainer.getInventory());
                else if (hook.mode == Mode.DIRECT) {
                    PacketBuffer data = new PacketBuffer(Unpooled.buffer()); data.writeString("MC|PickItem"); data.writeVarInt(player.inventory.currentItem);
                    try {
                        CPacketCustomPayload packet = new CPacketCustomPayload();
                        packet.readPacketData(data); packet.processPacket(player.connection);
                    } catch (java.io.IOException malformed) { throw new IllegalStateException("Fixture direct-origin packet failed common decoder", malformed); }
                    finally { data.release(); }
                } else if (hook.mode == Mode.REPLACE) {
                    player.inventoryContainer.detectAndSendChanges();
                    ItemStack copy = original.copy();
                    PublicationFixture.log("equal_replacement", "distinct", copy != original, "equal", ItemStack.areItemStacksEqual(copy, original));
                    player.setHeldItem(player.getHeldItemMainhand() == original ? net.minecraft.util.EnumHand.MAIN_HAND : net.minecraft.util.EnumHand.OFF_HAND, copy);
                    player.inventoryContainer.detectAndSendChanges();
                } else if (hook.mode == Mode.SPELL) WandHelper.selectSpell(original, 1);
                else if (hook.mode == Mode.UPGRADE) WandHelper.applyUpgrade(original, WizardryItems.cooldown_upgrade);
                else if (EnumSet.of(Mode.PUB_FIRST, Mode.SOURCE_REENTRY, Mode.CALLBACK, Mode.PAIR_FAULT).contains(hook.mode)) {
                    WandHelper.setCurrentCooldown(original, WandHelper.getCurrentCooldown(original) + 1);
                    player.inventoryContainer.detectAndSendChanges();
                    if (hook.mode == Mode.PAIR_FAULT) player.connection.sendPacket(new SPacketHeldItemChange(player.inventory.currentItem));
                }
            });
            return null;
        }
    }
}
