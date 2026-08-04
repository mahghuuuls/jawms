package com.mahghuuuls.jawms.internal.integration.wizardry.payment;

import com.mahghuuuls.jawms.internal.mana.ManaCapability;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.FoodStats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CastPaymentServiceTest {

    private EntityPlayer player;

    @AfterEach
    void clearClaims() {
        CastPaymentService.clear(player);
    }

    @Test
    void mixedSuccessCommitsBothResourcesAndStartsLockout() {
        Fixture fixture = fixture(7, 20);
        DemonicSealPolicy policy = new DemonicSealPolicy(true, 8L, 5, 6, false);

        CastPaymentReservation reservation = CastPaymentService.reserve(
                fixture.player, 12, policy, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "spell")
        );

        assertEquals(7, fixture.state.getCurrentMana());
        assertEquals(20, fixture.food.getFoodLevel());
        assertEquals(7, reservation.getPaidMana());
        assertEquals(1, reservation.getPaidHunger());
        CastPaymentService.commit(
                fixture.player, reservation, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "spell")
        );

        assertEquals(0, fixture.state.getCurrentMana());
        assertEquals(19, fixture.food.getFoodLevel());
        assertTrue(fixture.state.getRemainingLockoutTicks() > 0L);
        assertFalse(reservation.isActive());
    }

    @Test
    void hungerPaymentClampsSaturationToTheReducedFoodLevel() {
        Fixture fixture = fixture(0, 20);
        fixture.food.setFoodSaturationLevel(20.0F);
        DemonicSealPolicy policy = new DemonicSealPolicy(true, 8L, 5, 6, false);

        CastPaymentReservation reservation = CastPaymentService.reserve(
                fixture.player, 10, policy, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "saturation")
        );
        CastPaymentService.commit(
                fixture.player, reservation, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "saturation")
        );

        assertEquals(18, fixture.food.getFoodLevel());
        assertEquals(18.0F, fixture.food.getSaturationLevel());
    }

    @Test
    void nestedReservationsCannotDoubleSpendManaOrHunger() {
        Fixture fixture = fixture(7, 8);
        DemonicSealPolicy policy = new DemonicSealPolicy(true, 9L, 5, 6, false);

        CastPaymentReservation outer = CastPaymentService.reserve(
                fixture.player, 7, policy, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "outer")
        );
        CastPaymentReservation inner = CastPaymentService.reserve(
                fixture.player, 10, policy, false,
                EnumHand.OFF_HAND, new ResourceLocation("test", "inner")
        );
        CastPaymentReservation rejected = CastPaymentService.reserve(
                fixture.player, 1, policy, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "rejected")
        );

        assertEquals(7, outer.getPaidMana());
        assertEquals(0, outer.getPaidHunger());
        assertEquals(0, inner.getPaidMana());
        assertEquals(2, inner.getPaidHunger());
        assertEquals(null, rejected);
        assertEquals(2, CastPaymentService.getReservedHunger(fixture.player));

        CastPaymentService.commit(
                fixture.player, inner, false,
                EnumHand.OFF_HAND, new ResourceLocation("test", "inner")
        );
        CastPaymentService.commit(
                fixture.player, outer, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "outer")
        );

        assertEquals(0, fixture.state.getCurrentMana());
        assertEquals(6, fixture.food.getFoodLevel());
        assertEquals(0, CastPaymentService.getReservedHunger(fixture.player));
    }

    @Test
    void releaseAndCommitTimeFailureNeverPartiallyMutateResources() {
        Fixture fixture = fixture(7, 20);
        DemonicSealPolicy policy = new DemonicSealPolicy(true, 10L, 5, 6, false);
        CastPaymentReservation released = CastPaymentService.reserve(
                fixture.player, 12, policy, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "released")
        );
        CastPaymentService.release(fixture.player, released);

        assertEquals(7, fixture.state.getCurrentMana());
        assertEquals(20, fixture.food.getFoodLevel());

        CastPaymentReservation changed = CastPaymentService.reserve(
                fixture.player, 12, policy, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "changed")
        );
        fixture.food.setFoodLevel(6);
        assertThrows(IllegalStateException.class, () -> CastPaymentService.commit(
                fixture.player, changed, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "changed")
        ));

        assertEquals(7, fixture.state.getCurrentMana());
        assertEquals(6, fixture.food.getFoodLevel());
        assertTrue(changed.isActive());
        CastPaymentService.release(fixture.player, changed);
        assertEquals(0, CastPaymentService.getReservedHunger(fixture.player));
    }

    @Test
    void enabledContinuousPortionUsesHungerAndDefersLockoutUntilFinish() {
        Fixture fixture = fixture(0, 20);
        DemonicSealPolicy policy = new DemonicSealPolicy(true, 11L, 5, 6, true);
        ResourceLocation spell = new ResourceLocation("test", "beam");
        ManaService.beginContinuousCast(fixture.player, EnumHand.OFF_HAND, spell);

        CastPaymentReservation reservation = CastPaymentService.reserve(
                fixture.player, 5, policy, true, EnumHand.OFF_HAND, spell
        );
        CastPaymentService.commit(
                fixture.player, reservation, true, EnumHand.OFF_HAND, spell
        );

        assertEquals(0, fixture.state.getCurrentMana());
        assertEquals(19, fixture.food.getFoodLevel());
        assertTrue(fixture.state.wasContinuousCastSuccessful());
        assertEquals(0L, fixture.state.getRemainingLockoutTicks());

        assertTrue(ManaService.finishContinuousCast(fixture.player));
        assertTrue(fixture.state.getRemainingLockoutTicks() > 0L);
    }

    @Test
    void creativeDiagnosticRepresentsAZeroResourcePlan() {
        Fixture fixture = fixture(3, 9);
        DemonicSealPolicy policy = new DemonicSealPolicy(true, 12L, 5, 6, true);

        CastPaymentService.recordCreative(
                fixture.player, 40, policy, false,
                EnumHand.MAIN_HAND, new ResourceLocation("test", "creative"), true
        );

        String description = CastPaymentService.describeLastPayment(fixture.player);
        assertTrue(description.contains("outcome=creative_success"));
        assertTrue(description.contains("paidMana=0"));
        assertTrue(description.contains("paidHunger=0"));
        assertEquals(3, fixture.state.getCurrentMana());
        assertEquals(9, fixture.food.getFoodLevel());
    }

    private Fixture fixture(int mana, int hunger) {
        EntityPlayer mockedPlayer = mock(EntityPlayerMP.class);
        WorldServer world = mock(WorldServer.class);
        ManaState state = new ManaState();
        FoodStats food = new FoodStats();
        food.setFoodLevel(hunger);
        mockedPlayer.world = world;
        when(world.isCallingFromMinecraftThread()).thenReturn(true);
        when(mockedPlayer.getUniqueID()).thenReturn(UUID.randomUUID());
        when(mockedPlayer.getCapability(ManaCapability.INSTANCE, null)).thenReturn(state);
        when(mockedPlayer.getFoodStats()).thenReturn(food);
        new ManaService().setCurrentMana(
                mockedPlayer, mana, new ResourceLocation("test", "setup")
        );
        this.player = mockedPlayer;
        return new Fixture(mockedPlayer, state, food);
    }

    private static final class Fixture {
        private final EntityPlayer player;
        private final ManaState state;
        private final FoodStats food;

        private Fixture(EntityPlayer player, ManaState state, FoodStats food) {
            this.player = player;
            this.state = state;
            this.food = food;
        }
    }
}
