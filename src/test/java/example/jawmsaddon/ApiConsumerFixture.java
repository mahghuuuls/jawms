package example.jawmsaddon;

import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Compile-time fixture representing a server-side addon using only the public API.
 */
public final class ApiConsumerFixture {

    private ApiConsumerFixture() {
    }

    public static boolean spend(EntityPlayer player, int amount) {
        IManaService mana = ManaApi.getManaService();
        return mana.tryConsumeMana(player, amount);
    }
}
