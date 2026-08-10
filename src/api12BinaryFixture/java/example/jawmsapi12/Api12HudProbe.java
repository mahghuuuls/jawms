package example.jawmsapi12;

import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.client.ManaHudRenderEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** Runtime linkage probe using only the HUD event surface published in API 1.2. */
public final class Api12HudProbe {

    static final Api12HudProbe INSTANCE = new Api12HudProbe();

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.2 Fixture");
    private boolean observed;
    private int lastCurrent;
    private int lastMaximum;

    private Api12HudProbe() {
    }

    @SubscribeEvent
    public void observe(ManaHudRenderEvent event) {
        ClientManaSnapshot snapshot = event.getSnapshot();
        if (!observed
                || snapshot.getCurrentMana() != lastCurrent
                || snapshot.getMaximumMana() != lastMaximum) {
            observed = true;
            lastCurrent = snapshot.getCurrentMana();
            lastMaximum = snapshot.getMaximumMana();
            LOGGER.info(
                    "JAWMS API 1.2 binary HUD callback: cancelable={}, current={}, maximum={}, "
                            + "regeneration={}, partialTicks={}, scaledWidth={}, scaledHeight={}",
                    event.isCancelable(),
                    snapshot.getCurrentMana(),
                    snapshot.getMaximumMana(),
                    snapshot.getEffectiveRegeneration(),
                    event.getPartialTicks(),
                    event.getScaledWidth(),
                    event.getScaledHeight()
            );
        }
    }
}
