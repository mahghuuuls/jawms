package com.mahghuuuls.jawms.testfixture.client;

import com.mahghuuuls.jawms.internal.casting.ClientUseState;
import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import com.mahghuuuls.jawms.testfixture.automation.InputPilotHarness;
import com.mahghuuuls.jawms.testfixture.automation.PilotSequence;
import com.mahghuuuls.jawms.testfixture.automation.PilotLease;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import java.util.*;

/** Supplies only an explicitly armed use key; vanilla owns clicks, packets and release. */
public final class InputPilotDriver {
    private static InputPilotDriver active;
    private static Preflight preflight;
    private static boolean installed;
    private final InputPilotHarness.Message start;
    private final Object connection, world, player;
    private final PilotSequence sequence;
    private final PilotLease lease;
    private final Map<Integer, Long> probes = new LinkedHashMap<>();
    private final EnumHand hand;
    private Map<String, String> server;
    private int sample, request, ticks, controllerCalls, releaseCalls, clientSamples;
    private boolean key, ownsKey, inputHook, began;
    private PilotSequence.Phase lastPhase;
    private InputPilotDriver(InputPilotHarness.Message message, long started) {
        Minecraft mc = Minecraft.getMinecraft(); start = message;
        connection = mc.getConnection(); world = mc.world; player = mc.player;
        hand = "off".equals(message.hand) ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
        server = message.values;
        sequence = new PilotSequence(PilotSequence.Kind.valueOf(message.kind), started);
        lease = new PilotLease(started); request = 1;
    }
    public static void install() {
        if (!installed && InputPilotHarness.enabled()) { installed = true; MinecraftForge.EVENT_BUS.register(new InputPilotDriverListener()); }
    }
    public static void receive(InputPilotHarness.Message message, Object handler) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!InputPilotHarness.enabled() || handler != mc.getConnection() || mc.player == null) return;
        if (message.code == 6) {
            if (active != null || preflight != null || !InputPilotHarness.disposable(message.level)
                    || !mc.player.getUniqueID().toString().equals(message.values.get("player"))) return;
            preflight = new Preflight(message); return;
        }
        if (message.code == 2 && preflight != null && preflight.message.run.equals(message.run)) { preflight.abort("server_abort"); return; }
        if (message.code == 0) {
            if (active != null) { active.abort("overlapping_run"); return; }
            if (preflight == null || !preflight.ready || !preflight.message.run.equals(message.run) || !preflight.scope(mc)
                    || !preflight.message.kind.equals(message.kind) || !preflight.message.hand.equals(message.hand)
                    || !preflight.message.level.equals(message.level) || System.nanoTime() - preflight.started >= 10_000_000_000L) return;
            if (!InputPilotHarness.disposable(message.level) || !mc.player.getUniqueID().toString().equals(message.values.get("player"))) return;
            if (mc.isSingleplayer() && !message.level.equals(mc.world.getWorldInfo().getWorldName())) return;
            active = new InputPilotDriver(message, preflight.started); preflight = null;
            Map<String, String> armed = metadata(message);
            armed.put("baselineIntent", Long.toString(ContinuousTransportObserver.observedState().intent()));
            armed.put("baselineEpoch", String.valueOf(ContinuousTransportObserver.observedState().epoch()));
            InputPilotHarness.record(message.run, "CLIENT_ARMED", armed);
        } else if (active != null && active.start.run.equals(message.run)) {
            if (message.code == 2) active.abort("server_abort");
            else if (message.sequence > active.sample) {
                Long sent = active.probes.remove(message.sequence);
                if (sent == null || !message.kind.equals(active.start.kind) || !message.hand.equals(active.start.hand)
                        || !message.level.equals(active.start.level) || !message.values.get("player").equals(active.start.values.get("player"))
                        || !active.lease.acknowledge(sent, System.nanoTime(), Long.parseLong(message.values.get("remainingNanos")))) {
                    active.abort("stale_or_unmatched_live_ack"); return;
                }
                active.probes.keySet().removeIf(n -> n <= message.sequence);
                active.sample = message.sequence; active.server = message.values;
                InputPilotHarness.record(message.run, "LIVE_ACK", Collections.singletonMap("sequence", Integer.toString(message.sequence)));
            }
        }
    }
    private static Map<String, String> metadata(InputPilotHarness.Message message) {
        Map<String, String> data = new LinkedHashMap<>(); data.put("kind", message.kind); data.put("hand", message.hand);
        data.put("level", message.level); data.put("player", message.values.get("player")); return data;
    }
    /** Called at vanilla processKeyBinds HEAD, after native event polling. Never calls a controller. */
    public static void beforeKeys() {
        if (active == null) return;
        active.inputHook = true;
        Minecraft mc = Minecraft.getMinecraft();
        if (active.sequence.phase() == PilotSequence.Phase.READY && !active.safe(mc)) return;
        if (active.began && !active.lease.authorized(System.nanoTime())) { active.abort("live_authorization_expired"); return; }
        if (!active.safe(mc)) { active.abort("input_scope_lost"); return; }
        boolean wanted = active.sequence.key();
        if (wanted != active.key) {
            active.key = wanted;
            active.ownsKey = true;
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), wanted);
            if (wanted) KeyBinding.onTick(mc.gameSettings.keyBindUseItem.getKeyCode());
            Map<String, String> data = new LinkedHashMap<>(); data.put("down", Boolean.toString(wanted));
            data.put("epoch", String.valueOf(ContinuousTransportObserver.observedState().epoch()));
            InputPilotHarness.record(active.start.run, "KEY_TRANSITION", data);
        }
    }
    public static void controller(EnumHand hand, boolean release) {
        if (active == null || !active.began) return;
        if (!release && hand != active.hand) return;
        if (release) active.releaseCalls++; else active.controllerCalls++;
        Map<String, String> data = new LinkedHashMap<>();
        data.put("release", Boolean.toString(release)); data.put("key", Boolean.toString(Minecraft.getMinecraft().gameSettings.keyBindUseItem.isKeyDown()));
        data.put("hand", release ? active.start.hand : hand == EnumHand.OFF_HAND ? "off" : "main");
        InputPilotHarness.record(active.start.run, "CONTROLLER", data);
    }
    private boolean safe(Minecraft mc) {
        if (mc.getConnection() != connection || mc.world != world || mc.player != player || mc.currentScreen != null
                || !mc.inGameHasFocus || mc.isGamePaused() || !InputPilotHarness.enabled()
                || mc.gameSettings.keyBindUseItem.getKeyCode() != -99 || Mouse.isButtonDown(1)) return false;
        // Default right mouse only; sharing it with another action is not a safe pilot.
        for (KeyBinding binding : mc.gameSettings.keyBindings)
            if (binding != mc.gameSettings.keyBindUseItem && binding.getKeyCode() == -99) return false;
        for (int i = 0; i < Keyboard.KEYBOARD_SIZE; i++) if (Keyboard.isKeyDown(i)) return false;
        if (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.MISS) return false;
        if (hand == EnumHand.OFF_HAND && !mc.player.getHeldItemMainhand().isEmpty()) return false;
        net.minecraft.util.ResourceLocation spell = WandHelper.getCurrentSpell(mc.player.getHeldItem(hand)).getRegistryName();
        return spell != null && "jawms_cast_fixture".equals(spell.getNamespace()) && "stream".equals(spell.getPath());
    }
    private void tick() {
        Minecraft mc = Minecraft.getMinecraft(); ticks++;
        if (sequence.phase() == PilotSequence.Phase.READY && mc.currentScreen != null) {
            if (ticks > 200) abort("close_chat_to_arm_unreached"); return;
        }
        if (!safe(mc)) {
            if (sequence.phase() == PilotSequence.Phase.READY && ticks <= 200) return;
            abort("input_scope_lost"); return;
        }
        if (!inputHook) { abort("missing_vanilla_key_hook"); return; }
        ClientUseState state = ContinuousTransportObserver.observedState();
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(mc.getConnection().getNetworkManager());
        if (hook == null || state.faulted() || !state.enabled()) { abort("missing_protocol_readiness"); return; }
        Map<String, String> client;
        try { client = new LinkedHashMap<>(InputPilotHarness.snapshot(mc.player)); }
        catch (IllegalStateException failure) { if (ticks < 40 && !began) return; abort("missing_counter_baseline"); return; }
        if (lease.expired(System.nanoTime())) { abort("live_authorization_expired"); return; }
        boolean ready = lease.authorized(System.nanoTime()) && sample > 0
                && !mc.player.isHandActive() && !state.pending() && !state.blocked() && hook.queued() == 0
                && !mc.gameSettings.keyBindUseItem.isKeyDown() && integer(client, "effects") == 0
                && integer(client, "finishEvents") == 0 && integer(client, "finishBodies") == 0;
        boolean settled = integer(server, "claimed") == 0 && integer(server, "settle") == integer(server, "effects")
                && integer(server, "resolved") == integer(server, "effects");
        boolean clean = !state.pending() && !state.blocked() && !mc.player.isHandActive() && hook.queued() == 0 && !key;
        sequence.advance(System.nanoTime(), true, ready, sample, integer(server, "effects"), state.pending(),
                ContinuousTransportObserver.discardedInput(), settled, clean);
        if (sequence.phase() != lastPhase) {
            lastPhase = sequence.phase();
            Map<String, String> data = metadata(start); data.put("phase", lastPhase.name());
            InputPilotHarness.record(start.run, "PHASE", data);
            if (lastPhase == PilotSequence.Phase.HOLD) began = true;
        }
        if (ticks % 4 == 0 || sequence.phase() == PilotSequence.Phase.COMPLETE) {
            client.put("sequence", Integer.toString(sample)); client.put("epoch", String.valueOf(state.epoch()));
            client.put("sample", Integer.toString(++clientSamples));
            client.put("intent", Long.toString(state.intent())); client.put("use", Long.toString(state.use()));
            client.put("pending", Boolean.toString(state.pending())); client.put("blocked", Boolean.toString(state.blocked()));
            client.put("fault", Boolean.toString(state.faulted())); client.put("discarded", Boolean.toString(ContinuousTransportObserver.discardedInput()));
            client.put("key", Boolean.toString(mc.gameSettings.keyBindUseItem.isKeyDown())); client.put("queued", Integer.toString(hook.queued()));
            client.put("controllerCalls", Integer.toString(controllerCalls)); client.put("releaseCalls", Integer.toString(releaseCalls));
            InputPilotHarness.record(start.run, "CLIENT_SAMPLE", client);
            if (probes.size() >= 32) { abort("probe_bound_exceeded"); return; }
            probes.put(request + 1, System.nanoTime());
            InputPilotHarness.request(start.run, ++request, sequence.phase() == PilotSequence.Phase.COMPLETE);
        }
        if (sequence.phase() == PilotSequence.Phase.ABORTED) abort(sequence.reason());
        else if (sequence.phase() == PilotSequence.Phase.COMPLETE) {
            releaseOwnedKey(mc);
            InputPilotHarness.record(start.run, "ACTION_COMPLETE", Collections.singletonMap("verdict", "NOT_QUALIFIED")); active = null;
        }
    }
    private static int integer(Map<String, String> values, String key) { return Integer.parseInt(Objects.requireNonNull(values.get(key), key)); }
    private void releaseOwnedKey(Minecraft mc) {
        if (ownsKey) {
            KeyBinding.setKeyBindState(-99, false);
            // Drain only this pilot's use binding, never all keyboard state or a production latch.
            while (mc.gameSettings.keyBindUseItem.isPressed()) { }
            ownsKey = false; key = false;
        }
    }
    private void abort(String reason) {
        sequence.abort(reason); releaseOwnedKey(Minecraft.getMinecraft());
        InputPilotHarness.record(start.run, "UNREACHED", Collections.singletonMap("reason", reason));
        if (Minecraft.getMinecraft().getConnection() == connection) InputPilotHarness.request(start.run, ++request, true);
        active = null;
    }
    public static final class InputPilotDriverListener {
        @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                try { if (preflight != null) preflight.tick(); if (active != null) active.tick(); }
                catch (RuntimeException failure) {
                    if (active != null) active.abort("observer_failure_" + failure.getClass().getSimpleName());
                    if (preflight != null) preflight.abort("preflight_observer_failure");
                }
            }
        }
    }
    private static final class Preflight {
        final InputPilotHarness.Message message;
        final Object connection, world, player;
        final long started = System.nanoTime();
        boolean ready;
        Preflight(InputPilotHarness.Message message) {
            this.message = message; Minecraft mc = Minecraft.getMinecraft();
            connection = mc.getConnection(); world = mc.world; player = mc.player;
        }
        boolean scope(Minecraft mc) { return mc.getConnection() == connection && mc.world == world && mc.player == player && InputPilotHarness.enabled(); }
        void abort(String reason) {
            InputPilotHarness.record(message.run, "UNREACHED", Collections.singletonMap("reason", reason));
            if (Minecraft.getMinecraft().getConnection() == connection) InputPilotHarness.request(message.run, 2, true);
            preflight = null;
        }
        void tick() {
            Minecraft mc = Minecraft.getMinecraft();
            if (!scope(mc) || System.nanoTime() - started >= 10_000_000_000L) { abort("preflight_scope_or_deadline"); return; }
            if (mc.currentScreen != null || !mc.inGameHasFocus || mc.isGamePaused() || Mouse.isButtonDown(1)) {
                if (ready) abort("preflight_interrupted"); return;
            }
            for (int i = 0; i < Keyboard.KEYBOARD_SIZE; i++) if (Keyboard.isKeyDown(i)) {
                if (ready) abort("preflight_input_changed"); return;
            }
            ClientUseState state = ContinuousTransportObserver.observedState();
            ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(mc.getConnection().getNetworkManager());
            if (mc.player.isHandActive() || mc.gameSettings.keyBindUseItem.isKeyDown() || !state.enabled() || state.pending()
                    || state.blocked() || state.faulted() || hook == null || hook.queued() != 0) { abort("preflight_not_idle"); return; }
            if (!ready) {
                ready = true;
                InputPilotHarness.record(message.run, "CLIENT_PREFLIGHT_IDLE", Collections.singletonMap("epoch", String.valueOf(state.epoch())));
                InputPilotHarness.preflightReady(message.run);
            }
        }
    }
}
