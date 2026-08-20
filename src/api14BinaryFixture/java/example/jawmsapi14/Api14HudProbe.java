package example.jawmsapi14;

import com.mahghuuuls.jawms.api.client.ClientHudContext;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Runtime linkage probe retained from the API 1.3 client contract. */
public final class Api14HudProbe {

    static final Api14HudProbe INSTANCE = new Api14HudProbe();

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.4 Fixture");
    private boolean observed;

    private Api14HudProbe() {
    }

    @SubscribeEvent
    public void observe(ManaHudRenderEvent event) {
        if (observed) {
            return;
        }
        observed = true;
        ClientHudContext context = event.getContext();
        LOGGER.info(
                "JAWMS API 1.4 binary HUD callback: revision={}, creativeKnown={}, creative={}",
                event.getSnapshot().getRevision(),
                context.isCreativeModeKnown(),
                context.isCreativeMode()
        );
    }
}
