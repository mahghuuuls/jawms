package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.internal.modifier.ProviderPreviewDeclaration;
import com.mahghuuuls.jawms.internal.modifier.ProviderPreviewRegistry;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

/** Builds the sole server-authoritative eligible preview index for one rules revision. */
public final class ProviderPreviewIndex {

    private ProviderPreviewIndex() {
    }

    public static List<ProviderPreviewEntry> build(
            GameplayRules rules,
            List<ProviderPreviewDeclaration> declarations
    ) {
        return build(rules, declarations, generatedOverrideItems(rules));
    }

    static List<ProviderPreviewEntry> build(
            GameplayRules rules,
            List<ProviderPreviewDeclaration> declarations,
            Set<ResourceLocation> generatedOverrideItems
    ) {
        if (rules == null || declarations == null) {
            throw new NullPointerException("rules and declarations must not be null");
        }
        if (generatedOverrideItems == null) {
            throw new NullPointerException("generatedOverrideItems");
        }
        Set<ResourceLocation> fullyOverriddenItems = new HashSet<>();
        Map<ResourceLocation, SortedSet<Integer>> excludedMetadata = new HashMap<>();
        for (ConfiguredItemRule rule : rules.getConfiguredItemRules()) {
            if (rule.getMetadata() == ConfiguredItemRule.WILDCARD_METADATA) {
                fullyOverriddenItems.add(rule.getItemId());
                excludedMetadata.remove(rule.getItemId());
            } else if (!fullyOverriddenItems.contains(rule.getItemId())) {
                excludedMetadata.computeIfAbsent(
                        rule.getItemId(), ignored -> new TreeSet<>()
                ).add(rule.getMetadata());
            }
        }
        fullyOverriddenItems.addAll(generatedOverrideItems);

        List<ProviderPreviewDeclaration> ordered = new ArrayList<>(declarations);
        ordered.sort(Comparator.comparing(ProviderPreviewDeclaration::getProviderId));
        List<ProviderPreviewEntry> accepted = new ArrayList<>();
        for (ProviderPreviewDeclaration declaration : ordered) {
            ResourceLocation itemId = declaration.getTargetItemId();
            if (itemId == null
                    || rules.isProviderDisabled(declaration.getProviderId())
                    || fullyOverriddenItems.contains(itemId)
                    || declaration.getContribution().isEmpty()) {
                continue;
            }
            try {
                ProviderPreviewRegistry.validateContribution(declaration.getContribution());
            } catch (RuntimeException ignored) {
                continue;
            }
            accepted.add(new ProviderPreviewEntry(
                    declaration.getProviderId(), itemId, declaration.getContribution(),
                    excludedMetadata.getOrDefault(itemId, java.util.Collections.emptySortedSet())
            ));
        }
        return accepted;
    }

    static Set<ResourceLocation> generatedOverrideItems(GameplayRules rules) {
        if (!rules.getArmorProfile().isEnabled()
                && !rules.getArmorEfficiencyProfile().isEnabled()) {
            return java.util.Collections.emptySet();
        }
        return rules.getGeneratedWizardryArmorIds();
    }
}
