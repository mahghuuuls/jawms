package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IContextualManaModifierProvider;
import com.mahghuuuls.jawms.api.IItemManaModifierProvider;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionDisposition;
import com.mahghuuuls.jawms.api.ManaContributionFailure;
import com.mahghuuuls.jawms.api.ManaContributionSourceKind;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaProviderDescriptor;
import com.mahghuuuls.jawms.api.ManaProviderKind;
import com.mahghuuuls.jawms.internal.mana.ManaEvaluationGuard;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Internal registry and bounded per-player evaluation cache for public modifier providers. */
public final class ManaProviderRegistry {

    private static final long DIAGNOSTIC_INTERVAL_TICKS = 1200L;
    private static final ManaProviderRegistry INSTANCE = new ManaProviderRegistry();

    private final TreeMap<ResourceLocation, Registration> registrations = new TreeMap<>();
    private final Map<UUID, PlayerCache> playerCaches = new HashMap<>();
    private final Set<UUID> pendingRefreshes = new HashSet<>();
    private final Map<String, Long> lastDiagnostics = new HashMap<>();
    private long diagnosticEmissions;
    private boolean frozen;

    ManaProviderRegistry() {
    }

    public static ManaProviderRegistry instance() {
        return INSTANCE;
    }

    public synchronized void registerItem(
            ResourceLocation id,
            Item item,
            IItemManaModifierProvider provider
    ) {
        requireRegistration(id, provider);
        if (item == null) {
            throw new NullPointerException("targetItem");
        }
        put(id, new ItemRegistration(id, item, provider));
    }

    public synchronized void registerItemWithPreview(
            ResourceLocation id,
            Item item,
            IItemManaModifierProvider provider,
            ManaContribution contributionPreview
    ) {
        ProviderPreviewRegistry.validateContribution(contributionPreview);
        requireRegistration(id, provider);
        if (item == null) {
            throw new NullPointerException("targetItem");
        }
        put(id, new ItemRegistration(id, item, provider));
        try {
            ProviderPreviewRegistry.instance().registerInitial(id, contributionPreview);
        } catch (RuntimeException exception) {
            registrations.remove(id);
            throw exception;
        }
    }

    public synchronized void registerContextual(
            ResourceLocation id,
            IContextualManaModifierProvider provider
    ) {
        requireRegistration(id, provider);
        put(id, new ContextualRegistration(id, provider));
    }

    public synchronized void freeze() {
        frozen = true;
    }

    public synchronized boolean isFrozen() {
        return frozen;
    }

    public synchronized List<ManaProviderDescriptor> descriptors() {
        List<ManaProviderDescriptor> result = new ArrayList<>(registrations.size());
        for (Registration registration : registrations.values()) {
            if (registration instanceof ItemRegistration) {
                ItemRegistration item = (ItemRegistration) registration;
                result.add(new ManaProviderDescriptor(
                        registration.id,
                        ManaProviderKind.ITEM,
                        item.item.getRegistryName()
                ));
            } else {
                result.add(new ManaProviderDescriptor(
                        registration.id,
                        ManaProviderKind.CONTEXTUAL,
                        null
                ));
            }
        }
        return Collections.unmodifiableList(result);
    }

    synchronized ResourceLocation requireItemTargetId(ResourceLocation providerId) {
        if (providerId == null) {
            throw new NullPointerException("providerId");
        }
        Registration registration = registrations.get(providerId);
        if (registration == null) {
            throw new IllegalArgumentException(
                    "Unknown JAWMS modifier-provider ID: " + providerId
            );
        }
        if (!(registration instanceof ItemRegistration)) {
            throw new IllegalArgumentException(
                    "JAWMS provider is not item-targeted: " + providerId
            );
        }
        return ((ItemRegistration) registration).item.getRegistryName();
    }

    public synchronized List<ManaContribution> evaluateItem(
            EntityPlayer player,
            ManaItemContext context,
            GameplayRules rules
    ) {
        return acceptedContributions(evaluateItemRecords(player, context, rules, false));
    }

