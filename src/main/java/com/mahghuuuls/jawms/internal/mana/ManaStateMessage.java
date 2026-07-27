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
    private long revision;

    public ManaStateMessage() {
    }

    public ManaStateMessage(int currentMana, int maximumMana, long revision) {
        this.currentMana = currentMana;
        this.maximumMana = maximumMana;
        this.revision = revision;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        currentMana = buffer.readInt();
        maximumMana = buffer.readInt();
        revision = buffer.readLong();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(currentMana);
        buffer.writeInt(maximumMana);
        buffer.writeLong(revision);
    }

    boolean isValid() {
        return maximumMana > 0
                && currentMana >= 0
                && currentMana <= maximumMana
                && revision >= 0L;
    }

    int getCurrentMana() {
        return currentMana;
    }

    int getMaximumMana() {
        return maximumMana;
    }

    long getRevision() {
        return revision;
    }

    public static final class Handler implements IMessageHandler<ManaStateMessage, IMessage> {

        @Override
        public IMessage onMessage(ManaStateMessage message, MessageContext context) {
            if (!message.isValid()) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "Discarded invalid player mana packet: current={}, maximum={}, revision={}.",
                        message.currentMana,
                        message.maximumMana,
                        message.revision
                );
                return null;
            }

            IThreadListener thread = FMLCommonHandler.instance().getWorldThread(context.netHandler);
            thread.addScheduledTask(() -> JustAnotherWizardryManaSystemMod.proxy.acceptManaState(
                    message.currentMana,
                    message.maximumMana,
                    message.revision
            ));
            return null;
        }
    }
}
