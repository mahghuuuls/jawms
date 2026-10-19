package com.mahghuuuls.jawms.internal.casting;

import electroblob.wizardry.spell.Spell;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Exercises the production owner without starting Forge networking or a game. */
class ParticipantUseBindingTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @AfterEach void clear() { ParticipantUseService.clear(); }

    @Test void replacedStackCannotAcquireTheBoundUseEvenOnNotApplicableFallback() throws Exception {
        Fixture f = new Fixture();
        ParticipantUseService.enterOrigin(f.player, f.stack, EnumHand.MAIN_HAND, false);
        ParticipantUseService.Scope original = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
        assertTrue(original.valid()); assertTrue(original.permitsLegacy());
        ParticipantUseService.exitCast(original); ParticipantUseService.exitOrigin();
        ItemStack replacement = f.stack.copy();
        when(f.player.getHeldItem(EnumHand.MAIN_HAND)).thenReturn(replacement);
        ParticipantUseService.enterOrigin(f.player, replacement, EnumHand.MAIN_HAND, false);
        ParticipantUseService.Scope changed = ParticipantUseService.enterCast(f.player, replacement, f.spell, EnumHand.MAIN_HAND);
        assertFalse(changed.valid()); assertFalse(changed.permitsLegacy());
        // clear() retires this deliberately invalid draft scope without sending a real packet.
    }

    @Test void syntheticChildFailureCannotTakeOverThePhysicalParentsClose() throws Exception {
        Fixture f = new Fixture();
        ParticipantUseService.enterOrigin(f.player, f.stack, EnumHand.MAIN_HAND, false);
        ParticipantUseService.Scope parent = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
        ParticipantUseService.Scope child = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
        assertTrue(ParticipantUseService.syntheticCast());
        assertNotEquals(parent.id(), child.id());
        assertFalse(ParticipantUseService.interrupt(f.player));
        assertFalse(ParticipantUseService.rejectStart(f.player, EnumHand.MAIN_HAND, f.stack, f.spell));
        assertTrue(f.state.isOpen(f.use));
        ParticipantUseService.exitCast(child);
        assertFalse(ParticipantUseService.syntheticCast()); assertTrue(parent.valid());
        ParticipantUseService.exitCast(parent); ParticipantUseService.exitOrigin();
    }

    @Test void reentrantFinishCannotCancelOrConsumeTheOwningRelease() throws Exception {
        Fixture f = new Fixture();
        Constructor<?> constructor = ParticipantUseService.ReleaseFrame.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Object release = constructor.newInstance(f.connection, f.use);
        set(f.connection, "release", release);
        assertTrue(ParticipantUseService.permitsFinish(f.player, f.stack, EnumHand.MAIN_HAND, f.spell));
        assertFalse(ParticipantUseService.permitsFinish(f.player, f.stack, EnumHand.MAIN_HAND, f.spell));
        assertTrue(f.state.isOpen(f.use));
        assertFalse((boolean) get(release, "finished"));
        ParticipantUseService.finishedBody(f.player, f.stack.copy(), f.spell);
        assertFalse((boolean) get(release, "finished"));
        ParticipantUseService.finishedBody(f.player, f.stack, f.spell);
        assertTrue((boolean) get(release, "finished"));
        ParticipantUseService.endFinish(f.player);
        assertFalse((boolean) get(release, "callbackActive"));
    }
    @Test void closedContinuousUseCannotLabelLaterDistinctPhysicalDiscreteGestures() throws Exception {
        Fixture f = new Fixture();
        ParticipantUseService.enterOrigin(f.player, f.stack, EnumHand.MAIN_HAND, false);
        ParticipantUseService.Scope continuous = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
        String oldId = continuous.id();
        ParticipantUseService.exitCast(continuous); ParticipantUseService.exitOrigin();
        f.state.close(f.use, ParticipantUseMessages.CloseCause.NORMAL_NO_FINISH);
        f.state.release(f.state.epoch(), f.use.intent);
        Field continuousFlag = Spell.class.getField("isContinuous"); continuousFlag.setAccessible(true); continuousFlag.setBoolean(f.spell, false);
        String prior = oldId;
        for (int gesture = 0; gesture < 2; gesture++) {
            ParticipantUseService.enterOrigin(f.player, f.stack, EnumHand.MAIN_HAND, true);
            ParticipantUseService.Scope discrete = ParticipantUseService.enterCast(f.player, f.stack, f.spell, EnumHand.MAIN_HAND);
            try { assertNotEquals(oldId, discrete.id()); assertNotEquals(prior, discrete.id()); prior = discrete.id(); }
            finally { ParticipantUseService.exitCast(discrete); ParticipantUseService.exitOrigin(); }
        }
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static final class Fixture {
        final EntityPlayerMP player = mock(EntityPlayerMP.class);
        final ItemStack stack = new ItemStack(Items.STICK);
        final Spell spell = mock(Spell.class);
        final ParticipantUseState state;
        final ParticipantUseState.Use use;
        final Object connection;
        @SuppressWarnings("unchecked") Fixture() throws Exception {
            player.world = mock(WorldServer.class);
            player.inventory = new net.minecraft.entity.player.InventoryPlayer(player);
            player.inventory.mainInventory.set(0, stack);
            player.connection = mock(NetHandlerPlayServer.class);
            player.connection.player = player;
            when(player.getUniqueID()).thenReturn(UUID.randomUUID());
            when(player.getHeldItem(EnumHand.MAIN_HAND)).thenReturn(stack);
            net.minecraftforge.registries.IRegistryDelegate<Spell> delegate = mock(net.minecraftforge.registries.IRegistryDelegate.class);
            when(delegate.name()).thenReturn(new ResourceLocation("fixture", "continuous"));
            Field delegateField = net.minecraftforge.registries.IForgeRegistryEntry.Impl.class.getField("delegate");
            delegateField.setAccessible(true); delegateField.set(spell, delegate);
            Field continuous = Spell.class.getField("isContinuous");
            continuous.setAccessible(true); continuous.setBoolean(spell, true);
            Class<?> type = Class.forName(ParticipantUseService.class.getName() + "$Connection");
            Constructor<?> constructor = type.getDeclaredConstructor(EntityPlayerMP.class); constructor.setAccessible(true);
            connection = constructor.newInstance(player);
            state = (ParticipantUseState) get(connection, "state");
            state.intent(state.epoch(), 1, EnumHand.MAIN_HAND, spell.getRegistryName());
            use = state.bind(EnumHand.MAIN_HAND, spell.getRegistryName(), stack, 7);
            Field connections = ParticipantUseService.class.getDeclaredField("CONNECTIONS"); connections.setAccessible(true);
            ((Map<Object, Object>) connections.get(null)).put(player.connection, connection);
            use.generation = ParticipantUseService.wands(player).bind(player, EnumHand.MAIN_HAND, stack, use.intent, use.id);
        }
    }
}
