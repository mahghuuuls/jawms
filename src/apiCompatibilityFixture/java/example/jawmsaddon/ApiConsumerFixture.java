package example.jawmsaddon;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaPublicState;
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

import java.util.concurrent.atomic.AtomicInteger;
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
    private static final AtomicInteger ITEM_CALLS = new AtomicInteger();
    private static final AtomicInteger CONTEXT_CALLS = new AtomicInteger();
    private static final AtomicInteger FAILURE_CALLS = new AtomicInteger();
    private static final AtomicInteger MUTATION_REJECTIONS = new AtomicInteger();
    private static final AtomicReference<String> LAST_ITEM_CONTEXT =
            new AtomicReference<>("none");
    private static volatile boolean contextualEnabled;
    private static volatile boolean nullEnabled;
    private static volatile boolean throwingEnabled;
    private static volatile boolean mutationProbeEnabled;
    private static volatile boolean baubleProbeEnabled;

    private ManaStateChangeEvent lastEvent;

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
                }
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
                .flatLockoutSeconds(-0.25D)
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
        lastEvent = event;
    }

    public ManaStateChangeEvent getLastEvent() {
        return lastEvent;
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
            return "/jawms-api-fixture inspect | cycle | providers <status|contextual|null|throwing|mutation|bauble|invalidate> [true|false]";
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
                sender.sendMessage(new TextComponentString(
                        "JAWMS API fixture state=" + state + ", api=" + ManaApi.API_VERSION + "."
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
                ManaStateChangeEvent event = fixture.getLastEvent();
                sender.sendMessage(new TextComponentString(
                        "JAWMS API fixture cycle complete; restored=" + original
                                + ", lastCause=" + (event == null ? "none" : event.getCause()) + "."
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
                if ("status".equals(operation) && arguments.length == 2) {
                    sender.sendMessage(new TextComponentString(
                            "providers itemCalls=" + ITEM_CALLS.get()
                                    + ", lastItemContext=" + LAST_ITEM_CONTEXT.get()
                                    + ", contextCalls=" + CONTEXT_CALLS.get()
                                    + ", failureCalls=" + FAILURE_CALLS.get()
                                    + ", mutationRejections=" + MUTATION_REJECTIONS.get()
                                    + ", contextual=" + contextualEnabled
                                    + ", null=" + nullEnabled
                                    + ", throwing=" + throwingEnabled
                                    + ", mutation=" + mutationProbeEnabled
                                    + ", bauble=" + baubleProbeEnabled + "."
                    ));
                    return;
                }
                if (arguments.length == 3) {
                    boolean enabled = parseFlag(arguments[2]);
                    if ("contextual".equals(operation)) {
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
                    ManaApi.invalidateModifierCache(player);
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
}
