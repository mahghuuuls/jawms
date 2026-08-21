package example.jawmsaddon;

import com.mahghuuuls.jawms.api.ManaPublicState;
import com.mahghuuuls.jawms.api.ManaStateChangeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApiConsumerFixtureEventTest {

    @Test
    void observationsAreScopedByPlayerAndCause() {
        ApiConsumerFixture fixture = new ApiConsumerFixture();
        EntityPlayer first = player();
        EntityPlayer second = player();
        ResourceLocation lockout = new ResourceLocation("exampleaddon", "fixture_lockout");
        ResourceLocation unrelated = new ResourceLocation("otheraddon", "nested_change");
        ManaStateChangeEvent firstLockout = event(first, lockout);
        ManaStateChangeEvent secondLockout = event(second, lockout);
        ManaStateChangeEvent nested = event(first, unrelated);
        ManaStateChangeEvent restarted = event(first, lockout);

        fixture.observe(firstLockout);
        fixture.observe(secondLockout);
        fixture.observe(nested);
        fixture.observe(restarted);

        assertEquals(2, fixture.getStateEventCount(first, lockout));
        assertSame(restarted, fixture.getLastEvent(first, lockout));
        assertEquals(1, fixture.getStateEventCount(second, lockout));
        assertSame(secondLockout, fixture.getLastEvent(second, lockout));
        assertEquals(1, fixture.getStateEventCount(first, unrelated));
        assertSame(nested, fixture.getLastEvent(first, unrelated));
    }

    private static EntityPlayer player() {
        EntityPlayer player = mock(EntityPlayer.class);
        when(player.getUniqueID()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static ManaStateChangeEvent event(
            EntityPlayer player,
            ResourceLocation cause
    ) {
        ManaPublicState state = new ManaPublicState(
                30, 60, 2.4D, 40L, 0L,
                false, false, false, true
        );
        return new ManaStateChangeEvent(player, state, state, cause);
    }
}
