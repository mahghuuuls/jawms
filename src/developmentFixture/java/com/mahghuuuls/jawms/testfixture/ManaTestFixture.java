package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
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
            return "/jawms-test mana set <amount from 0 through the current maximum>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
                throws CommandException {
            if (arguments.length != 3
                    || !"mana".equalsIgnoreCase(arguments[0])
                    || !"set".equalsIgnoreCase(arguments[1])) {
                throw new CommandException(getUsage(sender));
            }

            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            IManaService service = ManaApi.getManaService();
            int target = parseInt(arguments[2], 0, service.getMaximumMana(player));
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
    }
}
