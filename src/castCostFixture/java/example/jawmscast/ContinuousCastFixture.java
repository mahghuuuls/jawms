package example.jawmscast;

import com.mahghuuuls.jawms.api.*;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.GameType;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.*;

/** Public-API-only resource and harmless effects. Counters are explicitly side-keyed. */
public final class ContinuousCastFixture {
    private static final Logger LOG = LogManager.getLogger(CastCostFixture.ID);
    private static final Map<UUID, State> SERVER = new HashMap<>();
    private static final Map<UUID, Counts> CLIENT = new HashMap<>();
    private static final List<Spell> SPELLS = new ArrayList<>();
    private static final ResourceLocation SETUP = new ResourceLocation(CastCostFixture.ID, "continuous_setup");
    private static SimpleNetworkWrapper channel;
    private ContinuousCastFixture() {}

    static void initialize() {
        MinecraftForge.EVENT_BUS.register(new ContinuousCastFixture());
        channel = NetworkRegistry.INSTANCE.newSimpleChannel("jawms_cast_counts");
        channel.registerMessage(CounterHandler.class, CounterMessage.class, 0, Side.CLIENT);
    }
    static void registerSpells(RegistryEvent.Register<Spell> event) {
        // Construction consumes Wizardry network IDs; preInit support must not run it before built-in registration.
        for (String name : Arrays.asList("stream", "slow_stream", "free_stream", "charging_stream")) {
            Spell spell = new Stream(name);
            event.getRegistry().register(spell);
            SPELLS.add(spell);
        }
    }
    static void starting(FMLServerStartingEvent event) { SERVER.clear(); event.registerServerCommand(new ContinuousFixtureCommand()); }
    static void clear() { SERVER.clear(); }

