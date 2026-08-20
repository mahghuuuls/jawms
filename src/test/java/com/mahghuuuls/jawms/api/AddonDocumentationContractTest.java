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
    void currentGuideStaysCurrentAndHistoryOwnsOlderVersions() throws IOException {
        String guide = read(GUIDE_PATH);
        String history = read("docs/API-HISTORY.md");

        assertTrue(guide.contains(
                "JAWMS `1.0.0` provides API `1.5`"
        ));
        assertTrue(guide.contains("[API History](API-HISTORY.md)"));
        assertTrue(guide.contains("jawms_curseforge_file=REPLACE_WITH_NUMERIC_FILE_ID"));
        assertTrue(guide.contains(
                "curse.maven:just-another-wizardry-mana-system-1643697:${jawms_curseforge_file}"
        ));
        assertTrue(guide.contains("curse.maven:electroblobs-wizardry-265642:8320066"));
        assertTrue(guide.contains("zone.rong:mixinbooter:11.8"));
        assertTrue(guide.contains("required-after:mixinbooter@[11.8,)"));
        assertTrue(guide.contains("jawms@[1.0.0,1.1.0)"));
        assertFalse(guide.contains("0.1.0"));
        assertFalse(guide.contains("0.2.0"));
        assertFalse(guide.contains("0.3.0"));
        assertFalse(guide.contains("0.4.0"));
        assertFalse(guide.contains("API `1.2`"));
        assertFalse(guide.contains("API `1.3`"));
        assertFalse(guide.contains("API `1.4`"));

        assertTrue(history.contains("`0.1.0` | `8597828` | `1.2`"));
        assertTrue(history.contains("`0.2.0` | `8621418` | `1.3`"));
        assertTrue(history.contains("`0.3.0` | `8632899` | `1.3`"));
        assertTrue(history.contains("`0.4.0` | `8651841` | `1.4`"));
        assertTrue(history.contains("`1.0.0` | See the CurseForge Files page | `1.5`"));
        assertTrue(history.contains("jawms@[0.4.0,0.5.0)"));
        assertTrue(history.contains("MixinBooter `[11.11,12.0)`"));
        assertFalse(guide.contains("Ã"));
        assertFalse(history.contains("Ã"));
    }

    @Test
    void canonicalGuideOwnsLifecycleAndRenderingContract() throws IOException {
        String guide = read(GUIDE_PATH);

        assertTrue(guide.contains("RenderGameOverlayEvent.Chat"));
        assertTrue(guide.contains("priority = EventPriority.LOWEST"));
        assertTrue(guide.contains("receiveCanceled = false"));
        assertTrue(guide.contains("This current API example"));
        assertTrue(guide.contains("event.getPartialTicks()"));
        assertTrue(guide.contains("event.getScaledWidth()"));
        assertTrue(guide.contains("event.getScaledHeight()"));
        assertTrue(guide.contains("ClientHudContext context = event.getContext()"));
        assertTrue(guide.contains("context.isCreativeModeKnown()"));
        assertTrue(guide.contains("event.setCanceled(true)"));
        assertTrue(guide.contains("if (hideWhenFull"));
        assertTrue(guide.indexOf("event.setCanceled(true)")
                < guide.indexOf("if (hideWhenFull"));
        assertTrue(guide.contains("mana per second"));
        assertTrue(guide.contains("max(0, configured base + sum(flat))"));
        assertTrue(guide.contains("After those factors, maximum mana is floored"));
        assertTrue(guide.contains("maximum mana are whole `int` mana points"));
        assertTrue(guide.contains("use `getActualAmount()`, not the requested amount"));
        assertTrue(guide.contains("`getActualDelta()` is signed"));
        assertTrue(guide.contains("flatLockoutIncreaseSeconds(value)"));
        assertTrue(guide.contains("flatLockoutReductionSeconds(value)"));
        assertTrue(guide.contains("last of these three builder calls wins"));
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
        assertTrue(guide.contains("ManaApi.getConfiguredFlaskSnapshot()"));
        assertTrue(guide.contains("ClientManaApi.getConfiguredFlaskSnapshot()"));
        assertTrue(guide.contains("unavailable snapshot is not three configured zero-value flasks"));
        assertTrue(guide.contains("two snapshot types can therefore carry different revisions"));
        assertTrue(guide.contains("four-argument registration overload"));
        assertTrue(guide.contains("invariant presentation promise"));
        assertTrue(guide.contains("invoke providers zero additional times"));
        assertTrue(guide.contains("Seeing a preview on an unequipped stack is informational"));
        assertTrue(guide.contains("after ordinary Forge tooltip-event subscribers return"));
        assertTrue(guide.contains("not precedence over another coremod or Mixin"));
        assertTrue(guide.contains("glPushAttrib/glPopAttrib"));
        assertTrue(guide.contains(
                "src/apiCompatibilityFixture/java/example/jawmsaddon/"
                        + "CacheSafeReplacementHud.java"
        ));
        assertTrue(guide.contains("plain JVM test"));
        assertTrue(guide.contains("launched Forge test"));
        assertTrue(guide.contains("docs/COMMANDS.md"));
        assertTrue(guide.contains("jawms-devtools-1.0.0.jar"));
        assertTrue(guide.contains("JAWMS `[1.0.0,1.1.0)`"));
    }

    @Test
    void publicDocumentationRejectsMojibakeAndNonAsciiFormulaOperators()
            throws IOException {
        String[] paths = {
                GUIDE_PATH,
                "docs/API-HISTORY.md",
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
        assertTrue(readme.contains("JAWMS `1.0.0` uses additive addon API `1.5`"));
        assertTrue(readme.contains("Use the guide for current dependency setup and API usage"));
        assertTrue(readme.contains("[API History](docs/API-HISTORY.md)"));
        assertTrue(readme.contains("published API `1.2`, `1.3`, and `1.4` binary surfaces"));
        assertFalse(readme.contains("public beta"));
        assertFalse(modPage.contains("Beta release:"));
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
                        + "JAWMS includes a documented API for addons. "
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
        assertTrue(companionSection.contains(
                "- [JAWMS Integrations]"
                        + "(https://www.curseforge.com/minecraft/mc-mods/jawms-integrations) "
                        + "adds optional compatibility between JAWMS and other mods."
        ));
    }

    private static String read(String relativePath) throws IOException {
        Path path = Paths.get(System.getProperty("user.dir")).resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
