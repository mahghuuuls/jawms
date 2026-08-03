package com.mahghuuuls.jawms.testfixture;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

public final class BalanceCommand extends CommandBase {

    private final BalanceRecorder recorder;

    public BalanceCommand(BalanceRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public String getName() {
        return "jawms-balance";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jawms-balance rotations"
                + " | setup <rotation>"
                + " | start <rotation> <1|2|3>"
                + " | note <hit|miss|failure> [count]"
                + " | note downtime <ticks>"
                + " | boost <true|false>"
                + " | profile"
                + " | status"
                + " | stop <completed|depleted|aborted>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args)
            throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (args.length == 1 && "rotations".equalsIgnoreCase(args[0])) {
            for (BalanceRotation rotation : BalanceRotation.all()) {
                BalanceSimulator.Result result = BalanceSimulator.simulate(rotation);
                sender.sendMessage(new TextComponentString(
                        rotation.getId()
                                + ": profile=" + rotation.getGear()
                                + ", cadence=" + rotation.getCadenceTicks() + " ticks"
                                + ", duration=" + rotation.getDurationTicks() + " ticks"
                                + ", predictedFirstRequired="
                                + result.getFirstUnaffordableTick() + " ticks."
                ));
            }
            return;
        }
        if (args.length == 2 && "setup".equalsIgnoreCase(args[0])) {
            recorder.setup(player, requireRotation(args[1]));
            return;
        }
        if (args.length == 3 && "start".equalsIgnoreCase(args[0])) {
            recorder.start(player, requireRotation(args[1]), parseInt(args[2], 1, 3));
            return;
        }
        if ((args.length == 2 || args.length == 3)
                && "note".equalsIgnoreCase(args[0])) {
            int amount = args.length == 3 ? parseInt(args[2], 1) : 1;
            recorder.note(player, args[1].toLowerCase(java.util.Locale.ROOT), amount);
            return;
        }
        if (args.length == 1 && "status".equalsIgnoreCase(args[0])) {
            recorder.status(player);
            return;
        }
        if (args.length == 1 && "profile".equalsIgnoreCase(args[0])) {
            recorder.profile(player);
            return;
        }
        if (args.length == 2 && "boost".equalsIgnoreCase(args[0])) {
            if (!"true".equalsIgnoreCase(args[1])
                    && !"false".equalsIgnoreCase(args[1])) {
                throw new CommandException("Expected true or false.");
            }
            boolean enabled = Boolean.parseBoolean(args[1]);
            ManaTestFixture.setBalancePercentBoost(player, enabled);
            sender.sendMessage(new TextComponentString(
                    "JAWMS balance percentage boost enabled=" + enabled
                            + "; profile refreshes by the next server tick."
            ));
            return;
        }
        if (args.length == 2 && "stop".equalsIgnoreCase(args[0])) {
            recorder.stop(player, args[1].toLowerCase(java.util.Locale.ROOT));
            return;
        }
        throw new CommandException(getUsage(sender));
    }

    private static BalanceRotation requireRotation(String id) throws CommandException {
        BalanceRotation rotation = BalanceRotation.find(id.toLowerCase(java.util.Locale.ROOT));
        if (rotation == null) {
            throw new CommandException("Unknown balance rotation: " + id);
        }
        return rotation;
    }
}
