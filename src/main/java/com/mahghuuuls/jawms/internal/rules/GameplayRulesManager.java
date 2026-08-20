package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.internal.mana.ManaNetwork;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaEvaluationGuard;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryFlaskBridge;
import com.mahghuuuls.jawms.internal.modifier.ProviderPreviewRegistry;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class GameplayRulesManager {

    private static final AtomicLong NEXT_REVISION = new AtomicLong(1L);
    private static final AtomicLong NEXT_INTEGRATION_REVISION = new AtomicLong(1L);
    private static volatile GameplayRules current = GameplayRules.initialDefaults();
    private static File configurationFile;
    private static File integrationConfigurationFile;
    private static volatile Thread serverAuthorityThread;
    private static volatile MinecraftServer serverAuthority;

    private GameplayRulesManager() {
    }

    public static synchronized void configure(File file, File integrationFile) {
        if (file == null || integrationFile == null) {
            throw new NullPointerException("file and integrationFile must not be null");
        }
        configurationFile = file;
        integrationConfigurationFile = integrationFile;
    }

    public static synchronized void initialize() {
        if (configurationFile == null || integrationConfigurationFile == null) {
            throw new IllegalStateException("Gameplay and integration rules files were not configured.");
        }
        BuildResult result = build(current);
        current = result.rules;
        WizardryFlaskBridge.refreshCapacities(result.rules);
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Published initial JAWMS gameplay rules revision {}: baseMaximum={}, baseRegeneration={}, "
                        + "lockoutTicks={}, integrationRevision={}, spellCosts={}, itemRules={}, "
                        + "disabledProviders={}, warnings={}.",
                result.rules.getRevision(),
                result.rules.getBaseMaximum(),
                result.rules.getBaseRegeneration(),
                result.rules.getRegenerationLockoutTicks(),
                result.rules.getWizardryIntegrationRules().getRevision(),
                result.rules.getSpellCosts().size(),
                result.rules.getConfiguredItemRules().size(),
                result.rules.getDisabledProviderIds().size(),
                result.warningCount
        );
    }

    public static GameplayRules current() {
        return current;
    }

    /** Publishes declarations accepted during other mods' initialization. */
    public static synchronized void completeProviderRegistration() {
        if (serverAuthorityThread != null) {
            throw new IllegalStateException(
                    "Provider registration must complete before a logical server starts."
            );
        }
        refreshProviderPreviewIndex("provider registration completion", false);
    }

    public static ConfiguredFlaskSnapshot configuredFlaskSnapshot() {
        GameplayRules rules = current;
        if (Thread.currentThread() != serverAuthorityThread) {
            return ConfiguredFlaskSnapshot.unavailable();
        }
        return ConfiguredFlaskSnapshot.available(
                rules.getSmallFlask(),
                rules.getMediumFlask(),
                rules.getLargeFlask(),
                rules.getRevision()
        );
    }

    public static synchronized void replaceProviderPreview(
            ResourceLocation providerId,
            ManaContribution contribution
    ) {
        requireServerAuthority();
        ProviderPreviewRegistry.instance().replaceActive(providerId, contribution);
        publishProviderPreviews();
    }

    public static synchronized void clearProviderPreview(ResourceLocation providerId) {
        requireServerAuthority();
        if (ProviderPreviewRegistry.instance().clearActive(providerId)) {
            publishProviderPreviews();
        }
    }

    /** Restores server-side snapshot availability when another local server starts. */
    public static synchronized void serverStarted() {
        serverStarted(null);
    }

    public static synchronized void serverStarted(MinecraftServer server) {
        serverAuthorityThread = Thread.currentThread();
        serverAuthority = server;
        ProviderPreviewRegistry.instance().serverStarted();
    }

    /** Clears server-side snapshot availability after the logical server stops. */
    public static synchronized void serverStopped() {
        ProviderPreviewRegistry.instance().serverStopped();
        refreshProviderPreviewIndex("logical-server stop", false);
        serverAuthority = null;
        serverAuthorityThread = null;
    }

    public static ReloadResult reload(MinecraftServer server) {
        if (server == null) {
            throw new NullPointerException("server");
        }
        if (!server.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Gameplay rules reload must run on the Minecraft server thread.");
        }

        GameplayRules previous = current;
        final BuildResult result;
        try {
            result = build(previous);
        } catch (RuntimeException exception) {
            JustAnotherWizardryManaSystemMod.LOGGER.error(
                    "JAWMS gameplay rules reload failed before publication; revision {} remains active.",
                    previous.getRevision(),
                    exception
            );
            return new ReloadResult(false, previous.getRevision(), previous.getRevision(), 0);
        }

        current = result.rules;
        WizardryFlaskBridge.refreshCapacities(result.rules);
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            ManaService.applyRulesReload(player, previous, result.rules);
            ManaNetwork.synchronizeRules(player, result.rules);
        }
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Published JAWMS gameplay rules reload: oldRevision={}, newRevision={}, warnings={}, players={}.",
                previous.getRevision(),
                result.rules.getRevision(),
                result.warningCount,
                server.getCurrentPlayerCount()
        );
        return new ReloadResult(
                true,
                previous.getRevision(),
                result.rules.getRevision(),
                result.warningCount
        );
    }

    private static BuildResult build(GameplayRules previous) {
        List<String> warnings = new ArrayList<>();
        GameplayRules mainCandidate = GameplayRulesLoader.load(
                configurationFile,
                previous,
                NEXT_REVISION.getAndIncrement(),
                collectSpells(),
                warnings::add
        );
        long candidateRevision = NEXT_INTEGRATION_REVISION.getAndIncrement();
        WizardryIntegrationRules integration = loadIntegrationCandidate(
                integrationConfigurationFile,
                previous.getWizardryIntegrationRules(),
                candidateRevision,
                warnings
        );
        GameplayRules rules = mainCandidate.withWizardryIntegrationRules(integration);
        rules = rules.withRevisionAndProviderPreviews(
                rules.getRevision(),
                ProviderPreviewIndex.build(
                        rules, ProviderPreviewRegistry.instance().declarations()
                )
        );
        for (String warning : warnings) {
            JustAnotherWizardryManaSystemMod.LOGGER.warn(
                    "JAWMS rules revision {}: {}",
                    rules.getRevision(),
                    warning
            );
        }
        return new BuildResult(rules, warnings.size());
    }

    private static void requireServerAuthority() {
        if (Thread.currentThread() != serverAuthorityThread) {
            throw new IllegalStateException(
                    "Provider-preview mutation requires the active logical-server thread."
            );
        }
        if (ManaEvaluationGuard.isEvaluatingOnCurrentThread()) {
            throw new IllegalStateException(
                    "Provider-preview mutation is not allowed during provider evaluation."
            );
        }
    }

    private static void publishProviderPreviews() {
        GameplayRules previous = current;
        GameplayRules replacement = refreshProviderPreviewIndex(
                "active preview mutation", true
        );
        MinecraftServer server = serverAuthority;
        if (server != null) {
            for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
                ManaNetwork.synchronizeRules(player, replacement);
            }
        }
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Published JAWMS provider previews: oldRevision={}, newRevision={}, entries={}.",
                previous.getRevision(), replacement.getRevision(),
                replacement.getProviderPreviews().size()
        );
    }

    private static GameplayRules refreshProviderPreviewIndex(
            String reason,
            boolean forceRevision
    ) {
        GameplayRules previous = current;
        List<ProviderPreviewEntry> previews = ProviderPreviewIndex.build(
                previous, ProviderPreviewRegistry.instance().declarations()
        );
        if (!forceRevision && samePreviewIndex(previous.getProviderPreviews(), previews)) {
            return previous;
        }
        GameplayRules replacement = previous.withRevisionAndProviderPreviews(
                NEXT_REVISION.getAndIncrement(), previews
        );
        current = replacement;
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Updated JAWMS provider-preview index during {}: oldRevision={}, "
                        + "newRevision={}, entries={}.",
                reason, previous.getRevision(), replacement.getRevision(), previews.size()
        );
        return replacement;
    }

    private static boolean samePreviewIndex(
            List<ProviderPreviewEntry> left,
            List<ProviderPreviewEntry> right
    ) {
        if (left.size() != right.size()) {
            return false;
        }
        for (int index = 0; index < left.size(); index++) {
            ProviderPreviewEntry first = left.get(index);
            ProviderPreviewEntry second = right.get(index);
            if (!first.getProviderId().equals(second.getProviderId())
                    || !first.getTargetItemId().equals(second.getTargetItemId())
                    || !first.getExcludedMetadata().equals(second.getExcludedMetadata())
                    || first.getContribution() != second.getContribution()) {
                return false;
            }
        }
        return true;
    }

    static WizardryIntegrationRules loadIntegrationCandidate(
            File file,
            WizardryIntegrationRules previous,
            long candidateRevision,
            List<String> warnings
    ) {
        try {
            return WizardryIntegrationRulesLoader.load(file, candidateRevision);
        } catch (WizardryIntegrationRulesLoader.InvalidIntegrationRulesException exception) {
            for (String error : exception.getErrors()) {
                warnings.add("Rejected integration candidate " + candidateRevision + ": " + error
                        + "; retaining integration revision " + previous.getRevision() + ".");
            }
            return previous;
        } catch (RuntimeException exception) {
            warnings.add("Rejected integration candidate " + candidateRevision
                    + " because it could not be parsed: " + exception.getMessage()
                    + "; retaining integration revision " + previous.getRevision() + ".");
            return previous;
        }
    }

    private static List<SpellCostCompiler.SpellDefinition> collectSpells() {
        List<SpellCostCompiler.SpellDefinition> definitions = new ArrayList<>();
        for (Spell spell : Spell.getAllSpells()) {
            if (spell.getRegistryName() == null) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "Ignored a registered Wizardry spell without a registry name while building JAWMS rules."
                );
                continue;
            }
            definitions.add(new SpellCostCompiler.SpellDefinition(
                    spell.getRegistryName(),
                    spell.getCost(),
                    spell.getTier().ordinal() + 1
            ));
        }
        return definitions;
    }

    private static final class BuildResult {

        private final GameplayRules rules;
        private final int warningCount;

        private BuildResult(GameplayRules rules, int warningCount) {
            this.rules = rules;
            this.warningCount = warningCount;
        }
    }

    public static final class ReloadResult {

        private final boolean published;
        private final long oldRevision;
        private final long newRevision;
        private final int warningCount;

        private ReloadResult(boolean published, long oldRevision, long newRevision, int warningCount) {
            this.published = published;
            this.oldRevision = oldRevision;
            this.newRevision = newRevision;
            this.warningCount = warningCount;
        }

        public boolean isPublished() {
            return published;
        }

        public long getOldRevision() {
            return oldRevision;
        }

        public long getNewRevision() {
            return newRevision;
        }

        public int getWarningCount() {
            return warningCount;
        }
    }
}
