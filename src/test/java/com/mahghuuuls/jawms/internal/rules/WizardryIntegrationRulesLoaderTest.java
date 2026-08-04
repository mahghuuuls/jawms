package com.mahghuuuls.jawms.internal.rules;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WizardryIntegrationRulesLoaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileGeneratesEveryApprovedCategoryAndDefault() throws IOException {
        Path file = temporaryDirectory.resolve("integrations/ebwizardry.cfg");

        WizardryIntegrationRules rules = WizardryIntegrationRulesLoader.load(
                file.toFile(), 7L
        );

        assertEquals(7L, rules.getRevision());
        assertEquals(10.0D, rules.getStorageManaIncreasePerLevel());
        assertEquals(10.0D, rules.getCondenserRegenerationIncreasePerLevel());
        assertEquals(WizardryIntegrationRules.HeldWandAggregation.HIGHEST,
                rules.getHeldWandAggregation());
        assertEquals(100.0D, rules.getPerfectionistRefund());
        assertEquals(5, rules.getDemonicSealManaPerHunger());
        assertEquals(6, rules.getDemonicSealMinimumHunger());
        assertFalse(rules.isDemonicSealContinuous());
        assertEquals(1.0D, rules.getFontRegenerationPerLevel());
        assertTrue(rules.isFontLockoutPermission());
        String generated = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        assertTrue(generated.contains("wand_upgrades"));
        assertTrue(generated.contains("artefacts"));
        assertTrue(generated.contains("demonic_seal"));
        assertTrue(generated.contains("font_of_mana"));
        assertTrue(generated.contains("heldWandAggregation=HIGHEST"));
        assertTrue(generated.contains("condensingRegenPerRing=0.5"));
    }

    @Test
    void validCustomFileLoadsAsOneRevision() throws IOException {
        Path file = temporaryDirectory.resolve("ebwizardry.cfg");
        Files.write(file, ("wand_upgrades {\n"
                + " S:heldWandAggregation=SUM\n"
                + " S:storageEnabled=true\n"
                + " S:storageManaIncreasePercentPerLevel=12.5\n"
                + " S:condenserEnabled=true\n"
                + " S:condenserRegenIncreasePercentPerLevel=7.5\n"
                + " S:siphonEnabled=true\n S:siphonManaPerLevel=3\n"
                + " S:meleeEnabled=true\n S:meleeDamagePerLevel=2\n S:meleeManaPerLevel=4\n}\n"
                + "artefacts {\n S:condensingEnabled=true\n S:condensingRegenPerRing=1.25\n"
                + " S:siphoningEnabled=true\n S:siphoningIncreasePercent=30\n"
                + " S:extractionEnabled=true\n S:extractionMana=5\n"
                + " S:perfectionistEnabled=true\n S:perfectionistRefundPercent=80\n}\n"
                + "demonic_seal {\n S:enabled=true\n S:manaPerHunger=4\n"
                + " S:minimumHunger=5\n S:allowContinuous=true\n}\n"
                + "font_of_mana {\n S:enabled=true\n S:regenPerLevel=2\n"
                + " S:regenerateDuringLockout=false\n S:regenerateDuringContinuous=true\n}\n")
                .getBytes(StandardCharsets.UTF_8));

        WizardryIntegrationRules rules = WizardryIntegrationRulesLoader.load(
                file.toFile(), 3L
        );

        assertEquals(WizardryIntegrationRules.HeldWandAggregation.SUM,
                rules.getHeldWandAggregation());
        assertEquals(12.5D, rules.getStorageManaIncreasePerLevel());
        assertEquals(7.5D, rules.getCondenserRegenerationIncreasePerLevel());
        assertEquals(1.25D, rules.getCondensingRegenerationPerRing());
        assertEquals(80.0D, rules.getPerfectionistRefund());
        assertTrue(rules.isDemonicSealContinuous());
        assertFalse(rules.isFontLockoutPermission());
        assertTrue(rules.isFontContinuousPermission());
    }

    @Test
    void anyInvalidValueRejectsTheCompleteCandidateWithoutRewritingIt() throws IOException {
        Path file = temporaryDirectory.resolve("ebwizardry.cfg");
        String invalid = "wand_upgrades {\n S:heldWandAggregation=AVERAGE\n"
                + " S:storageManaIncreasePercentPerLevel=-1\n}\n"
                + "artefacts {\n S:perfectionistRefundPercent=101\n}\n"
                + "demonic_seal {\n S:enabled=true\n S:manaPerHunger=0\n"
                + " S:minimumHunger=21\n}\n";
        Files.write(file, invalid.getBytes(StandardCharsets.UTF_8));

        WizardryIntegrationRulesLoader.InvalidIntegrationRulesException exception =
                assertThrows(
                        WizardryIntegrationRulesLoader.InvalidIntegrationRulesException.class,
                        () -> WizardryIntegrationRulesLoader.load(file.toFile(), 9L)
                );

        assertTrue(exception.getErrors().size() >= 5);
        assertEquals(invalid, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void invalidCandidateRetainsTheWholePriorChildAndNamesBothRevisions() throws IOException {
        Path file = temporaryDirectory.resolve("ebwizardry.cfg");
        Files.write(file, ("wand_upgrades {\n"
                + " S:storageManaIncreasePercentPerLevel=-1\n}\n").getBytes(StandardCharsets.UTF_8));
        WizardryIntegrationRules previous = new WizardryIntegrationRules(
                4L,
                true, 17, true, 0.75D, true, 3,
                WizardryIntegrationRules.HeldWandAggregation.SUM,
                true, 2.0D, 4, true, 1.0D, true, 30.0D,
                true, 5, true, 100.0D,
                true, 5, 6, false,
                true, 1.0D, true, false
        );
        List<String> warnings = new ArrayList<>();

        WizardryIntegrationRules selected = GameplayRulesManager.loadIntegrationCandidate(
                file.toFile(), previous, 9L, warnings
        );
        GameplayRules mainCandidate = new GameplayRules(
                12L, 50, 2.0D, 2.0D, GameplayRules.DEFAULT_COST_FORMULA,
                20, 50, 100, Collections.emptyMap(), Collections.emptyList(),
                WizardryArmorProfile.DEFAULT
        );
        GameplayRules published = mainCandidate
                .withWizardryIntegrationRules(selected);

        assertEquals(12L, published.getRevision());
        assertEquals(4L, published.getWizardryIntegrationRules().getRevision());
        assertEquals(17.0D,
                published.getWizardryIntegrationRules().getStorageManaIncreasePerLevel());
        assertEquals(WizardryIntegrationRules.HeldWandAggregation.SUM,
                published.getWizardryIntegrationRules().getHeldWandAggregation());
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.get(0).contains("candidate 9"));
        assertTrue(warnings.get(0).contains("revision 4"));
    }

}
