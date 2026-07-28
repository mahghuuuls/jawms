package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.internal.casting.CastingDiagnostics;
import com.mahghuuuls.jawms.internal.casting.SpellCostService;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
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

    private ManaTestFixture() {
    }

    public static void register(FMLServerStartingEvent event) {
        CAST_CANCELLATION.clear();
        if (!cancellationFixtureRegistered) {
            MinecraftForge.EVENT_BUS.register(CAST_CANCELLATION);
            cancellationFixtureRegistered = true;
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
                    + " | cost get <namespace:spell> | casting debug <true|false>"
                    + " | casting cancel <true|false>"
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
            if (arguments.length == 3
                    && "cost".equalsIgnoreCase(arguments[0])
                    && "get".equalsIgnoreCase(arguments[1])) {
                inspectCost(sender, arguments[2]);
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
            int current = service.getCurrentMana(player);
            if (target < current) {
                service.tryConsumeMana(player, current - target);
            } else {
                service.restoreMana(player, target - current);
            }

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
                            + ", itemRules=" + rules.getConfiguredItemRules().size() + "."
            ));
        }

        private static void inspectMana(ICommandSender sender) throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            ManaState state = ManaService.requireState(player);
            sender.sendMessage(new TextComponentString(
                    "JAWMS mana current=" + state.getCurrentMana()
                            + ", maximum=" + state.getMaximumMana()
                            + ", available=" + state.getAvailableMana()
                            + ", regeneration=" + state.getEffectiveRegeneration()
                            + ", lockoutTicks=" + state.getRemainingLockoutTicks()
                            + ", revision=" + state.getRevision() + "."
            ));
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

    private static final class CastCancellationFixture {

        private UUID playerId;

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

        private void enableFor(EntityPlayerMP player) {
            playerId = player.getUniqueID();
        }

        private void clearFor(EntityPlayerMP player) {
            if (appliesTo(player)) {
                clear();
            }
        }

        private boolean appliesTo(EntityPlayerMP player) {
            return playerId != null && playerId.equals(player.getUniqueID());
        }

        private void clear() {
            playerId = null;
        }
    }
}
