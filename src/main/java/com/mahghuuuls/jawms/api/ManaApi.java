package com.mahghuuuls.jawms.api;

import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import com.mahghuuuls.jawms.internal.modifier.ProviderPreviewRegistry;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.List;

/**
 * Stable entry point for the JAWMS player-mana API.
 */
public final class ManaApi {

    public static final String API_VERSION = ManaApiVersion.CURRENT;

    private static final IManaService SERVICE = new ManaService();

    private ManaApi() {
    }

    public static IManaService getManaService() {
        return SERVICE;
    }

    /**
     * Register during pre-initialization/initialization, before load-complete.
     * IDs are unique; callbacks and settlement follow lexicographic full-ID order.
     * Applicability is decided per effect, independently of spell namespace.
     * Registrations survive world restart; enablement changes require callbacks
     * to return not-applicable or an application restart to change registration.
     */
    public static void registerCastCostParticipant(
            ResourceLocation participantId, ICastCostParticipant participant) {
        com.mahghuuuls.jawms.internal.casting.CastCostRegistry.instance()
                .register(participantId, participant);
    }

    /**
     * Returns the authoritative accepted ordinary-flask configuration on the
     * active logical-server thread. Other threads receive an unavailable
     * snapshot and client code must use {@code ClientManaApi}. This read does
     * not expose or consult Wizardry's mutable flask enum.
     */
    public static ConfiguredFlaskSnapshot getConfiguredFlaskSnapshot() {
        return GameplayRulesManager.configuredFlaskSnapshot();
    }

    /**
     * Registers a globally unique provider ID during Forge pre-initialization
     * or initialization. Registrations are evaluated in provider-ID order and
     * registration closes at load-complete. Duplicate IDs and late calls fail.
     */
    public static void registerItemProvider(
            ResourceLocation providerId,
            Item targetItem,
            IItemManaModifierProvider provider
    ) {
        ManaProviderRegistry.instance().registerItem(providerId, targetItem, provider);
    }

    /**
     * Registers an item provider together with one invariant item-wide
     * presentation preview. The preview is the addon's promise that the value
     * truthfully describes every matching stack independent of player, slot,
     * or stack data. Dynamic providers should use the three-argument overload.
     */
    public static void registerItemProvider(
            ResourceLocation providerId,
            Item targetItem,
            IItemManaModifierProvider provider,
            ManaContribution contributionPreview
    ) {
        ManaProviderRegistry.instance().registerItemWithPreview(
                providerId, targetItem, provider, contributionPreview
        );
    }

    /**
     * Replaces an invariant item-provider preview during an active server.
     * This is presentation-only, requires the logical-server thread, and
     * publishes one new complete gameplay-rules revision to clients.
     */
    public static void setItemProviderContributionPreview(
            ResourceLocation providerId,
            ManaContribution contributionPreview
    ) {
        GameplayRulesManager.replaceProviderPreview(providerId, contributionPreview);
    }

    /**
     * Clears an item-provider preview during an active server. Clearing an
     * absent declaration is a no-op; gameplay provider evaluation is unchanged.
     */
    public static void clearItemProviderContributionPreview(
            ResourceLocation providerId
    ) {
        GameplayRulesManager.clearProviderPreview(providerId);
    }

    /**
     * Registers a globally unique player-wide provider under the same ordering
     * and lifecycle rules as {@link #registerItemProvider}.
     */
    public static void registerContextualProvider(
            ResourceLocation providerId,
            IContextualManaModifierProvider provider
    ) {
        ManaProviderRegistry.instance().registerContextual(providerId, provider);
    }

    /**
     * Invalidates cached modifiers so this logical-server player's next
     * profile evaluation is fresh. Call only on the server thread after
     * authoritative addon state changes. Calling this from a provider callback
     * is rejected.
     */
    public static void invalidateModifierCache(EntityPlayer player) {
        ManaProviderRegistry.instance().invalidate(player);
    }

    /** Returns whether public modifier-provider registration has closed. */
    public static boolean isProviderRegistrationFrozen() {
        return ManaProviderRegistry.instance().isFrozen();
    }

    /**
     * Returns an immutable provider-ID-sorted snapshot of registrations
     * accepted so far.
     */
    public static List<ManaProviderDescriptor> getRegisteredProviders() {
        return ManaProviderRegistry.instance().descriptors();
    }

    /**
     * Coalesces a refresh for the player's END tick. Use this from callbacks
     * that run before the authoritative state transition they report. This
     * requires the logical server thread and never evaluates synchronously.
     */
    public static void markModifierCacheDirty(EntityPlayer player) {
        ManaProviderRegistry.instance().markDirty(player);
    }

    /**
     * Returns the last profile evaluation accepted for this logical-server
     * player. The query uses retained provenance and never invokes providers.
     *
     * @throws IllegalStateException when no evaluation has been accepted yet
     */
    public static ManaProfileExplanation explainManaProfile(EntityPlayer player) {
        return ManaService.explainAcceptedProfile(player);
    }
}
