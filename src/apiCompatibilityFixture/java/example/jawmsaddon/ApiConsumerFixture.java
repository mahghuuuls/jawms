package example.jawmsaddon;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaProviderDescriptor;
import com.mahghuuuls.jawms.api.ManaProfileExplanation;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import com.mahghuuuls.jawms.api.IItemManaModifierProvider;
import com.mahghuuuls.jawms.api.IContextualManaModifierProvider;
import com.mahghuuuls.jawms.api.ManaItemContext;
import com.mahghuuuls.jawms.api.ManaTooltips;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.constants.Element;
import electroblob.wizardry.registry.WizardryItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.init.Items;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** A separately compiled Java 8 example addon that uses only JAWMS public API types. */
@Mod(
        modid = ApiConsumerFixture.MOD_ID,
        name = "JAWMS API Compatibility Fixture",
        version = "1.0",
        dependencies = "required-after:jawms",
        acceptableRemoteVersions = "*"
)
public final class ApiConsumerFixture {

    public static final String MOD_ID = "jawms_api_fixture";

    @SidedProxy(
            modId = MOD_ID,
            clientSide = "example.jawmsaddon.ClientApiFixtureProxy",
            serverSide = "example.jawmsaddon.CommonApiFixtureProxy"
    )
    public static CommonApiFixtureProxy proxy;

    private static final ResourceLocation CAUSE =
            new ResourceLocation("exampleaddon", "fixture_operation");
    private static final ResourceLocation LOCKOUT_CAUSE =
            new ResourceLocation("exampleaddon", "fixture_lockout");
    private static final AtomicInteger ITEM_CALLS = new AtomicInteger();
    private static final AtomicInteger CONTEXT_CALLS = new AtomicInteger();
    private static final AtomicInteger FAILURE_CALLS = new AtomicInteger();
    private static final AtomicInteger MUTATION_REJECTIONS = new AtomicInteger();
    private static final AtomicReference<String> LAST_ITEM_CONTEXT =
            new AtomicReference<>("none");
    private static final AtomicReference<String> LAST_CONTEXT_PLAYER =
            new AtomicReference<>("none");
    private static final AtomicLong LAST_CONTEXT_TICK = new AtomicLong(-1L);
    private static volatile boolean contextualEnabled;
    private static volatile boolean nullEnabled;
    private static volatile boolean throwingEnabled;
    private static volatile boolean mutationProbeEnabled;
    private static volatile boolean baubleProbeEnabled;

