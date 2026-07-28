package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import electroblob.wizardry.spell.Spell;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

/**
 * Development-only controlled-state fixture. This source set is not packaged.
 */
public final class ManaTestFixture {

    private ManaTestFixture() {
    }

    public static void register(FMLServerStartingEvent event) {
        event.registerServerCommand(new SetManaCommand());
    }

    private static final class SetManaCommand extends CommandBase {

        @Override
        public String getName() {
            return "jawms-test";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/jawms-test mana set <amount> | rules inspect | cost get <namespace:spell>";
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
            if (arguments.length == 3
                    && "cost".equalsIgnoreCase(arguments[0])
                    && "get".equalsIgnoreCase(arguments[1])) {
                inspectCost(sender, arguments[2]);
                return;
            }
            if (arguments.length == 3
                    && "mana".equalsIgnoreCase(arguments[0])
                    && "set".equalsIgnoreCase(arguments[1])) {
                setMana(sender, arguments[2]);
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

        private static void inspectCost(ICommandSender sender, String identifier) throws CommandException {
            final ResourceLocation id;
            try {
                if (identifier.indexOf(':') <= 0) {
                    throw new IllegalArgumentException("A full namespace:path identifier is required.");
                }
                id = new ResourceLocation(identifier);
            } catch (RuntimeException exception) {
                throw new CommandException("Invalid spell identifier: " + identifier);
            }
            Spell spell = Spell.registry.getValue(id);
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
    }
}
