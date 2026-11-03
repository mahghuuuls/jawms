package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.internal.mana.ManaCapability;
import com.mahghuuuls.jawms.internal.mana.ManaReservation;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesManager;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent.Clone;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerChangedDimensionEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Disposable-world probes. Claims exist only inside one synchronous probe or event dispatch. */
public final class ManaLifecycleFixture {
    private static final Logger LOG = LogManager.getLogger("jawms_mv03");
    private static final ResourceLocation RESTORE = new ResourceLocation("jawms", "mv03_fixture_restore");
    private static final ManaLifecycleFixture INSTANCE = new ManaLifecycleFixture();

    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<Object, ManaReservation> witnesses = new IdentityHashMap<>();
    private boolean registered;

    static void initialize() {
        INSTANCE.armed.clear();
        INSTANCE.witnesses.clear();
        if (!INSTANCE.registered) {
            MinecraftForge.EVENT_BUS.register(INSTANCE);
            INSTANCE.registered = true;
        }
    }

    static void execute(ICommandSender sender, String operation) throws CommandException {
        if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) {
            throw new CommandException("Player sender required");
        }
        EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
        String world = player.world.getWorldInfo().getWorldName();
        if (!"JAWMS-C007-MV03-I".equals(world) && !"JAWMS-C007-MV03-D".equals(world)) {
            throw new CommandException("MV-03 probes require the named disposable world");
        }
        switch (operation) {
            case "inspect":
                LOG.info("MV03_LIFECYCLE inspect {}", identity(player));
                break;
            case "profile":
                probeProfile(player);
                break;
            case "clone":
            case "dimension":
            case "logout":
                INSTANCE.armed.put(player.getUniqueID(), operation);
                LOG.info("MV03_LIFECYCLE armed kind={} {}", operation, identity(player));
                break;
            case "transfer":
                if (player.dimension != 0 || !player.isCreative()
                        || !"dimension".equals(INSTANCE.armed.get(player.getUniqueID()))) {
                    throw new CommandException("Arm the dimension witness in Creative in the Overworld");
                }
                player.changeDimension(-1);
                break;
            case "clear":
                INSTANCE.armed.remove(player.getUniqueID());
                break;
            default:
                throw new CommandException("Unknown MV-03 lifecycle probe");
        }
    }

    private static void probeProfile(EntityPlayerMP player) throws CommandException {
        GameplayRules original = GameplayRulesManager.current();
        ManaState state = ManaService.requireState(player);
        int before = state.getCurrentMana();
        if (before < 30 || state.getMaximumMana() < 30) {
            throw new CommandException("Fund the isolated fixture and remove maximum-mana equipment first");
        }
        ManaReservation claim = ManaService.reserveMana(player, 30);
        if (claim == null) throw new CommandException("Could not open the fixture claim");
        GameplayRules first = withMaximum(original, 10);
        GameplayRules latest = withMaximum(first, 20);
        try {
            LOG.info("MV03_PROFILE before {} revision={} available={}", identity(player),
                    ManaService.explainAcceptedProfile(player).getGameplayRulesRevision(), state.getAvailableMana());
            ManaService.applyRulesReload(player, original, first);
            ManaService.applyRulesReload(player, first, latest);
            LOG.info("MV03_PROFILE during {} revision={} explanationMax={} valid={} available={}",
                    identity(player), ManaService.explainAcceptedProfile(player).getGameplayRulesRevision(),
                    ManaService.explainAcceptedProfile(player).getFinalMaximumMana(),
                    ManaService.isReservationValid(player, claim), state.getAvailableMana());
            ManaService.releaseReservation(claim);
            ManaService.flushPendingProfileEvaluation(player, claim);
            LOG.info("MV03_PROFILE after {} revision={} explanationMax={} valid={} available={}",
                    identity(player), ManaService.explainAcceptedProfile(player).getGameplayRulesRevision(),
                    ManaService.explainAcceptedProfile(player).getFinalMaximumMana(),
                    ManaService.isReservationValid(player, claim), state.getAvailableMana());
        } finally {
            ManaService.releaseReservation(claim);
            ManaService.applyRulesReload(player, latest, original);
            ManaApi.getManaService().setCurrentMana(player, before, RESTORE);
        }
    }

    private static GameplayRules withMaximum(GameplayRules base, int maximum) {
        return new GameplayRules(base.getRevision() + 1L, maximum, base.getBaseRegeneration(),
                base.getRegenerationLockoutSeconds(), base.getCostFormula(), base.getSmallFlask(),
                base.getMediumFlask(), base.getLargeFlask(), base.getSpellCosts(), base.getConfiguredItemRules(),
                base.getArmorProfile(), base.getDisabledProviderIds(), base.getEfficiencyScale(),
                base.getArmorEfficiencyProfile(), base.getWizardryIntegrationRules(), base.getProviderPreviews());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeClone(Clone event) {
        if (event.isWasDeath()) begin(event, "clone", event.getOriginal());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterClone(Clone event) { end(event, "clone", event.getOriginal(), event.getEntityPlayer()); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeDimension(PlayerChangedDimensionEvent event) {
        if ("dimension".equals(armed.get(event.player.getUniqueID()))) {
            LOG.info("MV03_LIFECYCLE transfer_event from={} to={}", event.fromDim, event.toDim);
        }
        begin(event, "dimension", event.player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterDimension(PlayerChangedDimensionEvent event) { end(event, "dimension", event.player, event.player); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void beforeLogout(PlayerLoggedOutEvent event) { begin(event, "logout", event.player); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterLogout(PlayerLoggedOutEvent event) { end(event, "logout", event.player, null); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void afterLogin(PlayerLoggedInEvent event) {
        if ("rejoin".equals(armed.remove(event.player.getUniqueID()))) {
            LOG.info("MV03_LIFECYCLE rejoin {}", identity(event.player));
        }
    }

    private void begin(Object event, String kind, EntityPlayer player) {
        if (player.world.isRemote || !kind.equals(armed.get(player.getUniqueID()))) return;
        if ("logout".equals(kind)) armed.put(player.getUniqueID(), "rejoin");
        else armed.remove(player.getUniqueID());
        ManaReservation claim = ManaService.reserveMana(player, 1);
        if (claim == null) {
            LOG.warn("MV03_LIFECYCLE unreached kind={} reason=no_available_mana {}", kind, identity(player));
            return;
        }
        witnesses.put(event, claim);
        LOG.info("MV03_LIFECYCLE before kind={} {} reserved={} payable={}", kind, identity(player),
                claim.getAmount(), ManaService.isReservationValid(player, claim));
    }

    private void end(Object event, String kind, EntityPlayer original, EntityPlayer replacement) {
        ManaReservation claim = witnesses.remove(event);
        if (claim == null) return;
        try {
            boolean payable = claim.isCurrentGeneration() && ManaService.isReservationValid(original, claim);
            LOG.info("MV03_LIFECYCLE after kind={} old=[{}] new=[{}] generationCurrent={} originalPayable={}",
                    kind, identity(original), replacement == null ? "none" : identity(replacement),
                    claim.isCurrentGeneration(), payable);
        } finally {
            ManaService.releaseReservation(claim);
        }
    }

    private static String identity(EntityPlayer player) {
        ManaState state = player.getCapability(ManaCapability.INSTANCE, null);
        return "uuid=" + player.getUniqueID() + " name=" + player.getName()
                + " entity=" + player.getEntityId() + " object=" + System.identityHashCode(player)
                + " dimension=" + player.dimension + " capability=" + System.identityHashCode(state)
                + " connection=" + (player instanceof EntityPlayerMP
                        ? System.identityHashCode(((EntityPlayerMP) player).connection) : 0)
                + " mana=" + (state == null ? -1 : state.getCurrentMana())
                + " maximum=" + (state == null ? -1 : state.getMaximumMana())
                + " available=" + (state == null ? -1 : state.getAvailableMana());
    }
}