    static CastCostPreparation prepare(CastAttemptContext context) {
        State state = SERVER.get(context.getPlayer().getUniqueID());
        if (state == null) return CastCostPreparation.notApplicable();
        state.prepares++;
        state.lastContext = "use=" + context.getUseId() + ",attempt=" + context.getAttemptId()
                + ",hand=" + context.getHand() + ",spell=" + context.getSpellId()
                + ",creative=" + context.isCreative() + ",portion=" + context.getManaPortion()
                + ",localMana=" + context.getPlannedMana() + ",rate=" + context.getAcceptedCost().getTotalOrPerSecondCost();
        if (EnumSet.of(Mode.INAPPLICABLE, Mode.TICK_INTERRUPT, Mode.CREATIVE_INTERRUPT).contains(state.mode))
            return CastCostPreparation.notApplicable();
        LOG.info("CONTINUOUS_FIXTURE prepare estimateTerminal={} {}", state.mode == Mode.PREDICTION || state.balance - state.claimed <= 3, state.lastContext);
        boolean child = state.depth > 0;
        if (state.mode == Mode.REFUSED || state.balance - state.claimed <= 0) return CastCostPreparation.refused("fixture_empty");
        int amount = Math.min(3, state.balance - state.claimed);
        // Synthetic child has its own partial two-unit debit; it must not stop its physical parent.
        if (state.mode == Mode.SYNTHETIC && child) amount = 2;
        final int debit = amount;
        state.claimed += debit;
        int generation = state.invalidations;
        return CastCostPreparation.accepted(new ICastCostReservation() {
            boolean open = true;
            public boolean isValid() {
                return open && SERVER.get(context.getPlayer().getUniqueID()) == state
                        && generation == state.invalidations && state.balance >= state.claimed;
            }
            public CastCostSettlement settle() {
                if (!isValid()) return CastCostSettlement.invalidated();
                open = false; state.claimed -= debit; state.balance -= debit;
                state.settles++; state.paid += debit;
                boolean terminal = (state.mode == Mode.SYNTHETIC && child) || state.balance == 0;
                LOG.info("CONTINUOUS_FIXTURE settle amount={} terminal={} child={} {}", debit, terminal, child, state.lastContext);
                return CastCostSettlement.paid(debit, terminal);
            }
            public void release(CastCostReleaseReason reason) {
                if (open) { open = false; state.claimed -= debit; state.releases++; }
            }
        });
    }
    static void resolved(CastAttemptOutcome outcome) {
        State state = SERVER.get(outcome.getPlayerId());
        if (state == null || !SPELLS.stream().anyMatch(s -> s.getRegistryName().equals(outcome.getSpellId()))) return;
        state.resolutions++;
        state.lastUse = outcome.getUseId(); state.lastAttempt = outcome.getAttemptId();
        state.outcomeMana += outcome.getPaidMana(); state.outcomeHunger += outcome.getPaidHunger();
        state.lastStatus = outcome.getStatus().name();
        state.hand = outcome.getHand();
        LOG.info("CONTINUOUS_FIXTURE resolved status={} stop={} mana={} hunger={} use={} attempt={} participants={}",
                outcome.getStatus(), outcome.mustStopAfterSuccess(), outcome.getPaidMana(), outcome.getPaidHunger(),
                outcome.getUseId(), outcome.getAttemptId(), outcome.getParticipants().size());
    }
    @SubscribeEvent public void finish(SpellCastEvent.Finish event) {
        if (event.getCaster() instanceof EntityPlayer && SPELLS.contains(event.getSpell())) {
            EntityPlayer player = (EntityPlayer) event.getCaster();
            counts(player).finishEvents++;
            LOG.info("CONTINUOUS_FIXTURE actual_finish_event side={} count={} modifierSignature={} cooldownFactor={}",
                    player.world.isRemote ? "CLIENT" : "SERVER", event.getCount(), event.getModifiers().getModifiers().hashCode(),
                    event.getModifiers().get(electroblob.wizardry.registry.WizardryItems.cooldown_upgrade));
            report(player);
        }
    }
    @SubscribeEvent public void noFinish(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntityLiving().world.isRemote || !(event.getEntityLiving() instanceof EntityPlayer)) return;
        State state = SERVER.get(event.getEntityLiving().getUniqueID());
        if (state != null && state.mode == Mode.NOFINISH && SPELLS.contains(WandHelper.getCurrentSpell(event.getItem()))) {
            event.setCanceled(true);
            LOG.info("CONTINUOUS_FIXTURE original stop callback cancelled on SERVER");
        }
    }
    @SubscribeEvent public void interruptTick(SpellCastEvent.Tick event) {
        if (event.getSource() != SpellCastEvent.Source.WAND || !(event.getCaster() instanceof EntityPlayer)
                || event.getCaster().world.isRemote || !SPELLS.contains(event.getSpell())) return;
        State state = SERVER.get(event.getCaster().getUniqueID());
        if (state != null && EnumSet.of(Mode.TICK_INTERRUPT, Mode.CREATIVE_INTERRUPT).contains(state.mode)
                && state.counts.effects >= 2) {
            event.setCanceled(true); // Actual ordinary dependency boundary, before the next effect/claim.
            LOG.info("CONTINUOUS_FIXTURE original Tick cancelled on SERVER after effects={} mode={}", state.counts.effects, state.mode);
        }
    }
    @SubscribeEvent public void cooldown(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        EntityPlayer player = event.player;
        if (player.world.isRemote ? !CLIENT.containsKey(player.getUniqueID()) : !SERVER.containsKey(player.getUniqueID())) return;
        Counts c = counts(player);
        int value = Math.max(WandHelper.getCurrentCooldown(player.getHeldItemMainhand()), WandHelper.getCurrentCooldown(player.getHeldItemOffhand()));
        if (c.finishBodies > 0 && value > c.previousCooldown) { c.cooldownIncreases++; report(player); }
        c.previousCooldown = value;
    }
    private static Counts counts(EntityPlayer player) {
        if (player.world.isRemote) return CLIENT.computeIfAbsent(player.getUniqueID(), id -> new Counts());
        State state = SERVER.get(player.getUniqueID());
        return state == null ? new Counts() : state.counts;
    }
    private static void report(EntityPlayer player) {
        Counts c = counts(player);
        LOG.info("CONTINUOUS_FIXTURE side={} player={} effects={} childEffects={} finishEvents={} finishBodies={} cooldownIncreases={} cooldown={}",
                player.world.isRemote ? "CLIENT" : "SERVER", player.getUniqueID(), c.effects, c.childEffects,
                c.finishEvents, c.finishBodies, c.cooldownIncreases, Math.max(WandHelper.getCurrentCooldown(player.getHeldItemMainhand()), WandHelper.getCurrentCooldown(player.getHeldItemOffhand())));
    }
    /** Immutable independent readback for optional development orchestration; never resets a counter. */
    public static Map<String, String> snapshot(EntityPlayer player) {
        Counts c = player.world.isRemote ? CLIENT.get(player.getUniqueID())
                : SERVER.containsKey(player.getUniqueID()) ? SERVER.get(player.getUniqueID()).counts : null;
        if (c == null) throw new IllegalStateException("Continuous fixture has no established counter baseline");
        Map<String, String> result = new LinkedHashMap<>();
        result.put("player", player.getUniqueID().toString());
        result.put("effects", Integer.toString(c.effects)); result.put("childEffects", Integer.toString(c.childEffects));
        result.put("finishEvents", Integer.toString(c.finishEvents)); result.put("finishBodies", Integer.toString(c.finishBodies));
        result.put("cooldownIncreases", Integer.toString(c.cooldownIncreases));
        if (!player.world.isRemote) {
            State s = SERVER.get(player.getUniqueID());
            result.put("mode", s.mode.name()); result.put("units", Integer.toString(s.balance));
            result.put("claimed", Integer.toString(s.claimed)); result.put("paid", Integer.toString(s.paid));
            result.put("prepare", Integer.toString(s.prepares)); result.put("settle", Integer.toString(s.settles));
            result.put("resolved", Integer.toString(s.resolutions)); result.put("release", Integer.toString(s.releases));
            result.put("mana", Integer.toString(ManaApi.getManaService().getCurrentMana(player)));
            result.put("use", s.lastUse); result.put("attempt", s.lastAttempt);
            result.put("outcomeMana", Integer.toString(s.outcomeMana)); result.put("outcomeHunger", Integer.toString(s.outcomeHunger));
            result.put("status", s.lastStatus);
            result.put("hand", s.hand == EnumHand.OFF_HAND ? "off" : "main");
        }
        return Collections.unmodifiableMap(result);
    }
    private enum Mode { NORMAL, TERMINAL, CREATIVE, ZERO, ZERO_PORTION, PREDICTION, INAPPLICABLE, REFUSED, INVALIDATED, CHARGEUP, SYNTHETIC, NOFINISH, TICK_INTERRUPT, CREATIVE_INTERRUPT }
    private static final class Counts { int effects, childEffects, finishEvents, finishBodies, previousCooldown, cooldownIncreases; }
    private static final class State {
        Mode mode; int balance, claimed, paid, prepares, settles, releases, resolutions, depth, invalidations;
        boolean childInvoked;
        String lastContext = "none";
        String lastUse = "0", lastAttempt = "0";
        int outcomeMana, outcomeHunger;
        String lastStatus = "NONE";
        EnumHand hand;
        final Counts counts = new Counts();
    }
    private static final class Stream extends Spell {
        Stream(String name) { super(CastCostFixture.ID, name, EnumAction.BOW, true); }
        @Override public boolean requiresPacket() { return false; }
        @Override public boolean cast(World world, EntityPlayer caster, EnumHand hand, int tick, SpellModifiers modifiers) {
            Counts c = counts(caster);
            State state = world.isRemote ? null : SERVER.get(caster.getUniqueID());
            if (state != null && state.depth > 0) c.childEffects++; else c.effects++;
            if (state != null && state.mode == Mode.INVALIDATED) state.invalidations++;
            if (state != null && state.mode == Mode.SYNTHETIC && !state.childInvoked) {
                state.childInvoked = true;
                state.depth++;
                try {
                    ItemStack stack = caster.getHeldItem(hand);
                    ((ItemWand) stack.getItem()).cast(stack, this, caster, hand, tick, modifiers.copy());
                } finally { state.depth--; }
            }
            caster.sendStatusMessage(new TextComponentString("Continuous fixture: effect reached"), true);
            report(caster);
            return true;
        }
        @Override public void finishCasting(World world, EntityLivingBase caster, double x, double y, double z,
                EnumFacing facing, int tick, SpellModifiers modifiers) {
            if (caster instanceof EntityPlayer) {
                counts((EntityPlayer) caster).finishBodies++;
                LOG.info("CONTINUOUS_FIXTURE actual_finish_body side={} tick={} modifierSignature={} cooldownFactor={}",
                        world.isRemote ? "CLIENT" : "SERVER", tick, modifiers.getModifiers().hashCode(),
                        modifiers.get(electroblob.wizardry.registry.WizardryItems.cooldown_upgrade));
                caster.sendMessage(new TextComponentString("Continuous fixture: finish reached"));
                report((EntityPlayer) caster);
            }
        }
    }
    public static final class ContinuousFixtureCommand extends CommandBase {
        @Override public String getName() { return "jawms-continuous-fixture"; }
        @Override public String getUsage(ICommandSender sender) { return "/jawms-continuous-fixture setup <mode> [main|off] | inspect | teardown"; }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
            if (!(sender.getCommandSenderEntity() instanceof EntityPlayerMP)) throw new CommandException("Player sender required");
            EntityPlayerMP player = (EntityPlayerMP) sender.getCommandSenderEntity();
            if (args.length == 0) throw new CommandException(getUsage(sender));
            if ("setup".equals(args[0]) && (args.length == 2 || args.length == 3)) {
                if (player.isHandActive()) throw new CommandException("Release use before fixture setup");
                State state = new State();
                try { state.mode = Mode.valueOf(args[1].toUpperCase(Locale.ROOT).replace('-', '_')); }
                catch (IllegalArgumentException invalid) { throw new CommandException(getUsage(sender)); }
                EnumHand hand = args.length == 3 && "off".equals(args[2]) ? EnumHand.OFF_HAND : EnumHand.MAIN_HAND;
                state.hand = hand;
                if (args.length == 3 && !"off".equals(args[2]) && !"main".equals(args[2])) throw new CommandException(getUsage(sender));
                state.balance = state.mode == Mode.ZERO_PORTION ? 5
                        : EnumSet.of(Mode.TERMINAL, Mode.CREATIVE, Mode.ZERO).contains(state.mode) ? 2 : 1_000_000;
                Spell selected = SPELLS.get(state.mode == Mode.ZERO_PORTION ? 1 : state.mode == Mode.ZERO ? 2 : state.mode == Mode.CHARGEUP ? 3 : 0);
                if (selected.getRegistryName() == null || selected.getCost() != (state.mode == Mode.ZERO ? 0 : state.mode == Mode.ZERO_PORTION ? 1 : 4))
                    throw new CommandException("Continuous spell properties not discovered; stop validation");
                if (ManaApi.getManaService().getMaximumMana(player) != 100)
                    throw new CommandException("MV-02 requires the retained maximum-mana-100 configuration; stop setup");
                SERVER.put(player.getUniqueID(), state);
                player.setGameType(state.mode == Mode.CREATIVE || state.mode == Mode.CREATIVE_INTERRUPT ? GameType.CREATIVE : GameType.SURVIVAL);
                ItemStack wand = new ItemStack(ItemWand.getWand(Tier.NOVICE, null));
                Spell[] spells = new Spell[ItemWand.BASE_SPELL_SLOTS]; Arrays.fill(spells, selected);
                WandHelper.setSpells(wand, spells); WandHelper.setCurrentCooldown(wand, 0);
                player.setHeldItem(EnumHand.MAIN_HAND, ItemStack.EMPTY); player.setHeldItem(EnumHand.OFF_HAND, ItemStack.EMPTY);
                player.setHeldItem(hand, wand);
                ManaApi.getManaService().setCurrentMana(player, 100, SETUP); player.getFoodStats().setFoodLevel(20);
                channel.sendTo(new CounterMessage(player.getUniqueID(), true), player);
                LOG.info("CONTINUOUS_FIXTURE READY mode={} hand={} spell={} cost={} charge={} units={} registrationOff={}",
                        state.mode, hand, selected.getRegistryName(), selected.getCost(), selected.getChargeup(), state.balance,
                        Boolean.getBoolean("jawms.castFixtureRegistrationOff"));
            } else if ("teardown".equals(args[0]) && args.length == 1) {
                if (player.isHandActive()) throw new CommandException("Release use before teardown");
                SERVER.remove(player.getUniqueID()); channel.sendTo(new CounterMessage(player.getUniqueID(), true), player);
            } else if (!"inspect".equals(args[0]) || args.length != 1) throw new CommandException(getUsage(sender));
            State state = SERVER.get(player.getUniqueID());
            if (state != null) LOG.info("CONTINUOUS_FIXTURE inspect units={} claimed={} paid={} prepare={} settle={} release={} resolved={} mana={} context={}",
                    state.balance, state.claimed, state.paid, state.prepares, state.settles, state.releases,
                    state.resolutions, ManaApi.getManaService().getCurrentMana(player), state.lastContext);
            report(player); channel.sendTo(new CounterMessage(player.getUniqueID(), false), player);
        }
    }
    public static final class CounterMessage implements IMessage {
        UUID player; boolean reset;
        public CounterMessage() {}
        CounterMessage(UUID player, boolean reset) { this.player = player; this.reset = reset; }
        public void toBytes(ByteBuf out) { out.writeLong(player.getMostSignificantBits()); out.writeLong(player.getLeastSignificantBits()); out.writeBoolean(reset); }
        public void fromBytes(ByteBuf in) { player = new UUID(in.readLong(), in.readLong()); reset = in.readBoolean(); }
    }
    public static final class CounterHandler implements IMessageHandler<CounterMessage, IMessage> {
        public IMessage onMessage(CounterMessage message, MessageContext context) {
            FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() -> {
                if (message.reset) CLIENT.put(message.player, new Counts());
                Counts c = CLIENT.get(message.player);
                LOG.info("CONTINUOUS_FIXTURE CLIENT_READBACK player={} reset={} effects={} finishEvents={} finishBodies={}",
                        message.player, message.reset, c == null ? 0 : c.effects, c == null ? 0 : c.finishEvents, c == null ? 0 : c.finishBodies);
            });
            return null;
        }
    }
}
