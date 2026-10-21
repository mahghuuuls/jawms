package com.mahghuuuls.jawms.testfixture.automation;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import net.minecraft.command.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;

/** Operator-bound, non-shipping orchestration. No product state or payment is reset here. */
public final class InputPilotHarness {
    private static final Logger LOG = LogManager.getLogger("jawms_mv02_pilot");
    private static final Gson JSON = new Gson();
    private static final Map<UUID, Session> SESSIONS = new java.util.concurrent.ConcurrentHashMap<>();
    private static SimpleNetworkWrapper channel;
    private InputPilotHarness() {}
    public static boolean enabled() { return Boolean.getBoolean("jawms.enableInputPilot"); }
    public static void initialize() {
        if (!enabled()) return;
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("jawms_mv02_pilot");
        channel.registerMessage(ClientHandler.class, Message.class, 0, Side.CLIENT);
        channel.registerMessage(ServerHandler.class, Message.class, 1, Side.SERVER);
        MinecraftForge.EVENT_BUS.register(new InputPilotHarness());
        if (FMLCommonHandler.instance().getSide().isClient()) invokeClient("install", new Class<?>[0]);
    }
    public static void register(FMLServerStartingEvent event) { event.registerServerCommand(new PilotCommand()); }
    private static Object invokeClient(String name, Class<?>[] signature, Object... args) {
        try { return Class.forName("com.mahghuuuls.jawms.testfixture.client.InputPilotDriver").getMethod(name, signature).invoke(null, args); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Development input pilot unavailable", failure); }
    }
    @SuppressWarnings("unchecked") public static Map<String, String> snapshot(EntityPlayer player) {
        try {
            return (Map<String, String>) Class.forName("example.jawmscast.ContinuousCastFixture")
                    .getMethod("snapshot", EntityPlayer.class).invoke(null, player);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Independent fixture snapshot unavailable", failure); }
    }
    public static void record(UUID run, String event, Map<String, String> data) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("run", run.toString()); record.put("event", event); record.put("data", data);
        LOG.info("MV02_PILOT {}", JSON.toJson(record));
    }
    public static void request(UUID run, int sequence, boolean end) {
        if (enabled() && channel != null) channel.sendToServer(new Message(end ? 4 : 3, run, sequence, "", "", "", Collections.emptyMap()));
    }
    public static void preflightReady(UUID run) {
        if (enabled() && channel != null) channel.sendToServer(new Message(5, run, 1, "", "", "", Collections.emptyMap()));
    }
    public static boolean disposable(String name) { return name != null && name.startsWith("JAWMS-C007-MV02-") && name.length() <= 96; }
    private static final class Session {
        final EntityPlayerMP player;
        final Object connection, world;
        final UUID run;
        final String kind, hand, level;
        final long started = System.nanoTime();
        int last;
        boolean prepared;
        Session(EntityPlayerMP player, UUID run, String kind, String hand, String level) {
            this.player = player; this.connection = player.connection; this.world = player.world;
            this.run = run; this.kind = kind; this.hand = hand; this.level = level;
        }
        boolean valid(EntityPlayerMP current) {
            return current == player && current.connection == connection && current.world == world
                    && current.canUseCommand(4, "jawms-mv02-pilot") && System.nanoTime() - started < PilotSequence.LIMIT_NANOS;
        }
        Message message(int code, int sequence) {
            Map<String, String> values = new LinkedHashMap<>(); values.put("player", player.getUniqueID().toString());
            if (prepared && code <= 1) values.putAll(snapshot(player));
            values.put("remainingNanos", Long.toString(Math.max(0, PilotSequence.LIMIT_NANOS - (System.nanoTime() - started))));
            return new Message(code, run, sequence, kind, hand, level, values);
        }
    }
    public static final class PilotCommand extends CommandBase {
        @Override public String getName() { return "jawms-mv02-pilot"; }
        @Override public int getRequiredPermissionLevel() { return 4; }
        @Override public String getUsage(ICommandSender sender) { return "/jawms-mv02-pilot start <normal|terminal|delayed-normal|delayed-terminal> <main|off> <exact-disposable-level> | abort"; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (!enabled() || channel == null || !(sender.getCommandSenderEntity() instanceof EntityPlayerMP))
                throw new CommandException("Explicit development input pilot and player operator required");
            EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
            if (!player.canUseCommand(4, getName())) throw new CommandException("Level4 player authorization required");
            if (args.length == 1 && "abort".equals(args[0])) {
                Session old = SESSIONS.remove(player.getUniqueID());
                if (old != null) { channel.sendTo(old.message(2, old.last), player); record(old.run, "ABORT", Collections.singletonMap("reason", "operator")); }
                return;
            }
            if (args.length != 4 || !"start".equals(args[0]) || !Arrays.asList("main", "off").contains(args[2])) throw new CommandException(getUsage(sender));
            PilotSequence.Kind kind;
            try { kind = PilotSequence.Kind.valueOf(args[1].toUpperCase(Locale.ROOT).replace('-', '_')); }
            catch (IllegalArgumentException failure) { throw new CommandException(getUsage(sender)); }
            String level = player.world.getWorldInfo().getWorldName();
            if (!disposable(level) || !level.equals(args[3]) || player.isHandActive() || SESSIONS.containsKey(player.getUniqueID())
                    || !com.mahghuuuls.jawms.testfixture.observation.PublicationFixture.enabled()
                    || Boolean.getBoolean("jawms.castFixtureRegistrationOff")) throw new CommandException("Exact disposable world, released input and no active pilot required");
            // Never clear a pending production guard to make setup succeed. The client must independently be idle before pressing.
            UUID run = UUID.randomUUID();
            Session session = new Session(player, run, kind.name(), args[2], level);
            SESSIONS.put(player.getUniqueID(), session);
            record(run, "PREFLIGHT_REQUEST", Collections.singletonMap("level", level));
            channel.sendTo(session.message(6, 0), player);
        }
    }
    private static void prepare(Session session) throws CommandException {
            EntityPlayerMP player = session.player;
            MinecraftServer server = player.getServer();
            PilotSequence.Kind kind = PilotSequence.Kind.valueOf(session.kind);
            UUID run = session.run;
            String level = session.level;
            record(run, "SETUP_BEGIN", Collections.singletonMap("level", level));
            String transport = kind.delayed ? kind.terminal ? "gate" : "pub-first" : "normal";
            for (String command : Arrays.asList("jawms-test regen pause true", "jawms-test casting debug true",
                    "jawms-use-transport " + transport, "jawms-continuous-fixture setup " + (kind.terminal ? "terminal" : "normal") + " " + session.hand)) {
                if (server.getCommandManager().executeCommand(player, command) != 1) {
                    record(run, "UNREACHED", Collections.singletonMap("command", command));
                    throw new CommandException("Pilot setup command failed; preserve evidence, do not retry automatically");
                }
            }
            electroblob.wizardry.data.WizardData discovery = electroblob.wizardry.data.WizardData.get(player);
            electroblob.wizardry.spell.Spell spell = electroblob.wizardry.spell.Spell.get("jawms_cast_fixture:stream");
            if (discovery == null || spell == null) throw new CommandException("Fixture discovery state missing");
            if (!discovery.hasSpellBeenDiscovered(spell)) {
                String command = electroblob.wizardry.Wizardry.settings.discoverspellCommandName + " jawms_cast_fixture:stream " + player.getName();
                if (server.getCommandManager().executeCommand(player, command) != 1 || !discovery.hasSpellBeenDiscovered(spell))
                    throw new CommandException("Fixture spell discovery did not reach readiness");
            }
            Map<String, String> baseline = snapshot(player);
            for (String key : Arrays.asList("effects", "paid", "claimed", "prepare", "settle", "resolved", "finishEvents", "finishBodies"))
                if (!"0".equals(baseline.get(key))) throw new CommandException("Independent zero baseline missing: " + key);
            if (!"100".equals(baseline.get("mana"))) throw new CommandException("Expected baseline mana100");
            if (!session.valid(player)) throw new CommandException("Original pilot authorization expired during setup");
            session.prepared = true;
            record(run, "SERVER_READY", baseline); channel.sendTo(session.message(0, 0), player);
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ServerDisconnectionFromClientEvent event) {
        SESSIONS.values().removeIf(s -> s.player.connection.netManager == event.getManager());
    }
    @SubscribeEvent public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) SESSIONS.values().removeIf(s -> {
            if (s.valid(s.player)) return false;
            record(s.run, "UNREACHED", Collections.singletonMap("reason", "server_scope_or_deadline"));
            if (s.player.connection == s.connection) channel.sendTo(s.message(2, s.last), s.player);
            return true;
        });
    }
    public static final class Message implements IMessage {
        public int code, sequence;
        public UUID run;
        public String kind, hand, level;
        public Map<String, String> values;
        public Message() {}
        public Message(int code, UUID run, int sequence, String kind, String hand, String level, Map<String, String> values) {
            this.code = code; this.run = run; this.sequence = sequence; this.kind = kind; this.hand = hand; this.level = level;
            this.values = new LinkedHashMap<>(values);
        }
        @Override public void toBytes(ByteBuf out) {
            PacketBuffer b = new PacketBuffer(out); b.writeByte(code); b.writeLong(run.getMostSignificantBits()); b.writeLong(run.getLeastSignificantBits());
            b.writeInt(sequence); b.writeString(kind); b.writeString(hand); b.writeString(level);
            if (values.size() > 32) throw new IllegalArgumentException("Unbounded pilot snapshot");
            b.writeByte(values.size()); values.forEach((k, v) -> { b.writeString(k); b.writeString(v); });
        }
        @Override public void fromBytes(ByteBuf in) {
            PacketBuffer b = new PacketBuffer(in); code = b.readUnsignedByte(); run = new UUID(b.readLong(), b.readLong()); sequence = b.readInt();
            kind = b.readString(32); hand = b.readString(8); level = b.readString(96);
            int size = b.readUnsignedByte(); if (size > 32 || sequence < 0 || code > 6) throw new IllegalArgumentException("Invalid pilot frame");
            values = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) if (values.put(b.readString(32), b.readString(96)) != null) throw new IllegalArgumentException("Duplicate snapshot key");
            if (b.isReadable()) throw new IllegalArgumentException("Trailing pilot frame");
        }
    }
    public static final class ClientHandler implements IMessageHandler<Message, IMessage> {
        @Override public IMessage onMessage(Message message, MessageContext context) {
            if (enabled() && (message.code <= 2 || message.code == 6)) FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() ->
                    invokeClient("receive", new Class<?>[] { Message.class, Object.class }, message, context.netHandler));
            return null;
        }
    }
    public static final class ServerHandler implements IMessageHandler<Message, IMessage> {
        @Override public IMessage onMessage(Message message, MessageContext context) {
            if (!enabled() || message.code < 3 || message.code > 5) return null;
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                Session session = SESSIONS.get(player.getUniqueID());
                if (session == null || !session.run.equals(message.run) || !session.valid(player) || message.sequence <= session.last) return;
                session.last = message.sequence;
                if (message.code == 5) {
                    if (session.prepared) return;
                    try { prepare(session); }
                    catch (RuntimeException | CommandException failed) {
                        record(session.run, "UNREACHED", Collections.singletonMap("reason", "setup_failed_" + failed.getClass().getSimpleName()));
                        channel.sendTo(session.message(2, session.last), player); SESSIONS.remove(player.getUniqueID());
                    }
                    return;
                }
                if (!session.prepared) {
                    if (message.code == 4) SESSIONS.remove(player.getUniqueID());
                    return;
                }
                Message sample = session.message(1, message.sequence);
                Map<String, String> recorded = new LinkedHashMap<>(sample.values); recorded.put("sequence", Integer.toString(message.sequence));
                record(session.run, "SERVER_SAMPLE", recorded);
                if (message.code == 4) {
                    record(session.run, "SERVER_END", Collections.singletonMap("sequence", Integer.toString(message.sequence)));
                    SESSIONS.remove(player.getUniqueID());
                }
                else channel.sendTo(sample, player);
            });
            return null;
        }
    }
}
