package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import net.minecraft.util.ResourceLocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientGameplayRulesTest {

    @AfterEach
    void reset() {
        ClientGameplayRules.clear();
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
}
