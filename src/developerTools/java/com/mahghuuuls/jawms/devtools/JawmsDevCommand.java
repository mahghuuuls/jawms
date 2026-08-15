package com.mahghuuuls.jawms.devtools;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionExplanation;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaProfileAggregation;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaProviderDescriptor;
import com.mahghuuuls.jawms.api.ManaPublicState;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Permission-gated command coordinator backed exclusively by the public JAWMS API. */
public final class JawmsDevCommand extends CommandBase {

    static final ResourceLocation TOOL_CAUSE =
            new ResourceLocation(JawmsDeveloperToolsMod.MOD_ID, "command");
    private static final Logger LOGGER = LogManager.getLogger("JAWMS Developer Tools");
    private static final String PREFIX = "[jawms-dev] ";

    private final PublicApi access;

    public JawmsDevCommand() {
        this(new LivePublicApi());
    }

    JawmsDevCommand(PublicApi access) {
        if (access == null) {
            throw new NullPointerException("access");
        }
        this.access = access;
    }

    @Override
    public String getName() {
        return "jawms-dev";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jawms-dev state [player]"
                + " | mana <set|restore|consume|drain> <player> <amount>"
                + " | providers | explain [player]"
                + " | cache <invalidate|dirty> <player>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
            throws CommandException {
        try {
            executeAuthorized(server, sender, arguments);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            throw new CommandException(
                    "JAWMS Developer Tools rejected the operation: "
                            + failure.getMessage()
            );
        }
    }

    private void executeAuthorized(
            MinecraftServer server, ICommandSender sender, String[] arguments
    ) throws CommandException {
        if (arguments.length >= 1 && arguments.length <= 2
                && "state".equalsIgnoreCase(arguments[0])) {
            EntityPlayerMP player = resolveOptionalPlayer(
                    server, sender, arguments.length == 2 ? arguments[1] : null
            );
            reply(sender, formatState(player.getName(), access.service().getState(player)));
            return;
        }
        if (arguments.length == 1 && "providers".equalsIgnoreCase(arguments[0])) {
            List<ManaProviderDescriptor> providers = access.providers();
            reply(sender, PREFIX + "providers frozen=" + access.providersFrozen()
                    + " count=" + providers.size());
            for (int index = 0; index < providers.size(); index++) {
                reply(sender, formatProvider(index, providers.get(index)));
            }
            return;
        }
        if (arguments.length >= 1 && arguments.length <= 2
                && "explain".equalsIgnoreCase(arguments[0])) {
            EntityPlayerMP player = resolveOptionalPlayer(
                    server, sender, arguments.length == 2 ? arguments[1] : null
            );
            for (String line : formatExplanation(
                    player.getName(), access.explain(player)
            )) {
                reply(sender, line);
            }
            return;
        }
        if (arguments.length == 4 && "mana".equalsIgnoreCase(arguments[0])) {
            String operation = arguments[1].toLowerCase(Locale.ROOT);
            EntityPlayerMP player = access.resolvePlayer(server, sender, arguments[2]);
            int amount = parseInt(arguments[3], 0);
            ManaMutationResult result = mutate(
                    access.service(), operation, player, amount
            );
            reply(sender, formatMutation(operation, player.getName(), result));
            return;
        }
        if (arguments.length == 3 && "cache".equalsIgnoreCase(arguments[0])) {
            String operation = arguments[1].toLowerCase(Locale.ROOT);
            EntityPlayerMP player = access.resolvePlayer(server, sender, arguments[2]);
            if ("invalidate".equals(operation)) {
                access.invalidate(player);
                reply(sender, PREFIX + "cache operation=invalidate player="
                        + player.getName() + " timing=next-profile-evaluation-fresh");
                return;
            }
            if ("dirty".equals(operation)) {
                access.dirty(player);
                reply(sender, PREFIX + "cache operation=dirty player="
                        + player.getName() + " timing=deferred-to-player-END-tick");
                return;
            }
            throw new CommandException(getUsage(sender));
        }
        throw new CommandException(getUsage(sender));
    }

    private EntityPlayerMP resolveOptionalPlayer(
            MinecraftServer server, ICommandSender sender, String target
    ) throws CommandException {
        return target == null
                ? access.resolveSenderPlayer(sender)
                : access.resolvePlayer(server, sender, target);
    }

    static ManaMutationResult mutate(
            IManaService service,
            String operation,
            EntityPlayerMP player,
            int amount
    ) throws CommandException {
        if ("set".equals(operation)) {
            return service.setCurrentMana(player, amount, TOOL_CAUSE);
        }
        if ("restore".equals(operation)) {
            return service.restoreMana(player, amount, TOOL_CAUSE);
        }
        if ("consume".equals(operation)) {
            return service.consumeMana(player, amount, TOOL_CAUSE);
        }
        if ("drain".equals(operation)) {
            return service.drainMana(player, amount, TOOL_CAUSE);
        }
        throw new CommandException("Unknown mana operation: " + operation);
    }

    static String formatState(String playerName, ManaPublicState state) {
        return PREFIX + "state player=" + playerName
                + " current=" + state.getCurrentMana()
                + " maximum=" + state.getMaximumMana()
                + " regeneration=" + state.getEffectiveRegeneration()
                + " effectiveLockoutTicks=" + state.getEffectiveLockoutTicks()
                + " effectiveLockoutSeconds=" + state.getEffectiveLockoutSeconds()
                + " remainingLockoutTicks=" + state.getRemainingLockoutTicks()
                + " remainingLockoutSeconds=" + state.getRemainingLockoutSeconds()
                + " postCastPermission=" + state.canRegenerateDuringPostCastLockout()
                + " continuousPermission=" + state.canRegenerateDuringContinuousCasting()
                + " continuousActive=" + state.isContinuousCastActive()
                + " regenerationEligible=" + state.isRegenerationEligible();
    }

    static String formatMutation(
            String operation, String playerName, ManaMutationResult result
    ) {
        return PREFIX + "mana operation=" + operation
                + " player=" + playerName
                + " success=" + result.isSuccessful()
                + " failure=" + result.getFailure()
                + " requested=" + result.getRequestedAmount()
                + " actualDelta=" + result.getActualDelta()
                + " cause=" + result.getCause()
                + " old=" + statePair(result.getOldState())
                + " new=" + statePair(result.getNewState());
    }

    static String formatProvider(int index, ManaProviderDescriptor provider) {
        return PREFIX + "provider index=" + index
                + " id=" + provider.getProviderId()
                + " kind=" + provider.getKind()
                + " target=" + (provider.hasKnownTargetItemId()
                ? provider.getTargetItemId() : "none");
    }

    static List<String> formatExplanation(
            String playerName, ManaProfileExplanation explanation
    ) {
        List<String> lines = new ArrayList<>();
        lines.add(PREFIX + "explain player=" + playerName
                + " tick=" + explanation.getEvaluatedWorldTick()
                + " rulesRevision=" + explanation.getGameplayRulesRevision()
                + " baseMaximum=" + explanation.getConfiguredBaseMaximumMana()
                + " baseRegeneration=" + explanation.getConfiguredBaseRegeneration()
                + " baseLockoutSeconds=" + explanation.getConfiguredBaseLockoutSeconds());
        lines.add(PREFIX + "explain final maximum=" + explanation.getFinalMaximumMana()
                + " regeneration=" + explanation.getFinalRegeneration()
                + " lockoutSeconds=" + explanation.getFinalLockoutSeconds()
                + " lockoutTicks=" + explanation.getFinalLockoutTicks()
                + " postCastPermission="
                + explanation.canRegenerateDuringPostCastLockout()
                + " continuousPermission="
                + explanation.canRegenerateDuringContinuousCasting());
        lines.add(formatAggregation("maximum", explanation.getMaximumManaAggregation()));
        lines.add(formatAggregation("regeneration", explanation.getRegenerationAggregation()));
        lines.add(formatAggregation("lockout", explanation.getLockoutAggregation()));
        lines.add(PREFIX + "explain spell globalEfficiency="
                + explanation.getGlobalSpellEfficiency()
                + " elementEfficiency=" + formatElementMap(
                explanation.getElementSpellEfficiency())
                + " globalCostMultiplier="
                + explanation.getGlobalSpellCostMultiplier()
                + " elementCostMultipliers=" + formatElementMap(
                explanation.getElementSpellCostMultipliers()));
        List<ManaContributionExplanation> sources = explanation.getContributions();
        lines.add(PREFIX + "explain sources=" + sources.size());
        for (int index = 0; index < sources.size(); index++) {
            lines.add(formatSource(index, sources.get(index)));
        }
        return lines;
    }

    private static String formatAggregation(String name, ManaProfileAggregation value) {
        return PREFIX + "explain aggregation=" + name
                + " base=" + value.getConfiguredBase()
                + " flat=" + value.getFlatTotal()
                + " subtotal=" + value.getNonNegativeSubtotal()
                + " increase=" + value.getIncreasePercentage()
                + " reductionFactor=" + value.getReductionFactor()
                + " resolved=" + value.getResolvedValue();
    }

    private static String formatSource(int index, ManaContributionExplanation source) {
        StringBuilder line = new StringBuilder(PREFIX)
                .append("source index=").append(index)
                .append(" id=").append(source.getSourceId())
                .append(" kind=").append(source.getSourceKind())
                .append(" disposition=").append(source.getDisposition())
                .append(" failure=").append(source.getFailure())
                .append(" item=").append(source.hasItemId() ? source.getItemId() : "none")
                .append(" slot=").append(source.hasSlot()
                ? source.getSlotCategory() + ":" + source.getSlotIndex() : "none");
        if (source.hasAcceptedContribution()) {
            line.append(' ').append(formatContribution(source.getAcceptedContribution()));
        }
        return line.toString();
    }

    private static String formatContribution(ManaContribution value) {
        return "contribution={flatMaximum=" + value.getFlatMaximumMana()
                + ",maximumIncrease=" + value.getMaximumManaIncrease()
                + ",maximumReduction=" + value.getMaximumManaReduction()
                + ",flatRegeneration=" + value.getFlatRegeneration()
                + ",regenerationIncrease=" + value.getRegenerationIncrease()
                + ",regenerationReduction=" + value.getRegenerationReduction()
                + ",flatLockout=" + value.getFlatLockoutSeconds()
                + ",lockoutIncrease=" + value.getLockoutIncrease()
                + ",lockoutReduction=" + value.getLockoutReduction()
                + ",globalEfficiency=" + value.getGlobalSpellEfficiency()
                + ",elementEfficiency=" + formatElementMap(
                value.getElementSpellEfficiency())
                + ",globalCostMultiplier=" + value.getGlobalSpellCostMultiplier()
                + ",elementCostMultipliers=" + formatElementMap(
                value.getElementSpellCostMultipliers())
                + ",postCastPermission="
                + value.canRegenerateDuringPostCastLockout()
                + ",continuousPermission="
                + value.canRegenerateDuringContinuousCasting() + '}';
    }

    private static String formatElementMap(Map<?, Double> values) {
        List<Map.Entry<?, Double>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> stableKey(entry.getKey())));
        StringBuilder result = new StringBuilder("{");
        for (int index = 0; index < entries.size(); index++) {
            if (index > 0) {
                result.append(',');
            }
            Map.Entry<?, Double> entry = entries.get(index);
            result.append(stableKey(entry.getKey())).append('=').append(entry.getValue());
        }
        return result.append('}').toString();
    }

    private static String stableKey(Object key) {
        return key instanceof Enum<?>
                ? ((Enum<?>) key).name()
                : String.valueOf(key);
    }

    private static String statePair(ManaPublicState state) {
        return state.getCurrentMana() + "/" + state.getMaximumMana();
    }

    private static void reply(ICommandSender sender, String message) {
        LOGGER.info(message);
        sender.sendMessage(new TextComponentString(message));
    }

    interface PublicApi {
        IManaService service();

        EntityPlayerMP resolveSenderPlayer(ICommandSender sender) throws CommandException;

        EntityPlayerMP resolvePlayer(
                MinecraftServer server, ICommandSender sender, String target
        ) throws CommandException;

        List<ManaProviderDescriptor> providers();

        boolean providersFrozen();

        ManaProfileExplanation explain(EntityPlayerMP player);

        void invalidate(EntityPlayerMP player);

        void dirty(EntityPlayerMP player);
    }

    private static final class LivePublicApi implements PublicApi {

        @Override
        public IManaService service() {
            return ManaApi.getManaService();
        }

        @Override
        public EntityPlayerMP resolveSenderPlayer(ICommandSender sender)
                throws CommandException {
            return getCommandSenderAsPlayer(sender);
        }

        @Override
        public EntityPlayerMP resolvePlayer(
                MinecraftServer server, ICommandSender sender, String target
        ) throws CommandException {
            return getPlayer(server, sender, target);
        }

        @Override
        public List<ManaProviderDescriptor> providers() {
            return ManaApi.getRegisteredProviders();
        }

        @Override
        public boolean providersFrozen() {
            return ManaApi.isProviderRegistrationFrozen();
        }

        @Override
        public ManaProfileExplanation explain(EntityPlayerMP player) {
            return ManaApi.explainManaProfile(player);
        }

        @Override
        public void invalidate(EntityPlayerMP player) {
            ManaApi.invalidateModifierCache(player);
        }

        @Override
        public void dirty(EntityPlayerMP player) {
            ManaApi.markModifierCacheDirty(player);
        }
    }
}
