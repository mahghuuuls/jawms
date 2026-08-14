package example.jawmsapi13;

import com.mahghuuuls.jawms.api.client.ClientHudContext;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Runtime linkage probe using the HUD context and revision surface published in API 1.3. */
public final class Api13HudProbe {

    static final Api13HudProbe INSTANCE = new Api13HudProbe();

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.3 Fixture");
    private boolean observed;

    private Api13HudProbe() {
    }

    @SubscribeEvent
    public void observe(ManaHudRenderEvent event) {
        if (observed) {
            return;
        }
        observed = true;
        ClientManaSnapshot snapshot = event.getSnapshot();
        ClientHudContext context = event.getContext();
        LOGGER.info(
                "JAWMS API 1.3 binary HUD callback: revisionKnown={}, revision={}, "
                        + "creativeKnown={}, creative={}, partialTicks={}, scaledWidth={}, scaledHeight={}",
                snapshot.hasKnownRevision(),
                snapshot.getRevision(),
                context.isCreativeModeKnown(),
                context.isCreativeMode(),
                event.getPartialTicks(),
                event.getScaledWidth(),
                event.getScaledHeight()
        );
    }
}
