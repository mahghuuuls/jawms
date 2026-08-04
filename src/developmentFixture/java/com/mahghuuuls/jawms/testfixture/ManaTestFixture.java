package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.internal.casting.CastingDiagnostics;
import com.mahghuuuls.jawms.internal.casting.SpellCostService;
import com.mahghuuuls.jawms.internal.casting.CastCostContextManager;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import com.mahghuuuls.jawms.internal.modifier.ActiveStackCollector;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryPassiveContributionAdapter;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemManaFlask;
import electroblob.wizardry.item.ItemWizardArmour;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.registry.WizardryItems;
import electroblob.wizardry.registry.WizardryPotions;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.lang.reflect.Field;

/**
 * Development-only controlled-state fixture. This source set is not packaged.
 */
public final class ManaTestFixture {

    private static final CastCancellationFixture CAST_CANCELLATION =
            new CastCancellationFixture();
    private static boolean cancellationFixtureRegistered;
    private static final ManaEventProbe MANA_EVENT_PROBE = new ManaEventProbe();
    private static boolean manaEventProbeRegistered;
    private static final Set<UUID> REGENERATION_DISABLED = new HashSet<>();
    private static final Set<UUID> BALANCE_PERCENT_BOOST = new HashSet<>();
    private static final ResourceLocation REGENERATION_TOGGLE_PROVIDER =
            new ResourceLocation("jawms_test", "regeneration_toggle");
    private static final ResourceLocation BALANCE_PERCENT_BOOST_PROVIDER =
            new ResourceLocation("jawms_test", "balance_percent_boost");
    private static boolean developmentProviderRegistered;
    private static final BalanceRecorder BALANCE_RECORDER = new BalanceRecorder();
    private static boolean balanceRecorderRegistered;

    private ManaTestFixture() {
    }

    public static void preInitialize() {
        if (developmentProviderRegistered) {
            return;
        }
        ManaApi.registerContextualProvider(
                REGENERATION_TOGGLE_PROVIDER,
                player -> REGENERATION_DISABLED.contains(player.getUniqueID())
                        ? ManaContribution.builder().regenerationReduction(100.0D).build()
                        : ManaContribution.EMPTY
        );
        ManaApi.registerContextualProvider(
                BALANCE_PERCENT_BOOST_PROVIDER,
                player -> BALANCE_PERCENT_BOOST.contains(player.getUniqueID())
                        ? ManaContribution.builder()
                        .maximumManaIncrease(50.0D)
                        .regenerationIncrease(50.0D)
                        .build()
                        : ManaContribution.EMPTY
        );
        developmentProviderRegistered = true;
    }

    public static void register(FMLServerStartingEvent event) {
        CAST_CANCELLATION.clear();
        REGENERATION_DISABLED.clear();
        BALANCE_PERCENT_BOOST.clear();
        BALANCE_RECORDER.clear();
        if (!cancellationFixtureRegistered) {
            MinecraftForge.EVENT_BUS.register(CAST_CANCELLATION);
            cancellationFixtureRegistered = true;
        }
        MANA_EVENT_PROBE.clear();
        if (!manaEventProbeRegistered) {
            MinecraftForge.EVENT_BUS.register(MANA_EVENT_PROBE);
            manaEventProbeRegistered = true;
        }
        if (!balanceRecorderRegistered) {
            MinecraftForge.EVENT_BUS.register(BALANCE_RECORDER);
            balanceRecorderRegistered = true;
        }
        event.registerServerCommand(new SetManaCommand());
        event.registerServerCommand(new BalanceCommand(BALANCE_RECORDER));
    }

    static void setBalancePercentBoost(EntityPlayerMP player, boolean enabled) {
        if (enabled) {
            BALANCE_PERCENT_BOOST.add(player.getUniqueID());
        } else {
            BALANCE_PERCENT_BOOST.remove(player.getUniqueID());
        }
        ManaApi.invalidateModifierCache(player);
    }

    static boolean isBalancePercentBoostEnabled(EntityPlayerMP player) {
        return BALANCE_PERCENT_BOOST.contains(player.getUniqueID());
    }

    static void clearPlayerState(EntityPlayerMP player) {
        UUID playerId = player.getUniqueID();
        REGENERATION_DISABLED.remove(playerId);
        BALANCE_PERCENT_BOOST.remove(playerId);
        ManaApi.invalidateModifierCache(player);
    }

    private static final class SetManaCommand extends CommandBase {

