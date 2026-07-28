package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.mana.ManaNetwork;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class GameplayRulesManager {

    private static final AtomicLong NEXT_REVISION = new AtomicLong(1L);
    private static volatile GameplayRules current = GameplayRules.initialDefaults();
    private static File configurationFile;

    private GameplayRulesManager() {
    }

    public static synchronized void configure(File file) {
        if (file == null) {
            throw new NullPointerException("file");
        }
        configurationFile = file;
    }

    public static synchronized void initialize() {
        if (configurationFile == null) {
            throw new IllegalStateException("Gameplay rules configuration file was not configured.");
        }
        BuildResult result = build(current);
        current = result.rules;
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Published initial JAWMS gameplay rules revision {}: baseMaximum={}, baseRegeneration={}, "
                        + "lockoutTicks={}, spellCosts={}, itemRules={}, warnings={}.",
                result.rules.getRevision(),
                result.rules.getBaseMaximum(),
                result.rules.getBaseRegeneration(),
                result.rules.getRegenerationLockoutTicks(),
                result.rules.getSpellCosts().size(),
                result.rules.getConfiguredItemRules().size(),
                result.warningCount
        );
    }

    public static GameplayRules current() {
        return current;
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
        for (EntityPlayerMP player : server.getPlayerList().getPlayers()) {
            ManaState state = ManaService.requireState(player);
            state.clampLockoutAfterReload(result.rules.getRegenerationLockoutTicks());
            state.applyEffectiveStats(
                    result.rules.getBaseMaximum(),
                    result.rules.getBaseRegeneration()
            );
            ManaNetwork.synchronizeRules(player, result.rules);
            ManaNetwork.synchronize(player, state);
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
        GameplayRules rules = GameplayRulesLoader.load(
                configurationFile,
                previous,
                NEXT_REVISION.getAndIncrement(),
                collectSpells(),
                warnings::add
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
