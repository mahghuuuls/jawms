package com.mahghuuuls.jawms.internal.rules;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class JawmsCommand extends CommandBase {

    @Override
    public String getName() {
        return "jawms";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jawms reload";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 4;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
            throws CommandException {
        if (arguments.length != 1 || !"reload".equalsIgnoreCase(arguments[0])) {
            throw new CommandException(getUsage(sender));
        }
        GameplayRulesManager.ReloadResult result = GameplayRulesManager.reload(server);
        if (!result.isPublished()) {
            throw new CommandException(
                    "JAWMS reload failed; rules revision "
                            + result.getOldRevision() + " remains active. See the server log."
            );
        }
        sender.sendMessage(new TextComponentString(
                "JAWMS rules reloaded: revision " + result.getOldRevision()
                        + " -> " + result.getNewRevision()
                        + ", warnings=" + result.getWarningCount() + "."
        ));
    }
}
