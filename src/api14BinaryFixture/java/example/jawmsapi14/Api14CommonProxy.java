package example.jawmsapi14;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaMutationFailure;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.api.ManaProviderDescriptor;
import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/** Common-side old-binary probe; deliberately limited to API 1.4 methods. */
public class Api14CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.4 Fixture");

    public void initialize() {
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        List<ManaProviderDescriptor> providers = ManaApi.getRegisteredProviders();
        ManaPublicState state = new ManaPublicState(
                20, 60, 2.4D, 40L, 0L, false, false, false, true
        );
        ManaMutationResult result = new ManaMutationResult(
                ManaMutationFailure.INSUFFICIENT_MANA,
                30,
                0,
                new ResourceLocation("jawms_api14_fixture", "legacy_result"),
                state,
                state
        );
        LOGGER.info(
                "JAWMS API 1.4 binary common probe loaded: available={}, frozen={}, providers={}, "
                        + "failure={}, requested={}, actualDelta={}, actualAmount={}",
                snapshot.isAvailable(),
                ManaApi.isProviderRegistrationFrozen(),
                providers.size(),
                result.getFailure(),
                result.getRequestedAmount(),
                result.getActualDelta(),
                result.getActualAmount()
        );
    }

    /** Retains linkage to the API 1.4 refresh and explanation surface. */
    public void probePlayerMethods(net.minecraft.entity.player.EntityPlayer player) {
        ManaApi.markModifierCacheDirty(player);
        ManaApi.invalidateModifierCache(player);
        ManaApi.explainManaProfile(player);
    }
}
