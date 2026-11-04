package example.jawmsreadiness;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;

@SideOnly(Side.CLIENT)
public final class ClientObserver {
    private Object connection;
    private EntityPlayerSP player;
    private int dimension;
    private boolean available;
    private int current;
    private int maximum;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft client = Minecraft.getMinecraft();
        Object active = client.getConnection();
        EntityPlayerSP local = client.player;
        int localDimension = local == null ? 0 : local.dimension;
        ClientManaSnapshot view = ClientManaApi.getSnapshot();
        if (active == connection && local == player && localDimension == dimension && available == view.isAvailable()
                && current == view.getCurrentMana() && maximum == view.getMaximumMana()) return;
        connection = active;
        player = local;
        dimension = localDimension;
        available = view.isAvailable();
        current = view.getCurrentMana();
        maximum = view.getMaximumMana();
        LogManager.getLogger(ReadinessFixture.ID).info("READINESS_FIXTURE_CLIENT connection={} available={} current={} maximum={} playerIdentity={} entity={} dimension={}",
                active == null ? "none" : Integer.toHexString(System.identityHashCode(active)), available, current, maximum,
                local == null ? "none" : Integer.toHexString(System.identityHashCode(local)),
                local == null ? "none" : local.getEntityId(), local == null ? "none" : localDimension);
    }
}
