package com.mahghuuuls.jawms.api;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDocumentationContractTest {

    private static final String DOCUMENT = "docs/COMMANDS.md";

    private static final List<Contract> CONTRACTS = Arrays.asList(
            new Contract(
                    "Contributor Development Fixtures", "jawms-mv02-pilot",
                    "PilotCommand", "src/developmentFixture/java/com/mahghuuuls/jawms/testfixture/automation/InputPilotHarness.java"
            ),
            new Contract(
                    "API Compatibility Fixtures", "jawms-continuous-fixture",
                    "ContinuousFixtureCommand", "src/castCostFixture/java/example/jawmscast/ContinuousCastFixture.java"
            ),
            new Contract(
                    "Contributor Development Fixtures", "jawms-use-transport",
                    "TransportCommand", "src/developmentFixture/java/com/mahghuuuls/jawms/testfixture/ContinuousTransportFixture.java"
            ),
            new Contract(
                    "API Compatibility Fixtures", "jawms-cast-fixture",
                    "CastFixtureCommand", "src/castCostFixture/java/example/jawmscast/CastCostFixture.java"
            ),
            new Contract(
                    "Primary JAWMS", "jawms",
                    "JawmsCommand",
                    "src/main/java/com/mahghuuuls/jawms/internal/rules/JawmsCommand.java"
            ),
            new Contract(
                    "Optional JAWMS Developer Tools", "jawms-dev",
                    "JawmsDevCommand",
                    "src/developerTools/java/com/mahghuuuls/jawms/devtools/JawmsDevCommand.java"
            ),
            new Contract(
                    "Contributor Development Fixtures", "jawms-test",
                    "SetManaCommand",
                    "src/developmentFixture/java/com/mahghuuuls/jawms/testfixture/ManaTestFixture.java"
            ),
            new Contract(
                    "Contributor Development Fixtures", "jawms-balance",
                    "BalanceCommand",
                    "src/developmentFixture/java/com/mahghuuuls/jawms/testfixture/BalanceCommand.java"
            ),
            new Contract(
                    "API Compatibility Fixtures", "jawms-api-fixture",
                    "FixtureCommand",
                    "src/apiCompatibilityFixture/java/example/jawmsaddon/ApiConsumerFixture.java"
            ),
            new Contract(
                    "API Compatibility Fixtures", "jawms-api-hud",
                    "ClientReplacementHudFixture",
                    "src/apiCompatibilityFixture/java/example/jawmsaddon/ClientReplacementHudFixture.java"
            )
    );

    @Test
    void everyRegisteredRootAndAuthoritativeUsageAppearsInExactlyOneTier()
            throws IOException {
        String document = read(DOCUMENT);
        assertTrue(documentSatisfiesContracts(document));
        assertEquals(registeredCommandClasses(), contractedCommandClasses());
    }

    @Test
    void omissionAndTierMisclassificationAreDetected() throws IOException {
        String document = read(DOCUMENT);
        assertFalse(documentSatisfiesContracts(
                document.replace("### `/jawms-dev`", "### `/removed-dev-root`")
        ));

        String devSection = tierSection(document, "Optional JAWMS Developer Tools");
        String moved = document.replace(devSection, "")
                + "\n" + devSection.replace(
                "## Optional JAWMS Developer Tools",
                "## Contributor Development Fixtures"
        );
        assertFalse(documentSatisfiesContracts(moved));

        assertFalse(documentSatisfiesContracts(document.replace(
                "Failure conditions: Invalid syntax, target, number, permission, side,"
                        + " API state, or operation fails without mutation.",
                "Invalid operations fail without mutation."
        )));
    }

    private static boolean documentSatisfiesContracts(String document) throws IOException {
        for (Contract contract : CONTRACTS) {
            String source = read(contract.sourcePath);
            String usage = extractUsage(source, contract.root);
            String tier = tierSection(document, contract.tier);
            String heading = "### `/" + contract.root + "`";
            String rootSection = rootSection(document, contract.root);
            if (count(document, heading) != 1
                    || !tier.contains(heading)
                    || !tier.contains(usage)
                    || !hasRequiredFields(rootSection)
                    || !source.contains("return \"" + contract.root + "\"")) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasRequiredFields(String section) {
        return section.contains("Permission level:")
                && section.contains("Side:")
                && section.contains("Audience:")
                && section.contains("Purpose/result:")
                && section.contains("Failure conditions:")
                && section.contains("Cleanup/reset:");
    }

    private static Set<String> registeredCommandClasses() throws IOException {
        Set<String> result = new HashSet<>();
        Pattern registration = Pattern.compile(
                "register(?:Server)?Command\\s*\\(\\s*(?:new\\s+)?([A-Za-z][A-Za-z0-9_]*)"
        );
        for (String sourceRoot : Arrays.asList(
                "src/main/java", "src/developerTools/java",
                "src/developmentFixture/java", "src/apiCompatibilityFixture/java", "src/castCostFixture/java"
        )) {
            try (java.util.stream.Stream<Path> files = Files.walk(Paths.get(sourceRoot))) {
                files.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> {
                            try {
                                Matcher matches = registration.matcher(read(path.toString()));
                                while (matches.find()) {
                                    result.add(matches.group(1));
                                }
                            } catch (IOException failure) {
                                throw new IllegalStateException(failure);
                            }
                        });
            }
        }
        return result;
    }

    private static Set<String> contractedCommandClasses() {
        Set<String> result = new HashSet<>();
        for (Contract contract : CONTRACTS) {
            result.add(contract.implementationClass);
        }
        return result;
    }

    private static String extractUsage(String source, String root) {
        int name = source.indexOf("return \"" + root + "\"");
        int method = source.indexOf("String getUsage", name);
        int returned = source.indexOf("return ", method);
        int end = source.indexOf(';', returned);
        if (name < 0 || method < 0 || returned < 0 || end < 0) {
            throw new IllegalArgumentException("Cannot find command usage for " + root);
        }
        Matcher strings = Pattern.compile("\\\"((?:\\\\.|[^\\\"])*)\\\"")
                .matcher(source.substring(returned, end));
        StringBuilder usage = new StringBuilder();
        while (strings.find()) {
            usage.append(strings.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\"));
        }
        return usage.toString();
    }

    private static String tierSection(String document, String tier) {
        String heading = "## " + tier;
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int end = document.indexOf("\n## ", start + heading.length());
        return end < 0 ? document.substring(start) : document.substring(start, end);
    }

    private static String rootSection(String document, String root) {
        String heading = "### `/" + root + "`";
        int start = document.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int nextRoot = document.indexOf("\n### ", start + heading.length());
        int nextTier = document.indexOf("\n## ", start + heading.length());
        int end = nextRoot < 0 ? nextTier
                : nextTier < 0 ? nextRoot : Math.min(nextRoot, nextTier);
        return end < 0 ? document.substring(start) : document.substring(start, end);
    }

    private static int count(String source, String needle) {
        int result = 0;
        int cursor = 0;
        while ((cursor = source.indexOf(needle, cursor)) >= 0) {
            result++;
            cursor += needle.length();
        }
        return result;
    }

    private static String read(String path) throws IOException {
        Path resolved = Paths.get(path);
        return new String(Files.readAllBytes(resolved), StandardCharsets.UTF_8);
    }

    private static final class Contract {
        private final String tier;
        private final String root;
        private final String implementationClass;
        private final String sourcePath;

        private Contract(
                String tier, String root, String implementationClass, String sourcePath
        ) {
            this.tier = tier;
            this.root = root;
            this.implementationClass = implementationClass;
            this.sourcePath = sourcePath;
        }
    }
}
