package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderPreviewRegistryTest {

    @Test
    void initialDeclarationReplacementClearingAndServerResetNeverEvaluateProvider() {
        ManaProviderRegistry providers = new ManaProviderRegistry();
        ProviderPreviewRegistry previews = new ProviderPreviewRegistry(providers);
        ResourceLocation id = new ResourceLocation("fixture", "stick");
        AtomicInteger calls = new AtomicInteger();
        providers.registerItem(id, item("stick"), (player, context) -> {
            calls.incrementAndGet();
            return ManaContribution.EMPTY;
        });
        ManaContribution initial = ManaContribution.builder().flatMaximumMana(2).build();
        previews.registerInitial(id, initial);

        assertEquals(2, previews.declarations().get(0)
                .getContribution().getFlatMaximumMana());
        previews.serverStarted();
        previews.replaceActive(
                id, ManaContribution.builder().flatMaximumMana(5).build()
        );
        assertEquals(5, previews.declarations().get(0)
                .getContribution().getFlatMaximumMana());
        assertEquals(0, calls.get());

        assertEquals(true, previews.clearActive(id));
        assertEquals(0, previews.declarations().size());
        assertEquals(false, previews.clearActive(id));

        previews.serverStopped();
        assertEquals(2, previews.declarations().get(0)
                .getContribution().getFlatMaximumMana());
        assertEquals(0, calls.get());
    }

    @Test
    void rejectsUnknownContextualDuplicateLateNullAndInvalidDeclarations() throws Exception {
        ManaProviderRegistry providers = new ManaProviderRegistry();
        ProviderPreviewRegistry previews = new ProviderPreviewRegistry(providers);
        ResourceLocation item = new ResourceLocation("fixture", "item");
        ResourceLocation other = new ResourceLocation("fixture", "other");
        ResourceLocation contextual = new ResourceLocation("fixture", "contextual");
        providers.registerItem(item, item("item"), (player, context) -> ManaContribution.EMPTY);
        providers.registerItem(other, item("other"), (player, context) -> ManaContribution.EMPTY);
        providers.registerContextual(contextual, player -> ManaContribution.EMPTY);
        previews.registerInitial(item, ManaContribution.builder().flatMaximumMana(1).build());

        assertThrows(IllegalArgumentException.class, () -> previews.registerInitial(
                item, ManaContribution.builder().flatMaximumMana(2).build()
        ));
        assertThrows(IllegalArgumentException.class, () -> previews.registerInitial(
                contextual, ManaContribution.builder().flatMaximumMana(1).build()
        ));
        assertThrows(IllegalArgumentException.class, () -> previews.registerInitial(
                new ResourceLocation("fixture", "missing"),
                ManaContribution.builder().flatMaximumMana(1).build()
        ));
        assertThrows(NullPointerException.class, () -> previews.registerInitial(item, null));

        ManaContribution corrupted = ManaContribution.builder().flatMaximumMana(1).build();
        java.lang.reflect.Field field = ManaContribution.class.getDeclaredField(
                "globalSpellCostMultiplier"
        );
        field.setAccessible(true);
        field.setDouble(corrupted, Double.NaN);
        assertThrows(IllegalArgumentException.class, () -> previews.registerInitial(
                other, corrupted
        ));

        providers.freeze();
        assertThrows(IllegalStateException.class, () -> previews.registerInitial(
                item, ManaContribution.builder().flatMaximumMana(3).build()
        ));
        assertThrows(IllegalStateException.class, () -> previews.replaceActive(
                item, ManaContribution.builder().flatMaximumMana(3).build()
        ));
    }

    private static Item item(String path) {
        return new Item().setRegistryName("fixture", path);
    }
}
