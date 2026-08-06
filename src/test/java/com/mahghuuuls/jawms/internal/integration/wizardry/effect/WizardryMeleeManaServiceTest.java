package com.mahghuuuls.jawms.internal.integration.wizardry.effect;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WizardryMeleeManaServiceTest {

    @Test
    void onlyDirectPlayerAttackDamageQualifiesAndThornsNeverDoes() {
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        DamageSource directAttack = new EntityDamageSource("player", player);
        DamageSource thorns = new EntityDamageSource("thorns", player).setIsThornsDamage();

        assertTrue(WizardryMeleeManaService.isDirectPlayerAttack(
                player, directAttack
        ));
        assertFalse(WizardryMeleeManaService.isDirectPlayerAttack(
                player, thorns
        ));
        assertEquals(3.0F, WizardryMeleeManaService.applyFinalDamage(
                mock(EntityLivingBase.class), thorns, 3.0F
        ));
    }

    @Test
    void defaultMeleeTwoPlansExactDamageAndMana() {
        WizardryMeleeManaService.Plan plan = WizardryMeleeManaService.plan(
                2, 2.0D, 4, 1.0F, 20, false
        );

        assertEquals(5.0F, plan.getAugmentedDamage());
        assertEquals(8, plan.getManaCost());
        assertTrue(plan.hasDamageBonus());
        assertTrue(plan.isAffordable());
    }

    @Test
    void unaffordablePlanPreservesOrdinaryDamage() {
        WizardryMeleeManaService.Plan plan = WizardryMeleeManaService.plan(
                2, 2.0D, 4, 3.0F, 7, false
        );

        assertEquals(7.0F, plan.getAugmentedDamage());
        assertEquals(8, plan.getManaCost());
        assertFalse(plan.isAffordable());
    }

    @Test
    void creativePlanIgnoresAffordabilityButRetainsCostForDiagnostics() {
        WizardryMeleeManaService.Plan plan = WizardryMeleeManaService.plan(
                3, 2.0D, 4, 2.0F, 0, true
        );

        assertEquals(8.0F, plan.getAugmentedDamage());
        assertEquals(12, plan.getManaCost());
        assertTrue(plan.isAffordable());
    }

    @Test
    void nonPositiveAndZeroBonusPlansNeverEnhanceOrCharge() {
        WizardryMeleeManaService.Plan zeroDamage = WizardryMeleeManaService.plan(
                2, 2.0D, 4, 0.0F, 20, false
        );
        WizardryMeleeManaService.Plan zeroBonus = WizardryMeleeManaService.plan(
                2, 0.0D, 4, 3.0F, 20, false
        );

        assertFalse(zeroDamage.hasDamageBonus());
        assertFalse(zeroDamage.isAffordable());
        assertFalse(zeroBonus.hasDamageBonus());
        assertFalse(zeroBonus.isAffordable());
    }

    @Test
    void levelAndCostOverflowAreBounded() {
        WizardryMeleeManaService.Plan plan = WizardryMeleeManaService.plan(
                Integer.MAX_VALUE, Double.MAX_VALUE, Integer.MAX_VALUE,
                1.0F, Integer.MAX_VALUE, false
        );

        assertEquals(Float.MAX_VALUE, plan.getAugmentedDamage());
        assertEquals(Integer.MAX_VALUE, plan.getManaCost());
        assertTrue(plan.isAffordable());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void logoutRemovesRetainedDevelopmentDiagnostic() throws Exception {
        UUID playerId = UUID.randomUUID();
        EntityPlayerMP player = mock(EntityPlayerMP.class);
        when(player.getUniqueID()).thenReturn(playerId);
        Field field = WizardryMeleeManaService.class.getDeclaredField("LAST_MELEE");
        field.setAccessible(true);
        Map records = (Map) field.get(null);
        records.put(playerId, null);

        new WizardryMeleeManaService().onPlayerLoggedOut(
                new PlayerEvent.PlayerLoggedOutEvent(player)
        );

        assertFalse(records.containsKey(playerId));
    }
}
