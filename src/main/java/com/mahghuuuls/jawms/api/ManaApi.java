package com.mahghuuuls.jawms.api;

import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
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
}
