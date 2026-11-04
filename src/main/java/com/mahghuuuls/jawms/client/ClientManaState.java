package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.internal.mana.ManaReadinessMessage;
import electroblob.wizardry.constants.Element;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import com.mahghuuuls.jawms.internal.casting.CastCostContextManager;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

public final class ClientManaState {

    private static Object viewConnection;
    private static long managedSequence;
    private static boolean managedHeld;
    private static UUID managedGeneration;
    private static boolean awaitingLifecycle;
    private static int currentMana;

    public static boolean acceptsLegacy(Object handler, Object activeConnection) {
        if (handler == null || handler != activeConnection) return false;
        bindConnection(handler);
        return managedSequence == 0;
    }

    public static void acceptManaged(ManaReadinessMessage message,
            Object handler, Object activeConnection) {
        if (handler == null || handler != activeConnection) return;
        bindConnection(handler);
        if (message.getSequence() <= managedSequence) return;
        managedSequence = message.getSequence();
        UUID generation = message.getGeneration();
        boolean ready = message.getPhase() == ManaProfileReadiness.READY;
        if (awaitingLifecycle && (ready || generation.equals(managedGeneration))) return;
        if (ready && managedGeneration != null && !generation.equals(managedGeneration)) return;
        awaitingLifecycle = false;
        if (!generation.equals(managedGeneration)) clear();
        managedGeneration = generation;
        managedHeld = !ready;
        if (managedHeld) clear();
        else message.deliverPayload();
    }

    public static void invalidateLifecycle(Object handler, Object activeConnection) {
        if (handler == null || handler != activeConnection || handler != viewConnection || managedSequence == 0) return;
        awaitingLifecycle = true;
        managedHeld = true;
        clear();
    }

    public static boolean isManagedHeld(Object activeConnection) {
        return activeConnection != null && viewConnection == activeConnection && managedHeld;
    }

    private static void bindConnection(Object handler) {
        if (handler != null && viewConnection == handler) return;
        viewConnection = handler;
        managedSequence = 0;
        managedHeld = false;
        managedGeneration = null;
        awaitingLifecycle = false;
        clear();
    }
    private static int maximumMana;
    private static double effectiveRegeneration;
    private static long revision = Long.MIN_VALUE;
    private static boolean available;
    private static double globalSpellEfficiency;
    private static final EnumMap<Element, Double> elementSpellEfficiency = new EnumMap<>(Element.class);
    private static double globalSpellCostMultiplier = 1.0D;
    private static final EnumMap<Element, Double> elementSpellCostMultiplier = new EnumMap<>(Element.class);
    private static volatile PublishedSnapshot publishedSnapshot;

    private static final class PublishedSnapshot {
        private final Object connection;
        private final ClientManaSnapshot value;

        private PublishedSnapshot(Object connection, ClientManaSnapshot value) {
            this.connection = connection;
            this.value = value;
        }
    }

    public static void accept(int current, int maximum, double regeneration, long incomingRevision) {
        accept(current, maximum, regeneration, incomingRevision, 0.0D,
                java.util.Collections.emptyMap(), 1.0D,
                java.util.Collections.emptyMap());
    }

    public static void accept(
            int current,
            int maximum,
            double regeneration,
            long incomingRevision,
            double globalEfficiency,
            Map<Element, Double> elementEfficiency,
            double globalCostMultiplier,
            Map<Element, Double> elementCostMultiplier
    ) {
        if (!available || incomingRevision > revision) {
            currentMana = current;
            maximumMana = maximum;
            effectiveRegeneration = regeneration;
            revision = incomingRevision;
            globalSpellEfficiency = globalEfficiency;
            elementSpellEfficiency.clear();
            elementSpellEfficiency.putAll(elementEfficiency);
            globalSpellCostMultiplier = globalCostMultiplier;
            elementSpellCostMultiplier.clear();
            elementSpellCostMultiplier.putAll(elementCostMultiplier);
            available = true;
            publishedSnapshot = new PublishedSnapshot(viewConnection, ClientManaSnapshot.available(
                    current,
                    maximum,
                    regeneration,
                    incomingRevision
            ));
        }
    }

    static ClientManaSnapshot snapshot(Object activeConnection) {
        // The visible connection can disappear before the queued disconnect cleanup runs.
        PublishedSnapshot published = publishedSnapshot;
        return activeConnection != null && published != null && published.connection == activeConnection
                ? published.value
                : ClientManaSnapshot.unavailable();
    }

    public static boolean isAvailable() {
        return available;
    }

    public static int getCurrentMana() {
        return currentMana;
    }

    public static int getMaximumMana() {
        return maximumMana;
    }

    public static double getEffectiveRegeneration() {
        return effectiveRegeneration;
    }

    public static double getSpellEfficiency(Element element) {
        Double value = elementSpellEfficiency.get(element);
        return globalSpellEfficiency + (value == null ? 0.0D : value);
    }

    public static double getSpellCostMultiplier(Element element) {
        Double value = elementSpellCostMultiplier.get(element);
        return value == null ? globalSpellCostMultiplier : value;
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft client = Minecraft.getMinecraft();
        client.addScheduledTask(() -> {
            Object active = client.getConnection();
            if (active != null && active != event.getHandler()) return;
            CastCostContextManager.clearClientSide();
            CastPaymentContextManager.clearClientSide();
            bindConnection(null);
        });
    }

    static void clear() {
        currentMana = 0;
        maximumMana = 0;
        effectiveRegeneration = 0.0D;
        revision = Long.MIN_VALUE;
        available = false;
        globalSpellEfficiency = 0.0D;
        elementSpellEfficiency.clear();
        globalSpellCostMultiplier = 1.0D;
        elementSpellCostMultiplier.clear();
        publishedSnapshot = null;
    }
}
