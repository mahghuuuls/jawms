package com.mahghuuuls.jawms.api;

import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

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
     * Forces one fresh modifier evaluation for this logical-server player in
     * the current tick. Calling this from a provider callback is rejected.
     */
    public static void invalidateModifierCache(EntityPlayer player) {
        ManaProviderRegistry.instance().invalidate(player);
    }
}
