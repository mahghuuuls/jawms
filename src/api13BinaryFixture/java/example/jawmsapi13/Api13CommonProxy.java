package example.jawmsapi13;

import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaPublicState;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Common-side old-binary probe; deliberately limited to API 1.3 methods. */
public class Api13CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.3 Fixture");

    public void initialize() {
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        LOGGER.info(
                "JAWMS API 1.3 binary common probe loaded: available={}, revisionKnown={}, revision={}",
                snapshot.isAvailable(),
                snapshot.hasKnownRevision(),
                snapshot.getRevision()
        );
        logLegacyMutationResultProbe();
    }

    private static void logLegacyMutationResultProbe() {
        ManaPublicState state = new ManaPublicState(
                20, 60, 2.4D, 40L, 0L, false, false, false, true
        );
        ResourceLocation cause = new ResourceLocation("jawms_api13_fixture", "legacy_result");
        ManaMutationResult success = new ManaMutationResult(
                true, 0, 0, cause, state, state
        );
        ManaMutationResult failure = new ManaMutationResult(
                false, 30, 0, cause, state, state
        );
        LOGGER.info(
                "JAWMS API 1.3 legacy mutation result: success={}, requested={}, actual={}, "
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
