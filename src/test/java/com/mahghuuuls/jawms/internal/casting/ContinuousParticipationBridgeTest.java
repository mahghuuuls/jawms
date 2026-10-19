package com.mahghuuuls.jawms.internal.casting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mahghuuuls.jawms.api.*;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentContextManager;
import com.mahghuuuls.jawms.internal.integration.wizardry.payment.CastPaymentService;
import com.mahghuuuls.jawms.internal.mana.*;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.SpellProperties;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.registries.IForgeRegistryEntry;
import net.minecraftforge.registries.IRegistryDelegate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Calls the real reservation/coordinator bridge; does not pretend to transform ItemWand. */
class ContinuousParticipationBridgeTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }

    @Test void actualBridgeCoordinatesZeroTotalZeroPortionAndCreative() throws Exception {
        // Integer tick1 of rate1 is zero; Creative must still coordinate its positive-rate effect.
        for (int[] variant : new int[][] { {0, 0, 0}, {1, 1, 0}, {4, 0, 1} }) {
            Fixture f = new Fixture(variant[0], variant[2] == 1);
            List<CastAttemptOutcome> outcomes = new ArrayList<>();
            AtomicInteger prepares = new AtomicInteger(), settlements = new AtomicInteger();
            ICastCostParticipant participant = new ICastCostParticipant() {
                public CastCostPreparation prepare(CastAttemptContext context) {
                    prepares.incrementAndGet(); assertTrue(context.isContinuous()); assertEquals(0, context.getPlannedMana());
                    return CastCostPreparation.accepted(new ICastCostReservation() {
                        public boolean isValid() { return true; }
                        public CastCostSettlement settle() { settlements.incrementAndGet(); return CastCostSettlement.paid(2, true); }
                        public void release(CastCostReleaseReason reason) {}
                    });
                }
                public void onAttemptResolved(CastAttemptOutcome outcome) { outcomes.add(outcome); }
            };
            withRegistry(participant, () -> {
                ParticipantUseService.Scope scope = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
                int depth = WizardryWandBridge.captureAttemptDepth();
                try {
                    assertTrue(WizardryWandBridge.castWithReservation(f.stack, f.spell, f.world, f.player,
                            EnumHand.MAIN_HAND, variant[1], new SpellModifiers()));
                    assertEquals(1, prepares.get()); assertEquals(1, settlements.get());
                    assertEquals(1, outcomes.size()); assertTrue(outcomes.get(0).mustStopAfterSuccess());
                    assertEquals(f.startingMana, f.state.getCurrentMana());
                    assertEquals(0, f.state.getRemainingLockoutTicks());
                    verify(f.spell, times(1)).cast(eq(f.world), eq(f.player), eq(EnumHand.MAIN_HAND), eq(variant[1]), any());
                } finally {
                    ParticipantUseService.exitCast(scope); WizardryWandBridge.cleanupToDepth(f.player, depth); f.clear();
                }
            });
        }
    }
    @Test void actualProgressionMutationInsideContinuousEffectDoesNotInvalidateItsOuterPayment() throws Exception {
        Fixture f = new Fixture(4, false);
        List<CastAttemptOutcome> outcomes = new ArrayList<>();
        ICastCostParticipant participant = new ICastCostParticipant() {
            public CastCostPreparation prepare(CastAttemptContext context) {
                return CastCostPreparation.accepted(new ICastCostReservation() {
                    public boolean isValid() { return true; }
                    public CastCostSettlement settle() { return CastCostSettlement.paid(1, false); }
                    public void release(CastCostReleaseReason reason) {}
                });
            }
            public void onAttemptResolved(CastAttemptOutcome outcome) { outcomes.add(outcome); }
        };
        // Real Wizardry helper at the production failure boundary. Full nested ItemWand transforms
        // and the physical parent/child success remainder remain the P9 runtime obligation.
        when(f.spell.cast(eq(f.world), eq(f.player), eq(EnumHand.MAIN_HAND), anyInt(), any())).thenAnswer(call -> {
            electroblob.wizardry.util.WandHelper.addProgression(f.stack, 1); return true;
        });
        withRegistry(participant, () -> {
            ParticipantUseService.Scope scope = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
            int depth = WizardryWandBridge.captureAttemptDepth();
            try {
                assertTrue(WizardryWandBridge.castWithReservation(f.stack, f.spell, f.world, f.player, EnumHand.MAIN_HAND, 0, new SpellModifiers()));
                assertEquals(1, electroblob.wizardry.util.WandHelper.getProgression(f.stack));
                assertEquals(CastAttemptOutcome.Status.ACCEPTED, outcomes.get(0).getStatus());
                assertFalse(outcomes.get(0).mustStopAfterSuccess());
            } finally {
                ParticipantUseService.exitCast(scope); WizardryWandBridge.cleanupToDepth(f.player, depth); f.clear();
            }
        });
    }
    @Test void allNotApplicableSuccessfulUseRetainsOrdinaryInterruptedFinishEligibility() throws Exception {
        for (boolean creative : new boolean[] { false, true }) {
            Fixture f = new Fixture(4, creative); f.bindPhysical();
            withRegistry(context -> CastCostPreparation.notApplicable(), () -> {
                int depth = WizardryWandBridge.captureAttemptDepth();
                ParticipantUseService.enterOrigin(f.player, f.stack, EnumHand.MAIN_HAND, false);
                try {
                    f.state.beginContinuousCast(EnumHand.MAIN_HAND, f.spell.getRegistryName());
                    ParticipantUseService.Scope scope = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
                    try {
                        assertTrue(WizardryWandBridge.castWithReservation(f.stack, f.spell, f.world, f.player,
                                EnumHand.MAIN_HAND, 0, new SpellModifiers()));
                        WizardryWandBridge.commitFromSuccessfulDeduction(null, f.stack, 4, f.player, f.spell);
                    } finally { ParticipantUseService.exitCast(scope); }
                    assertTrue(creative || f.state.wasContinuousCastSuccessful());
                    // This is the real bridge called by the canCast-false/Tick-cancel redirect, before
                    // vanilla stopActiveHand. Transformed callback/close observation remains runtime P9.
                    try { WizardryWandBridge.interruptContinuousCast(f.player, f.spell, EnumHand.MAIN_HAND); }
                    finally { assertTrue(f.physicalState.isOpen(f.physicalUse), "ordinary interruption must not pre-close/reset before the actual stop callback"); }
                    verify(f.player, never()).resetActiveHand();
                    assertEquals(Integer.MAX_VALUE, WizardryWandBridge.getManaForContinuousFinish(0,
                            f.player, f.spell, EnumHand.MAIN_HAND));
                    assertFalse(f.state.wasContinuousCastManaExhausted());
                } finally {
                    ParticipantUseService.exitOrigin(); WizardryWandBridge.cleanupToDepth(f.player, depth); f.clear();
                }
            });
        }
    }
    @SuppressWarnings({"unchecked", "rawtypes"}) private static void withRegistry(ICastCostParticipant participant, Runnable body) throws Exception {
        CastCostRegistry registry = CastCostRegistry.instance();
        Field entries = CastCostRegistry.class.getDeclaredField("entries"); entries.setAccessible(true);
        Field frozen = CastCostRegistry.class.getDeclaredField("frozen"); frozen.setAccessible(true);
        Map map = (Map) entries.get(registry); Map before = new HashMap(map); boolean wasFrozen = frozen.getBoolean(registry);
        try {
            map.clear(); frozen.setBoolean(registry, false);
            registry.register(new ResourceLocation("fixture", "bridge"), participant); registry.freeze(); body.run();
        } finally { map.clear(); map.putAll(before); frozen.setBoolean(registry, wasFrozen); }
    }
    private static final class Fixture {
        final EntityPlayerMP player = mock(EntityPlayerMP.class);
        final WorldServer world = mock(WorldServer.class);
        final ManaState state = new ManaState();
        final int startingMana = state.getCurrentMana();
        final ItemStack stack = new ItemStack(Items.STICK);
        final Spell spell = mock(Spell.class);
        final net.minecraftforge.registries.IForgeRegistry<Spell> originalRegistry = Spell.registry;
        ParticipantUseState physicalState;
        ParticipantUseState.Use physicalUse;
        @SuppressWarnings("unchecked") Fixture(int cost, boolean creative) throws Exception {
            player.world = world;
            when(world.isCallingFromMinecraftThread()).thenReturn(true);
            when(world.getMinecraftServer()).thenReturn(mock(MinecraftServer.class));
            when(player.getUniqueID()).thenReturn(UUID.randomUUID());
            when(player.getHeldItem(EnumHand.MAIN_HAND)).thenReturn(stack);
            when(player.isCreative()).thenReturn(creative);
            when(player.getCapability(ManaCapability.INSTANCE, null)).thenReturn(state);
            when(player.getFoodStats()).thenReturn(new FoodStats());
            new ManaService().setCurrentMana(player, 100, new ResourceLocation("fixture", "setup"));
            IRegistryDelegate<Spell> delegate = mock(IRegistryDelegate.class);
            when(delegate.name()).thenReturn(new ResourceLocation("fixture", "continuous_bridge"));
            Field field = IForgeRegistryEntry.Impl.class.getField("delegate"); field.setAccessible(true); field.set(spell, delegate);
            field = Spell.class.getField("isContinuous"); field.setAccessible(true); field.setBoolean(spell, true);
            field = Spell.class.getDeclaredField("propertyKeys"); field.setAccessible(true); field.set(spell, Collections.emptySet());
            JsonObject json = new JsonParser().parse("{\"enabled\":{\"book\":false,\"scroll\":false,\"wands\":true,\"npcs\":false,\"dispensers\":false,\"commands\":false,\"treasure\":false,\"trades\":false,\"looting\":false},\"tier\":\"novice\",\"element\":\"magic\",\"type\":\"utility\",\"cost\":" + cost
                    + ",\"chargeup\":0,\"cooldown\":20,\"base_properties\":{}}").getAsJsonObject();
            Constructor<SpellProperties> parser = SpellProperties.class.getDeclaredConstructor(JsonObject.class, Spell.class); parser.setAccessible(true);
            field = Spell.class.getDeclaredField("properties"); field.setAccessible(true); field.set(spell, parser.newInstance(json, spell));
            when(spell.cast(eq(world), eq(player), eq(EnumHand.MAIN_HAND), anyInt(), any())).thenReturn(true);
            Spell.registry = mock(net.minecraftforge.registries.IForgeRegistry.class);
            when(Spell.registry.getValue(spell.getRegistryName())).thenReturn(spell);
        }
        @SuppressWarnings("unchecked") void bindPhysical() throws Exception {
            player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
            player.inventory.mainInventory.set(0, stack);
            player.connection = mock(net.minecraft.network.NetHandlerPlayServer.class); player.connection.player = player;
            when(player.isHandActive()).thenReturn(true); when(player.getActiveHand()).thenReturn(EnumHand.MAIN_HAND);
            when(player.getActiveItemStack()).thenReturn(stack);
            Class<?> type = Class.forName(ParticipantUseService.class.getName() + "$Connection");
            Constructor<?> constructor = type.getDeclaredConstructor(EntityPlayerMP.class); constructor.setAccessible(true);
            Object connection = constructor.newInstance(player);
            Field field = type.getDeclaredField("state"); field.setAccessible(true); physicalState = (ParticipantUseState) field.get(connection);
            field = ParticipantUseService.class.getDeclaredField("CONNECTIONS"); field.setAccessible(true);
            ((Map<Object, Object>) field.get(null)).put(player.connection, connection);
            physicalState.intent(physicalState.epoch(), 1, EnumHand.MAIN_HAND, spell.getRegistryName());
            physicalUse = physicalState.bind(EnumHand.MAIN_HAND, spell.getRegistryName(), stack, 7);
            physicalUse.generation = ParticipantUseService.wands(player).bind(player, EnumHand.MAIN_HAND, stack, 1, 7);
        }
        void clear() {
            CastPaymentService.clear(player); CastCostContextManager.clearAll(player); CastPaymentContextManager.clearAll(player);
            ParticipantUseService.clear();
            Spell.registry = originalRegistry;
        }
    }
}
