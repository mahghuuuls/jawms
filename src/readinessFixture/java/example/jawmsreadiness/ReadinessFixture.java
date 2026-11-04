package example.jawmsreadiness;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod(modid = ReadinessFixture.ID, name = "JAWMS Readiness Verification", version = "1.0",
        dependencies = "required-after:jawms@[1.3.0,)")
public final class ReadinessFixture {
    static final String ID = "jawmsreadinessfixture";
    private static final Logger LOG = LogManager.getLogger(ID);
    private static final ResourceLocation CAUSE = new ResourceLocation(ID, "probe");
    private static final ManaContribution BONUS = ManaContribution.builder().flatMaximumMana(20).build();

    private ManaReadinessProvider provider;
    private ManaReadinessProvider peer;
    private volatile LoginHold nextLoginHold;
    private int earlyEvents;
    private final Map<EntityPlayer, Integer> delayedPeers = new IdentityHashMap<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        provider = ManaApi.registerReadinessProvider(new ResourceLocation(ID, "bonus"));
        peer = ManaApi.registerReadinessProvider(new ResourceLocation(ID, "peer"));
        MinecraftForge.EVENT_BUS.register(this);
        if (event.getSide().isClient()) MinecraftForge.EVENT_BUS.register(new ClientObserver());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new ProbeCommand());
    }

    @Mod.EventHandler
    public void serverStopped(FMLServerStoppedEvent event) {
        delayedPeers.clear();
    }

    @SubscribeEvent
    public void onChange(ManaStateChangeEvent event) {
        if (CAUSE.equals(event.getCause())) earlyEvents++;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void earlyLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        IManaService service = ManaApi.getManaService();
        ManaPublicState before = service.getState(event.player);
        int beforeEvents = earlyEvents;
        long lockout = service.startRegenerationLockout(event.player, CAUSE);
        com.mahghuuuls.jawms.api.ManaMutationResult result =
                service.setCurrentMana(event.player, Math.max(0, before.getCurrentMana() - 1), CAUSE);
        LOG.info("READINESS_FIXTURE_EARLY player={} phase={} current={} maximum={} write={} delta={} lockout={} events={}",
                event.player.getUniqueID(), ManaApi.getProfileReadiness(event.player), before.getCurrentMana(),
                before.getMaximumMana(), result.getFailure(), result.getActualDelta(), lockout, earlyEvents - beforeEvents);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void completeLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        LoginHold hold = nextLoginHold;
        if (hold != null && hold.player.equals(event.player.getUniqueID())
                && hold.save.equals(saveDirectory(event.player.getServer()))) {
            nextLoginHold = null;
            LOG.info("READINESS_FIXTURE_WITHHELD player={}", event.player.getUniqueID());
            return;
        }
        complete(event.player, provider, BONUS);
        if (peer.getGeneration(event.player).isPresent()) {
            delayedPeers.put(event.player, event.player.getServer().getTickCounter() + 10);
        }
    }

    private static File saveDirectory(MinecraftServer server) {
        return server.getEntityWorld().getSaveHandler().getWorldDirectory().getAbsoluteFile();
    }

    private static final class LoginHold {
        final UUID player;
        final File save;

        private LoginHold(UUID player, File save) {
            this.player = player;
            this.save = save;
        }
    }

    @SubscribeEvent
    public void completeDelayedPeer(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        Integer due = delayedPeers.get(event.player);
        if (due != null && event.player.getServer().getTickCounter() - due >= 0) {
            delayedPeers.remove(event.player);
            complete(event.player, peer, ManaContribution.EMPTY);
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        delayedPeers.remove(event.player);
    }

    private static void complete(EntityPlayer player, ManaReadinessProvider source, ManaContribution group) {
        Optional<ManaProfileGeneration> token = source.getGeneration(player);
        if (token.isPresent()) {
            logReceipt(player, source.completeInitial(player, token.get(), group));
        }
    }

    private static void logReceipt(EntityPlayer player, ManaContributionPublicationResult result) {
        LOG.info("READINESS_FIXTURE_GROUP player={} result={} revision={}", player.getUniqueID(),
                result.getStatus(), result.getRevision());
    }

    private final class ProbeCommand extends CommandBase {
        @Override
        public String getName() {
            return "readinessfixture";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/readinessfixture <hold|release|seed|remove|inspect>";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] arguments) throws CommandException {
            if (arguments.length != 1) throw new CommandException(getUsage(sender));
            if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
                throw new CommandException("Player sender required.");
            }
            EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
            IManaService service = ManaApi.getManaService();
            switch (arguments[0]) {
                case "hold":
                    // Reopening single-player stops and recreates the logical server in this JVM.
                    nextLoginHold = new LoginHold(player.getUniqueID(), saveDirectory(server));
                    break;
                case "release":
                    nextLoginHold = null;
                    complete(player, provider, BONUS);
                    complete(player, peer, ManaContribution.EMPTY);
                    break;
                case "seed":
                    if (ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY
                            || service.getMaximumMana(player) != 120) throw new CommandException("Expected ready 120 max.");
                    service.setCurrentMana(player, 110, CAUSE);
                    break;
                case "remove":
                    ManaProfileGeneration token = provider.getGeneration(player)
                            .orElseThrow(() -> new CommandException("No readiness token."));
                    logReceipt(player, provider.update(player, token, 1, ManaContribution.EMPTY));
                    break;
                case "inspect":
                    break;
                default:
                    throw new CommandException(getUsage(sender));
            }
            ManaPublicState state = service.getState(player);
            LOG.info("READINESS_FIXTURE_INSPECT player={} action={} phase={} current={} maximum={}",
                    player.getUniqueID(), arguments[0], ManaApi.getProfileReadiness(player),
                    state.getCurrentMana(), state.getMaximumMana());
        }
    }
}
