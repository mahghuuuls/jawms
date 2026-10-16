package com.mahghuuuls.jawms.testfixture;

import com.mahghuuuls.jawms.internal.mana.ManaCapability;
import com.mahghuuuls.jawms.internal.mana.ManaState;
import com.mahghuuuls.jawms.internal.modifier.ManaProviderRegistry;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FoodStats;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ManaTestFixtureCommandTest {

    private final EntityPlayerMP player = mock(EntityPlayerMP.class);
    private final WorldServer world = mock(WorldServer.class);
    private final UUID playerId = UUID.randomUUID();

    @AfterEach
    void clearFixtureState() throws Exception {
        pausedPlayers().remove(playerId);
        ManaProviderRegistry.instance().remove(player);
    }

    @Test
    void directPlayerCanPauseAndResumeRegeneration() throws Exception {
        preparePlayer();
        assertPauseAndResume(player);
    }

    @Test
    void wrappedPlayerCanPauseAndResumeRegeneration() throws Exception {
        preparePlayer();
        assertPauseAndResume(wrapped(player));
    }

    @Test
    void directPlayerCanSetHunger() throws Exception {
        preparePlayer();
        assertHungerChange(player);
    }

    @Test
    void wrappedPlayerCanSetHunger() throws Exception {
        preparePlayer();
        assertHungerChange(wrapped(player));
    }

    @Test
    void consoleWithoutPlayerIsRejected() throws Exception {
        assertPlayerRequired(wrapped(null));
    }

    @Test
    void nonPlayerEntityIsRejected() throws Exception {
        assertPlayerRequired(wrapped(mock(Entity.class)));
    }

    @Test
    void nonServerPlayerIsRejected() throws Exception {
        assertPlayerRequired(wrapped(mock(EntityPlayer.class)));
    }

    @Test
    void permissionRemainsLevelTwoAndUsesTheSender() throws Exception {
        CommandBase command = command();
        ICommandSender sender = wrapped(player);
        MinecraftServer server = mock(MinecraftServer.class);
        assertEquals(2, command.getRequiredPermissionLevel());
        when(sender.canUseCommand(2, "jawms-test")).thenReturn(false, true);
        assertFalse(command.checkPermission(server, sender));
        assertTrue(command.checkPermission(server, sender));
        verify(sender, times(2)).canUseCommand(2, "jawms-test");
        verifyNoInteractions(player);
    }

    @Test
    void wrappedPlayerDoesNotBypassManaApiServerThreadCheck() throws Exception {
        preparePlayer();
        when(world.isCallingFromMinecraftThread()).thenReturn(false);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> command().execute(null, wrapped(player), new String[]{"regen", "pause", "true"}));
        assertTrue(error.getMessage().contains("Minecraft server thread"));
    }

    private void preparePlayer() {
        player.world = world;
        when(player.getUniqueID()).thenReturn(playerId);
        when(player.getCommandSenderEntity()).thenReturn(player);
        when(world.isCallingFromMinecraftThread()).thenReturn(true);
        when(player.getCapability(ManaCapability.INSTANCE, null)).thenReturn(new ManaState());
    }

    private void assertPauseAndResume(ICommandSender sender) throws Exception {
        CommandBase command = command();
        command.execute(null, sender, new String[]{"regen", "pause", "true"});
        assertTrue(pausedPlayers().contains(playerId));
        command.execute(null, sender, new String[]{"regen", "pause", "false"});
        assertFalse(pausedPlayers().contains(playerId));
        ArgumentCaptor<ITextComponent> messages = ArgumentCaptor.forClass(ITextComponent.class);
        verify(sender, times(2)).sendMessage(messages.capture());
        assertEquals("JAWMS test mana regeneration paused=true.",
                messages.getAllValues().get(0).getUnformattedText());
        assertEquals("JAWMS test mana regeneration paused=false.",
                messages.getAllValues().get(1).getUnformattedText());
    }

    private void assertHungerChange(ICommandSender sender) throws Exception {
        FoodStats food = new FoodStats();
        food.setFoodLevel(20);
        when(player.getFoodStats()).thenReturn(food);
        command().execute(null, sender, new String[]{"hunger", "set", "7"});
        assertEquals(7, food.getFoodLevel());
        verify(sender).sendMessage(any(ITextComponent.class));
    }

    private static void assertPlayerRequired(ICommandSender sender) throws Exception {
        CommandBase command = command();
        PlayerNotFoundException error = assertThrows(PlayerNotFoundException.class,
                () -> command.execute(null, sender, new String[]{"regen", "pause", "true"}));
        assertEquals("commands.generic.player.unspecified", error.getMessage());
        verify(sender, never()).sendMessage(any(ITextComponent.class));
    }

    private static ICommandSender wrapped(Entity entity) {
        ICommandSender sender = mock(ICommandSender.class);
        when(sender.getCommandSenderEntity()).thenReturn(entity);
        return sender;
    }

    private static CommandBase command() throws Exception {
        // Exercise the registered command without widening this private fixture boundary.
        Class<?> type = Class.forName(ManaTestFixture.class.getName() + "$SetManaCommand");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return (CommandBase) constructor.newInstance();
    }

    @SuppressWarnings("unchecked")
    private static Set<UUID> pausedPlayers() throws Exception {
        Field field = ManaTestFixture.class.getDeclaredField("REGENERATION_DISABLED");
        field.setAccessible(true);
        return (Set<UUID>) field.get(null);
    }
}
