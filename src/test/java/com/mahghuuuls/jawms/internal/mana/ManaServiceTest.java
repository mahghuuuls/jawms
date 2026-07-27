package com.mahghuuuls.jawms.internal.mana;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.WorldServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManaServiceTest {

    @Test
    void mutationsRejectOffThreadServerPlayersBeforeCapabilityAccess() {
        EntityPlayer player = mock(EntityPlayer.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(world.isCallingFromMinecraftThread()).thenReturn(false);
        ManaService service = new ManaService();

        assertThrows(IllegalStateException.class, () -> service.restoreMana(player, 1));
        assertThrows(IllegalStateException.class, () -> service.tryConsumeMana(player, 1));

        verify(player, never()).getCapability(any(), any());
    }

    @Test
    void readsRejectOffThreadServerPlayersBeforeCapabilityAccess() {
        EntityPlayer player = mock(EntityPlayer.class);
        WorldServer world = mock(WorldServer.class);
        player.world = world;
        when(world.isCallingFromMinecraftThread()).thenReturn(false);
        ManaService service = new ManaService();

        assertThrows(IllegalStateException.class, () -> service.getCurrentMana(player));
        assertThrows(IllegalStateException.class, () -> service.getMaximumMana(player));

        verify(player, never()).getCapability(any(), any());
    }
}
