package example.jawmsapi15;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Runtime client linkage probe retained from the API 1.5 synchronized view. */
public final class Api15HudProbe {

    static final Api15HudProbe INSTANCE = new Api15HudProbe();

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.5 Fixture");
    private boolean observed;

    private Api15HudProbe() {
    }

    @SubscribeEvent
    public void observe(ManaHudRenderEvent event) {
        if (observed) {
            return;
        }
        observed = true;
        ConfiguredFlaskSnapshot flasks = ClientManaApi.getConfiguredFlaskSnapshot();
        LOGGER.info(
                "JAWMS API 1.5 binary HUD callback: manaRevision={}, flaskAvailable={}, "
                        + "flaskRevisionKnown={}",
                event.getSnapshot().getRevision(),
                flasks.isAvailable(),
                flasks.hasKnownRevision()
        );
    }
}
