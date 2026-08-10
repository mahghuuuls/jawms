package com.mahghuuuls.jawms.api;

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
        assertTrue(guide.contains("zone.rong:mixinbooter:11.11"));
        assertTrue(guide.contains("Published `0.1.0` beta | `8597828` | `1.2`"));
        assertTrue(guide.contains(
                "Current development candidate | Not published; file identity pending | `1.3`"
        ));
        assertFalse(guide.contains(
                "curse.maven:just-another-wizardry-mana-system-1643697:PENDING"
        ));
    }

    @Test
    void canonicalGuideOwnsLifecycleAndRenderingContract() throws IOException {
        String guide = read(GUIDE_PATH);

        assertTrue(guide.contains("RenderGameOverlayEvent.Chat"));
        assertTrue(guide.contains("priority = EventPriority.LOWEST"));
        assertTrue(guide.contains("receiveCanceled = false"));
        assertTrue(guide.contains(
                "minimal flow compiles against the published `0.1.0` / API-`1.2`"
        ));
        assertTrue(guide.contains("event.getPartialTicks()"));
        assertTrue(guide.contains("event.getScaledWidth()"));
        assertTrue(guide.contains("event.getScaledHeight()"));
        assertTrue(guide.contains("Do not copy the published `0.1.0` dependency"));
        int publishedFlow = guide.indexOf("The following minimal flow compiles");
        int candidateDelta = guide.indexOf("The unpublished API-`1.3` candidate");
        assertTrue(publishedFlow >= 0);
        assertTrue(candidateDelta > publishedFlow);
        assertFalse(guide.substring(publishedFlow, candidateDelta).contains("getContext()"));
        assertTrue(guide.substring(candidateDelta).contains("getContext()"));
        assertTrue(guide.contains("event.setCanceled(true)"));
        assertTrue(guide.contains("if (hideWhenFull"));
        assertTrue(guide.indexOf("event.setCanceled(true)")
                < guide.indexOf("if (hideWhenFull"));
        assertTrue(guide.contains("mana per second"));
        assertTrue(guide.contains("max(0, configured base + sum(flat))"));
        assertTrue(guide.contains("After those factors, maximum mana is floored"));
        assertFalse(guide.contains("max(allowed minimum, configured base + sum(flat))"));
        assertTrue(guide.contains("Compare known revisions only within the current connection"));
        assertTrue(guide.contains("glPushAttrib/glPopAttrib"));
        assertTrue(guide.contains(
                "src/apiCompatibilityFixture/java/example/jawmsaddon/"
                        + "CacheSafeReplacementHud.java"
        ));
        assertTrue(guide.contains("plain JVM test"));
        assertTrue(guide.contains("launched Forge test"));
    }

    @Test
    void publicEntryPointsLinkSameGuideAndVersionBoundary() throws IOException {
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
        assertTrue(readme.contains("Published JAWMS `0.1.0` exposes API `1.2`"));
        assertTrue(readme.contains("development candidate exposes additive API `1.3`"));
        assertTrue(modPage.contains("Published JAWMS `0.1.0` provides API `1.2`"));
        assertTrue(modPage.contains("development candidate provides additive API `1.3`"));
    }

    private static String read(String relativePath) throws IOException {
        Path path = Paths.get(System.getProperty("user.dir")).resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
