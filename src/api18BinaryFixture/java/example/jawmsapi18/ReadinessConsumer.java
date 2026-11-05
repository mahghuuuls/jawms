package example.jawmsapi18;

import com.mahghuuuls.jawms.api.ManaContribution;
import com.mahghuuuls.jawms.api.ManaContributionPublicationResult;
import com.mahghuuuls.jawms.api.ManaContributionPublicationStatus;
import com.mahghuuuls.jawms.api.ManaProfileGeneration;
import com.mahghuuuls.jawms.api.ManaReadinessProvider;
import net.minecraft.entity.player.EntityPlayer;

import static com.mahghuuuls.jawms.api.ManaContributionPublicationStatus.*;

/** Compiled against the released API 1.8 oracle and executed against the candidate. */
public final class ReadinessConsumer {
    private ReadinessConsumer() { }

    public static long publish(ManaReadinessProvider provider, EntityPlayer player) {
        ManaContributionPublicationStatus[] original = {INITIAL_RECORDED, UPDATE_RECORDED, ALREADY_COMPLETED,
                INITIAL_REQUIRED, REVISION_MISMATCH, STALE_GENERATION, INAPPLICABLE, BUSY};
        for (int index = 0; index < original.length; index++) {
            if (original[index].ordinal() != index) throw new AssertionError("Changed status order: " + original[index]);
        }
        ManaProfileGeneration token = provider.getGeneration(player).get();
        ManaContributionPublicationResult initial = provider.completeInitial(player, token, ManaContribution.EMPTY);
        if (initial.getStatus() != INITIAL_RECORDED) throw new AssertionError(initial.getStatus());
        ManaContributionPublicationResult update = provider.update(player, token, initial.getRevision().getAsLong(),
                ManaContribution.EMPTY);
        if (update.getStatus() != UPDATE_RECORDED) throw new AssertionError(update.getStatus());
        return update.getRevision().getAsLong();
    }
}
