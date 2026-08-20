package com.mahghuuuls.jawms.api;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiClarityDocumentationContractTest {

    @Test
    void wholeManaDirectionalLockoutAndCommittedAccountingStayExplicit() throws Exception {
        String contribution = source("ManaContribution.java");
        String mutation = source("ManaMutationResult.java");

        assertTrue(contribution.contains("signed whole-mana maximum contribution"));
        assertTrue(contribution.contains("Positive values increase the delay and negative values reduce it"));
        assertTrue(contribution.contains("flatLockoutIncreaseSeconds"));
        assertTrue(contribution.contains("flatLockoutReductionSeconds"));
        assertTrue(contribution.contains("whichever flat-lockout setter is called last wins"));

        assertTrue(mutation.contains("amount actually committed to JAWMS state"));
        assertTrue(mutation.contains("rather than the requested amount"));
        assertTrue(mutation.contains("does not make that separate external mutation atomic"));
    }

    private static String source(String fileName) throws Exception {
        Path path = Paths.get(System.getProperty("user.dir"))
                .resolve("src/main/java/com/mahghuuuls/jawms/api")
                .resolve(fileName);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
