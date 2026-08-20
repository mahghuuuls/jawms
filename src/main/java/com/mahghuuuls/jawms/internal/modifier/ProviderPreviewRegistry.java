package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Owns optional invariant presentation declarations separately from provider evaluation. */
public final class ProviderPreviewRegistry {

    private static final ProviderPreviewRegistry INSTANCE =
            new ProviderPreviewRegistry(ManaProviderRegistry.instance());

    private final ManaProviderRegistry providers;
    private final TreeMap<ResourceLocation, ManaContribution> initialDeclarations =
            new TreeMap<>();
    private TreeMap<ResourceLocation, ManaContribution> activeDeclarations =
            new TreeMap<>();
    private boolean serverActive;

    ProviderPreviewRegistry(ManaProviderRegistry providers) {
        this.providers = providers;
    }

    public static ProviderPreviewRegistry instance() {
        return INSTANCE;
    }

    public synchronized void registerInitial(
            ResourceLocation providerId,
            ManaContribution contribution
    ) {
        if (providers.isFrozen()) {
            throw new IllegalStateException(
                    "JAWMS provider-preview registration is frozen; use active-server replacement."
            );
        }
        requireDeclaration(providerId, contribution);
        if (initialDeclarations.containsKey(providerId)) {
            throw new IllegalArgumentException(
                    "Duplicate JAWMS provider-preview declaration: " + providerId
            );
        }
        initialDeclarations.put(providerId, contribution);
        if (!serverActive) {
            activeDeclarations.put(providerId, contribution);
        }
    }

    public synchronized void serverStarted() {
        serverActive = true;
        activeDeclarations = new TreeMap<>(initialDeclarations);
    }

    public synchronized void serverStopped() {
        serverActive = false;
        activeDeclarations = new TreeMap<>(initialDeclarations);
    }

    public synchronized void replaceActive(
            ResourceLocation providerId,
            ManaContribution contribution
    ) {
        requireActive();
        requireDeclaration(providerId, contribution);
        activeDeclarations.put(providerId, contribution);
    }

    public synchronized boolean clearActive(ResourceLocation providerId) {
        requireActive();
        if (providerId == null) {
            throw new NullPointerException("providerId");
        }
        providers.requireItemTargetId(providerId);
        return activeDeclarations.remove(providerId) != null;
    }

    public synchronized List<ProviderPreviewDeclaration> declarations() {
        Map<ResourceLocation, ManaContribution> source = serverActive
                ? activeDeclarations : initialDeclarations;
        List<ProviderPreviewDeclaration> result = new ArrayList<>(source.size());
        for (Map.Entry<ResourceLocation, ManaContribution> entry : source.entrySet()) {
            result.add(new ProviderPreviewDeclaration(
                    entry.getKey(),
                    providers.requireItemTargetId(entry.getKey()),
                    entry.getValue()
            ));
        }
        return result;
    }

    public static void validateContribution(ManaContribution contribution) {
        if (contribution == null) {
            throw new NullPointerException("contribution");
        }
        ManaProfileResolver.validateContribution(contribution);
    }

    private void requireDeclaration(
            ResourceLocation providerId,
            ManaContribution contribution
    ) {
        if (providerId == null) {
            throw new NullPointerException("providerId");
        }
        providers.requireItemTargetId(providerId);
        validateContribution(contribution);
    }

    private void requireActive() {
        if (!serverActive) {
            throw new IllegalStateException("No logical server is active for provider-preview mutation.");
        }
    }
}
