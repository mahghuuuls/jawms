package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ContinuousCastStopMessage implements IMessage {

    private static final int MAX_IDENTIFIER_LENGTH = 256;

    private EnumHand hand;
    private ResourceLocation spellId;
    private boolean resetImmediately;
    private boolean malformed;

    public ContinuousCastStopMessage() {
    }

    public ContinuousCastStopMessage(
            EnumHand hand,
            ResourceLocation spellId,
            boolean resetImmediately
    ) {
        if (hand == null) {
            throw new NullPointerException("hand");
        }
        if (spellId == null) {
            throw new NullPointerException("spellId");
        }
        this.hand = hand;
        this.spellId = spellId;
        this.resetImmediately = resetImmediately;
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
            resetImmediately = buffer.readBoolean();
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
        buffer.writeBoolean(resetImmediately);
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

    boolean shouldResetImmediately() {
        return resetImmediately;
    }

    public static final class Handler
            implements IMessageHandler<ContinuousCastStopMessage, IMessage> {

        @Override
        public IMessage onMessage(
                ContinuousCastStopMessage message,
                MessageContext context
        ) {
            if (!message.isValid()) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "Discarded invalid continuous-cast stop packet."
                );
                return null;
            }
            IThreadListener thread =
                    FMLCommonHandler.instance().getWorldThread(context.netHandler);
            thread.addScheduledTask(
                    () -> JustAnotherWizardryManaSystemMod.proxy.stopContinuousCastVisual(
                            message.hand,
                            message.spellId,
                            message.resetImmediately
                    )
            );
            return null;
        }
    }
}
