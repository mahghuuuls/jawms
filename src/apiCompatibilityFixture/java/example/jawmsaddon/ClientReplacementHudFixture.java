package example.jawmsaddon;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Development-only controller and ownership probe for a replacement HUD that
 * uses only JAWMS public API.
 */
public final class ClientReplacementHudFixture extends CommandBase {

    static final ClientReplacementHudFixture INSTANCE = new ClientReplacementHudFixture();

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API Fixture");

    private final EqualClaimant claimantA = new EqualClaimant("A");
    private final EqualClaimant claimantB = new EqualClaimant("B");

    private volatile Mode mode = Mode.OFF;
    private volatile EqualOrder equalOrder = EqualOrder.OFF;
    private volatile boolean probeEnabled;
    private volatile boolean earlierCancellationEnabled;
    private volatile boolean parentCancellationEnabled;

    private boolean observedContext;
    private long lastRevision;
    private boolean lastCreative;
    private String lastOutcome = "";
    private boolean earlierCancellationRecorded;
    private boolean parentCancellationRecorded;
    private long replacementCallbacks;
    private long earlierClaims;
    private long parentCancellations;
    private long equalClaims;

    private ClientReplacementHudFixture() {
    }

    void install() {
        MinecraftForge.EVENT_BUS.register(this);
        registerEqualClaimants(EqualOrder.AB);
        equalOrder = EqualOrder.OFF;
    }

