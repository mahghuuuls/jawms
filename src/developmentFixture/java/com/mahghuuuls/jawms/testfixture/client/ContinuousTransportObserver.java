package com.mahghuuuls.jawms.testfixture.client;

import com.mahghuuuls.jawms.internal.casting.ClientUseState;
import com.mahghuuuls.jawms.client.ClientParticipantUseGuard;
import com.mahghuuuls.jawms.testfixture.ContinuousTransportFixture;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.common.MinecraftForge;
import electroblob.wizardry.event.SpellCastEvent;
import com.mahghuuuls.jawms.testfixture.observation.PublicationFixture;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.lang.reflect.Field;

/** Read-only production/key observation; never presses input or mutates a guard. */
public final class ContinuousTransportObserver {
    private static ContinuousTransportObserver instance;
    private static final Logger LOG = LogManager.getLogger("jawms_c007_transport");
    private String previous = "";
    private boolean deliveredForPress;
    private static Field stateField, discardedField, pendingField;
    private boolean captureSeen, wheelDone, callbackDone;
    private electroblob.wizardry.util.SpellModifiers changedLive, savedLive;
    private java.lang.ref.WeakReference<net.minecraft.entity.player.EntityPlayer> modifierPlayer = new java.lang.ref.WeakReference<>(null);
    public static void install() throws Exception {
        stateField = ClientParticipantUseGuard.class.getDeclaredField("state"); stateField.setAccessible(true);
        discardedField = ClientUseState.class.getDeclaredField("discardedGesture"); discardedField.setAccessible(true);
        pendingField = ClientParticipantUseGuard.class.getDeclaredField("pending"); pendingField.setAccessible(true);
        ContinuousTransportObserver observer = new ContinuousTransportObserver();
        instance = observer;
        FMLCommonHandler.instance().bus().register(observer); MinecraftForge.EVENT_BUS.register(observer);
    }
    public static void captureObserved(net.minecraft.entity.player.EntityPlayer player, EnumHand hand, Object frame) {
        if (!PublicationFixture.enabled() || instance == null || Minecraft.getMinecraft().player != player
                || Minecraft.getMinecraft().getConnection() == null) return;
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(Minecraft.getMinecraft().getConnection().getNetworkManager());
        if (hook == null) return;
        ClientUseState owner = (ClientUseState) PublicationFixture.field(frame, "owner");
        hook.pendingObserved(owner.epoch(), (long) PublicationFixture.field(frame, "intent"), hand, (int) PublicationFixture.field(frame, "slot"));
        instance.captureSeen = true;
    }
    /** Main-thread card boundary: restore the optional live-input probe before rearming or resetting. */
    public static void rearm() {
        if (instance == null) return;
        instance.restoreModifiers(Minecraft.getMinecraft().player);
        instance.captureSeen = instance.wheelDone = instance.callbackDone = instance.deliveredForPress = false;
        instance.previous = "";
    }
    /** Read-only access to the actual owner, not a second model of client authority. */
    public static ClientUseState observedState() {
        try {
            if (stateField == null) throw new IllegalStateException("Client observer not installed");
            return (ClientUseState) stateField.get(null);
        } catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }
    public static boolean discardedInput() {
        try { return discardedField.getBoolean(observedState()); }
        catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
    }
    private void restoreModifiers(net.minecraft.entity.player.EntityPlayer player) {
        if (changedLive == null) return;
        if (player != null && modifierPlayer.get() == player) {
            electroblob.wizardry.data.WizardData data = electroblob.wizardry.data.WizardData.get(player);
            if (data != null && data.itemCastingModifiers == changedLive) data.itemCastingModifiers = savedLive;
        }
        changedLive = savedLive = null; modifierPlayer.clear(); PublicationFixture.log("live_modifier_probe_restored");
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) throws IllegalAccessException {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.getConnection() == null || mc.player == null) {
            previous = ""; deliveredForPress = false; changedLive = savedLive = null; modifierPlayer.clear(); return;
        }
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(mc.getConnection().getNetworkManager());
        if (hook == null) return;
        ClientUseState state = (ClientUseState) stateField.get(null);
        if (PublicationFixture.enabled()) {
            PublicationFixture.watch(mc.player);
            if (state.bound()) hook.newerBound(state.intent());
            if (state.pending()) {
                Object frame = pendingField.get(null);
                if (frame != null) {
                    EnumHand originalHand = (EnumHand) PublicationFixture.field(frame, "hand");
                    hook.pendingObserved(state.epoch(), state.intent(), originalHand, (int) PublicationFixture.field(frame, "slot"));
                    if (hook.mode() == ContinuousTransportFixture.Mode.PUB_FIRST && changedLive == null) {
                        electroblob.wizardry.data.WizardData data = electroblob.wizardry.data.WizardData.get(mc.player);
                        if (data == null || data.itemCastingModifiers == null) PublicationFixture.log("live_modifier_probe", "verdict", "UNREACHED");
                        else {
                            changedLive = data.itemCastingModifiers; savedLive = changedLive.copy(); modifierPlayer = new java.lang.ref.WeakReference<>(mc.player);
                            int before = changedLive.getModifiers().hashCode();
                            changedLive.set(electroblob.wizardry.registry.WizardryItems.cooldown_upgrade, .25F, false);
                            changedLive.set("jawms_c007_live_probe", 7F, false);
                            PublicationFixture.log("live_modifier_probe", "originalSignature", before,
                                    "changedSignature", changedLive.getModifiers().hashCode(), "changedCooldownFactor", .25F);
                        }
                    }
                }
                if (pendingField.get(null) != null) captureSeen = true;
                if (hook.mode() == ContinuousTransportFixture.Mode.WHEEL && !wheelDone) {
                    wheelDone = true;
                    mc.player.inventory.changeCurrentItem(1); mc.player.inventory.changeCurrentItem(-1);
                    PublicationFixture.log("actual_wheel_away_back", "slot", mc.player.inventory.currentItem);
                    hook.contextMutationObserved(state.epoch(), state.intent());
                }
                if ((hook.mode() == ContinuousTransportFixture.Mode.CHANGE || hook.mode() == ContinuousTransportFixture.Mode.PAIR_FAULT)
                        && captureSeen && pendingField.get(null) == null) hook.contextMutationObserved(state.epoch(), state.intent());
            } else {
                captureSeen = wheelDone = false;
                restoreModifiers(mc.player);
            }
            if (hook.mode() != ContinuousTransportFixture.Mode.CALLBACK) callbackDone = false;
            if (state.faulted() && hook.mode() == ContinuousTransportFixture.Mode.PAIR_FAULT) hook.contextMutationObserved(state.epoch(), state.intent());
        }
        boolean key = mc.gameSettings.keyBindUseItem.isKeyDown();
        if (!key) deliveredForPress = false;
        boolean rejected = discardedField.getBoolean(state);
        if (key && rejected && state.pending() && !deliveredForPress) {
            deliveredForPress = true; hook.blockedGestureObserved(state.epoch(), state.intent());
        }
        String value = "epoch=" + state.epoch() + ",intent=" + state.intent() + ",use=" + state.use()
                + ",pending=" + state.pending() + ",blocked=" + state.blocked() + ",fault=" + state.faulted()
                + ",key=" + key + ",discarded=" + rejected + ",queued=" + hook.queued()
                + ",mainStack=" + System.identityHashCode(mc.player.getHeldItem(EnumHand.MAIN_HAND))
                + ",offStack=" + System.identityHashCode(mc.player.getHeldItem(EnumHand.OFF_HAND));
        if (!value.equals(previous)) {
            previous = value;
            LOG.info("USE_TRANSPORT CLIENT_STATE {} remainingNanos={} mainCooldown={} offCooldown={} paused={}", value,
                    state.remainingNanos(), WandHelper.getCurrentCooldown(mc.player.getHeldItemMainhand()),
                    WandHelper.getCurrentCooldown(mc.player.getHeldItemOffhand()), mc.isGamePaused());
        }
    }
    @SubscribeEvent public void finish(SpellCastEvent.Finish event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!PublicationFixture.enabled() || event.getCaster() != mc.player || mc.getConnection() == null
                || event.getSpell().getRegistryName() == null || !"jawms_cast_fixture".equals(event.getSpell().getRegistryName().getNamespace())) return;
        ContinuousTransportFixture.Hook hook = ContinuousTransportFixture.clientHook(mc.getConnection().getNetworkManager());
        if (hook == null || hook.mode() != ContinuousTransportFixture.Mode.CALLBACK || callbackDone) return;
        callbackDone = true;
        net.minecraft.item.ItemStack original = mc.player.getHeldItemMainhand();
        if (original.isEmpty()) original = mc.player.getHeldItemOffhand();
        net.minecraft.util.EnumHand hand = original == mc.player.getHeldItemMainhand() ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
        net.minecraft.item.ItemStack replacement = original.copy();
        mc.player.setHeldItem(hand, replacement);
        PublicationFixture.log("real_finish_callback_replacement", "distinct", original != replacement,
                "equal", net.minecraft.item.ItemStack.areItemStacksEqual(original, replacement));
    }
}
