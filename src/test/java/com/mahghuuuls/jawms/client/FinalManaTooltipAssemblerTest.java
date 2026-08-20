package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.rules.ProviderPreviewEntry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinalManaTooltipAssemblerTest {

    @Test
    void appendsOneConfiguredThenProviderBlockAfterEveryForeignLine() {
        List<String> foreign = Arrays.asList(
                "vanilla", "foreign-high", "foreign-normal",
                "foreign-lowest-a", "foreign-lowest-b"
        );
        List<String> tooltip = new ArrayList<>(foreign);
        ProviderPreviewEntry first = preview(
                "a_provider",
                ManaContribution.builder().flatRegeneration(1).build()
        );
        ProviderPreviewEntry second = preview(
                "b_provider",
                ManaContribution.builder().globalSpellEfficiency(10).build()
        );

        FinalManaTooltipAssembler.appendContributions(
                tooltip,
                ManaContribution.builder().flatMaximumMana(4).build(),
                Arrays.asList(first, second)
        );

        assertEquals(Arrays.asList(
                "vanilla", "foreign-high", "foreign-normal",
                "foreign-lowest-a", "foreign-lowest-b",
                TextFormatting.BLUE + "+4 Mana",
                TextFormatting.BLUE + "+1 Mana regen",
                TextFormatting.BLUE + "+10 Spell efficiency"
        ), tooltip);
        assertEquals(foreign, tooltip.subList(0, foreign.size()));
    }

    @Test
    void forgeSubscribersAtEveryAndEqualPriorityFinishBeforeFinalBlock() {
        EventBus bus = new EventBus();
        bus.register(new HighSubscriber());
        bus.register(new NormalSubscriber());
        bus.register(new LowestSubscriber("foreign-lowest-a"));
        bus.register(new LowestSubscriber("foreign-lowest-b"));
        List<String> tooltip = new ArrayList<>(Collections.singletonList("vanilla"));

        bus.post(new ItemTooltipEvent(
                ItemStack.EMPTY, null, tooltip, ITooltipFlag.TooltipFlags.NORMAL
        ));
        List<String> completedForeignTooltip = new ArrayList<>(tooltip);
        FinalManaTooltipAssembler.appendContributions(
                tooltip,
                ManaContribution.builder().flatMaximumMana(2).build(),
                Collections.emptyList()
        );

        assertEquals(
                completedForeignTooltip,
                tooltip.subList(0, completedForeignTooltip.size())
        );
        assertEquals(
                TextFormatting.BLUE + "+2 Mana",
                tooltip.get(tooltip.size() - 1)
        );
    }

    @Test
    void neutralOrAbsentSourcesAddNoLineOrSeparator() {
        List<String> tooltip = new ArrayList<>(Collections.singletonList("foreign"));

        FinalManaTooltipAssembler.appendContributions(
                tooltip, ManaContribution.EMPTY, Collections.emptyList()
        );

        assertEquals(Collections.singletonList("foreign"), tooltip);
    }

    @Test
    void repeatedConstructionDoesNotRetainOrMovePriorLists() {
        ManaContribution configured = ManaContribution.builder()
                .regenerationReduction(25)
                .build();
        List<String> first = new ArrayList<>(Collections.singletonList("first foreign"));
        List<String> second = new ArrayList<>(Collections.singletonList("second foreign"));

        FinalManaTooltipAssembler.appendContributions(
                first, configured, Collections.emptyList()
        );
        FinalManaTooltipAssembler.appendContributions(
                second, configured, Collections.emptyList()
        );

        assertEquals(Arrays.asList(
                "first foreign", TextFormatting.RED + "-25% Mana regen"
        ), first);
        assertEquals(Arrays.asList(
                "second foreign", TextFormatting.RED + "-25% Mana regen"
        ), second);
    }

    private static ProviderPreviewEntry preview(
            String providerPath,
            ManaContribution contribution
    ) {
        return new ProviderPreviewEntry(
                new ResourceLocation("fixture", providerPath),
                new ResourceLocation("fixture", "item"),
                contribution
        );
    }

    private static final class HighSubscriber {

        @SubscribeEvent(priority = EventPriority.HIGH)
        public void tooltip(ItemTooltipEvent event) {
            event.getToolTip().add("foreign-high");
        }
    }

    private static final class NormalSubscriber {

        @SubscribeEvent(priority = EventPriority.NORMAL)
        public void tooltip(ItemTooltipEvent event) {
            event.getToolTip().add("foreign-normal");
        }
    }

    private static final class LowestSubscriber {

        private final String line;

        private LowestSubscriber(String line) {
            this.line = line;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void tooltip(ItemTooltipEvent event) {
            event.getToolTip().add(line);
        }
    }
}
