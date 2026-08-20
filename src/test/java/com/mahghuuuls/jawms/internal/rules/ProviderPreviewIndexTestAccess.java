package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.internal.modifier.ProviderPreviewDeclaration;
import net.minecraft.util.ResourceLocation;

import java.util.List;
import java.util.Set;

/** Test-only access to package-private pure preview-index boundaries. */
public final class ProviderPreviewIndexTestAccess {

    private ProviderPreviewIndexTestAccess() {
    }

    public static List<ProviderPreviewEntry> build(
            GameplayRules rules,
            List<ProviderPreviewDeclaration> declarations,
            Set<ResourceLocation> generatedOverrideItems
    ) {
        return ProviderPreviewIndex.build(rules, declarations, generatedOverrideItems);
    }

    public static Set<ResourceLocation> generatedOverrideItems(GameplayRules rules) {
        return ProviderPreviewIndex.generatedOverrideItems(rules);
    }
}