    private final Map<EventKey, EventObservation> stateEvents = new HashMap<>();

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
        proxy.initialize();
        ManaApi.registerItemProvider(
                new ResourceLocation(MOD_ID, "stick_first"),
                Items.STICK,
                new IItemManaModifierProvider() {
                    @Override
                    public ManaContribution getContribution(
                            EntityPlayer player,
                            ManaItemContext context
                    ) {
                        ITEM_CALLS.incrementAndGet();
                        LAST_ITEM_CONTEXT.set(context.toString());
                        return ManaContribution.builder().flatMaximumMana(1).build();
                    }
                },
                ManaContribution.builder().flatMaximumMana(1).build()
        );
        ManaApi.registerItemProvider(
                new ResourceLocation(MOD_ID, "stick_second"),
                Items.STICK,
                new IItemManaModifierProvider() {
                    @Override
                    public ManaContribution getContribution(
                            EntityPlayer player,
                            ManaItemContext context
                    ) {
                        ITEM_CALLS.incrementAndGet();
                        LAST_ITEM_CONTEXT.set(context.toString());
                        context.getStack().setCount(0); // proves the context stack is defensive
                        return ManaContribution.builder().flatMaximumMana(2).build();
                    }
                }
        );
        ManaApi.registerContextualProvider(
                new ResourceLocation(MOD_ID, "contextual_probe"),
                new IContextualManaModifierProvider() {
                    @Override
                    public ManaContribution getContribution(EntityPlayer player) {
                        CONTEXT_CALLS.incrementAndGet();
                        LAST_CONTEXT_PLAYER.set(player.getUniqueID().toString());
                        LAST_CONTEXT_TICK.set(player.world.getTotalWorldTime());
                        return contextualEnabled
                                ? ManaContribution.builder().flatRegeneration(1.0D).build()
                                : ManaContribution.EMPTY;
                    }
                }
        );
        ManaApi.registerItemProvider(
                new ResourceLocation(MOD_ID, "bauble_probe"),
                WizardryItems.ring_condensing,
                new IItemManaModifierProvider() {
                    @Override
                    public ManaContribution getContribution(
                            EntityPlayer player,
                            ManaItemContext context
                    ) {
                        ITEM_CALLS.incrementAndGet();
                        LAST_ITEM_CONTEXT.set(context.toString());
                        return baubleProbeEnabled
                                ? ManaContribution.builder()
                                .flatMaximumMana(7)
                                .flatRegeneration(0.5D)
                                .build()
                                : ManaContribution.EMPTY;
                    }
                }
        );
        ManaApi.registerContextualProvider(
                new ResourceLocation(MOD_ID, "failure_probe"),
                new IContextualManaModifierProvider() {
                    @Override
                    public ManaContribution getContribution(EntityPlayer player) {
                        FAILURE_CALLS.incrementAndGet();
                        if (throwingEnabled) {
                            throw new IllegalStateException("Intentional fixture provider failure");
                        }
                        return nullEnabled ? null : ManaContribution.EMPTY;
                    }
                }
        );
        ManaApi.registerContextualProvider(
                new ResourceLocation(MOD_ID, "mutation_probe"),
                new IContextualManaModifierProvider() {
                    @Override
                    public ManaContribution getContribution(EntityPlayer player) {
                        if (mutationProbeEnabled) {
                            try {
                                ManaApi.getManaService().restoreMana(player, 1, CAUSE);
                            } catch (IllegalStateException expected) {
                                MUTATION_REJECTIONS.incrementAndGet();
                            }
                        }
                        return ManaContribution.EMPTY;
                    }
                }
        );
        ManaTooltips.format(exampleContribution());
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new FixtureCommand(this));
    }

    public static ManaMutationResult set(EntityPlayer player, int target) {
        return ManaApi.getManaService().setCurrentMana(player, target, CAUSE);
    }

    public static ManaMutationResult restore(EntityPlayer player, int amount) {
        return ManaApi.getManaService().restoreMana(player, amount, CAUSE);
    }

    public static ManaMutationResult consume(EntityPlayer player, int amount) {
        return ManaApi.getManaService().consumeMana(player, amount, CAUSE);
    }

    public static ManaMutationFailure consumeFailure(EntityPlayer player, int amount) {
        return consume(player, amount).getFailure();
    }

    public static ManaMutationResult drain(EntityPlayer player, int amount) {
        return ManaApi.getManaService().drainMana(player, amount, CAUSE);
    }

    public static ManaPublicState inspect(EntityPlayer player) {
        IManaService mana = ManaApi.getManaService();
        ManaPublicState state = mana.getState(player);
        mana.getCurrentMana(player);
        mana.getMaximumMana(player);
        mana.getEffectiveRegeneration(player);
        mana.getEffectiveRegenerationLockoutTicks(player);
        mana.getRemainingRegenerationLockoutTicks(player);
        mana.isRegenerationEligible(player);
        mana.canRegenerateDuringPostCastLockout(player);
        mana.canRegenerateDuringContinuousCasting(player);
        return state;
    }

    public static double inspectEfficiency(EntityPlayer player, Spell spell) {
        return ManaApi.getManaService().getApplicableSpellEfficiency(player, spell);
    }

    public static double inspectCostMultiplier(EntityPlayer player, Spell spell) {
        return ManaApi.getManaService().getApplicableSpellCostMultiplier(player, spell);
    }

    public static ManaContribution exampleContribution() {
        return ManaContribution.builder()
                .flatMaximumMana(10)
                .maximumManaIncrease(25.0D)
                .maximumManaReduction(10.0D)
                .flatRegeneration(0.5D)
                .regenerationIncrease(20.0D)
                .regenerationReduction(5.0D)
                .flatLockoutIncreaseSeconds(0.5D)
                .flatLockoutReductionSeconds(0.25D)
                .lockoutIncrease(10.0D)
                .lockoutReduction(50.0D)
                .globalSpellEfficiency(5.0D)
                .elementSpellEfficiency(Element.FIRE, 10.0D)
                .globalSpellCostMultiplier(0.5D)
                .elementSpellCostMultiplier(Element.FIRE, 1.5D)
                .regenerateDuringPostCastLockout(true)
                .regenerateDuringContinuousCasting(true)
                .build();
    }

    @SubscribeEvent
    public void observe(ManaStateChangeEvent event) {
        EventKey key = new EventKey(event.getPlayer().getUniqueID(), event.getCause());
        EventObservation observation = stateEvents.computeIfAbsent(
                key, ignored -> new EventObservation()
        );
        observation.lastEvent = event;
        observation.count++;
    }

    public ManaStateChangeEvent getLastEvent(EntityPlayer player, ResourceLocation cause) {
        EventObservation observation = stateEvents.get(
                new EventKey(player.getUniqueID(), cause)
        );
        return observation == null ? null : observation.lastEvent;
    }

    public int getStateEventCount(EntityPlayer player, ResourceLocation cause) {
        EventObservation observation = stateEvents.get(
                new EventKey(player.getUniqueID(), cause)
        );
        return observation == null ? 0 : observation.count;
    }

    private static final class FixtureCommand extends CommandBase {

        private final ApiConsumerFixture fixture;

        private FixtureCommand(ApiConsumerFixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public String getName() {
            return "jawms-api-fixture";
        }

        @Override
        public String getUsage(ICommandSender sender) {
            return "/jawms-api-fixture inspect | explain | cycle | lockout | providers <status|registrations|preview-replace|preview-clear|invalidate|dirty|contextual|contextual-deferred|null|throwing|mutation|bauble> [true|false]";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 2;
        }

        @Override
        public void execute(MinecraftServer server, ICommandSender sender, String[] arguments)
                throws CommandException {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (arguments.length == 1 && "inspect".equalsIgnoreCase(arguments[0])) {
                ManaPublicState state = inspect(player);
                ConfiguredFlaskSnapshot flasks = ManaApi.getConfiguredFlaskSnapshot();
                sender.sendMessage(new TextComponentString(
                        "JAWMS API fixture state=" + state + ", api=" + ManaApi.API_VERSION
                                + ", flasks=" + flasks + "."
                ));
                return;
            }
            if (arguments.length == 1 && "explain".equalsIgnoreCase(arguments[0])) {
                int callsBefore = ITEM_CALLS.get() + CONTEXT_CALLS.get()
                        + FAILURE_CALLS.get();
                ManaProfileExplanation explanation = ManaApi.explainManaProfile(player);
                int callsAfter = ITEM_CALLS.get() + CONTEXT_CALLS.get()
                        + FAILURE_CALLS.get();
                sender.sendMessage(new TextComponentString(
                        "JAWMS explanation tick=" + explanation.getEvaluatedWorldTick()
                                + ", rulesRevision=" + explanation.getGameplayRulesRevision()
                                + ", maximum=" + explanation.getFinalMaximumMana()
                                + ", regeneration=" + explanation.getFinalRegeneration()
                                + ", lockoutTicks=" + explanation.getFinalLockoutTicks()
                                + ", postCast="
                                + explanation.canRegenerateDuringPostCastLockout()
                                + ", continuous="
                                + explanation.canRegenerateDuringContinuousCasting()
                                + ", sources=" + explanation.getContributions().size()
                                + ", providerCallsBefore=" + callsBefore
                                + ", providerCallsAfter=" + callsAfter + "."
                ));
                return;
            }
            if (arguments.length == 1 && "cycle".equalsIgnoreCase(arguments[0])) {
                int original = inspect(player).getCurrentMana();
                set(player, 0);
                restore(player, 2);
                consume(player, 1);
                drain(player, 1);
                set(player, original);
                ManaStateChangeEvent event = fixture.getLastEvent(player, CAUSE);
                sender.sendMessage(new TextComponentString(
                        "JAWMS API fixture cycle complete; restored=" + original
                                + ", lastCause=" + (event == null ? "none" : event.getCause()) + "."
                ));
                return;
            }
            if (arguments.length == 1 && "lockout".equalsIgnoreCase(arguments[0])) {
                IManaService service = ManaApi.getManaService();
                ManaPublicState before = service.getState(player);
                int eventsBefore = fixture.getStateEventCount(player, LOCKOUT_CAUSE);
                int providerCallsBefore = ITEM_CALLS.get() + CONTEXT_CALLS.get()
                        + FAILURE_CALLS.get();
                long appliedTicks = service.startRegenerationLockout(player, LOCKOUT_CAUSE);
                ManaPublicState after = service.getState(player);
                int providerCallsAfter = ITEM_CALLS.get() + CONTEXT_CALLS.get()
                        + FAILURE_CALLS.get();
                ManaStateChangeEvent event = fixture.getLastEvent(player, LOCKOUT_CAUSE);
                sender.sendMessage(new TextComponentString(
                        "JAWMS API fixture lockout appliedTicks=" + appliedTicks
                                + ", before=" + before
                                + ", after=" + after
                                + ", eventCountDelta="
                                + (fixture.getStateEventCount(player, LOCKOUT_CAUSE)
                                - eventsBefore)
                                + ", lastCause="
                                + (event == null ? "none" : event.getCause())
                                + ", providerCallsBefore=" + providerCallsBefore
                                + ", providerCallsAfter=" + providerCallsAfter + "."
                ));
                return;
            }
            if (arguments.length >= 2 && "providers".equalsIgnoreCase(arguments[0])) {
                String operation = arguments[1].toLowerCase();
                if ("invalidate".equals(operation) && arguments.length == 2) {
                    ManaApi.invalidateModifierCache(player);
                    sender.sendMessage(new TextComponentString("JAWMS provider cache invalidated."));
                    return;
                }
                if ("dirty".equals(operation) && arguments.length == 2) {
                    ManaApi.markModifierCacheDirty(player);
                    sender.sendMessage(new TextComponentString(
                            "JAWMS provider cache marked dirty for the next END tick."
                    ));
                    return;
                }
                if ("registrations".equals(operation) && arguments.length == 2) {
                    List<ManaProviderDescriptor> providers = ManaApi.getRegisteredProviders();
                    sender.sendMessage(new TextComponentString(
                            "providers frozen=" + ManaApi.isProviderRegistrationFrozen()
                                    + ", count=" + providers.size()
                                    + ", registrations=" + providers + "."
                    ));
                    return;
                }
                if ("status".equals(operation) && arguments.length == 2) {
                    ConfiguredFlaskSnapshot flasks = ManaApi.getConfiguredFlaskSnapshot();
                    sender.sendMessage(new TextComponentString(
                            "providers itemCalls=" + ITEM_CALLS.get()
                                    + ", lastItemContext=" + LAST_ITEM_CONTEXT.get()
                                    + ", contextCalls=" + CONTEXT_CALLS.get()
                                    + ", lastContextPlayer=" + LAST_CONTEXT_PLAYER.get()
                                    + ", lastContextTick=" + LAST_CONTEXT_TICK.get()
                                    + ", failureCalls=" + FAILURE_CALLS.get()
                                    + ", mutationRejections=" + MUTATION_REJECTIONS.get()
                                    + ", contextual=" + contextualEnabled
                                    + ", null=" + nullEnabled
                                    + ", throwing=" + throwingEnabled
                                    + ", mutation=" + mutationProbeEnabled
                                    + ", bauble=" + baubleProbeEnabled
                                    + ", rulesRevision="
                                    + (flasks.hasKnownRevision()
                                    ? Long.toString(flasks.getRevision()) : "unknown") + "."
                    ));
                    return;
                }
                if ("preview-replace".equals(operation) && arguments.length == 2) {
                    ManaApi.setItemProviderContributionPreview(
                            new ResourceLocation(MOD_ID, "stick_first"),
                            ManaContribution.builder().flatMaximumMana(3).build()
                    );
                    sender.sendMessage(new TextComponentString(
                            "provider preview replaced; itemCalls=" + ITEM_CALLS.get()
                                    + ", rulesRevision="
                                    + ManaApi.getConfiguredFlaskSnapshot().getRevision() + "."
                    ));
                    return;
                }
                if ("preview-clear".equals(operation) && arguments.length == 2) {
                    ManaApi.clearItemProviderContributionPreview(
                            new ResourceLocation(MOD_ID, "stick_first")
                    );
                    sender.sendMessage(new TextComponentString(
                            "provider preview cleared; itemCalls=" + ITEM_CALLS.get()
                                    + ", rulesRevision="
                                    + ManaApi.getConfiguredFlaskSnapshot().getRevision() + "."
                    ));
                    return;
                }
                if (arguments.length == 3) {
                    boolean enabled = parseFlag(arguments[2]);
                    boolean deferred = "contextual-deferred".equals(operation);
                    if (deferred) {
                        ManaApi.markModifierCacheDirty(player);
                        contextualEnabled = enabled;
                    } else if ("contextual".equals(operation)) {
                        contextualEnabled = enabled;
                    } else if ("null".equals(operation)) {
                        nullEnabled = enabled;
                    } else if ("throwing".equals(operation)) {
                        throwingEnabled = enabled;
                    } else if ("mutation".equals(operation)) {
                        mutationProbeEnabled = enabled;
                    } else if ("bauble".equals(operation)) {
                        baubleProbeEnabled = enabled;
                    } else {
                        throw new CommandException(getUsage(sender));
                    }
                    if (!deferred) {
                        ManaApi.invalidateModifierCache(player);
                    }
                    sender.sendMessage(new TextComponentString(operation + "=" + enabled + "."));
                    return;
                }
            }
            throw new CommandException(getUsage(sender));
        }

        private static boolean parseFlag(String value) throws CommandException {
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw new CommandException("Expected true or false.");
        }
    }

    private static final class EventKey {

        private final UUID playerId;
        private final ResourceLocation cause;

        private EventKey(UUID playerId, ResourceLocation cause) {
            this.playerId = playerId;
            this.cause = cause;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) return true;
            if (!(value instanceof EventKey)) return false;
            EventKey other = (EventKey) value;
            return playerId.equals(other.playerId) && cause.equals(other.cause);
        }

        @Override
        public int hashCode() {
            return Objects.hash(playerId, cause);
        }
    }

    private static final class EventObservation {

        private ManaStateChangeEvent lastEvent;
        private int count;
    }
}