        @Override
        public String getName() {
            return "jawms-test";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/jawms-test mana set <amount> | mana inspect | rules inspect"
                    + " | integration inspect | regen pause <true|false>"
                    + " | mana restore <amount> | mana consume <amount> | mana drain <amount>"
                    + " | mana regen <true|false>"
                    + " | mana events <true|false> | mana event-inspect"
                    + " | cost get <namespace:spell> | casting debug <true|false>"
                    + " | casting cancel <true|false>"
                    + " | casting cancel-tick <true|false>"
                    + " | casting factor <positive-number>"
                    + " | casting factor-delay <positive-number> <ticks>"
                    + " | flask inspect | armor mana <head|chest|legs|feet> <amount>"
                    + " | wand set <main|off> <namespace:spell>"
                    + " | wand mana <main|off> <amount> | wand inspect <main|off>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
                throws CommandException {
            if (arguments.length == 2
                    && "rules".equalsIgnoreCase(arguments[0])
                    && "inspect".equalsIgnoreCase(arguments[1])) {
                inspectRules(sender);
                return;
            }
            if (arguments.length == 2
                    && "integration".equalsIgnoreCase(arguments[0])
                    && "inspect".equalsIgnoreCase(arguments[1])) {
                inspectIntegration(sender);
                return;
            }
            if (arguments.length == 2
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "event-inspect".equalsIgnoreCase(arguments[1])) {
                inspectManaEvent(sender);
                return;
            }
            if (arguments.length == 2
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "inspect".equalsIgnoreCase(arguments[1])) {
                inspectMana(sender);
                return;
            }
            if (arguments.length == 2
                    && "flask".equalsIgnoreCase(arguments[0])
                    && "inspect".equalsIgnoreCase(arguments[1])) {
                inspectFlask(sender);
                return;
            }
            if (arguments.length == 2
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "debug".equalsIgnoreCase(arguments[1])) {
                inspectCastingDebug(sender);
                return;
            }
            if (arguments.length == 2
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "cancel".equalsIgnoreCase(arguments[1])) {
                inspectCastingCancellation(sender);
                return;
            }
            if (arguments.length == 2
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "cancel-tick".equalsIgnoreCase(arguments[1])) {
                inspectCastingTickCancellation(sender);
                return;
            }
            if (arguments.length == 3
                    && "cost".equalsIgnoreCase(arguments[0])
                    && "get".equalsIgnoreCase(arguments[1])) {
                inspectCost(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "restore".equalsIgnoreCase(arguments[1])) {
                mutateMana(sender, arguments[2], "restore");
                return;
            }
            if (arguments.length == 3
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "consume".equalsIgnoreCase(arguments[1])) {
                mutateMana(sender, arguments[2], "consume");
                return;
            }
            if (arguments.length == 3
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "drain".equalsIgnoreCase(arguments[1])) {
                mutateMana(sender, arguments[2], "drain");
                return;
            }
            if (arguments.length == 3
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "events".equalsIgnoreCase(arguments[1])) {
                setManaEvents(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "regen".equalsIgnoreCase(arguments[1])) {
                setManaRegeneration(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "regen".equalsIgnoreCase(arguments[0])
                    && "pause".equalsIgnoreCase(arguments[1])) {
                setRegenerationPaused(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "factor".equalsIgnoreCase(arguments[1])) {
                setCastingFactor(sender, arguments[2]);
                return;
            }
            if (arguments.length == 4
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "factor-delay".equalsIgnoreCase(arguments[1])) {
                scheduleCastingFactor(sender, arguments[2], arguments[3]);
                return;
            }
            if (arguments.length == 3
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "debug".equalsIgnoreCase(arguments[1])) {
                setCastingDebug(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "cancel".equalsIgnoreCase(arguments[1])) {
                setCastingCancellation(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "casting".equalsIgnoreCase(arguments[0])
                    && "cancel-tick".equalsIgnoreCase(arguments[1])) {
                setCastingTickCancellation(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "wand".equalsIgnoreCase(arguments[0])
                    && "inspect".equalsIgnoreCase(arguments[1])) {
                inspectWand(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "set".equalsIgnoreCase(arguments[1])) {
                setMana(sender, arguments[2]);
                return;
            }
            if (arguments.length == 4
                    && "wand".equalsIgnoreCase(arguments[0])
                    && "mana".equalsIgnoreCase(arguments[1])) {
                setWandMana(sender, arguments[2], arguments[3]);
                return;
            }
            if (arguments.length == 4
                    && "wand".equalsIgnoreCase(arguments[0])
                    && "set".equalsIgnoreCase(arguments[1])) {
                setWandSpell(sender, arguments[2], arguments[3]);
                return;
            }
            if (arguments.length == 4
                    && "armor".equalsIgnoreCase(arguments[0])
                    && "mana".equalsIgnoreCase(arguments[1])) {
                setArmorMana(sender, arguments[2], arguments[3]);
                return;
            }
            throw new CommandException(getUsage(sender));
        }

        private static void setMana(ICommandSender sender, String amount) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            IManaService service = ManaApi.getManaService();
            int target = parseInt(amount, 0, service.getMaximumMana(player));
            service.setCurrentMana(player, target, ManaCauses.DEVELOPMENT_COMMAND);

            sender.sendMessage(new TextComponentString(
                    "JAWMS mana set to " + service.getCurrentMana(player)
                            + " / " + service.getMaximumMana(player) + "."
            ));
        }

        private static void inspectRules(ICommandSender sender) {
            GameplayRules rules = GameplayRulesManager.current();
            sender.sendMessage(new TextComponentString(
                    "JAWMS rules revision=" + rules.getRevision()
                            + ", baseMaximum=" + rules.getBaseMaximum()
                            + ", baseRegeneration=" + rules.getBaseRegeneration()
                            + ", lockoutTicks=" + rules.getRegenerationLockoutTicks()
                            + ", flasks=" + rules.getSmallFlask() + "/"
                            + rules.getMediumFlask() + "/" + rules.getLargeFlask()
                            + ", spellCosts=" + rules.getSpellCosts().size()
                            + ", itemRules=" + rules.getConfiguredItemRules().size()
                            + ", generatedArmorIds="
                            + rules.getGeneratedWizardryArmorIds().size()
                            + ", efficiencyScale=" + rules.getEfficiencyScale()
                            + ", armorEfficiency="
                            + rules.getArmorEfficiencyProfile().getWizard() + "/"
                            + rules.getArmorEfficiencyProfile().getSage() + "/"
                            + rules.getArmorEfficiencyProfile().getBattlemage() + "/"
                            + rules.getArmorEfficiencyProfile().getWarlock()
                            + ", sageNonmatching="
                            + rules.getArmorEfficiencyProfile().getSageNonmatchingFullSet() + "."
            ));
        }

        private static void inspectIntegration(ICommandSender sender) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            GameplayRules rules = GameplayRulesManager.current();
            WizardryIntegrationRules integration = rules.getWizardryIntegrationRules();
            int storage = WizardryPassiveContributionAdapter.aggregateHeldUpgrade(
                    player, WizardryItems.storage_upgrade, integration.getHeldWandAggregation()
            );
            int condenser = WizardryPassiveContributionAdapter.aggregateHeldUpgrade(
                    player, WizardryItems.condenser_upgrade, integration.getHeldWandAggregation()
            );
            int condensingCount = integration.isCondensingEnabled()
                    ? WizardryPassiveContributionAdapter.activeCondensingRingCount(player)
                    : 0;
            PotionEffect font = WizardryPotions.font_of_mana == null
                    ? null : player.getActivePotionEffect(WizardryPotions.font_of_mana);
            int fontLevel = font == null ? 0 : Math.max(1, font.getAmplifier() + 1);
            ManaPublicState state = ManaApi.getManaService().getState(player);
            sender.sendMessage(new TextComponentString(
                    "JAWMS integration compositeRevision=" + rules.getRevision()
                            + ", integrationRevision=" + integration.getRevision()
                            + ", heldAggregation=" + integration.getHeldWandAggregation()
                            + ", storage=" + integration.isStorageEnabled() + "@"
                            + integration.getStorageManaIncreasePerLevel() + "%"
                            + ", condenser=" + integration.isCondenserEnabled() + "@"
                            + integration.getCondenserRegenerationIncreasePerLevel() + "%"
                            + ", condensing=" + integration.isCondensingEnabled() + "@"
                            + integration.getCondensingRegenerationPerRing()
                            + ", font=" + integration.isFontEnabled() + "@"
                            + integration.getFontRegenerationPerLevel()
                            + ", fontGates=" + integration.isFontLockoutPermission() + "/"
                            + integration.isFontContinuousPermission() + "."
            ));
            sender.sendMessage(new TextComponentString(
                    "JAWMS integration inputs storageMain/Off="
                            + heldUpgradeLevel(player.getHeldItemMainhand(), WizardryItems.storage_upgrade)
                            + "/"
                            + heldUpgradeLevel(player.getHeldItemOffhand(), WizardryItems.storage_upgrade)
                            + ", condenserMain/Off="
                            + heldUpgradeLevel(player.getHeldItemMainhand(), WizardryItems.condenser_upgrade)
                            + "/"
                            + heldUpgradeLevel(player.getHeldItemOffhand(), WizardryItems.condenser_upgrade)
                            + ", storageLevel=" + storage
                            + ", condenserLevel=" + condenser
                            + ", condensingCount=" + condensingCount
                            + ", fontLevel=" + fontLevel
                            + ", resolvedMaximum=" + state.getMaximumMana()
                            + ", resolvedRegeneration=" + state.getEffectiveRegeneration()
                            + ", lockoutPermission="
                            + state.canRegenerateDuringPostCastLockout()
                            + ", continuousPermission="
                            + state.canRegenerateDuringContinuousCasting() + "."
            ));
        }

        private static int heldUpgradeLevel(ItemStack stack, net.minecraft.item.Item upgrade) {
            return stack.getItem() instanceof ItemWand
                    ? Math.max(0, WandHelper.getUpgradeLevel(stack, upgrade)) : 0;
        }

        private static void inspectMana(ICommandSender sender) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            IManaService service = ManaApi.getManaService();
            ManaPublicState state = service.getState(player);
            int activeContributions = ActiveStackCollector.collect(
                    player,
                    GameplayRulesManager.current()
            ).size();
            sender.sendMessage(new TextComponentString(
                    "JAWMS mana current=" + state.getCurrentMana()
                            + ", maximum=" + state.getMaximumMana()
                            + ", regeneration=" + state.getEffectiveRegeneration()
                            + ", effectiveLockoutTicks=" + state.getEffectiveLockoutTicks()
                            + ", remainingLockoutTicks=" + state.getRemainingLockoutTicks()
                            + ", postCastPermission="
                            + state.canRegenerateDuringPostCastLockout()
                            + ", continuousPermission="
                            + state.canRegenerateDuringContinuousCasting()
                            + ", continuousActive=" + state.isContinuousCastActive()
                            + ", regenerationEligible=" + state.isRegenerationEligible() + "."
                            + " activeContributions=" + activeContributions
                            + ", rulesRevision=" + GameplayRulesManager.current().getRevision() + "."
            ));
        }

        private static void inspectFlask(ICommandSender sender) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            IManaService service = ManaApi.getManaService();
            StringBuilder armor = new StringBuilder();
            for (int slot = 0; slot < player.inventory.armorInventory.size(); slot++) {
                ItemStack stack = player.inventory.armorInventory.get(slot);
                if (!(stack.getItem() instanceof ItemWizardArmour)
                        || !(stack.getItem() instanceof IManaStoringItem)) {
                    continue;
                }
                IManaStoringItem item = (IManaStoringItem) stack.getItem();
                if (armor.length() > 0) armor.append(", ");
                armor.append(slot).append('=')
                        .append(item.getMana(stack)).append('/')
                        .append(item.getManaCapacity(stack));
            }
            if (armor.length() == 0) armor.append("none");
            GameplayRules rules = GameplayRulesManager.current();
            sender.sendMessage(new TextComponentString(
                    "JAWMS flask player=" + service.getCurrentMana(player) + "/"
                            + service.getMaximumMana(player)
                            + ", armor[feet=0..head=3]=" + armor
                            + ", configured=" + rules.getSmallFlask() + "/"
                            + rules.getMediumFlask() + "/" + rules.getLargeFlask()
                            + ", WizardrySize=" + ItemManaFlask.Size.SMALL.capacity + "/"
                            + ItemManaFlask.Size.MEDIUM.capacity + "/"
                            + ItemManaFlask.Size.LARGE.capacity
                            + ", lastEvent={" + MANA_EVENT_PROBE.describe(player) + "}."
            ));
        }

        private static void setArmorMana(
                ICommandSender sender, String slotName, String amount
        ) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            int slot = parseArmorSlot(slotName);
            ItemStack stack = player.inventory.armorInventory.get(slot);
            if (!(stack.getItem() instanceof ItemWizardArmour)
                    || !(stack.getItem() instanceof IManaStoringItem)) {
                throw new CommandException("Equip Wizardry armor in the selected slot.");
            }
            IManaStoringItem armor = (IManaStoringItem) stack.getItem();
            armor.setMana(stack, parseInt(amount, 0, armor.getManaCapacity(stack)));
            inspectFlask(sender);
        }

        private static int parseArmorSlot(String value) throws CommandException {
            if ("feet".equalsIgnoreCase(value)) return 0;
            if ("legs".equalsIgnoreCase(value)) return 1;
            if ("chest".equalsIgnoreCase(value)) return 2;
            if ("head".equalsIgnoreCase(value)) return 3;
            throw new CommandException("Expected head, chest, legs, or feet.");
        }

        private static void mutateMana(
                ICommandSender sender,
                String value,
                String operation
        ) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            int amount = parseInt(value, 0);
            IManaService service = ManaApi.getManaService();
            ManaMutationResult result;
            if ("restore".equals(operation)) {
                result = service.restoreMana(player, amount, ManaCauses.DEVELOPMENT_COMMAND);
            } else if ("consume".equals(operation)) {
                result = service.consumeMana(player, amount, ManaCauses.DEVELOPMENT_COMMAND);
            } else {
                result = service.drainMana(player, amount, ManaCauses.DEVELOPMENT_COMMAND);
            }
            sender.sendMessage(new TextComponentString(
                    "JAWMS mana mutation operation=" + operation
                            + ", successful=" + result.isSuccessful()
                            + ", requested=" + result.getRequestedAmount()
                            + ", actualDelta=" + result.getActualDelta()
                            + ", current=" + result.getNewState().getCurrentMana()
                            + ", cause=" + result.getCause() + "."
            ));
        }

        private static void setManaEvents(ICommandSender sender, String value)
                throws CommandException {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new CommandException("Expected true or false.");
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            MANA_EVENT_PROBE.setEnabled(player, Boolean.parseBoolean(value));
            sender.sendMessage(new TextComponentString(
                    "JAWMS mana event probe enabled=" + MANA_EVENT_PROBE.appliesTo(player) + "."
            ));
        }

        private static void setManaRegeneration(ICommandSender sender, String value)
                throws CommandException {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new CommandException("Expected true or false.");
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            boolean enabled = Boolean.parseBoolean(value);
            if (enabled) {
                REGENERATION_DISABLED.remove(player.getUniqueID());
            } else {
                REGENERATION_DISABLED.add(player.getUniqueID());
            }
            ManaApi.invalidateModifierCache(player);
            sender.sendMessage(new TextComponentString(
                    "JAWMS test mana regeneration enabled=" + enabled + "."
            ));
        }

        private static void setRegenerationPaused(ICommandSender sender, String value)
                throws CommandException {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new CommandException("Expected true or false.");
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            boolean paused = Boolean.parseBoolean(value);
            if (paused) {
                REGENERATION_DISABLED.add(player.getUniqueID());
            } else {
                REGENERATION_DISABLED.remove(player.getUniqueID());
            }
            ManaApi.invalidateModifierCache(player);
            sender.sendMessage(new TextComponentString(
                    "JAWMS test mana regeneration paused=" + paused + "."
            ));
        }

        private static void inspectManaEvent(ICommandSender sender) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            sender.sendMessage(new TextComponentString(MANA_EVENT_PROBE.describe(player)));
        }

        private static void inspectCastingDebug(ICommandSender sender) {
            sender.sendMessage(new TextComponentString(
                    "JAWMS casting diagnostics enabled=" + CastingDiagnostics.isEnabled() + "."
            ));
        }

        private static void setCastingDebug(ICommandSender sender, String value)
                throws CommandException {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new CommandException("Expected true or false.");
            }
            CastingDiagnostics.setDevelopmentEnabled(Boolean.parseBoolean(value));
            inspectCastingDebug(sender);
        }

        private static void inspectCastingCancellation(ICommandSender sender)
                throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            sender.sendMessage(new TextComponentString(
                    "JAWMS deterministic wand pre-cancellation enabled="
                            + CAST_CANCELLATION.appliesTo(player) + "."
            ));
        }