    @Override
    public String getName() {
        return "jawms-api-hud";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/jawms-api-hud probe <on|off> | mode "
                + "<off|visible|hide-full|hide-creative|exception> | "
                + "earlier <on|off> | parent <on|off> | equal <off|ab|ba> | "
                + "replacement <on|off> | status";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
            throws CommandException {
        if (arguments.length == 1 && "status".equalsIgnoreCase(arguments[0])) {
            String currentStatus = status();
            LOGGER.info(currentStatus);
            reply(sender, currentStatus);
            return;
        }
        if (arguments.length != 2) {
            throw new CommandException(getUsage(sender));
        }

        String operation = arguments[0].toLowerCase();
        String value = arguments[1].toLowerCase();
        if ("probe".equals(operation)) {
            probeEnabled = parseFlag(value);
            observedContext = false;
        } else if ("mode".equals(operation)) {
            mode = Mode.parse(value);
            observedContext = false;
            lastOutcome = "";
        } else if ("earlier".equals(operation)) {
            earlierCancellationEnabled = parseFlag(value);
            earlierCancellationRecorded = false;
        } else if ("parent".equals(operation)) {
            parentCancellationEnabled = parseFlag(value);
            parentCancellationRecorded = false;
        } else if ("equal".equals(operation)) {
            EqualOrder requested = EqualOrder.parse(value);
            if (requested == EqualOrder.OFF) {
                equalOrder = EqualOrder.OFF;
            } else {
                registerEqualClaimants(requested);
                equalOrder = requested;
            }
            lastOutcome = "";
        } else if ("replacement".equals(operation)) {
            // Retains the earlier fixture command while the richer modes are tested.
            mode = parseFlag(value) ? Mode.VISIBLE : Mode.OFF;
            observedContext = false;
            lastOutcome = "";
        } else {
            throw new CommandException(getUsage(sender));
        }

        String currentStatus = status();
        LOGGER.info(currentStatus);
        reply(sender, currentStatus);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void cancelParentChat(RenderGameOverlayEvent.Chat event) {
        if (parentCancellationEnabled) {
            parentCancellations++;
            event.setCanceled(true);
            if (!parentCancellationRecorded) {
                parentCancellationRecorded = true;
                LOGGER.info("JAWMS API HUD parent probe: Chat cancelled before JAWMS");
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void claimEarlier(ManaHudRenderEvent event) {
        if (earlierCancellationEnabled) {
            earlierClaims++;
            event.setCanceled(true);
            if (!earlierCancellationRecorded) {
                earlierCancellationRecorded = true;
                LOGGER.info("JAWMS API HUD ownership probe: earlier claimant cancelled");
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
    public void renderReplacement(ManaHudRenderEvent event) {
        replacementCallbacks++;
        observeContext(event);

        if (equalOrder != EqualOrder.OFF || mode == Mode.OFF) {
            return;
        }

        // Ownership is claimed before applying this replacement's visibility policy.
        event.setCanceled(true);
        ClientManaSnapshot snapshot = event.getSnapshot();
        boolean creative = event.getContext().isCreativeMode();
        if (mode == Mode.HIDE_FULL
                && snapshot.getCurrentMana() >= snapshot.getMaximumMana()) {
            recordOutcome("replacement claimed and intentionally hidden at full mana");
            return;
        }
        if (mode == Mode.HIDE_CREATIVE && creative) {
            recordOutcome("replacement claimed and intentionally hidden in Creative");
            return;
        }

        try {
            CacheSafeReplacementHud.render(event, mode == Mode.EXCEPTION);
            recordOutcome(mode == Mode.EXCEPTION
                    ? "controlled exceptional render unexpectedly completed"
                    : "textured replacement rendered");
        } catch (FixtureRenderException expected) {
            recordOutcome("controlled exceptional render cleaned up: " + expected.getMessage());
        }
    }

    private void claimEqual(String claimant, ManaHudRenderEvent event) {
        if (equalOrder == EqualOrder.OFF) {
            return;
        }
        equalClaims++;
        event.setCanceled(true);
        CacheSafeReplacementHud.render(event, false);
        recordOutcome("equal-priority claimant " + claimant + " won order " + equalOrder.name());
    }

    private void observeContext(ManaHudRenderEvent event) {
        if (!probeEnabled && mode == Mode.OFF && equalOrder == EqualOrder.OFF
                && !parentCancellationEnabled) {
            return;
        }
        ClientManaSnapshot snapshot = event.getSnapshot();
        boolean creative = event.getContext().isCreativeMode();
        if (!observedContext
                || snapshot.getRevision() != lastRevision
                || creative != lastCreative) {
            observedContext = true;
            lastRevision = snapshot.getRevision();
            lastCreative = creative;
            LOGGER.info(
                    "JAWMS API HUD probe: snapshot={}, creativeKnown={}, creative={}, "
                            + "partialTicks={}, scaledWidth={}, scaledHeight={}",
                    snapshot,
                    event.getContext().isCreativeModeKnown(),
                    creative,
                    event.getPartialTicks(),
                    event.getScaledWidth(),
                    event.getScaledHeight()
            );
        }
    }

    private void registerEqualClaimants(EqualOrder order) {
        MinecraftForge.EVENT_BUS.unregister(claimantA);
        MinecraftForge.EVENT_BUS.unregister(claimantB);
        if (order == EqualOrder.BA) {
            MinecraftForge.EVENT_BUS.register(claimantB);
            MinecraftForge.EVENT_BUS.register(claimantA);
        } else {
            MinecraftForge.EVENT_BUS.register(claimantA);
            MinecraftForge.EVENT_BUS.register(claimantB);
        }
    }

    private void recordOutcome(String outcome) {
        if (!outcome.equals(lastOutcome)) {
            lastOutcome = outcome;
            LOGGER.info("JAWMS API HUD ownership probe: {}", outcome);
        }
    }

    private String status() {
        return "JAWMS API HUD fixture: probe=" + probeEnabled
                + ", mode=" + mode.name()
                + ", earlier=" + earlierCancellationEnabled
                + ", parent=" + parentCancellationEnabled
                + ", equal=" + equalOrder.name()
                + ", replacementCallbacks=" + replacementCallbacks
                + ", earlierClaims=" + earlierClaims
                + ", parentCancellations=" + parentCancellations
                + ", equalClaims=" + equalClaims + ".";
    }

    private static void reply(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(message));
    }

    private static boolean parseFlag(String value) throws CommandException {
        if ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new CommandException("Expected on or off.");
    }

    private enum Mode {
        OFF,
        VISIBLE,
        HIDE_FULL,
        HIDE_CREATIVE,
        EXCEPTION;

        private static Mode parse(String value) throws CommandException {
            for (Mode candidate : values()) {
                if (candidate.name().replace('_', '-').equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
            throw new CommandException("Unknown replacement mode: " + value);
        }
    }

    private enum EqualOrder {
        OFF,
        AB,
        BA;

        private static EqualOrder parse(String value) throws CommandException {
            for (EqualOrder candidate : values()) {
                if (candidate.name().equalsIgnoreCase(value)) {
                    return candidate;
                }
            }
            throw new CommandException("Unknown equal-priority order: " + value);
        }
    }

    public final class EqualClaimant {

        private final String name;

        private EqualClaimant(String name) {
            this.name = name;
        }

        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = false)
        public void claim(ManaHudRenderEvent event) {
            claimEqual(name, event);
        }
    }
}
