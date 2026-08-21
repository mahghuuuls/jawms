package example.jawmsapi15;

import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Common-side old-binary probe deliberately limited to API 1.5 methods. */
public class Api15CommonProxy {

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API 1.5 Fixture");

    public void initialize() {
        ConfiguredFlaskSnapshot flasks = ManaApi.getConfiguredFlaskSnapshot();
        LOGGER.info(
                "JAWMS API 1.5 binary common probe loaded: available={}, revisionKnown={}",
                flasks.isAvailable(),
                flasks.hasKnownRevision()
        );
    }

    /** Retains linkage to API 1.5 invariant-preview mutation methods without invoking them. */
    public void probePreviewMethods(ResourceLocation providerId, ManaContribution contribution) {
        ManaApi.setItemProviderContributionPreview(providerId, contribution);
        ManaApi.clearItemProviderContributionPreview(providerId);
    }
}