    public synchronized List<ContributionEvaluationRecord> evaluateItemRecords(
            EntityPlayer player,
            ManaItemContext context,
            GameplayRules rules,
            boolean overridden
    ) {
        requireEvaluationArguments(player, rules);
        if (context == null) {
            throw new NullPointerException("context");
        }
        PlayerCache cache = cache(player, rules);
        List<ContributionEvaluationRecord> result = new ArrayList<>();
        ItemStack stack = context.getStack();
        ResourceLocation itemId = stack.getItem().getRegistryName();
        StackFingerprint fingerprint = StackFingerprint.of(stack);
        for (Registration registration : registrations.values()) {
            if (!(registration instanceof ItemRegistration)
                    ) {
                continue;
            }
            ItemRegistration itemRegistration = (ItemRegistration) registration;
            if (itemRegistration.item != stack.getItem()) {
                continue;
            }
            if (overridden) {
                result.add(ContributionEvaluationRecord.item(
                        registration.id,
                        ManaContributionSourceKind.ITEM_PROVIDER,
                        ManaContributionDisposition.OVERRIDDEN,
                        ManaContributionFailure.NONE,
                        itemId,
                        context,
                        null
                ));
                continue;
            }
            if (rules.isProviderDisabled(registration.id)) {
                result.add(ContributionEvaluationRecord.item(
                        registration.id,
                        ManaContributionSourceKind.ITEM_PROVIDER,
                        ManaContributionDisposition.DISABLED,
                        ManaContributionFailure.NONE,
                        itemId,
                        context,
                        null
                ));
                continue;
            }
            CacheKey key = CacheKey.item(registration.id, context, fingerprint);
            CachedEvaluation cached = cache.values.get(key);
            if (cached == null) {
                cached = evaluateItemProvider(player, context, itemRegistration);
                cache.values.put(key, cached);
            }
            result.add(ContributionEvaluationRecord.item(
                    registration.id,
                    ManaContributionSourceKind.ITEM_PROVIDER,
                    cached.disposition,
                    cached.failure,
                    itemId,
                    context,
                    cached.contribution
            ));
        }
        return result;
    }

    public synchronized List<ManaContribution> evaluateContextual(
            EntityPlayer player,
            GameplayRules rules
    ) {
        return acceptedContributions(evaluateContextualRecords(player, rules));
    }

    public synchronized List<ContributionEvaluationRecord> evaluateContextualRecords(
            EntityPlayer player,
            GameplayRules rules
    ) {
        requireEvaluationArguments(player, rules);
        PlayerCache cache = cache(player, rules);
        List<ContributionEvaluationRecord> result = new ArrayList<>();
        for (Registration registration : registrations.values()) {
            if (!(registration instanceof ContextualRegistration)) {
                continue;
            }
            if (rules.isProviderDisabled(registration.id)) {
                result.add(ContributionEvaluationRecord.playerWide(
                        registration.id,
                        ManaContributionSourceKind.CONTEXTUAL_PROVIDER,
                        ManaContributionDisposition.DISABLED,
                        ManaContributionFailure.NONE,
                        null
                ));
                continue;
            }
            CacheKey key = CacheKey.contextual(registration.id);
            CachedEvaluation cached = cache.values.get(key);
            if (cached == null) {
                cached = evaluateContextualProvider(player, (ContextualRegistration) registration);
                cache.values.put(key, cached);
            }
            result.add(ContributionEvaluationRecord.playerWide(
                    registration.id,
                    ManaContributionSourceKind.CONTEXTUAL_PROVIDER,
                    cached.disposition,
                    cached.failure,
                    cached.contribution
            ));
        }
        return result;
    }

    public synchronized void invalidate(EntityPlayer player) {
        UUID playerId = requireRefreshContext(player);
        pendingRefreshes.remove(playerId);
        advanceGeneration(player, playerId);
    }

    public synchronized void markDirty(EntityPlayer player) {
        pendingRefreshes.add(requireRefreshContext(player));
    }

    /** Consumes one coalesced request immediately before END-tick evaluation. */
    public synchronized boolean consumeDeferredRefresh(EntityPlayer player) {
        UUID playerId = requireRefreshContext(player);
        if (!pendingRefreshes.remove(playerId)) {
            return false;
        }
        advanceGeneration(player, playerId);
        return true;
    }

