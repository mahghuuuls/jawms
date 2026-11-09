package example.jawmsreadiness;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
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

@Mod(modid = ReadinessFixture.ID, name = "JAWMS Readiness Verification", version = "1.2",
        dependencies = "required-after:jawms@[1.5.0,)")
public final class ReadinessFixture {
    static final String ID = "jawmsreadinessfixture";
    private static final Logger LOG = LogManager.getLogger(ID);
    private static final ResourceLocation CAUSE = new ResourceLocation(ID, "probe");
    private static final ManaContribution BONUS = ManaContribution.builder().flatMaximumMana(20).build();
    private static final ManaContribution REDUCTION = ManaContribution.builder().flatMaximumMana(-20).build();
    private static final boolean VERIFY_FIRST_ACCEPTANCE =
            Boolean.getBoolean("jawms.readinessFixture.verifyFirstAcceptance");

    private ManaReadinessProvider provider;
    private ManaReadinessProvider peer;
    private volatile LoginHold nextLoginHold;
    private TransitionProbe nextTransition;
    private int earlyEvents;
    private final Map<EntityPlayer, Integer> delayedPeers = new IdentityHashMap<>();
    private final Map<EntityPlayer, SuspensionProbe> suspensions = new IdentityHashMap<>();
    private final Map<EntityPlayer, VerificationProbe> verifications = new IdentityHashMap<>();

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ResourceLocation id = new ResourceLocation(ID, "bonus");
        provider = VERIFY_FIRST_ACCEPTANCE ? ManaApi.registerReadinessProvider(id, this::verifyContribution)
                : ManaApi.registerReadinessProvider(id);
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
        suspensions.clear();
        verifications.clear();
        nextTransition = null;
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
            if ("hold-suspend".equals(hold.action)) {
                beginSuspension(event.player);
                return;
            }
            if ("hold-verify-false".equals(hold.action) || "hold-verify-throw".equals(hold.action)) {
                beginVerification(event.player, "hold-verify-throw".equals(hold.action));
                return;
            }
            LOG.info("READINESS_FIXTURE_WITHHELD player={}", event.player.getUniqueID());
            return;
        }
        complete(event.player, provider, VERIFY_FIRST_ACCEPTANCE ? ManaContribution.EMPTY : BONUS);
        delayPeer(event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void completeTransfer(PlayerEvent.PlayerChangedDimensionEvent event) {
        completeTransition(event.player, "transfer");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void completeRespawn(PlayerEvent.PlayerRespawnEvent event) {
        completeTransition(event.player, event.isEndConquered() ? "end-return" : "respawn");
    }

    private void completeTransition(EntityPlayer player, String lifecycle) {
        if (!(player instanceof EntityPlayerMP)) return;
        TransitionProbe probe = nextTransition;
        if (probe != null && !probe.source.getUniqueID().equals(player.getUniqueID())) probe = null;
        boolean held = probe != null && "hold-transition".equals(probe.action);
        boolean empty = probe != null && "empty-transition".equals(probe.action);
        if (probe != null) {
            nextTransition = null;
            delayedPeers.remove(probe.source);
            ManaContributionPublicationResult replay = provider.update(
                    probe.source, probe.token, 1, ManaContribution.EMPTY);
            LOG.info("READINESS_FIXTURE_RETIRED_REPLAY player={} lifecycle={} sameEntity={} result={}",
                    player.getUniqueID(), lifecycle, probe.source == player, replay.getStatus());
        }
        LOG.info("READINESS_FIXTURE_TRANSITION player={} lifecycle={} dimension={} entity={} phase={} held={} empty={} token={}",
                player.getUniqueID(), lifecycle, player.dimension, player.getEntityId(),
                ManaApi.getProfileReadiness(player), held, empty, provider.getGeneration(player).isPresent());
        if (!held) {
            complete(player, provider, empty ? ManaContribution.EMPTY : BONUS);
            delayPeer(player);
        }
    }

    private void delayPeer(EntityPlayer player) {
        if (peer.getGeneration(player).isPresent()) {
            delayedPeers.put(player, player.getServer().getTickCounter() + 10);
        }
    }

    private void beginSuspension(EntityPlayer player) {
        Optional<ManaProfileGeneration> token = provider.getGeneration(player);
        if (!token.isPresent()) {
            LOG.error("READINESS_FIXTURE_SUSPENSION no token for player={}", player.getUniqueID());
            return;
        }
        ManaContributionPublicationResult initial = provider.completeInitial(player, token.get(), ManaContribution.EMPTY);
        logSuspension(player, "record-empty", initial);
        if (initial.getStatus() != ManaContributionPublicationStatus.INITIAL_RECORDED) return;
        ManaContributionPublicationResult suspended = provider.suspend(player, token.get(), initial.getRevision().getAsLong());
        logSuspension(player, "suspend", suspended);
        if (suspended.getStatus() != ManaContributionPublicationStatus.SUSPENDED) return;
        suspensions.put(player, new SuspensionProbe(token.get(), suspended.getRevision().getAsLong()));
        delayPeer(player);
    }

    private static final class SuspensionProbe {
        final ManaProfileGeneration token;
        long revision;

        SuspensionProbe(ManaProfileGeneration token, long revision) {
            this.token = token;
            this.revision = revision;
        }
    }

    private void beginVerification(EntityPlayer player, boolean throwsException) {
        Optional<ManaProfileGeneration> token = provider.getGeneration(player);
        if (!token.isPresent()) {
            LOG.error("READINESS_FIXTURE_VERIFY no token for player={}", player.getUniqueID());
            return;
        }
        ManaContributionPublicationResult initial = provider.completeInitial(player, token.get(), REDUCTION);
        logVerification(player, "record-reduction", initial);
        if (initial.getStatus() != ManaContributionPublicationStatus.INITIAL_RECORDED) return;
        int tick = player.getServer().getTickCounter();
        verifications.put(player, new VerificationProbe(token.get(), initial.getRevision().getAsLong(),
                throwsException, tick + 35));
        delayPeer(player);
    }

    private boolean verifyContribution(EntityPlayer player, ManaProfileGeneration token, long revision,
            ManaContribution contribution) {
        VerificationProbe probe = verifications.get(player);
        if (probe == null) return true;
        // Test-only observation. Settlement and publication are controlled outside the verifier.
        probe.calls++;
        if (probe.token != token || probe.revision != revision
                || contribution != (probe.recovered ? ManaContribution.EMPTY : REDUCTION)) {
            LOG.error("READINESS_FIXTURE_VERIFY unexpected candidate player={} revision={} flatMaximum={}",
                    player.getUniqueID(), revision, contribution.getFlatMaximumMana());
            return false;
        }
        if (probe.calls == 1 || probe.recovered) {
            LOG.info("READINESS_FIXTURE_VERDICT player={} tick={} revision={} flatMaximum={} outcome={} calls={}",
                    player.getUniqueID(), player.getServer().getTickCounter(), revision,
                    contribution.getFlatMaximumMana(),
                    probe.recovered ? "VERIFIED_EMPTY" : probe.throwsException ? "THROW" : "DECLINE", probe.calls);
        }
        if (probe.recovered) return true;
        if (probe.throwsException) throw new IllegalStateException("Synthetic first-acceptance verifier exception");
        return false;
    }

    private boolean recoverVerification(EntityPlayer player, VerificationProbe probe) {
        ManaContributionPublicationResult update = provider.update(
                player, probe.token, probe.revision, ManaContribution.EMPTY);
        logVerification(player, "recover-empty", update);
        if (update.getStatus() != ManaContributionPublicationStatus.UPDATE_RECORDED) return false;
        probe.revision = update.getRevision().getAsLong();
        probe.recovered = true;
        return true;
    }

    private static final class VerificationProbe {
        final ManaProfileGeneration token;
        final boolean throwsException;
        final int recoveryTick;
        long revision;
        int calls;
        boolean recovered;
        boolean recoveryAttempted;

        VerificationProbe(ManaProfileGeneration token, long revision, boolean throwsException, int recoveryTick) {
            this.token = token;
            this.revision = revision;
            this.throwsException = throwsException;
            this.recoveryTick = recoveryTick;
        }
    }

    private static final class TransitionProbe {
        final EntityPlayerMP source;
        final ManaProfileGeneration token;
        final String action;

        TransitionProbe(EntityPlayerMP source, ManaProfileGeneration token, String action) {
            this.source = source;
            this.token = token;
            this.action = action;
        }
    }

    private static File saveDirectory(MinecraftServer server) {
        return server.getEntityWorld().getSaveHandler().getWorldDirectory().getAbsoluteFile();
    }

    private static final class LoginHold {
        final UUID player;
        final File save;
        final String action;

        private LoginHold(UUID player, File save, String action) {
            this.player = player;
            this.save = save;
            this.action = action;
        }
    }

    @SubscribeEvent
    public void completeDelayedPeer(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) return;
        Integer due = delayedPeers.get(event.player);
        if (due != null && event.player.getServer().getTickCounter() - due >= 0) {
            delayedPeers.remove(event.player);
            if (suspensions.containsKey(event.player)) {
                peer.getGeneration(event.player).ifPresent(token -> logSuspension(event.player, "complete-peer",
                        peer.completeInitial(event.player, token, ManaContribution.EMPTY)));
            } else if (verifications.containsKey(event.player)) {
                peer.getGeneration(event.player).ifPresent(token -> logVerification(event.player, "complete-peer",
                        peer.completeInitial(event.player, token, ManaContribution.EMPTY)));
            } else {
                complete(event.player, peer, ManaContribution.EMPTY);
            }
        }
        VerificationProbe probe = verifications.get(event.player);
        if (probe != null && !probe.throwsException && !probe.recoveryAttempted
                && event.player.getServer().getTickCounter() - probe.recoveryTick >= 0) {
            probe.recoveryAttempted = true;
            if (probe.calls == 0 || ManaApi.getProfileReadiness(event.player) != ManaProfileReadiness.PENDING) {
                LOG.error("READINESS_FIXTURE_VERIFY pre-deadline recovery fixture did not reach the held verdict");
            } else {
                recoverVerification(event.player, probe);
            }
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        delayedPeers.remove(event.player);
        suspensions.remove(event.player);
        verifications.remove(event.player);
        if (nextTransition != null && nextTransition.source == event.player) nextTransition = null;
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

    private static void logSuspension(EntityPlayer player, String action, ManaContributionPublicationResult result) {
        LOG.info("READINESS_FIXTURE_SUSPENSION player={} action={} tick={} result={} revision={}",
                player.getUniqueID(), action, player.getServer().getTickCounter(), result.getStatus(), result.getRevision());
    }

    private static void logVerification(EntityPlayer player, String action, ManaContributionPublicationResult result) {
        LOG.info("READINESS_FIXTURE_VERIFY player={} action={} tick={} result={} revision={}",
                player.getUniqueID(), action, player.getServer().getTickCounter(), result.getStatus(), result.getRevision());
    }

    private final class ProbeCommand extends CommandBase {
        @Override
        public String getName() {
            return "readinessfixture";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/readinessfixture <hold|hold-suspend|release|recover-suspended|refuse-suspend|seed|remove|inspect"
                    + "|hold-transition|empty-transition|trace-transition|hold-verify-false|hold-verify-throw"
                    + "|recover-verified|update-verified>";
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
            SuspensionProbe probe = suspensions.get(player);
            VerificationProbe verification = verifications.get(player);
            switch (arguments[0]) {
                case "hold":
                case "hold-suspend":
                    // Reopening single-player stops and recreates the logical server in this JVM.
                    nextLoginHold = new LoginHold(player.getUniqueID(), saveDirectory(server), arguments[0]);
                    break;
                case "hold-verify-false":
                case "hold-verify-throw":
                    if (!VERIFY_FIRST_ACCEPTANCE || ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY
                            || service.getCurrentMana(player) != 110 || service.getMaximumMana(player) != 120) {
                        throw new CommandException("Expected verification mode and a seeded 110/120 profile.");
                    }
                    nextLoginHold = new LoginHold(player.getUniqueID(), saveDirectory(server), arguments[0]);
                    break;
                case "recover-verified":
                    if (verification == null || verification.calls == 0 || verification.recovered
                            || ManaApi.getProfileReadiness(player) != ManaProfileReadiness.UNAVAILABLE
                            || !recoverVerification(player, verification)) {
                        throw new CommandException("Expected a verified recovery from the held timeout.");
                    }
                    break;
                case "update-verified":
                    if (verification == null || !verification.recovered
                            || ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY) {
                        throw new CommandException("Expected the accepted recovered generation.");
                    }
                    ManaContributionPublicationResult verifiedUpdate = provider.update(
                            player, verification.token, verification.revision, REDUCTION);
                    logVerification(player, "update-accepted", verifiedUpdate);
                    if (verifiedUpdate.getStatus() != ManaContributionPublicationStatus.UPDATE_RECORDED) {
                        throw new CommandException("Accepted profile update refused.");
                    }
                    verification.revision = verifiedUpdate.getRevision().getAsLong();
                    LOG.info("READINESS_FIXTURE_VERIFY acceptedUpdate player={} verifierCalls={}",
                            player.getUniqueID(), verification.calls);
                    break;
                case "recover-suspended":
                    if (probe == null || ManaApi.getProfileReadiness(player) == ManaProfileReadiness.READY) {
                        throw new CommandException("Expected a suspended generation before first acceptance.");
                    }
                    ManaContributionPublicationResult recovery = provider.update(player, probe.token, probe.revision, BONUS);
                    logSuspension(player, "recover", recovery);
                    if (recovery.getStatus() != ManaContributionPublicationStatus.UPDATE_RECORDED) {
                        throw new CommandException("Recovery publication refused.");
                    }
                    probe.revision = recovery.getRevision().getAsLong();
                    break;
                case "refuse-suspend":
                    if (probe == null || ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY) {
                        throw new CommandException("Expected an accepted generation after recovery.");
                    }
                    ManaContributionPublicationResult refusal = provider.suspend(player, probe.token, probe.revision);
                    logSuspension(player, "refuse-accepted", refusal);
                    if (refusal.getStatus() != ManaContributionPublicationStatus.ALREADY_ACCEPTED) {
                        throw new CommandException("Expected suspension to refuse the accepted generation.");
                    }
                    break;
                case "release":
                    nextLoginHold = null;
                    complete(player, provider, BONUS);
                    complete(player, peer, ManaContribution.EMPTY);
                    break;
                case "hold-transition":
                case "empty-transition":
                case "trace-transition":
                    if (ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY) {
                        throw new CommandException("Expected ready source profile.");
                    }
                    ManaProfileGeneration sourceToken = provider.getGeneration(player)
                            .orElseThrow(() -> new CommandException("No source readiness token."));
                    nextTransition = new TransitionProbe(player, sourceToken, arguments[0]);
                    break;
                case "seed":
                    if (ManaApi.getProfileReadiness(player) != ManaProfileReadiness.READY
                            || service.getMaximumMana(player) != 120) throw new CommandException("Expected ready 120 max.");
                    service.setCurrentMana(player, 110, CAUSE);
                    break;
                case "remove":
                    ManaProfileGeneration token = provider.getGeneration(player)
                            .orElseThrow(() -> new CommandException("No readiness token."));
                    ManaContributionPublicationResult removal = provider.update(player, token,
                            probe != null && probe.token == token ? probe.revision : 1, ManaContribution.EMPTY);
                    logReceipt(player, removal);
                    if (probe != null && probe.token == token && removal.getStatus() == ManaContributionPublicationStatus.UPDATE_RECORDED) {
                        probe.revision = removal.getRevision().getAsLong();
                    }
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
