package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.ManaProfileReadiness;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.UUID;

/** Ordered readiness and authoritative mana view for a managed connection. */
public final class ManaReadinessMessage implements IMessage {
    private long sequence;
    private UUID generation;
    private ManaProfileReadiness phase;
    private ManaStateMessage payload;

    public ManaReadinessMessage() {
    }

    ManaReadinessMessage(long sequence, UUID generation, ManaProfileReadiness phase, ManaStateMessage payload) {
        this.sequence = sequence;
        this.generation = generation;
        this.phase = phase;
        this.payload = payload;
    }

    public long getSequence() {
        return sequence;
    }

    public UUID getGeneration() {
        return generation;
    }

    public ManaProfileReadiness getPhase() {
        return phase;
    }

    public void deliverPayload() {
        if (payload != null) payload.deliver();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        sequence = buffer.readLong();
        generation = new UUID(buffer.readLong(), buffer.readLong());
        switch (buffer.readUnsignedByte()) {
            case 0:
                phase = ManaProfileReadiness.PENDING;
                break;
            case 1:
                phase = ManaProfileReadiness.UNAVAILABLE;
                break;
            case 2:
                phase = ManaProfileReadiness.READY;
                payload = new ManaStateMessage();
                payload.fromBytes(buffer);
                break;
            default:
                throw new IllegalArgumentException("Invalid managed mana phase.");
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(sequence);
        buffer.writeLong(generation.getMostSignificantBits());
        buffer.writeLong(generation.getLeastSignificantBits());
        buffer.writeByte(phase == ManaProfileReadiness.READY ? 2
                : phase == ManaProfileReadiness.UNAVAILABLE ? 1 : 0);
        if (payload != null) payload.toBytes(buffer);
    }

    public static final class Handler implements IMessageHandler<ManaReadinessMessage, IMessage> {
        @Override
        public IMessage onMessage(ManaReadinessMessage message, MessageContext context) {
            if (message.sequence > 0 && (message.payload == null || message.payload.isValid())) {
                FMLCommonHandler.instance().getWorldThread(context.netHandler).addScheduledTask(() ->
                        JustAnotherWizardryManaSystemMod.proxy.acceptManagedMana(message, context.netHandler));
            }
            return null;
        }
    }
}
