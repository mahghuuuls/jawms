package example.jawmsapi19;

import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/** Compiled against released API 1.9 and executed against the candidate. */
public final class ReadinessConsumer {
    private ReadinessConsumer() { }

    public static ManaReadinessProvider register(ResourceLocation id) {
        return ManaApi.registerReadinessProvider(id);
    }

    public static long publish(ManaReadinessProvider provider, EntityPlayer player) {
        ManaProfileGeneration generation = provider.getGeneration(player).get();
        ManaContributionPublicationResult initial = provider.completeInitial(player, generation, ManaContribution.EMPTY);
        long recorded = revision(initial, ManaContributionPublicationStatus.INITIAL_RECORDED);
        ManaContributionPublicationResult suspended = provider.suspend(player, generation, recorded);
        long withdrawn = revision(suspended, ManaContributionPublicationStatus.SUSPENDED);
        ManaContributionPublicationResult updated = provider.update(player, generation, withdrawn, ManaContribution.EMPTY);
        return revision(updated, ManaContributionPublicationStatus.UPDATE_RECORDED);
    }

    private static long revision(ManaContributionPublicationResult result, ManaContributionPublicationStatus expected) {
        if (result.getStatus() != expected) throw new AssertionError(result.getStatus());
        return result.getRevision().getAsLong();
    }
}
