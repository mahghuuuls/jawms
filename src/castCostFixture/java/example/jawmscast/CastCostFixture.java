package example.jawmscast;

import com.mahghuuuls.jawms.api.*;
import electroblob.wizardry.constants.Tier;
import electroblob.wizardry.event.SpellCastEvent;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.registry.Spells;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import electroblob.wizardry.util.WandHelper;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Separately compiled public-only synthetic resource owner; never part of JAWMS shipping output. */
@Mod(modid = CastCostFixture.ID, name = "JAWMS Cast-Cost Fixture", version = "1.7",
        dependencies = "required-after:jawms@[1.2.0,)")
public final class CastCostFixture implements ICastCostParticipant {
    public static final String ID = "jawms_cast_fixture";
    private static final Logger LOG = LogManager.getLogger(ID);
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static Spell probeSpell;
    private static final ResourceLocation SETUP = new ResourceLocation(ID, "setup");

    @Mod.EventHandler public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        ManaApi.registerCastCostParticipant(new ResourceLocation(ID, "resource"), this);
    }
    @SubscribeEvent public void spells(RegistryEvent.Register<Spell> event) {
        probeSpell = new ProbeSpell();
        event.getRegistry().register(probeSpell);
    }
    @Mod.EventHandler public void starting(FMLServerStartingEvent event) {
        STATES.clear();
        event.registerServerCommand(new CastFixtureCommand());
    }
    @Mod.EventHandler public void stopped(FMLServerStoppedEvent event) { STATES.clear(); }

    @Override public CastCostPreparation prepare(CastAttemptContext context) {
        State state = STATES.get(context.getPlayer().getUniqueID());
        if (state == null || context.isContinuous()) return CastCostPreparation.notApplicable();
        state.preparations++;
        state.trace.add("prepare");
        state.context = "spell=" + context.getSpellId() + ",hand=" + context.getHand()
                + ",stack=" + context.getStack().getItem().getRegistryName()
                + ",cost=" + context.getAcceptedCost().getTotalOrPerSecondCost()
                + ",portion=" + context.getManaPortion() + ",mana=" + context.getPlannedMana()
                + ",hunger=" + context.getPlannedHunger() + ",attempt=" + context.getAttemptId();
        if (state.mode == Mode.ABSENT) return CastCostPreparation.notApplicable();
        if (state.mode == Mode.REFUSE || (state.mode != Mode.FREE && state.balance - state.claimed < 4)) {
            return CastCostPreparation.refused("fixture_refusal");
        }
        final int amount = state.mode == Mode.FREE ? 0 : 4;
        final UUID ownerId = context.getPlayer().getUniqueID();
        state.claimed += amount;
        return CastCostPreparation.accepted(new ICastCostReservation() {
            private boolean open = true;
            public boolean isValid() {
                return open && STATES.get(ownerId) == state
                        && state.balance >= state.claimed;
            }
            public CastCostSettlement settle() {
                if (!isValid()) return CastCostSettlement.invalidated();
                open = false;
                state.claimed -= amount;
                state.balance -= amount;
                state.settlements++;
                if (amount > 0) state.delayStarts++;
                state.trace.add("provider_settle");
                return amount == 0 ? CastCostSettlement.free(false) : CastCostSettlement.paid(amount, false);
            }
            public void release(CastCostReleaseReason reason) {
                if (!open) return;
                open = false;
                state.claimed -= amount;
                state.releases++;
                state.trace.add("release:" + reason);
            }
        });
    }

    @Override public void onAttemptResolved(CastAttemptOutcome outcome) {
        State state = STATES.get(outcome.getPlayerId());
        if (state == null) return;
        state.notifications++;
        state.trace.add("resolved:" + outcome.getStatus());
        state.last = outcome.getStatus() + ",paidMana=" + outcome.getPaidMana()
                + ",paidHunger=" + outcome.getPaidHunger();
    }

    @SubscribeEvent public void post(SpellCastEvent.Post event) {
        if (event.getSource() != SpellCastEvent.Source.WAND || event.getCaster().world.isRemote) return;
        State state = STATES.get(event.getCaster().getUniqueID());
        if (state == null) return;
        state.posts++;
        state.trace.add("wizardry_post:balance=" + state.balance + ":mana="
                + ManaApi.getManaService().getCurrentMana((EntityPlayer) event.getCaster()));
        inspect((EntityPlayer) event.getCaster(), state);
    }
    @SubscribeEvent public void mana(ManaStateChangeEvent event) {
        State state = STATES.get(event.getPlayer().getUniqueID());
        if (state != null && ManaCauses.SPELL_COST.equals(event.getCause())) {
            state.trace.add("mana_commit:providerBalance=" + state.balance);
        }
    }

    private static State state(EntityPlayer player) {
        return STATES.computeIfAbsent(player.getUniqueID(), id -> new State());
    }
    private static void inspect(EntityPlayer player, State state) {
        LOG.info("CAST_FIXTURE player={} mode={} balance={} claimed={} prepare={} effect={} settle={} release={} delay={} post={} notify={} mana={} hunger={} cooldown={} progression={} last={} context={} trace={}",
                player.getUniqueID(), state.mode, state.balance, state.claimed, state.preparations,
                state.effects, state.settlements, state.releases, state.delayStarts, state.posts,
                state.notifications, ManaApi.getManaService().getCurrentMana(player),
                player.getFoodStats().getFoodLevel(), WandHelper.getCurrentCooldown(player.getHeldItemMainhand()),
                WandHelper.getProgression(player.getHeldItemMainhand()), state.last, state.context, state.trace);
    }
    private enum Mode { ABSENT, ALLOW, REFUSE, FREE }
    private static final class State {
        Mode mode = Mode.ABSENT;
        int balance = 40, claimed, preparations, effects, settlements, releases, delayStarts, posts, notifications;
        String last = "none", context = "none", probe = "true";
        final List<String> trace = new ArrayList<>();
    }

    public static final class ProbeSpell extends Spell {
        ProbeSpell() { super(ID, "probe", EnumAction.BOW, false); }
        @Override public boolean requiresPacket() { return false; }
        @Override public boolean cast(World world, EntityPlayer caster, EnumHand hand,
                int ticks, SpellModifiers modifiers) {
            if (world.isRemote) return false;
            State state = state(caster);
            state.effects++;
            state.trace.add("effect:" + state.probe);
            if ("throw".equals(state.probe)) throw new ProbeFailure();
            if ("false".equals(state.probe)) return false;
            caster.sendStatusMessage(new TextComponentString("Cast fixture: effect reached"), true);
            return true;
        }
    }
    private static final class ProbeFailure extends RuntimeException { }

    public static final class CastFixtureCommand extends CommandBase {
        @Override public String getName() { return "jawms-cast-fixture"; }
        @Override public String getUsage(ICommandSender sender) {
            return "/jawms-cast-fixture setup <absent|allow|refuse|free> [base] | inspect | probe <false|throw> | teardown";
        }
        @Override public int getRequiredPermissionLevel() { return 2; }
        @Override public void execute(MinecraftServer server, ICommandSender sender, String[] args)
                throws CommandException {
            if (!(sender.getCommandSenderEntity() instanceof EntityPlayer)) {
                throw new CommandException("This fixture needs a player command sender.");
            }
            EntityPlayer player = (EntityPlayer) sender.getCommandSenderEntity();
            if (args.length == 0) throw new CommandException(getUsage(sender));
            if ("setup".equals(args[0]) && (args.length == 2 || args.length == 3)) {
                Mode mode;
                try { mode = Mode.valueOf(args[1].toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException invalid) { throw new CommandException(getUsage(sender)); }
                State state = new State(); state.mode = mode;
                STATES.put(player.getUniqueID(), state);
                player.resetActiveHand();
                Spell selected = args.length == 3 && "base".equals(args[2]) ? Spells.light : probeSpell;
                ItemStack wand = new ItemStack(ItemWand.getWand(Tier.NOVICE, null));
                Spell[] spells = new Spell[ItemWand.BASE_SPELL_SLOTS];
                java.util.Arrays.fill(spells, selected);
                WandHelper.setSpells(wand, spells);
                WandHelper.setCurrentCooldown(wand, 0);
                WandHelper.setProgression(wand, 0);
                player.setHeldItem(EnumHand.MAIN_HAND, wand);
                ManaApi.getManaService().setCurrentMana(player, 50, SETUP);
                player.getFoodStats().setFoodLevel(20);
                inspect(player, state);
            } else if ("inspect".equals(args[0]) && args.length == 1) {
                inspect(player, state(player));
            } else if ("teardown".equals(args[0]) && args.length == 1) {
                STATES.remove(player.getUniqueID());
            } else if ("probe".equals(args[0]) && args.length == 2
                    && ("false".equals(args[1]) || "throw".equals(args[1]))) {
                State state = state(player);
                ItemStack stack = player.getHeldItemMainhand();
                if (!(stack.getItem() instanceof ItemWand) || WandHelper.getCurrentSpell(stack) != probeSpell) {
                    throw new CommandException("Run fixture setup with the synthetic spell first.");
                }
                ItemWand wand = (ItemWand) stack.getItem();
                state.probe = args[1];
                try {
                    SpellModifiers modifiers = wand.calculateModifiers(stack, player, probeSpell);
                    if (wand.canCast(stack, probeSpell, player, EnumHand.MAIN_HAND, 0, modifiers)) {
                        wand.cast(stack, probeSpell, player, EnumHand.MAIN_HAND, 0, modifiers);
                    }
                } catch (ProbeFailure expected) {
                    LOG.info("CAST_FIXTURE intentional effect exception caught at command boundary");
                } finally { state.probe = "true"; inspect(player, state); }
            } else throw new CommandException(getUsage(sender));
        }
    }
}
