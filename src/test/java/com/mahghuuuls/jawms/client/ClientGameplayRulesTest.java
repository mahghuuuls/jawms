package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryFlaskBridge;
import electroblob.wizardry.item.ItemManaFlask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientGameplayRulesTest {

    @AfterEach
    void reset() {
        ClientGameplayRules.clear();
        WizardryFlaskBridge.refreshCapacities(20, 50, 100);
    }

    @Test
    void acceptsOnlyNewerRuleRevisionsAndClearsAcrossSessions() {
        GameplayRulesMessage first = new GameplayRulesMessage(GameplayRules.initialDefaults());
        ClientGameplayRules.accept(first);

        assertEquals(0L, ClientGameplayRules.getRevision());
        assertEquals(50, ClientGameplayRules.getBaseMaximum());
        assertEquals(2.0D, ClientGameplayRules.getBaseRegeneration());
        assertEquals(40L, ClientGameplayRules.getLockoutTicks());
        assertEquals(20, ClientGameplayRules.getSmallFlask());
        assertEquals(
                17.0D,
                ClientGameplayRules.getSpellCost(
                        new ResourceLocation("example", "missing"),
                        17.0D
                )
        );

        ClientGameplayRules.clear();
        ClientGameplayRules.accept(first);
        assertEquals(0L, ClientGameplayRules.getRevision());
    }

    @Test
    void refreshesCapacitiesOnlyForAcceptedClientRevisions() {
        ClientGameplayRules.accept(new GameplayRulesMessage(rules(2L, 21, 51, 101)));
        assertEquals(21, ItemManaFlask.Size.SMALL.capacity);
        assertEquals(51, ItemManaFlask.Size.MEDIUM.capacity);
        assertEquals(101, ItemManaFlask.Size.LARGE.capacity);

        ClientGameplayRules.accept(new GameplayRulesMessage(rules(1L, 1, 2, 3)));
        assertEquals(21, ItemManaFlask.Size.SMALL.capacity);
        assertEquals(51, ItemManaFlask.Size.MEDIUM.capacity);
        assertEquals(101, ItemManaFlask.Size.LARGE.capacity);
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
