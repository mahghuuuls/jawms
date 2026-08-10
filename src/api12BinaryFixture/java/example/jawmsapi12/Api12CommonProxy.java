package example.jawmsapi12;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Common-side old-binary probe; deliberately limited to API 1.2 methods. */
public class Api12CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.2 Fixture");

    public void initialize() {
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        LOGGER.info(
                "JAWMS API 1.2 binary common probe loaded: available={}, current={}, maximum={}, regeneration={}",
                snapshot.isAvailable(),
                snapshot.getCurrentMana(),
                snapshot.getMaximumMana(),
                snapshot.getEffectiveRegeneration()
        );
    }
}
