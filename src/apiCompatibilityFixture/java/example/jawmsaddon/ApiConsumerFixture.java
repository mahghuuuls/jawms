package example.jawmsaddon;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.constants.Element;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

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
    private static final ResourceLocation CAUSE =
            new ResourceLocation("exampleaddon", "fixture_operation");

    private ManaStateChangeEvent lastEvent;

    @Mod.EventHandler
    public void initialize(FMLInitializationEvent event) {
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
            return "/jawms-api-fixture inspect | cycle";
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
            throw new CommandException(getUsage(sender));
        }
    }
}
