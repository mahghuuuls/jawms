package com.mahghuuuls.jawms.api;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientHudContext;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiBoundaryTest {

    @Test
    void apiVersionAndSurfaceAreStableAndServerSafe() throws Exception {
        assertEquals("1.8", ManaApi.API_VERSION);
        assertEquals(1, ManaApiVersion.MAJOR);
        assertEquals(8, ManaApiVersion.MINOR);
        String packageContract = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/mahghuuuls/jawms/api/package-info.java")), StandardCharsets.UTF_8);
        assertTrue(packageContract.contains("JAWMS API, version " + ManaApi.API_VERSION + "."));
        for (Class<?> type : new Class<?>[] { ICastCostParticipant.class,
                ICastCostReservation.class, CastCostPreparation.class, CastCostSettlement.class,
                CastCostReleaseReason.class, CastAttemptContext.class,
                CastAttemptContext.AcceptedCost.class, CastAttemptOutcome.class,
                CastParticipantOutcome.class }) {
            assertPublicSignaturesDoNotExposeImplementation(type);
        }

        assertPublicSignaturesDoNotExposeImplementation(ManaApi.class);
        assertPublicSignaturesDoNotExposeImplementation(IManaService.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaPublicState.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaMutationResult.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaMutationFailure.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaContributionDisposition.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaContributionExplanation.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaContributionFailure.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaContributionSourceKind.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaProfileAggregation.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaProfileExplanation.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaProviderDescriptor.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaProviderKind.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaStateChangeEvent.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaContribution.class);
        assertPublicSignaturesDoNotExposeImplementation(ConfiguredFlaskSnapshot.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaItemContext.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaTooltips.class);
        assertPublicSignaturesDoNotExposeImplementation(IItemManaModifierProvider.class);
        assertPublicSignaturesDoNotExposeImplementation(IContextualManaModifierProvider.class);
        assertPublicSignaturesDoNotExposeImplementation(ClientManaApi.class);
        assertPublicSignaturesDoNotExposeImplementation(ClientHudContext.class);
        assertPublicSignaturesDoNotExposeImplementation(ClientManaSnapshot.class);
        assertPublicSignaturesDoNotExposeImplementation(ManaHudRenderEvent.class);

        assertEquals(ConfiguredFlaskSnapshot.class,
                ManaApi.class.getMethod("getConfiguredFlaskSnapshot").getReturnType());
        assertEquals(ConfiguredFlaskSnapshot.class,
                ClientManaApi.class.getMethod("getConfiguredFlaskSnapshot").getReturnType());
        ManaApi.class.getMethod(
                "registerItemProvider", net.minecraft.util.ResourceLocation.class,
                net.minecraft.item.Item.class, IItemManaModifierProvider.class,
                ManaContribution.class
        );
        ManaApi.class.getMethod(
                "setItemProviderContributionPreview",
                net.minecraft.util.ResourceLocation.class, ManaContribution.class
        );
        ManaApi.class.getMethod(
                "clearItemProviderContributionPreview",
                net.minecraft.util.ResourceLocation.class
        );
        assertEquals(int.class,
                ManaContribution.class.getMethod("getFlatMaximumMana").getReturnType());
        ManaContribution.Builder.class.getMethod("flatMaximumMana", int.class);
        ManaContribution.Builder.class.getMethod("flatLockoutSeconds", double.class);
        ManaContribution.Builder.class.getMethod("flatLockoutIncreaseSeconds", double.class);
        ManaContribution.Builder.class.getMethod("flatLockoutReductionSeconds", double.class);
    }

    @Test
    void publishedApiOnePointTwoClientDescriptorsRemainPresent() throws Exception {
        ClientManaSnapshot.class.getMethod(
                "available", int.class, int.class, double.class
        );
        ClientManaSnapshot.class.getMethod("unavailable");
        ClientManaSnapshot.class.getMethod("isAvailable");
        ClientManaSnapshot.class.getMethod("getCurrentMana");
        ClientManaSnapshot.class.getMethod("getMaximumMana");
        ClientManaSnapshot.class.getMethod("getEffectiveRegeneration");

        ManaHudRenderEvent.class.getConstructor(
                ClientManaSnapshot.class, float.class, int.class, int.class
        );
        ManaHudRenderEvent.class.getMethod("getSnapshot");
        ManaHudRenderEvent.class.getMethod("getPartialTicks");
        ManaHudRenderEvent.class.getMethod("getScaledWidth");
        ManaHudRenderEvent.class.getMethod("getScaledHeight");
    }

    @Test
    void serverOnlyExplanationDoesNotEnterExistingPacketSchemas() throws Exception {
        for (String path : new String[]{
                "src/main/java/com/mahghuuuls/jawms/internal/mana/ManaStateMessage.java",
                "src/main/java/com/mahghuuuls/jawms/internal/rules/GameplayRulesMessage.java",
                "src/main/java/com/mahghuuuls/jawms/internal/casting/ContinuousCastReleaseMessage.java",
                "src/main/java/com/mahghuuuls/jawms/internal/casting/ContinuousCastStopMessage.java"
        }) {
            String source = new String(
                    Files.readAllBytes(Paths.get(System.getProperty("user.dir")).resolve(path)),
                    StandardCharsets.UTF_8
            );
            assertFalse(source.contains("ManaProfileExplanation"), path);
            assertFalse(source.contains("ManaContributionExplanation"), path);
        }
    }

    @Test
    void configuredFlaskSnapshotReusesExistingScalarRulesFields() throws Exception {
        String source = new String(
                Files.readAllBytes(Paths.get(System.getProperty("user.dir")).resolve(
                        "src/main/java/com/mahghuuuls/jawms/internal/rules/GameplayRulesMessage.java"
                )),
                StandardCharsets.UTF_8
        );

        assertFalse(source.contains("ConfiguredFlaskSnapshot"));
    }

    private static void assertPublicSignaturesDoNotExposeImplementation(Class<?> apiType) {
        for (Method method : apiType.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers())) {
                continue;
            }
            assertAllowedType(method.getReturnType());
            for (Class<?> parameterType : method.getParameterTypes()) {
                assertAllowedType(parameterType);
            }
        }
    }

    private static void assertAllowedType(Class<?> type) {
        String name = type.getName();
        assertFalse(name.startsWith("com.mahghuuuls.jawms.internal."), name);
        assertFalse(name.startsWith("com.mahghuuuls.jawms.client."), name);
        assertFalse(name.startsWith("net.minecraft.client."), name);
    }
}
