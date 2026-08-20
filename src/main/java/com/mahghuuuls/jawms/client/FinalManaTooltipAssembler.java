package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaTooltips;
import com.mahghuuuls.jawms.internal.rules.ProviderPreviewEntry;
import net.minecraft.item.ItemStack;

import java.util.List;

/** Appends JAWMS contribution text after ordinary Forge tooltip subscribers finish. */
public final class FinalManaTooltipAssembler {

    private FinalManaTooltipAssembler() {
    }

    public static void append(ItemStack stack, List<String> tooltip) {
        if (stack == null || stack.isEmpty() || tooltip == null) {
            return;
        }
        appendContributions(
                tooltip,
                ManaItemTooltipHandler.contributionFor(stack),
                ClientGameplayRules.getProviderPreviews(stack)
        );
    }

    static void appendContributions(
            List<String> tooltip,
            ManaContribution configuredOrGenerated,
            List<ProviderPreviewEntry> providerPreviews
    ) {
        if (tooltip == null || providerPreviews == null) {
            throw new NullPointerException("tooltip and providerPreviews must not be null");
        }
        if (configuredOrGenerated != null) {
            tooltip.addAll(ManaTooltips.format(configuredOrGenerated));
        }
        for (ProviderPreviewEntry preview : providerPreviews) {
            if (preview == null) {
                throw new IllegalArgumentException("providerPreviews must not contain null");
            }
            tooltip.addAll(ManaTooltips.format(preview.getContribution()));
        }
    }
}
