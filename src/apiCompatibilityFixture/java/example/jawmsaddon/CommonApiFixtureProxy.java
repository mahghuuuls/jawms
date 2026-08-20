package example.jawmsaddon;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ConfiguredFlaskSnapshot;
import com.mahghuuuls.jawms.api.client.ClientManaApi;
import com.mahghuuuls.jawms.api.client.ClientManaSnapshot;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Common-side fixture code. It deliberately exercises the client API safely. */
public class CommonApiFixtureProxy {

    private static final Logger LOGGER = LogManager.getLogger("JAWMS API Fixture");

    public void initialize() {
        ClientManaSnapshot snapshot = ClientManaApi.getSnapshot();
        ConfiguredFlaskSnapshot flasks = ClientManaApi.getConfiguredFlaskSnapshot();
        LOGGER.info(
                "JAWMS side-safe client API probe: api={}, available={}, revisionKnown={}, revision={}",
                ManaApi.API_VERSION,
                snapshot.isAvailable(),
                snapshot.hasKnownRevision(),
                snapshot.hasKnownRevision() ? Long.toString(snapshot.getRevision()) : "unknown"
        );
        LOGGER.info(
                "JAWMS side-safe configured-flask client probe: available={}, revision={}, values={}/{}/{}",
                flasks.isAvailable(),
                flasks.hasKnownRevision() ? Long.toString(flasks.getRevision()) : "unknown",
                flasks.getSmallRestoration(),
                flasks.getMediumRestoration(),
                flasks.getLargeRestoration()
        );
    }
}
