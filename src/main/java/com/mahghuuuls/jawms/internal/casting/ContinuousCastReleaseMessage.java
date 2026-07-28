package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.mana.ManaService;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ContinuousCastReleaseMessage implements IMessage {

    private static final int MAX_IDENTIFIER_LENGTH = 256;

    private EnumHand hand;
    private ResourceLocation spellId;
    private boolean malformed;

    public ContinuousCastReleaseMessage() {
    }

    public ContinuousCastReleaseMessage(EnumHand hand, ResourceLocation spellId) {
        if (hand == null) {
            throw new NullPointerException("hand");
        }
        if (spellId == null) {
            throw new NullPointerException("spellId");
        }
        this.hand = hand;
        this.spellId = spellId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            int handOrdinal = buffer.readUnsignedByte();
            if (handOrdinal >= EnumHand.values().length) {
                throw new IllegalArgumentException("Invalid hand ordinal.");
            }
            hand = EnumHand.values()[handOrdinal];
            String identifier = ByteBufUtils.readUTF8String(buffer);
            if (identifier.length() > MAX_IDENTIFIER_LENGTH) {
                throw new IllegalArgumentException("Spell identifier is too long.");
            }
            spellId = new ResourceLocation(identifier);
        } catch (RuntimeException exception) {
            malformed = true;
            hand = null;
            spellId = null;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(hand.ordinal());
        ByteBufUtils.writeUTF8String(buffer, spellId.toString());
    }

    boolean isValid() {
        return !malformed && hand != null && spellId != null;
    }

    EnumHand getHand() {
        return hand;
    }

    ResourceLocation getSpellId() {
        return spellId;
    }

    public static final class Handler
            implements IMessageHandler<ContinuousCastReleaseMessage, IMessage> {

        @Override
        public IMessage onMessage(
                ContinuousCastReleaseMessage message,
                MessageContext context
        ) {
            if (!message.isValid()) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "Discarded invalid continuous-cast release packet."
                );
                return null;
            }
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(
                    () -> ManaService.releaseContinuousCastRetryBlock(
                            player,
                            message.hand,
                            message.spellId
                    )
            );
            return null;
        }
    }
}
