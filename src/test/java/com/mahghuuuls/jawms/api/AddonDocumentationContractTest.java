package com.mahghuuuls.jawms.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class AddonDocumentationContractTest {

    private static final String GUIDE_PATH = "docs/ADDON-DEVELOPMENT.md";
    private static final String GUIDE_URL =
            "https://github.com/mahghuuuls/jawms/blob/main/" + GUIDE_PATH;

    @Test
    void canonicalGuidePinsPublishedRecipeAndTruthfulApiMatrix() throws IOException {
        String guide = read(GUIDE_PATH);

        assertTrue(guide.contains(
                "curse.maven:just-another-wizardry-mana-system-1643697:8597828"
        ));
        assertTrue(guide.contains("curse.maven:electroblobs-wizardry-265642:8320066"));
        assertTrue(guide.contains("Published `0.4.0` Dependency Recipe"));
        assertTrue(guide.contains(
                "Historical `0.1.0`, `0.2.0`, And `0.3.0` Dependencies"
        ));
        assertTrue(guide.contains(
                "curse.maven:just-another-wizardry-mana-system-1643697:8632899"
        ));
        assertTrue(guide.contains(
                "curse.maven:just-another-wizardry-mana-system-1643697:8651841"
        ));
        assertTrue(guide.contains("zone.rong:mixinbooter:11.8"));
        assertTrue(guide.contains("required-after:mixinbooter@[11.8,)"));
        assertTrue(guide.contains("historical `[11.11,12.0)` MixinBooter range"));
        assertTrue(guide.contains("Published `0.1.0` beta | `8597828` | `1.2`"));
        assertTrue(guide.contains(
                "Published `0.2.0` beta | `8621418` | `1.3`"
        ));
        assertTrue(guide.contains(
                "Published `0.3.0` beta | `8632899` | `1.3`"
        ));
        assertTrue(guide.contains(
                "Published `0.4.0` beta | `8651841` | `1.4`"
        ));
        assertTrue(guide.contains("Published API `1.4` Distribution Boundary"));
        assertTrue(guide.contains("jawms@[0.1.0,0.3.0)"));
        assertTrue(guide.contains("jawms@[0.3.0,0.4.0)"));
        assertTrue(guide.contains("jawms@[0.4.0,0.5.0)"));
        assertFalse(guide.contains("jawms@[0.1.0,0.2.0)"));
        assertFalse(guide.contains(
                "curse.maven:just-another-wizardry-mana-system-1643697:PENDING"
        ));
        assertFalse(guide.contains("curse.maven:just-another-wizardry-mana-system-1643697:0.4.0"));
        assertFalse(guide.contains("file identity pending"));
        assertFalse(guide.contains("Candidate `0.4.0`"));
        assertFalse(guide.contains("Ã"));
    }

    @Test
    void canonicalGuideOwnsLifecycleAndRenderingContract() throws IOException {
        String guide = read(GUIDE_PATH);

        assertTrue(guide.contains("RenderGameOverlayEvent.Chat"));
        assertTrue(guide.contains("priority = EventPriority.LOWEST"));
        assertTrue(guide.contains("receiveCanceled = false"));
        assertTrue(guide.contains(
                "minimal flow compiles against the published `0.4.0` / API-`1.4`"
        ));
        assertTrue(guide.contains("event.getPartialTicks()"));
        assertTrue(guide.contains("event.getScaledWidth()"));
        assertTrue(guide.contains("event.getScaledHeight()"));
        assertTrue(guide.contains("Do not copy the published `0.1.0` dependency"));
        int publishedFlow = guide.indexOf("The following minimal flow compiles");
        int contextDelta = guide.indexOf("API `1.3` added immutable `ClientHudContext`");
        assertTrue(publishedFlow >= 0);
        assertTrue(contextDelta > publishedFlow);
        assertFalse(guide.substring(publishedFlow, contextDelta).contains("getContext()"));
        assertTrue(guide.substring(contextDelta).contains("getContext()"));
        assertTrue(guide.contains("event.setCanceled(true)"));
        assertTrue(guide.contains("if (hideWhenFull"));
        assertTrue(guide.indexOf("event.setCanceled(true)")
                < guide.indexOf("if (hideWhenFull"));
        assertTrue(guide.contains("mana per second"));
        assertTrue(guide.contains("max(0, configured base + sum(flat))"));
        assertTrue(guide.contains("After those factors, maximum mana is floored"));
        assertTrue(guide.contains("* (1 + sum(percentage increases) / 100)"));
        assertFalse(guide.contains("max(allowed minimum, configured base + sum(flat))"));
        assertTrue(guide.contains("ManaApi.getRegisteredProviders()"));
        assertTrue(guide.contains("ManaApi.isProviderRegistrationFrozen()"));
        assertTrue(guide.contains("does not register anything by itself"));
        assertTrue(guide.contains("ManaApi.invalidateModifierCache(player)"));
        assertTrue(guide.contains("ManaApi.markModifierCacheDirty(player)"));
        assertTrue(guide.contains("runs **before** the authoritative addon state changes"));
        assertTrue(guide.contains("do not evaluate providers synchronously"));
        assertTrue(guide.contains("next server END-tick profile evaluation"));
        assertTrue(guide.contains("`PotionAddedEvent` is posted before"));
        assertTrue(guide.contains("amplifier replacement, removal, or expiry"));
        assertTrue(guide.contains("Filter out client-side entities"));
        assertTrue(guide.contains("ManaApi.explainManaProfile(player)"));
        assertTrue(guide.contains("does not scan equipment, invoke a provider"));
        assertTrue(guide.contains("`OVERRIDDEN`"));
        assertTrue(guide.contains("`INVALID`"));
        assertTrue(guide.contains("`FAILED`"));
        assertTrue(guide.contains("Only `APPLIED` records expose"));
        assertTrue(guide.contains("Unmatched item providers"));
        assertTrue(guide.contains("never expose exception text"));
        assertTrue(guide.contains("Compare known revisions only within the current connection"));
        assertTrue(guide.contains("glPushAttrib/glPopAttrib"));
        assertTrue(guide.contains(
                "src/apiCompatibilityFixture/java/example/jawmsaddon/"
                        + "CacheSafeReplacementHud.java"
        ));
        assertTrue(guide.contains("plain JVM test"));
        assertTrue(guide.contains("launched Forge test"));
        assertTrue(guide.contains("docs/COMMANDS.md"));
        assertTrue(guide.contains("jawms-devtools-0.4.0.jar"));
    }

    @Test
    void publicDocumentationRejectsMojibakeAndNonAsciiFormulaOperators()
            throws IOException {
        String[] paths = {
                GUIDE_PATH,
                "docs/COMMANDS.md",
                "README.md",
                "MOD-PAGE.md",
                "src/main/java/com/mahghuuuls/jawms/api/package-info.java",
                "src/main/java/com/mahghuuuls/jawms/api/client/package-info.java"
        };
        for (String path : paths) {
            String text = read(path);
            assertFalse(text.contains("Ã"), path);
            assertFalse(text.contains("Â"), path);
            assertFalse(text.contains("â€"), path);
            assertFalse(text.contains("\uFFFD"), path);
            assertFalse(text.contains("×"), path);
        }
    }

    @Test
    void publicEntryPointsLinkGuideAndKeepModPageDeveloperCopyConcise() throws IOException {
        String readme = read("README.md");
        String modPage = read("MOD-PAGE.md");
        String rootPackage = read(
                "src/main/java/com/mahghuuuls/jawms/api/package-info.java"
        );
        String clientPackage = read(
                "src/main/java/com/mahghuuuls/jawms/api/client/package-info.java"
        );

        assertTrue(readme.contains("(" + GUIDE_PATH + ")"));
        assertTrue(modPage.contains("(" + GUIDE_URL + ")"));
        assertTrue(rootPackage.contains(GUIDE_URL));
        assertTrue(clientPackage.contains(GUIDE_URL));
        assertTrue(readme.contains("current published `0.4.0` release"));
        assertTrue(readme.contains("Published JAWMS `0.4.0` file `8651841`"));
        assertTrue(readme.contains("documented API `1.2` binary surface"));
        assertTrue(readme.contains("docs/COMMANDS.md"));
        String normalizedModPage = modPage.replace("\r\n", "\n");
        int developerStart = normalizedModPage.indexOf("## For Mod Developers");
        int developerEnd = normalizedModPage.indexOf("## Detailed System Reference");
        assertTrue(developerStart >= 0);
        assertTrue(developerEnd > developerStart);
        String developerSection = normalizedModPage
                .substring(developerStart, developerEnd)
                .trim();
        assertEquals(
                "## For Mod Developers\n\n"
                        + "JAWMS includes a documented Java 8 API for addons. "
                        + "Developers can find setup instructions and the full technical "
                        + "reference in the [Addon Development Guide](" + GUIDE_URL + ").",
                developerSection
        );
        int companionsStart = normalizedModPage.indexOf(
                "### Mana Bar and Companion Addons"
        );
        int companionsEnd = normalizedModPage.indexOf("### Requirements");
        assertTrue(companionsStart >= 0);
        assertTrue(companionsEnd > companionsStart);
        String companionSection = normalizedModPage.substring(
                companionsStart,
                companionsEnd
        );
        assertTrue(companionSection.contains(
                "Players can customize or extend JAWMS with companion addons:"
        ));
        assertTrue(modPage.contains(
                "https://www.curseforge.com/minecraft/mc-mods/jawms-hud-styles"
        ));
        assertTrue(modPage.contains(
                "https://www.curseforge.com/minecraft/mc-mods/jawms-classic-bar"
        ));
        assertTrue(companionSection.contains(
                "- [JAWMS Additions]"
                        + "(https://www.curseforge.com/minecraft/mc-mods/jawms-additions) "
                        + "expands the mana system with new potions, enchantments, and Baubles."
        ));
    }

    private static String read(String relativePath) throws IOException {
        Path path = Paths.get(System.getProperty("user.dir")).resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
