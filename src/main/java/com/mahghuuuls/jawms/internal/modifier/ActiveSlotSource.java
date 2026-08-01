package com.mahghuuuls.jawms.internal.modifier;

import net.minecraft.entity.player.EntityPlayer;

/** Optional integrations contribute occupied active slots through this JAWMS-owned boundary. */
public interface ActiveSlotSource {

    void collect(EntityPlayer player, ActiveSlotConsumer consumer);
}
