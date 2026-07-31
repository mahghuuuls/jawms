package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.internal.casting.CastingDiagnostics;
import com.mahghuuuls.jawms.internal.casting.SpellCostService;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import com.mahghuuuls.jawms.internal.modifier.ActiveStackCollector;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.UUID;

/**
 * Development-only controlled-state fixture. This source set is not packaged.
 */
public final class ManaTestFixture {

    private static final CastCancellationFixture CAST_CANCELLATION =
            new CastCancellationFixture();
    private static boolean cancellationFixtureRegistered;
    private static final ManaEventProbe MANA_EVENT_PROBE = new ManaEventProbe();
    private static boolean manaEventProbeRegistered;

    private ManaTestFixture() {
    }

    public static void register(FMLServerStartingEvent event) {
        CAST_CANCELLATION.clear();
        if (!cancellationFixtureRegistered) {
            MinecraftForge.EVENT_BUS.register(CAST_CANCELLATION);
            cancellationFixtureRegistered = true;
        }
        MANA_EVENT_PROBE.clear();
        if (!manaEventProbeRegistered) {
            MinecraftForge.EVENT_BUS.register(MANA_EVENT_PROBE);
            manaEventProbeRegistered = true;
        }
        event.registerServerCommand(new SetManaCommand());
    }

    private static final class SetManaCommand extends CommandBase {

        @Override
        public String getName() {
            return "jawms-test";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/jawms-test mana set <amount> | mana inspect | rules inspect"
                    + " | mana restore <amount> | mana consume <amount> | mana drain <amount>"
                    + " | mana events <true|false> | mana event-inspect"
                    + " | cost get <namespace:spell> | casting debug <true|false>"
                    + " | casting cancel <true|false>"
                    + " | casting cancel-tick <true|false>"
                    + " | wand set <main|off> <namespace:spell> | wand inspect <main|off>";
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
                    && "set".equalsIgnoreCase(arguments[1])) {
                setWandSpell(sender, arguments[2], arguments[3]);
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
                            + rules.getGeneratedWizardryArmorIds().size() + "."
            ));
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
            sender.sendMessage(new TextComponentString(
                    "JAWMS wand hand=" + hand
                            + ", spell=" + spell.getRegistryName()
                            + ", chargeup=" + spell.getChargeup()
                            + ", continuous=" + spell.isContinuous
                            + ", configuredCost=" + GameplayRulesManager.current().getSpellCost(
                                    spell.getRegistryName(),
                                    spell.getCost()
                            )
                            + ", finalCost=" + SpellCostService.getFinalCost(spell, modifiers)
                            + ", storedWandMana=" + wand.getMana(stack)
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

        private void clear() {
            playerId = null;
            tickPlayerId = null;
        }
    }
}
