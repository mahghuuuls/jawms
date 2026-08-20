package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.ProviderPreviewEntry;
import com.mahghuuuls.jawms.internal.rules.ProviderPreviewIndex;
import com.mahghuuuls.jawms.internal.rules.ProviderPreviewIndexTestAccess;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorEfficiencyProfile;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPreviewIndexTest {

    @Test
    void acceptsEligiblePreviewsInProviderOrderWithoutCallingProviders() {
        ManaProviderRegistry providers = new ManaProviderRegistry();
        ProviderPreviewRegistry previews = new ProviderPreviewRegistry(providers);
        int[] calls = {0};
        ResourceLocation z = id("z_provider");
        ResourceLocation a = id("a_provider");
        Item apple = item("apple");
        Item stick = item("stick");
        providers.registerItem(z, apple, (player, context) -> {
            calls[0]++;
            return ManaContribution.EMPTY;
        });
        providers.registerItem(a, stick, (player, context) -> {
            calls[0]++;
            return ManaContribution.EMPTY;
        });
        previews.registerInitial(z, ManaContribution.builder().flatRegeneration(1).build());
        previews.registerInitial(a, ManaContribution.builder().flatMaximumMana(3).build());

        List<ProviderPreviewEntry> accepted = ProviderPreviewIndex.build(
                GameplayRules.initialDefaults(), previews.declarations()
        );

        assertEquals(Arrays.asList(a, z), Arrays.asList(
                accepted.get(0).getProviderId(), accepted.get(1).getProviderId()
        ));
        assertEquals(0, calls[0]);
    }

    @Test
    void omitsDisabledConfiguredNeutralUnavailableAndClearedDeclarations() {
        ManaProviderRegistry providers = new ManaProviderRegistry();
        ProviderPreviewRegistry previews = new ProviderPreviewRegistry(providers);
        ResourceLocation configured = id("configured");
        ResourceLocation disabled = id("disabled");
        ResourceLocation neutral = id("neutral");
        ResourceLocation unavailable = id("unavailable");
        Item stick = item("configured_item");
        providers.registerItem(configured, stick,
                (player, context) -> ManaContribution.EMPTY);
        providers.registerItem(disabled, item("disabled_item"),
                (player, context) -> ManaContribution.EMPTY);
        providers.registerItem(neutral, item("neutral_item"),
                (player, context) -> ManaContribution.EMPTY);
        net.minecraft.item.Item unregistered = new net.minecraft.item.Item();
        providers.registerItem(unavailable, unregistered,
                (player, context) -> ManaContribution.EMPTY);
        previews.registerInitial(configured,
                ManaContribution.builder().flatMaximumMana(1).build());
        previews.registerInitial(disabled,
                ManaContribution.builder().flatMaximumMana(2).build());
        previews.registerInitial(neutral, ManaContribution.EMPTY);
        previews.registerInitial(unavailable,
                ManaContribution.builder().flatMaximumMana(4).build());

        GameplayRules rules = new GameplayRules(
                4L, 60, 2.4D, 2.0D, GameplayRules.DEFAULT_COST_FORMULA,
                20, 50, 100, Collections.emptyMap(),
                Collections.singletonList(new ConfiguredItemRule(
                        stick.getRegistryName(), ConfiguredItemRule.WILDCARD_METADATA,
                        ManaContribution.builder().flatMaximumMana(9).build()
                )),
                WizardryArmorProfile.DEFAULT,
                new LinkedHashSet<>(Collections.singletonList(disabled))
        );

        assertEquals(0, ProviderPreviewIndex.build(
                rules, previews.declarations()
        ).size());

        previews.serverStarted();
        previews.clearActive(configured);
        assertEquals(0, ProviderPreviewIndex.build(
                rules, previews.declarations()
        ).size());
    }

    @Test
    void exactMetadataOverrideExcludesOnlyThatVariant() {
        ManaProviderRegistry providers = new ManaProviderRegistry();
        ProviderPreviewRegistry previews = new ProviderPreviewRegistry(providers);
        ResourceLocation providerId = id("metadata_provider");
        Item item = item("metadata_item");
        providers.registerItem(providerId, item,
                (player, context) -> ManaContribution.EMPTY);
        previews.registerInitial(providerId,
                ManaContribution.builder().flatMaximumMana(5).build());
        GameplayRules rules = new GameplayRules(
                5L, 60, 2.4D, 2.0D, GameplayRules.DEFAULT_COST_FORMULA,
                20, 50, 100, Collections.emptyMap(),
                Collections.singletonList(new ConfiguredItemRule(
                        item.getRegistryName(), 0,
                        ManaContribution.builder().flatMaximumMana(9).build()
                )),
                WizardryArmorProfile.DEFAULT
        );

        List<ProviderPreviewEntry> accepted = ProviderPreviewIndex.build(
                rules, previews.declarations()
        );

        assertEquals(1, accepted.size());
        assertEquals(Collections.singleton(0), accepted.get(0).getExcludedMetadata());
        assertTrue(!accepted.get(0).appliesToMetadata(0));
        assertTrue(accepted.get(0).appliesToMetadata(1));
    }

    @Test
    void generatedOverrideTracksWhetherEitherGeneratedArmorLayerIsEnabled() {
        ManaProviderRegistry providers = new ManaProviderRegistry();
        ProviderPreviewRegistry previews = new ProviderPreviewRegistry(providers);
        ResourceLocation providerId = id("armor_provider");
        Item item = item("armor_item");
        providers.registerItem(providerId, item,
                (player, context) -> ManaContribution.EMPTY);
        previews.registerInitial(providerId,
                ManaContribution.builder().flatMaximumMana(5).build());
        ResourceLocation itemId = item.getRegistryName();
        GameplayRules disabledLayers = rulesWithArmorLayers(false, false);

        assertTrue(ProviderPreviewIndexTestAccess.generatedOverrideItems(
                disabledLayers
        ).isEmpty());
        assertEquals(1, ProviderPreviewIndexTestAccess.build(
                disabledLayers, previews.declarations(), Collections.emptySet()
        ).size());
        assertEquals(0, ProviderPreviewIndexTestAccess.build(
                disabledLayers, previews.declarations(), Collections.singleton(itemId)
        ).size());
    }

    private static GameplayRules rulesWithArmorLayers(
            boolean manaEnabled,
            boolean efficiencyEnabled
    ) {
        return new GameplayRules(
                6L, 60, 2.4D, 2.0D, GameplayRules.DEFAULT_COST_FORMULA,
                20, 50, 100, Collections.emptyMap(), Collections.emptyList(),
                new WizardryArmorProfile(
                        manaEnabled, 0, 0, 0, 0, 0, 0, 0, 0
                ),
                Collections.emptySet(), GameplayRules.DEFAULT_EFFICIENCY_SCALE,
                new WizardryArmorEfficiencyProfile(
                        efficiencyEnabled, 0, 0, 0, 0, 0
                )
        );
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("fixture", path);
    }

    private static Item item(String path) {
        return new Item().setRegistryName("fixture", path);
    }
}
