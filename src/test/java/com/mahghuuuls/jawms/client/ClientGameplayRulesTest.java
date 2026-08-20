package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import com.mahghuuuls.jawms.internal.rules.ProviderPreviewEntry;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryFlaskBridge;
import electroblob.wizardry.item.ItemManaFlask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.minecraft.util.ResourceLocation;
import net.minecraft.item.Item;
import com.mahghuuuls.jawms.api.ManaContribution;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClientGameplayRulesTest {

    @AfterEach
    void reset() {
        ClientGameplayRules.clear();
        WizardryFlaskBridge.refreshCapacities(20, 50, 100);
    }

    @Test
    void acceptsOnlyNewerRuleRevisionsAndClearsAcrossSessions() {
        assertSame(
                ConfiguredFlaskSnapshot.unavailable(),
                ClientGameplayRules.getConfiguredFlaskSnapshot()
        );
        GameplayRulesMessage first = new GameplayRulesMessage(GameplayRules.initialDefaults());
        ClientGameplayRules.accept(first);

        assertEquals(0L, ClientGameplayRules.getRevision());
        assertEquals(60, ClientGameplayRules.getBaseMaximum());
        assertEquals(2.4D, ClientGameplayRules.getBaseRegeneration());
        assertEquals(40L, ClientGameplayRules.getLockoutTicks());
        assertEquals(20, ClientGameplayRules.getSmallFlask());
        assertEquals(
                ConfiguredFlaskSnapshot.available(20, 50, 100, 0L),
                ClientGameplayRules.getConfiguredFlaskSnapshot()
        );
        assertEquals(
                17.0D,
                ClientGameplayRules.getSpellCost(
                        new ResourceLocation("example", "missing"),
                        17.0D
                )
        );

        ClientGameplayRules.clear();
        assertSame(
                ConfiguredFlaskSnapshot.unavailable(),
                ClientGameplayRules.getConfiguredFlaskSnapshot()
        );
        ClientGameplayRules.accept(first);
        assertEquals(0L, ClientGameplayRules.getRevision());
    }

    @Test
    void refreshesCapacitiesOnlyForAcceptedClientRevisions() {
        GameplayRules accepted = rules(2L, 21, 51, 101)
                .withWizardryIntegrationRules(WizardryIntegrationRules.defaults(7L));
        ClientGameplayRules.accept(new GameplayRulesMessage(accepted));
        ConfiguredFlaskSnapshot acceptedSnapshot =
                ClientGameplayRules.getConfiguredFlaskSnapshot();
        assertEquals(21, ItemManaFlask.Size.SMALL.capacity);
        assertEquals(51, ItemManaFlask.Size.MEDIUM.capacity);
        assertEquals(101, ItemManaFlask.Size.LARGE.capacity);
        assertEquals(7L, ClientGameplayRules.getIntegrationRules().getRevision());

        ItemManaFlask.Size.SMALL.capacity = 999;
        assertEquals(acceptedSnapshot, ClientGameplayRules.getConfiguredFlaskSnapshot());
        WizardryFlaskBridge.refreshCapacities(21, 51, 101);

        GameplayRules stale = rules(1L, 1, 2, 3)
                .withWizardryIntegrationRules(WizardryIntegrationRules.defaults(99L));
        ClientGameplayRules.accept(new GameplayRulesMessage(stale));
        assertEquals(21, ItemManaFlask.Size.SMALL.capacity);
        assertEquals(51, ItemManaFlask.Size.MEDIUM.capacity);
        assertEquals(101, ItemManaFlask.Size.LARGE.capacity);
        assertEquals(7L, ClientGameplayRules.getIntegrationRules().getRevision());
        assertEquals(acceptedSnapshot, ClientGameplayRules.getConfiguredFlaskSnapshot());

        ClientGameplayRules.clear();
        assertEquals(0L, ClientGameplayRules.getIntegrationRules().getRevision());
    }

    @Test
    void atomicallyAcceptsAndClearsProviderPreviewsWithFlaskSnapshot() {
        Item stick = new Item().setRegistryName("fixture", "client_stick");
        ProviderPreviewEntry preview = new ProviderPreviewEntry(
                new ResourceLocation("fixture", "stick"),
                stick.getRegistryName(),
                ManaContribution.builder().flatMaximumMana(4).build(),
                Collections.singleton(0)
        );
        GameplayRules accepted = rules(3L, 22, 52, 102)
                .withRevisionAndProviderPreviews(3L, Collections.singletonList(preview));

        ClientGameplayRules.accept(new GameplayRulesMessage(accepted));

        assertEquals(3L, ClientGameplayRules.getConfiguredFlaskSnapshot().getRevision());
        assertEquals(1, ClientGameplayRules.getProviderPreviews(
                stick.getRegistryName()
        ).size());
        assertEquals(0, ClientGameplayRules.getProviderPreviews(
                stick.getRegistryName(), 0
        ).size());
        assertEquals(1, ClientGameplayRules.getProviderPreviews(
                stick.getRegistryName(), 1
        ).size());
        assertEquals(4, ClientGameplayRules.getProviderPreviews(
                stick.getRegistryName()
        ).get(0).getContribution().getFlatMaximumMana());
        assertThrows(UnsupportedOperationException.class, () ->
                ClientGameplayRules.getProviderPreviews(stick.getRegistryName()).clear()
        );

        ClientGameplayRules.accept(new GameplayRulesMessage(
                rules(2L, 1, 2, 3)
        ));
        assertEquals(1, ClientGameplayRules.getProviderPreviews(
                stick.getRegistryName()
        ).size());

        ClientGameplayRules.clear();
        assertSame(ConfiguredFlaskSnapshot.unavailable(),
                ClientGameplayRules.getConfiguredFlaskSnapshot());
        assertEquals(0, ClientGameplayRules.getProviderPreviews(
                stick.getRegistryName()
        ).size());
    }

    private static GameplayRules rules(
            long revision, int small, int medium, int large
    ) {
        return new GameplayRules(
                revision, 50, 2.0D, 40L,
                GameplayRules.DEFAULT_COST_FORMULA,
                small, medium, large,
                Collections.emptyMap(), Collections.emptyList(),
                WizardryArmorProfile.DEFAULT
        );
    }
}
