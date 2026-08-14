package example.jawmsapi12;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import net.minecraft.util.ResourceLocation;
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
        logLegacyMutationResultProbe();
    }

    private static void logLegacyMutationResultProbe() {
        ManaPublicState state = new ManaPublicState(
                20, 60, 2.4D, 40L, 0L, false, false, false, true
        );
        ResourceLocation cause = new ResourceLocation("jawms_api12_fixture", "legacy_result");
        ManaMutationResult success = new ManaMutationResult(
                true, 0, 0, cause, state, state
        );
        ManaMutationResult failure = new ManaMutationResult(
                false, 30, 0, cause, state, state
        );
        LOGGER.info(
                "JAWMS API 1.2 legacy mutation result: success={}, requested={}, actual={}, "
                        + "cause={}, old={}, new={}, failureSuccess={}",
                success.isSuccessful(),
                success.getRequestedAmount(),
                success.getActualAmount(),
                success.getCause(),
                success.getOldState().getCurrentMana(),
                success.getNewState().getCurrentMana(),
                failure.isSuccessful()
        );
    }
}
