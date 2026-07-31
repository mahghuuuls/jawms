package com.mahghuuuls.jawms.internal.modifier;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaSlotCategory;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import com.mahghuuuls.jawms.internal.mana.ManaCapability;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManaProviderRegistryTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        Bootstrap.register();
    }

    @Test
    void registrationIsGloballyUniqueAndFreezes() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        ResourceLocation id = id("one");
        Item item = item("one");
        registry.registerItem(id, item, (player, context) -> ManaContribution.EMPTY);
        assertThrows(IllegalArgumentException.class, () -> registry.registerContextual(
                id, player -> ManaContribution.EMPTY
        ));
        registry.freeze();
        assertThrows(IllegalStateException.class, () -> registry.registerContextual(
                id("late"), player -> ManaContribution.EMPTY
        ));
    }

    @Test
    void itemProvidersUseIdOrderAndOneResultPerTickAndFingerprint() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        Item item = item("cache");
        registry.registerItem(id("z"), item, (player, context) -> {
            calls.incrementAndGet();
            return ManaContribution.builder().flatMaximumMana(2).build();
        });
        registry.registerItem(id("a"), item, (player, context) -> {
            calls.incrementAndGet();
            return ManaContribution.builder().flatMaximumMana(1).build();
        });
        PlayerFixture fixture = player(10L);
        ManaItemContext context = context(item);

        List<ManaContribution> first = registry.evaluateItem(
                fixture.player, context, GameplayRules.initialDefaults()
        );
        List<ManaContribution> second = registry.evaluateItem(
                fixture.player, context, GameplayRules.initialDefaults()
        );

        assertEquals(Arrays.asList(1, 2), Arrays.asList(
                first.get(0).getFlatMaximumMana(), first.get(1).getFlatMaximumMana()
        ));
        assertEquals(2, calls.get());
        assertEquals(2, second.size());
        registry.evaluateItem(fixture.player, new ManaItemContext(
                new ItemStack(item), ManaSlotCategory.OFF_HAND, 0
        ), GameplayRules.initialDefaults());
        assertEquals(4, calls.get());
        registry.evaluateItem(fixture.player, new ManaItemContext(
                new ItemStack(item, 2), ManaSlotCategory.MAIN_HAND, 0
        ), GameplayRules.initialDefaults());
        assertEquals(6, calls.get());
        when(fixture.world.getTotalWorldTime()).thenReturn(11L);
        registry.evaluateItem(fixture.player, context, GameplayRules.initialDefaults());
        assertEquals(8, calls.get());
    }

    @Test
    void contextualFailuresAndNullsAreCachedAndOtherProvidersContinue() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger failures = new AtomicInteger();
        registry.registerContextual(id("a_throw"), player -> {
            failures.incrementAndGet();
            throw new IllegalStateException("test");
        });
        registry.registerContextual(id("b_null"), player -> {
            failures.incrementAndGet();
            return null;
        });
        registry.registerContextual(id("c_valid"), player ->
                ManaContribution.builder().flatRegeneration(1.0D).build());
        PlayerFixture fixture = player(20L);

        List<ManaContribution> first = registry.evaluateContextual(
                fixture.player, GameplayRules.initialDefaults()
        );
        List<ManaContribution> second = registry.evaluateContextual(
                fixture.player, GameplayRules.initialDefaults()
        );

        assertEquals(1, first.size());
        assertEquals(1.0D, first.get(0).getFlatRegeneration());
        assertEquals(1, second.size());
        assertEquals(2, failures.get());
    }

    @Test
    void disabledProviderIsNotInvoked() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        ResourceLocation disabled = id("disabled");
        registry.registerContextual(disabled, player -> {
            calls.incrementAndGet();
            return ManaContribution.EMPTY;
        });
        GameplayRules rules = new GameplayRules(
                3L, 50, 2.0D, 2.0D, GameplayRules.DEFAULT_COST_FORMULA,
                20, 50, 100, Collections.emptyMap(), Collections.emptyList(),
                WizardryArmorProfile.DEFAULT, Collections.singleton(disabled)
        );

        assertEquals(0, registry.evaluateContextual(player(1L).player, rules).size());
        assertEquals(0, calls.get());
    }

    @Test
    void explicitInvalidationPermitsOneFreshEvaluationInSameTick() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.registerContextual(id("invalidate"), player -> {
            calls.incrementAndGet();
            return ManaContribution.EMPTY;
        });
        PlayerFixture fixture = player(30L);
        GameplayRules rules = GameplayRules.initialDefaults();

        registry.evaluateContextual(fixture.player, rules);
        registry.evaluateContextual(fixture.player, rules);
        registry.invalidate(fixture.player);
        registry.evaluateContextual(fixture.player, rules);
        registry.evaluateContextual(fixture.player, rules);

        assertEquals(2, calls.get());
    }

    @Test
    void mutationAndInvalidationAreRejectedInsideProviderWithoutChangingMana() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        AtomicInteger mutationRejections = new AtomicInteger();
        AtomicInteger invalidationRejections = new AtomicInteger();
        AtomicInteger priorMaximum = new AtomicInteger();
        registry.registerContextual(id("guard"), player -> {
            priorMaximum.set(new ManaService().getMaximumMana(player));
            try {
                new ManaService().restoreMana(player, 1, id("mutation"));
            } catch (IllegalStateException expected) {
                mutationRejections.incrementAndGet();
            }
            try {
                registry.invalidate(player);
            } catch (IllegalStateException expected) {
                invalidationRejections.incrementAndGet();
            }
            return ManaContribution.EMPTY;
        });
        PlayerFixture fixture = player(40L);
        int before = fixture.state.getCurrentMana();

        registry.evaluateContextual(fixture.player, GameplayRules.initialDefaults());

        assertEquals(1, mutationRejections.get());
        assertEquals(1, invalidationRejections.get());
        assertEquals(50, priorMaximum.get());
        assertEquals(before, fixture.state.getCurrentMana());
    }

    @Test
    void diagnosticsAreRateLimitedPerContextAndClearedOnLogout() {
        ManaProviderRegistry registry = new ManaProviderRegistry();
        Item item = item("diagnostics");
        AtomicInteger failureNumber = new AtomicInteger();
        registry.registerItem(id("diagnostics"), item, (player, context) -> {
            throw new IllegalArgumentException("changing failure "
                    + failureNumber.incrementAndGet());
        });
        PlayerFixture fixture = player(50L);
        GameplayRules rules = GameplayRules.initialDefaults();
        ManaItemContext main = new ManaItemContext(
                new ItemStack(item), ManaSlotCategory.MAIN_HAND, 0
        );
        ManaItemContext off = new ManaItemContext(
                new ItemStack(item), ManaSlotCategory.OFF_HAND, 0
        );

        registry.evaluateItem(fixture.player, main, rules);
        registry.evaluateItem(fixture.player, off, rules);
        registry.evaluateItem(fixture.player, main, rules);
        assertEquals(2, registry.diagnosticEntryCount());
        assertEquals(2L, registry.diagnosticEmissionCount());
        when(fixture.world.getTotalWorldTime()).thenReturn(51L);
        registry.evaluateItem(fixture.player, main, rules);
        assertEquals(2L, registry.diagnosticEmissionCount());
        when(fixture.world.getTotalWorldTime()).thenReturn(1250L);
        registry.evaluateItem(fixture.player, main, rules);
        assertEquals(3L, registry.diagnosticEmissionCount());
        registry.remove(fixture.player);
        assertEquals(0, registry.diagnosticEntryCount());
    }

    private static PlayerFixture player(long tick) {
        EntityPlayer player = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        ManaState state = new ManaState();
        player.world = world;
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        when(world.getTotalWorldTime()).thenReturn(tick);
        when(world.isCallingFromMinecraftThread()).thenReturn(true);
        when(player.getCapability(ManaCapability.INSTANCE, null)).thenReturn(state);
        return new PlayerFixture(player, world, state);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("test", path);
    }

    private static Item item(String path) {
        return Items.STICK;
    }

    private static ManaItemContext context(Item item) {
        return new ManaItemContext(new ItemStack(item), ManaSlotCategory.MAIN_HAND, 0);
    }

    private static final class PlayerFixture {
        private final EntityPlayer player;
        private final WorldServer world;
        private final ManaState state;

        private PlayerFixture(EntityPlayer player, WorldServer world, ManaState state) {
            this.player = player;
            this.world = world;
            this.state = state;
        }
    }
}