    public synchronized void remove(EntityPlayer player) {
        if (player != null) {
            UUID playerId = player.getUniqueID();
            playerCaches.remove(playerId);
            pendingRefreshes.remove(playerId);
            String prefix = playerId + "|";
            lastDiagnostics.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    public synchronized void clearRuntimeState() {
        playerCaches.clear();
        pendingRefreshes.clear();
        lastDiagnostics.clear();
    }

    private UUID requireRefreshContext(EntityPlayer player) {
        ManaService.requireModifierRefreshContext(player);
        UUID playerId = player.getUniqueID();
        if (ManaEvaluationGuard.isEvaluating(playerId)) {
            throw new IllegalStateException(
                    "JAWMS modifier cache cannot be refreshed during provider evaluation."
            );
        }
        return playerId;
    }

    private void advanceGeneration(EntityPlayer player, UUID playerId) {
        PlayerCache old = playerCaches.get(playerId);
        long generation = old == null ? 1L : old.generation + 1L;
        playerCaches.put(playerId, new PlayerCache(currentTick(player), -1L, generation));
    }

    private void requireRegistration(ResourceLocation id, Object provider) {
        if (frozen) {
            throw new IllegalStateException("JAWMS modifier-provider registration is frozen.");
        }
        if (id == null) {
            throw new NullPointerException("providerId");
        }
        if (provider == null) {
            throw new NullPointerException("provider");
        }
    }

    private void put(ResourceLocation id, Registration registration) {
        if (registrations.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate JAWMS modifier-provider ID: " + id);
        }
        registrations.put(id, registration);
    }

    private PlayerCache cache(EntityPlayer player, GameplayRules rules) {
        UUID id = player.getUniqueID();
        long tick = currentTick(player);
        PlayerCache cache = playerCaches.get(id);
        if (cache == null || cache.tick != tick || cache.rulesRevision != rules.getRevision()) {
            long generation = cache == null ? 0L : cache.generation;
            cache = new PlayerCache(tick, rules.getRevision(), generation);
            playerCaches.put(id, cache);
        }
        return cache;
    }

    private CachedEvaluation evaluateItemProvider(
            EntityPlayer player,
            ManaItemContext context,
            ItemRegistration registration
    ) {
        try (ManaEvaluationGuard.Scope ignored = ManaEvaluationGuard.begin(player.getUniqueID())) {
            ManaContribution contribution = registration.provider.getContribution(player, context);
            if (contribution == null) {
                diagnostic(player, registration.id, "item", context.toString(), null);
                return CachedEvaluation.invalid(ManaContributionFailure.NULL_RESULT);
            }
            try {
                ManaProfileResolver.validateContribution(contribution);
            } catch (RuntimeException exception) {
                diagnostic(player, registration.id, "item", context.toString(), exception);
                return CachedEvaluation.invalid(ManaContributionFailure.INVALID_RESULT);
            }
            return CachedEvaluation.accepted(contribution);
        } catch (RuntimeException exception) {
            diagnostic(player, registration.id, "item", context.toString(), exception);
            return CachedEvaluation.failed();
        }
    }

    private CachedEvaluation evaluateContextualProvider(
            EntityPlayer player,
            ContextualRegistration registration
    ) {
        try (ManaEvaluationGuard.Scope ignored = ManaEvaluationGuard.begin(player.getUniqueID())) {
            ManaContribution contribution = registration.provider.getContribution(player);
            if (contribution == null) {
                diagnostic(player, registration.id, "contextual", "player", null);
                return CachedEvaluation.invalid(ManaContributionFailure.NULL_RESULT);
            }
            try {
                ManaProfileResolver.validateContribution(contribution);
            } catch (RuntimeException exception) {
                diagnostic(player, registration.id, "contextual", "player", exception);
                return CachedEvaluation.invalid(ManaContributionFailure.INVALID_RESULT);
            }
            return CachedEvaluation.accepted(contribution);
        } catch (RuntimeException exception) {
            diagnostic(player, registration.id, "contextual", "player", exception);
            return CachedEvaluation.failed();
        }
    }

    private static List<ManaContribution> acceptedContributions(
            List<ContributionEvaluationRecord> records
    ) {
        List<ManaContribution> result = new ArrayList<>();
        for (ContributionEvaluationRecord record : records) {
            if (record.getDisposition() == ManaContributionDisposition.APPLIED) {
                result.add(record.getContribution());
            }
        }
        return result;
    }

    private void diagnostic(
            EntityPlayer player,
            ResourceLocation id,
            String type,
            String context,
            RuntimeException exception
    ) {
        long tick = currentTick(player);
        String key = player.getUniqueID() + "|" + id + "|" + type
                + "|" + context + "|" + (exception == null ? "null"
                : exception.getClass().getName());
        Long last = lastDiagnostics.get(key);
        if (last != null && tick >= last && tick - last < DIAGNOSTIC_INTERVAL_TICKS) {
            return;
        }
        lastDiagnostics.put(key, tick);
        diagnosticEmissions++;
        String message = "Ignored JAWMS modifier-provider result: providerId=" + id
                + ", type=" + type + ", player=" + player.getUniqueID()
                + ", context=" + context + ".";
        if (exception == null) {
            JustAnotherWizardryManaSystemMod.LOGGER.warn(message + " Provider returned null.");
        } else {
            JustAnotherWizardryManaSystemMod.LOGGER.warn(message, exception);
        }
    }

    private static long currentTick(EntityPlayer player) {
        return player.world.getTotalWorldTime();
    }

    synchronized int diagnosticEntryCount() {
        return lastDiagnostics.size();
    }

    synchronized long diagnosticEmissionCount() {
        return diagnosticEmissions;
    }

    private static void requireEvaluationArguments(EntityPlayer player, GameplayRules rules) {
        if (player == null || rules == null) {
            throw new NullPointerException("player and rules must not be null");
        }
    }

    private abstract static class Registration {
        final ResourceLocation id;

        private Registration(ResourceLocation id) {
            this.id = id;
        }
    }

    private static final class ItemRegistration extends Registration {
        private final Item item;
        private final IItemManaModifierProvider provider;

        private ItemRegistration(ResourceLocation id, Item item, IItemManaModifierProvider provider) {
            super(id);
            this.item = item;
            this.provider = provider;
        }
    }

    private static final class ContextualRegistration extends Registration {
        private final IContextualManaModifierProvider provider;

        private ContextualRegistration(ResourceLocation id, IContextualManaModifierProvider provider) {
            super(id);
            this.provider = provider;
        }
    }

    private static final class PlayerCache {
        private final long tick;
        private final long rulesRevision;
        private final long generation;
        private final Map<CacheKey, CachedEvaluation> values = new HashMap<>();

        private PlayerCache(long tick, long rulesRevision, long generation) {
            this.tick = tick;
            this.rulesRevision = rulesRevision;
            this.generation = generation;
        }
    }

    private static final class CachedEvaluation {
        private final ManaContributionDisposition disposition;
        private final ManaContributionFailure failure;
        private final ManaContribution contribution;

        private CachedEvaluation(
                ManaContributionDisposition disposition,
                ManaContributionFailure failure,
                ManaContribution contribution
        ) {
            this.disposition = disposition;
            this.failure = failure;
            this.contribution = contribution;
        }

        private static CachedEvaluation accepted(ManaContribution contribution) {
            return contribution.isEmpty()
                    ? new CachedEvaluation(
                    ManaContributionDisposition.EMPTY, ManaContributionFailure.NONE, null
            )
                    : new CachedEvaluation(
                    ManaContributionDisposition.APPLIED, ManaContributionFailure.NONE, contribution
            );
        }

        private static CachedEvaluation invalid(ManaContributionFailure failure) {
            return new CachedEvaluation(ManaContributionDisposition.INVALID, failure, null);
        }

        private static CachedEvaluation failed() {
            return new CachedEvaluation(
                    ManaContributionDisposition.FAILED,
                    ManaContributionFailure.PROVIDER_EXCEPTION,
                    null
            );
        }
    }

    private static final class CacheKey {
        private final ResourceLocation providerId;
        private final Object slotCategory;
        private final int slotIndex;
        private final StackFingerprint stack;

        private CacheKey(
                ResourceLocation providerId,
                Object slotCategory,
                int slotIndex,
                StackFingerprint stack
        ) {
            this.providerId = providerId;
            this.slotCategory = slotCategory;
            this.slotIndex = slotIndex;
            this.stack = stack;
        }

        private static CacheKey contextual(ResourceLocation providerId) {
            return new CacheKey(providerId, null, -1, null);
        }

        private static CacheKey item(
                ResourceLocation providerId,
                ManaItemContext context,
                StackFingerprint stack
        ) {
            return new CacheKey(
                    providerId, context.getSlotCategory(), context.getSlotIndex(), stack
            );
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey)) {
                return false;
            }
            CacheKey that = (CacheKey) other;
            return slotIndex == that.slotIndex
                    && providerId.equals(that.providerId)
                    && java.util.Objects.equals(slotCategory, that.slotCategory)
                    && java.util.Objects.equals(stack, that.stack);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(providerId, slotCategory, slotIndex, stack);
        }
    }

    /** Collision-safe equality; hash codes only select a bucket. */
    private static final class StackFingerprint {
        private final Item item;
        private final int metadata;
        private final int count;
        private final NBTTagCompound tag;

        private StackFingerprint(Item item, int metadata, int count, NBTTagCompound tag) {
            this.item = item;
            this.metadata = metadata;
            this.count = count;
            this.tag = tag;
        }

        private static StackFingerprint of(ItemStack stack) {
            return new StackFingerprint(
                    stack.getItem(),
                    stack.getMetadata(),
                    stack.getCount(),
                    stack.hasTagCompound() ? stack.getTagCompound().copy() : null
            );
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof StackFingerprint)) {
                return false;
            }
            StackFingerprint that = (StackFingerprint) other;
            return item == that.item && metadata == that.metadata && count == that.count
                    && java.util.Objects.equals(tag, that.tag);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(System.identityHashCode(item), metadata, count, tag);
        }
    }
}
