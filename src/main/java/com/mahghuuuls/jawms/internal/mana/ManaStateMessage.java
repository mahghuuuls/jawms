package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.IThreadListener;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public final class ManaStateMessage implements IMessage {

    private int currentMana;
    private int maximumMana;
    private double effectiveRegeneration;
    private long revision;

    public ManaStateMessage() {
    }

    public ManaStateMessage(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision
    ) {
        this.currentMana = currentMana;
        this.maximumMana = maximumMana;
        this.effectiveRegeneration = effectiveRegeneration;
        this.revision = revision;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        currentMana = buffer.readInt();
        maximumMana = buffer.readInt();
        effectiveRegeneration = buffer.readDouble();
        revision = buffer.readLong();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(currentMana);
        buffer.writeInt(maximumMana);
        buffer.writeDouble(effectiveRegeneration);
        buffer.writeLong(revision);
    }

    boolean isValid() {
        return maximumMana > 0
                && currentMana >= 0
                && currentMana <= maximumMana
                && Double.isFinite(effectiveRegeneration)
                && effectiveRegeneration >= 0.0D
                && revision >= 0L;
    }

    int getCurrentMana() {
        return currentMana;
    }

    int getMaximumMana() {
        return maximumMana;
    }

    double getEffectiveRegeneration() {
        return effectiveRegeneration;
    }

    long getRevision() {
        return revision;
    }

    public static final class Handler implements IMessageHandler<ManaStateMessage, IMessage> {

        @Override
        public IMessage onMessage(ManaStateMessage message, MessageContext context) {
            if (!message.isValid()) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "Discarded invalid player mana packet: current={}, maximum={}, regeneration={}, revision={}.",
                        message.currentMana,
                        message.maximumMana,
                        message.effectiveRegeneration,
                        message.revision
                );
                return null;
            }

            IThreadListener thread = FMLCommonHandler.instance().getWorldThread(context.netHandler);
            thread.addScheduledTask(() -> JustAnotherWizardryManaSystemMod.proxy.acceptManaState(
                    message.currentMana,
                    message.maximumMana,
                    message.effectiveRegeneration,
                    message.revision
            ));
            return null;
        }
    }
}
