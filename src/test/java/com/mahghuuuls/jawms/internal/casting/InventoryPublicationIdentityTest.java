package com.mahghuuuls.jawms.internal.casting;

import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketSetSlot;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Vanilla replacement witness only, not a client Frame or healthy-finish pass. */
class InventoryPublicationIdentityTest {
    @BeforeAll static void bootstrap() { Bootstrap.register(); }
    @Test void ordinarySlotPublicationInstallsANewObjectEvenForEquivalentItemData() throws Exception {
        InventoryPlayer inventory = new InventoryPlayer(mock(EntityPlayerMP.class));
        for (boolean changedCooldown : new boolean[] { false, true }) {
            ItemStack original = new ItemStack(Items.STICK);
            inventory.setInventorySlotContents(0, original);
            ItemStack published = original.copy();
            if (changedCooldown) {
                NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("ordinary_server_cooldown", 20); published.setTagCompound(tag);
            }
            PacketBuffer wire = new PacketBuffer(Unpooled.buffer());
            try {
                new SPacketSetSlot(-2, 0, published).writePacketData(wire);
                SPacketSetSlot received = new SPacketSetSlot(); received.readPacketData(wire);
                // Exact install used by NetHandlerPlayClient.handleSetSlot's window -2 branch.
                inventory.setInventorySlotContents(received.getSlot(), received.getStack());
                assertNotSame(original, inventory.getStackInSlot(0));
                assertEquals(!changedCooldown, ItemStack.areItemStacksEqual(original, inventory.getStackInSlot(0)));
            } finally { wire.release(); }
        }
    }
}
