package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.casting.CastCostContextManager;
import com.mahghuuuls.jawms.internal.casting.ClientUseState;
import com.mahghuuuls.jawms.internal.casting.FinishContinuation;
import com.mahghuuuls.jawms.internal.casting.ParticipantUseMessages;
import com.mahghuuuls.jawms.internal.casting.WandBinding;
import com.mahghuuuls.jawms.internal.casting.WandPublicationRecord;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import com.mahghuuuls.jawms.internal.mana.ManaNetwork;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/** Client-only continuation owner; it neither prepares nor pays a resource claim. */
public final class ClientParticipantUseGuard {
    private static ClientUseState state = new ClientUseState();
    private static NetHandlerPlayClient connection;
    private static Frame pending;
    private static Origin origin;
    private static boolean capturing;
    private static long lastNanos;
    private static long announced;
    private static EnumHand announcedHand;
    private static ResourceLocation announcedSpell;
    private static int traces;

    public static void capability(ParticipantUseMessages.Capability message, Object handler) {
        if (handler != Minecraft.getMinecraft().getConnection()) return;
        if (connection != handler) clear();
        connection = (NetHandlerPlayClient) handler;
        state.capability(message.epoch());
        ClientWandPublications.capability(connection, state.epoch());
        trace("capability");
    }
    public static boolean input(EntityPlayer player, EnumHand hand) {
        if (!state.enabled()) return true;
        if (state.blocked()) { state.rejectInput(); trace("input_rejected"); return false; }
        ItemStack stack = player.getHeldItem(hand);
        if (!(stack.getItem() instanceof ItemWand)) return true;
        Spell spell = WandHelper.getCurrentSpell(stack);
        if (!spell.isContinuous) return true;
        if (state.bound() && state.unreleased() && origin != null
                && !origin.matches(player, stack, hand, spell)) { state.rejectInput(); return false; }
        long intent = state.input(hand, spell.getRegistryName());
        if (intent == 0) return false;
        if (announced != intent || !state.bound()) origin = new Origin(player, stack, hand, spell);
        if (announced != intent || announcedHand != hand || !spell.getRegistryName().equals(announcedSpell)) {
            if (announced != intent) traces = 0;
            ManaNetwork.participantIntent(new ParticipantUseMessages.Intent(state.epoch(), intent, hand, spell.getRegistryName()));
            announced = intent; announcedHand = hand; announcedSpell = spell.getRegistryName();
            trace("intent_sent");
        }
        return true;
    }
    public static void started(EntityPlayer player, EnumHand hand) {
        if (state.enabled() && origin != null && player.isHandActive() && player.getActiveHand() == hand
                && origin.matches(player, player.getHeldItem(hand), hand, WandHelper.getCurrentSpell(player.getHeldItem(hand))))
            state.bind(hand, origin.spell.getRegistryName());
    }
    public static void release() {
        if (!state.enabled() || connection != Minecraft.getMinecraft().getConnection()) return;
        boolean wasPending = state.pending();
        long intent = state.release();
        if (!wasPending && state.pending()) lastNanos = System.nanoTime();
        if (intent != 0) {
            ManaNetwork.participantRelease(new ParticipantUseMessages.Release(state.epoch(), intent));
            trace("release_sent");
        }
    }
    public static boolean blocked() { return state.enabled() && state.blocked(); }
    public static boolean suppressed(EnumHand hand, ResourceLocation spell) {
        return state.enabled() && state.suppresses(hand, spell);
    }
    public static FinishContinuation.Capture begin(EntityPlayer player, ItemStack stack, EnumHand hand, Spell spell) {
        if (!state.enabled() || !spell.isContinuous) return FinishContinuation.Capture.LEGACY;
        boolean wasPending = state.pending();
        if (!state.capture(hand, spell.getRegistryName())) return FinishContinuation.Capture.SUPPRESSED;
        if (!wasPending) lastNanos = System.nanoTime();
        if (state.ready() && !state.finishPermitted()) {
            resolve();
            return FinishContinuation.Capture.SUPPRESSED;
        }
        if (origin == null || !origin.matches(player, stack, hand, spell)) {
            origin = null;
            fault("released finish no longer owns its original input context");
            resolve();
            return FinishContinuation.Capture.SUPPRESSED;
        }
        pending = new Frame(player, stack, hand, spell, origin.binding);
        capturing = true;
        trace("finish_captured");
        return FinishContinuation.Capture.CAPTURED;
    }
    public static boolean capturing() { return capturing; }
    private static Frame capture() { return capturing ? pending : null; }
    public static boolean defer(Runnable action) {
        if (!capturing()) return false;
        Frame frame = capture();
        if (frame != null) frame.continuation.capture(action);
        return true;
    }
    public static void baseCooldown(int value) { Frame frame = capture(); if (frame != null) frame.baseCooldown = value; }
    public static boolean cooldown(int fallback, SpellModifiers modifiers, java.util.function.BiConsumer<ItemStack, Integer> operation) {
        if (!capturing()) return false;
        Frame frame = capture();
        if (frame != null) frame.continuation.capture(() -> operation.accept(frame.binding.representative(),
                (int) (frame.baseCooldown * modifiers.get(WizardryItems.cooldown_upgrade))));
        return true;
    }
    public static void end(boolean completed) {
        capturing = false;
        if (!completed && pending != null) {
            pending.continuation.resolve(false); pending = null; origin = null;
            fault("original release failed during finish capture; captured operations discarded");
        }
        resolve();
    }
    public static void close(ParticipantUseMessages.Close message, Object handler) {
        if (handler != connection || handler != Minecraft.getMinecraft().getConnection() || !state.accept(message)) return;
        if (origin != null && message.cause() == ParticipantUseMessages.CloseCause.INVALIDATED) origin.binding.retire();
        else if (origin != null && !origin.binding.adopt(message.generation())) {
            origin.binding.retire();
            fault("normal close does not own its original wand generation");
        }
        trace("close_" + message.cause());
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (message.cause() != ParticipantUseMessages.CloseCause.NORMAL_FINISH && player != null
                && origin != null && origin.matches(player, player.getHeldItem(message.hand()), message.hand(),
                WandHelper.getCurrentSpell(player.getHeldItem(message.hand())))) {
            CastCostContextManager.clear(player, message.hand());
            CastPaymentContextManager.clear(player, message.hand());
            if (player.isHandActive() && player.getActiveHand() == message.hand()) player.resetActiveHand();
        }
        if (message.cause() != ParticipantUseMessages.CloseCause.NORMAL_FINISH) origin = null;
        resolve();
    }
    private static void resolve() {
        if (capturing() || !state.ready()) return;
        Frame frame = pending;
        boolean permitted = state.finishPermitted();
        if (frame != null) frame.use = state.use();
        pending = null;
        origin = null;
        state.resolved(); // Retire before external event/spell callbacks can re-enter.
        trace(permitted ? "normal_permission_consumed" : "finish_suppressed");
        if (frame == null) return;
        try {
            if (frame.continuation.resolve(permitted) == FinishContinuation.Result.CANCELLED) fault("original finish context replaced");
        }
        catch (RuntimeException fault) {
            JustAnotherWizardryManaSystemMod.LOGGER.warn("Client continuous finish callback failed after close.", fault);
        }
    }
    private static void fault(String reason) {
        JustAnotherWizardryManaSystemMod.LOGGER.warn("Client continuous close: {}. No finish was replayed.", reason);
    }
    public static boolean correlatePublication(WandPublicationRecord record, WandPublicationRecord.Entry entry, WandBinding binding) {
        if (!state.enabled() || state.faulted() || !state.epoch().equals(record.epoch) || binding.retired()) return false;
        if (binding.generation() != 0) return binding.generation() == entry.generation;
        return origin != null && origin.binding == binding && entry.intent == state.intent()
                && state.associateUse(record.epoch, entry.intent, entry.use);
    }
    public static void protocolFault(String reason) {
        state.protocolFault();
        if (pending != null) pending.continuation.resolve(false);
        pending = null; origin = null;
        ClientWandPublications.faultState();
        fault(reason);
    }
    public static void contextRetired() {
        if (pending != null && !pending.valid()) {
            pending.continuation.resolve(false); pending = null;
            fault("original wand binding retired; awaiting its authoritative close");
        }
    }
    public static void observeArgument(EntityPlayer player, ItemStack stack) {
        if (origin != null && origin.player == player && origin.binding.representative() == stack
                && origin.matches(player, stack, origin.hand, origin.spell)) origin.activeArgument = stack;
    }
    private static void trace(String event) {
        if (!com.mahghuuuls.jawms.internal.casting.CastingDiagnostics.isEnabled() || traces++ >= 128) return;
        JustAnotherWizardryManaSystemMod.LOGGER.info("JAWMS client use: event={} epoch={} intent={} use={} pending={} blocked={} thread={}",
                event, state.epoch(), state.intent(), state.use(), state.pending(), state.blocked(), Thread.currentThread().getName());
    }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !state.enabled()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (connection != mc.getConnection()) { clear(); return; }
        ClientWandPublications.tick();
        if (origin != null && (mc.player != origin.player || origin.player.world != origin.world)) {
            if (pending != null) { pending.continuation.resolve(false); pending = null; }
            origin = null; // Teardown must drop a bound origin even when vanilla skipped its finish callback.
            fault("original player/world retired; awaiting its close or timeout");
        }
        if (!mc.gameSettings.keyBindUseItem.isKeyDown()) release();
        long now = System.nanoTime();
        long elapsed = lastNanos == 0 ? 0 : now - lastNanos;
        lastNanos = now;
        if (pending != null && !pending.valid()) {
            pending.continuation.resolve(false); pending = null; origin = null;
            fault("original finish context cancelled; awaiting close or timeout");
        }
        if (!mc.isGamePaused() && state.elapse(elapsed)) {
            protocolFault("confirmation timed out; reconnect required");
        }
        resolve();
    }
    @SubscribeEvent public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        NetHandlerPlayClient old = connection;
        if (old == null || event.getManager() != old.getNetworkManager()) return;
        Minecraft.getMinecraft().addScheduledTask(() -> { if (connection == old) clear(); });
    }
    private static void clear() {
        if (pending != null) pending.continuation.resolve(false);
        state = new ClientUseState(); connection = null; pending = null; origin = null; capturing = false;
        lastNanos = 0; announced = 0; announcedHand = null; announcedSpell = null;
        traces = 0;
        ClientWandPublications.clear();
    }
    private static final class Origin {
        final EntityPlayer player; final Object world; final WandBinding binding;
        ItemStack activeArgument;
        final EnumHand hand; final Spell spell;
        Origin(EntityPlayer player, ItemStack stack, EnumHand hand, Spell spell) {
            this.player = player; this.world = player.world; this.binding = ClientWandPublications.binding(player, hand);
            this.activeArgument = stack; this.hand = hand; this.spell = spell;
        }
        boolean matches(EntityPlayer player, ItemStack stack, EnumHand hand, Spell spell) {
            return this.player == player && this.world == player.world && this.hand == hand && this.spell == spell
                    && ClientWandPublications.owns(player, hand, binding)
                    && (stack == binding.representative() || stack == activeArgument)
                    && WandBinding.semanticEquals(stack, binding.representative());
        }
    }
    private static final class Frame {
        final ClientUseState owner = state;
        final long intent = state.intent();
        long use = state.use();
        final int slot;
        final EntityPlayer player; final Object world; final ItemStack stack, snapshot;
        final WandBinding binding;
        final EnumHand hand; final Spell spell; final FinishContinuation continuation;
        int baseCooldown;
        Frame(EntityPlayer player, ItemStack stack, EnumHand hand, Spell spell, WandBinding binding) {
            this.player = player; this.world = player.world; this.stack = stack; this.snapshot = stack.copy();
            this.hand = hand; this.spell = spell;
            this.slot = hand == EnumHand.MAIN_HAND ? player.inventory.currentItem : 40;
            this.binding = binding;
            this.continuation = new FinishContinuation(this::valid, this::ownsContext);
        }
        boolean valid() {
            return ownsContext();
        }
        boolean ownsContext() {
            return state == owner && owner.ownsFinish(intent, use)
                    && Minecraft.getMinecraft().player == player && player.world == world
                    && ClientWandPublications.owns(player, hand, binding)
                    && WandHelper.getCurrentSpell(binding.representative()) == spell;
        }
    }
}
