package com.mahghuuuls.jawms.testfixture.client;

import com.mahghuuuls.jawms.client.ClientParticipantUseGuard;
import com.mahghuuuls.jawms.client.ClientWandPublications;
import com.mahghuuuls.jawms.internal.casting.ClientUseState;
import com.mahghuuuls.jawms.internal.casting.WandBinding;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import com.mahghuuuls.jawms.internal.casting.WandPublicationPair;
import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.network.play.server.SPacketSetSlot;
import net.minecraft.network.play.server.SPacketWindowItems;
import java.lang.reflect.Field;

/** Read-only client observations; replay submission never asks a guard to validate itself. */
public final class GapClientObservation {
    private static final ThreadLocal<SPacketHeldItemChange> SELECTION = new ThreadLocal<>();
    private static final ThreadLocal<WandBinding> RETIRING = new ThreadLocal<>();
    private GapClientObservation() {}
    public static Object staticField(Class<?> type, String name) {
        try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(null); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Observer field missing: " + name, failure); }
    }
    public static ClientUseState state() { return (ClientUseState) staticField(ClientParticipantUseGuard.class, "state"); }
    public static void freshSeed() {
        ClientUseState state = state();
        if (state.epoch() == null || state.intent() != 0 || state.use() != 0 || state.bound() || state.pending() || state.faulted())
            throw new IllegalStateException("Epoch seed must be first cast after fresh startup; UNREACHED");
    }
    public static long watermark() {
        Object session = staticField(ClientWandPublications.class, "session");
        if (session == null) throw new IllegalStateException("Missing real publication session; UNREACHED");
        return watermark(session);
    }
    private static long watermark(Object session) {
        Object receiver = PublicationFixture.field(session, "receiver");
        synchronized (receiver) { return (long) PublicationFixture.field(receiver, "latest"); }
    }
    /** Network-thread RETURN probe: budget only the current client's relevant packets, including rejections. */
    public static void received(INetHandler handler, Packet<?> packet, boolean forward) {
        if (!PublicationFixture.enabled() || !(handler instanceof NetHandlerPlayClient)) return;
        boolean relevant = packet instanceof SPacketSetSlot || packet instanceof SPacketWindowItems
                || packet instanceof SPacketHeldItemChange || packet instanceof SPacketCustomPayload
                && WandPublicationPair.CHANNEL.equals(((SPacketCustomPayload) packet).getChannelName());
        if (!relevant) return;
        Object session = staticField(ClientWandPublications.class, "session");
        if (session == null || PublicationFixture.field(session, "handler") != handler) return;
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(((NetHandlerPlayClient) handler).getNetworkManager());
        if (hook == null || hook.mode() != ContinuousTransportFixture.Mode.GAP_EPOCH_TARGET
                && hook.mode() != ContinuousTransportFixture.Mode.GAP_RETIRED
                && hook.mode() != ContinuousTransportFixture.Mode.PAIR_FAULT) return;
        // Read the receiver from this captured session; disconnect must not turn a second lookup into a failure.
        long watermark = watermark(session);
        PublicationFixture.log("gap_receiver_disposition", "packet", PublicationFixture.token(packet),
                "type", packet.getClass().getSimpleName(), "forward", forward, "watermark", watermark);
    }
    public static void replay(ContinuousTransportFixture.Hook hook) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isCallingFromMinecraftThread()) throw new IllegalStateException("Replay readiness must be on client thread");
        ClientUseState state = state();
        if (mc.getConnection() == null || !state.bound() || !state.unreleased() || state.pending() || state.faulted())
            throw new IllegalStateException("New live held use not reached; UNREACHED");
        if (PublicationFixture.field(state, "hand") != net.minecraft.util.EnumHand.MAIN_HAND
                || !new net.minecraft.util.ResourceLocation("jawms_cast_fixture", "stream").equals(PublicationFixture.field(state, "spell")))
            throw new IllegalStateException("Epoch target must use same named hand/spell; UNREACHED");
        hook.replayEpoch(state.epoch(), state.intent(), state.use(), mc.getConnection());
    }
    public static void retiring(WandPublicationRecord record) {
        RETIRING.remove();
        if (!ContinuousTransportFixture.gapObserving()) return;
        Object[] bindings = (Object[]) staticField(ClientWandPublications.class, "bindings");
        int h = record.entries()[0].inventorySlot == 40 ? 1 : 0;
        if (bindings[h] != null) RETIRING.set((WandBinding) PublicationFixture.field(bindings[h], "identity"));
    }
    public static void retired(WandPublicationRecord record) {
        WandBinding binding = RETIRING.get(); RETIRING.remove();
        if (binding == null || Minecraft.getMinecraft().getConnection() == null) return;
        PublicationFixture.log("gap_actual_retirement", "epoch", record.epoch, "generation", binding.generation(),
                "retired", binding.retired(), "pending", state().pending());
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(Minecraft.getMinecraft().getConnection().getNetworkManager());
        if (hook != null) hook.retiredObserved(record, binding.retired());
    }
    public static void beginSelection(SPacketHeldItemChange packet) {
        if (SELECTION.get() != null) throw new IllegalStateException("Nested held-slot observation; UNREACHED");
        SELECTION.set(packet); PublicationFixture.packet("gap_held_handler", packet);
    }
    public static void endSelection() { SELECTION.remove(); }
    public static void selected(InventoryPlayer inventory, int index) {
        SPacketHeldItemChange packet = SELECTION.get();
        if (packet == null) return;
        PublicationFixture.log("gap_actual_held_write", "packet", PublicationFixture.token(packet), "requested", packet.getHeldItemHotbarIndex(),
                "argument", index, "actual", inventory.currentItem, "epoch", state().epoch(), "fault", state().faulted());
    }
}