        private static void setCastingCancellation(ICommandSender sender, String value)
                throws CommandException {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new CommandException("Expected true or false.");
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (Boolean.parseBoolean(value)) {
                CAST_CANCELLATION.enableFor(player);
            } else {
                CAST_CANCELLATION.clearFor(player);
            }
            inspectCastingCancellation(sender);
        }

        private static void inspectCastingTickCancellation(ICommandSender sender)
                throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            sender.sendMessage(new TextComponentString(
                    "JAWMS deterministic wand tick-cancellation enabled="
                            + CAST_CANCELLATION.tickAppliesTo(player) + "."
            ));
        }

        private static void setCastingTickCancellation(
                ICommandSender sender,
                String value
        ) throws CommandException {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new CommandException("Expected true or false.");
            }
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (Boolean.parseBoolean(value)) {
                CAST_CANCELLATION.enableTickFor(player);
            } else {
                CAST_CANCELLATION.clearTickFor(player);
            }
            inspectCastingTickCancellation(sender);
        }

        private static void inspectCost(ICommandSender sender, String identifier) throws CommandException {
            ResourceLocation id = parseIdentifier(identifier);
            Spell spell = getSpell(id);
            if (spell == null) {
                throw new CommandException("Unknown spell: " + id);
            }
            GameplayRules rules = GameplayRulesManager.current();
            sender.sendMessage(new TextComponentString(
                    "JAWMS cost " + id
                            + ": original=" + spell.getCost()
                            + ", tier=" + (spell.getTier().ordinal() + 1)
                            + ", configured=" + rules.getSpellCost(id, spell.getCost())
                            + ", rulesRevision=" + rules.getRevision() + "."
            ));
        }

        private static void setCastingFactor(ICommandSender sender, String value)
                throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            float factor = parseCastingFactor(value);
            CAST_CANCELLATION.setFactorFor(player, factor);
            sender.sendMessage(new TextComponentString(
                    "JAWMS test contextual cost factor=" + factor + "."
            ));
        }

        private static void scheduleCastingFactor(
                ICommandSender sender, String value, String ticksValue
        ) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            float factor = parseCastingFactor(value);
            int ticks = parseInt(ticksValue, 1, 1200);
            CAST_CANCELLATION.scheduleFactorFor(player, factor, ticks);
            sender.sendMessage(new TextComponentString(
                    "JAWMS test contextual cost factor=" + factor
                            + " scheduled in " + ticks + " ticks."
            ));
        }

        private static float parseCastingFactor(String value) throws CommandException {
            final float factor;
            try {
                factor = Float.parseFloat(value);
            } catch (RuntimeException exception) {
                throw new CommandException("Expected a finite positive casting factor.");
            }
            if (!Float.isFinite(factor) || factor <= 0.0F) {
                throw new CommandException("Expected a finite positive casting factor.");
            }
            return factor;
        }

        private static void setWandSpell(
                ICommandSender sender,
                String handName,
                String identifier
        ) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            EnumHand hand = parseHand(handName);
            ItemStack stack = player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemWand)) {
                throw new CommandException("Hold a Wizardry wand in the selected hand.");
            }
            ResourceLocation id = parseIdentifier(identifier);
            Spell spell = getSpell(id);
            Spell[] spells = new Spell[ItemWand.BASE_SPELL_SLOTS];
            spells[0] = spell;
            WandHelper.setSpells(stack, spells);
            WandHelper.selectSpell(stack, 0);
            inspectWand(sender, handName);
        }

        private static void setWandMana(
                ICommandSender sender, String handName, String amount
        ) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            EnumHand hand = parseHand(handName);
            ItemStack stack = player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemWand)) {
                throw new CommandException("Hold a Wizardry wand in the selected hand.");
            }
            ItemWand wand = (ItemWand) stack.getItem();
            int target = parseInt(amount, 0, wand.getManaCapacity(stack));
            setRawWandDamage(stack, wand.getManaCapacity(stack) - target);
            sender.sendMessage(new TextComponentString(
                    "JAWMS test raw wand mana="
                            + (wand.getManaCapacity(stack) - stack.getItemDamage())
                            + " / " + wand.getManaCapacity(stack)
                            + ", reported=" + wand.getMana(stack) + "."
            ));
        }

        private static void setRawWandDamage(ItemStack stack, int damage)
                throws CommandException {
            try {
                Field field = ItemStack.class.getDeclaredField("itemDamage");
                field.setAccessible(true);
                field.setInt(stack, damage);
            } catch (ReflectiveOperationException exception) {
                throw new CommandException("Could not set development-only raw wand damage.");
            }
        }

        private static void inspectWand(ICommandSender sender, String handName)
                throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            EnumHand hand = parseHand(handName);
            ItemStack stack = player.getHeldItem(hand);
            if (!(stack.getItem() instanceof ItemWand)) {
                throw new CommandException("Hold a Wizardry wand in the selected hand.");
            }
            ItemWand wand = (ItemWand) stack.getItem();
            Spell spell = WandHelper.getCurrentSpell(stack);
            SpellModifiers modifiers = wand.calculateModifiers(stack, player, spell);
            double efficiency = ManaApi.getManaService()
                    .getApplicableSpellEfficiency(player, spell);
            double profileMultiplier = ManaApi.getManaService()
                    .getApplicableSpellCostMultiplier(player, spell);
            sender.sendMessage(new TextComponentString(
                    "JAWMS wand hand=" + hand
                            + ", spell=" + spell.getRegistryName()
                            + ", chargeup=" + spell.getChargeup()
                            + ", continuous=" + spell.isContinuous
                            + ", configuredCost=" + GameplayRulesManager.current().getSpellCost(
                                    spell.getRegistryName(),
                                    spell.getCost()
                            )
                            + ", contextualFactor=" + modifiers.get(SpellModifiers.COST)
                            + ", profileMultiplier=" + profileMultiplier
                            + ", efficiencyPoints=" + efficiency
                            + ", efficiencyScale=" + GameplayRulesManager.current().getEfficiencyScale()
                            + ", finalCost=" + SpellCostService.getFinalCost(player, spell, modifiers)
                            + ", reportedWandMana=" + wand.getMana(stack)
                            + ", rawWandDamage=" + stack.getItemDamage()
                            + ", rawWandMana="
                            + (wand.getManaCapacity(stack) - stack.getItemDamage())
                            + ", currentCooldown=" + WandHelper.getCurrentCooldown(stack)
                            + ", maximumCooldown=" + WandHelper.getCurrentMaxCooldown(stack)
                            + ", storage=" + WandHelper.getUpgradeLevel(
                                    stack, electroblob.wizardry.registry.WizardryItems.storage_upgrade)
                            + ", condenser=" + WandHelper.getUpgradeLevel(
                                    stack, electroblob.wizardry.registry.WizardryItems.condenser_upgrade)
                            + ", siphon=" + WandHelper.getUpgradeLevel(
                                    stack, electroblob.wizardry.registry.WizardryItems.siphon_upgrade)
                            + ", melee=" + WandHelper.getUpgradeLevel(
                                    stack, electroblob.wizardry.registry.WizardryItems.melee_upgrade)
                            + ", food=" + player.getFoodStats().getFoodLevel()
                            + ", captured=" + CastCostContextManager.describe(player, hand)
                            + "."
            ));
        }

        private static EnumHand parseHand(String value) throws CommandException {
            if ("main".equalsIgnoreCase(value)) {
                return EnumHand.MAIN_HAND;
            }
            if ("off".equalsIgnoreCase(value)) {
                return EnumHand.OFF_HAND;
            }
            throw new CommandException("Expected main or off.");
        }

        private static ResourceLocation parseIdentifier(String identifier) throws CommandException {
            try {
                if (identifier.indexOf(':') <= 0) {
                    throw new IllegalArgumentException("A full namespace:path identifier is required.");
                }
                return new ResourceLocation(identifier);
            } catch (RuntimeException exception) {
                throw new CommandException("Invalid spell identifier: " + identifier);
            }
        }

        private static Spell getSpell(ResourceLocation id) throws CommandException {
            Spell spell = Spell.registry.getValue(id);
            if (spell == null) {
                throw new CommandException("Unknown spell: " + id);
            }
            return spell;
        }
    }

    private static final class ManaEventProbe {

        private UUID playerId;
        private int count;
        private ManaStateChangeEvent lastEvent;

        @SubscribeEvent
        public void observe(ManaStateChangeEvent event) {
            if (!(event.getPlayer() instanceof EntityPlayerMP)
                    || !appliesTo((EntityPlayerMP) event.getPlayer())) {
                return;
            }
            count++;
            lastEvent = event;
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS mana event probe: count={}, player={}, cause={}, delta={}, old={}, new={}.",
                    count,
                    playerId,
                    event.getCause(),
                    event.getActualManaDelta(),
                    event.getOldState(),
                    event.getNewState()
            );
        }

        private void setEnabled(EntityPlayerMP player, boolean enabled) {
            if (enabled) {
                playerId = player.getUniqueID();
                count = 0;
                lastEvent = null;
            } else if (appliesTo(player)) {
                clear();
            }
        }

        private boolean appliesTo(EntityPlayerMP player) {
            return playerId != null && playerId.equals(player.getUniqueID());
        }

        private String describe(EntityPlayerMP player) {
            if (!appliesTo(player)) {
                return "JAWMS mana event probe enabled=false.";
            }
            if (lastEvent == null) {
                return "JAWMS mana event probe enabled=true, count=0.";
            }
            return "JAWMS mana event probe enabled=true, count=" + count
                    + ", lastCause=" + lastEvent.getCause()
                    + ", lastDelta=" + lastEvent.getActualManaDelta()
                    + ", lastCurrent=" + lastEvent.getNewState().getCurrentMana() + ".";
        }

        private void clear() {
            playerId = null;
            count = 0;
            lastEvent = null;
        }
    }

    private static final class CastCancellationFixture {

        private UUID playerId;
        private UUID tickPlayerId;
        private UUID factorPlayerId;
        private float factor = 1.0F;
        private UUID scheduledFactorPlayerId;
        private float scheduledFactor = 1.0F;
        private int scheduledFactorTicks;

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void applyContextualCostFactor(SpellCastEvent.Pre event) {
            if (event.getSource() != SpellCastEvent.Source.WAND
                    || !(event.getCaster() instanceof EntityPlayerMP)
                    || factorPlayerId == null
                    || !factorPlayerId.equals(event.getCaster().getUniqueID())) {
                return;
            }
            event.getModifiers().set(
                    SpellModifiers.COST,
                    event.getModifiers().get(SpellModifiers.COST) * factor,
                    false
            );
        }

        @SubscribeEvent
        public void applyScheduledFactor(TickEvent.PlayerTickEvent event) {
            if (event.phase != TickEvent.Phase.END
                    || !(event.player instanceof EntityPlayerMP)
                    || scheduledFactorPlayerId == null
                    || !scheduledFactorPlayerId.equals(event.player.getUniqueID())) {
                return;
            }
            if (--scheduledFactorTicks > 0) {
                return;
            }
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            setFactorFor(player, scheduledFactor);
            scheduledFactorPlayerId = null;
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS test contextual factor changed during runtime: player={}, factor={}, mainCaptured={}, offCaptured={}.",
                    player.getUniqueID(),
                    factor,
                    CastCostContextManager.describe(player, EnumHand.MAIN_HAND),
                    CastCostContextManager.describe(player, EnumHand.OFF_HAND)
            );
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void cancelWandPreCast(SpellCastEvent.Pre event) {
            if (event.getSource() != SpellCastEvent.Source.WAND
                    || !(event.getCaster() instanceof EntityPlayerMP)
                    || !appliesTo((EntityPlayerMP) event.getCaster())) {
                return;
            }
            event.setCanceled(true);
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS test cast event: event=pre_cancelled, player={}, spell={}.",
                    playerId,
                    event.getSpell().getRegistryName()
            );
        }

        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void cancelWandTick(SpellCastEvent.Tick event) {
            if (event.getSource() != SpellCastEvent.Source.WAND
                    || !(event.getCaster() instanceof EntityPlayerMP)
                    || !tickAppliesTo((EntityPlayerMP) event.getCaster())) {
                return;
            }
            event.setCanceled(true);
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS test cast event: event=tick_cancelled, player={}, spell={}, tick={}.",
                    tickPlayerId,
                    event.getSpell().getRegistryName(),
                    event.getCount()
            );
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public void observeWandFinish(SpellCastEvent.Finish event) {
            if (event.getSource() != SpellCastEvent.Source.WAND
                    || !(event.getCaster() instanceof EntityPlayerMP)
                    || !tickAppliesTo((EntityPlayerMP) event.getCaster())) {
                return;
            }
            JustAnotherWizardryManaSystemMod.LOGGER.info(
                    "JAWMS test cast event: event=finish_observed, player={}, spell={}, tick={}.",
                    tickPlayerId,
                    event.getSpell().getRegistryName(),
                    event.getCount()
            );
        }

        private void enableFor(EntityPlayerMP player) {
            playerId = player.getUniqueID();
        }

        private void clearFor(EntityPlayerMP player) {
            if (appliesTo(player)) {
                clear();
            }
        }

        private void enableTickFor(EntityPlayerMP player) {
            tickPlayerId = player.getUniqueID();
        }

        private void clearTickFor(EntityPlayerMP player) {
            if (tickAppliesTo(player)) {
                tickPlayerId = null;
            }
        }

        private boolean tickAppliesTo(EntityPlayerMP player) {
            return tickPlayerId != null && tickPlayerId.equals(player.getUniqueID());
        }

        private boolean appliesTo(EntityPlayerMP player) {
            return playerId != null && playerId.equals(player.getUniqueID());
        }

        private void setFactorFor(EntityPlayerMP player, float value) {
            factorPlayerId = player.getUniqueID();
            factor = value;
        }

        private void scheduleFactorFor(EntityPlayerMP player, float value, int ticks) {
            scheduledFactorPlayerId = player.getUniqueID();
            scheduledFactor = value;
            scheduledFactorTicks = ticks;
        }

        private void clear() {
            playerId = null;
            tickPlayerId = null;
            factorPlayerId = null;
            factor = 1.0F;
            scheduledFactorPlayerId = null;
            scheduledFactor = 1.0F;
            scheduledFactorTicks = 0;
        }
    }
}
