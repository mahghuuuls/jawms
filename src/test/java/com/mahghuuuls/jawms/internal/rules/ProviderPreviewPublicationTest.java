package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import com.mahghuuuls.jawms.internal.mana.ManaEvaluationGuard;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPreviewPublicationTest {

    @AfterEach
    void stopServer() {
        GameplayRulesManager.serverStopped();
        ManaProviderRegistry.instance().clearRuntimeState();
    }

    @Test
    void activeMutationPublishesOneRulesRevisionWithoutProviderEvaluation() throws Exception {
        ResourceLocation id = new ResourceLocation("fixture", "publication");
        AtomicInteger calls = new AtomicInteger();
        ManaApi.registerItemProvider(
                id, item("publication_item"),
                (player, context) -> {
                    calls.incrementAndGet();
                    return ManaContribution.builder().flatMaximumMana(99).build();
                },
                ManaContribution.builder().flatMaximumMana(1).build()
        );
        GameplayRulesManager.completeProviderRegistration();
        assertEquals(1, preview(id).getContribution().getFlatMaximumMana());
        GameplayRulesManager.serverStarted();
        long before = GameplayRulesManager.current().getRevision();

        ManaApi.setItemProviderContributionPreview(
                id, ManaContribution.builder().flatMaximumMana(2).build()
        );

        GameplayRules replaced = GameplayRulesManager.current();
        assertTrue(replaced.getRevision() > before);
        assertEquals(1, replaced.getProviderPreviews().size());
        assertEquals(2, replaced.getProviderPreviews().get(0)
                .getContribution().getFlatMaximumMana());
        assertEquals(0, calls.get());

        long replacedRevision = replaced.getRevision();
        AtomicReference<RuntimeException> offThread = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                ManaApi.clearItemProviderContributionPreview(id);
            } catch (RuntimeException exception) {
                offThread.set(exception);
            }
        }, "preview-off-thread");
        worker.start();
        worker.join();
        assertTrue(offThread.get() instanceof IllegalStateException);
        assertEquals(replacedRevision, GameplayRulesManager.current().getRevision());

        ManaApi.clearItemProviderContributionPreview(id);
        assertTrue(GameplayRulesManager.current().getRevision() > replacedRevision);
        assertEquals(0, GameplayRulesManager.current().getProviderPreviews().size());
        long clearedRevision = GameplayRulesManager.current().getRevision();
        ManaApi.clearItemProviderContributionPreview(id);
        assertEquals(clearedRevision, GameplayRulesManager.current().getRevision());
        assertEquals(0, calls.get());

        GameplayRulesManager.serverStopped();
        assertEquals(1, preview(id).getContribution().getFlatMaximumMana());
    }

    @Test
    void activeMutationRejectsUnknownContextualNullAndInvalidPreviews() {
        GameplayRulesManager.serverStarted();
        ResourceLocation contextual = new ResourceLocation("fixture", "publication_context");
        ManaApi.registerContextualProvider(contextual, player -> ManaContribution.EMPTY);

        assertThrows(IllegalArgumentException.class, () ->
                ManaApi.setItemProviderContributionPreview(
                        contextual, ManaContribution.builder().flatMaximumMana(1).build()
                )
        );
        ResourceLocation itemProvider = new ResourceLocation("fixture", "null_preview");
        ManaApi.registerItemProvider(
                itemProvider, item("null_preview_item"),
                (player, context) -> ManaContribution.EMPTY
        );
        assertThrows(NullPointerException.class, () ->
                ManaApi.setItemProviderContributionPreview(itemProvider, null)
        );
        assertThrows(IllegalArgumentException.class, () ->
                ManaApi.clearItemProviderContributionPreview(
                        new ResourceLocation("fixture", "missing")
                )
        );
        try (ManaEvaluationGuard.Scope ignored = ManaEvaluationGuard.begin(
                UUID.fromString("00000000-0000-0000-0000-000000000035")
        )) {
            assertThrows(IllegalStateException.class, () ->
                    ManaApi.setItemProviderContributionPreview(
                            itemProvider,
                            ManaContribution.builder().flatMaximumMana(1).build()
                    )
            );
        }
    }

    private static Item item(String path) {
        return new Item().setRegistryName("fixture", path);
    }

    private static ProviderPreviewEntry preview(ResourceLocation providerId) {
        for (ProviderPreviewEntry preview : GameplayRulesManager.current()
                .getProviderPreviews()) {
            if (providerId.equals(preview.getProviderId())) {
                return preview;
            }
        }
        throw new AssertionError("Missing provider preview: " + providerId);
    }
}
