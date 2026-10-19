package com.mahghuuuls.jawms.internal.casting;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Versioned, bounded participant-use messages, separate from legacy mana stops. */
public final class ParticipantUseMessages {
    public static final int VERSION = 2;
    private static final int MAX_SPELL_BYTES = 256;

    private ParticipantUseMessages() {}

    public enum CloseCause { NORMAL_FINISH, NORMAL_NO_FINISH, TERMINAL, REFUSED, INVALIDATED, FAULT, CANCELLED }

    private static UUID readEpoch(ByteBuf in) {
        if (in.readUnsignedByte() != VERSION) throw new IllegalArgumentException("Protocol version");
        return new UUID(in.readLong(), in.readLong());
    }
    private static void writeEpoch(ByteBuf out, UUID epoch) {
        out.writeByte(VERSION);
        out.writeLong(epoch.getMostSignificantBits());
        out.writeLong(epoch.getLeastSignificantBits());
    }
    private static long readPositive(ByteBuf in) {
        long value = in.readLong();
        if (value <= 0) throw new IllegalArgumentException("Use sequence");
        return value;
    }
    private static EnumHand readHand(ByteBuf in) {
        int ordinal = in.readUnsignedByte();
        if (ordinal >= EnumHand.values().length) throw new IllegalArgumentException("Hand");
        return EnumHand.values()[ordinal];
    }
    private static ResourceLocation readSpell(ByteBuf in) {
        int count = in.readUnsignedShort();
        if (count == 0 || count > MAX_SPELL_BYTES || count > in.readableBytes()) {
            throw new IllegalArgumentException("Spell length");
        }
        byte[] bytes = new byte[count];
        in.readBytes(bytes);
        String name = new String(bytes, StandardCharsets.US_ASCII);
        if (!name.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Spell");
        return new ResourceLocation(name);
    }
    private static void writeSpell(ByteBuf out, ResourceLocation spell) {
        String name = Objects.requireNonNull(spell, "spell").toString();
        byte[] bytes = name.getBytes(StandardCharsets.US_ASCII);
        if (bytes.length > MAX_SPELL_BYTES || !name.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) {
            throw new IllegalArgumentException("Spell");
        }
        out.writeShort(bytes.length);
        out.writeBytes(bytes);
    }
    private static void requireEnd(ByteBuf in) {
        if (in.isReadable()) throw new IllegalArgumentException("Trailing participant-use data");
    }
    private static long positive(long value) {
        if (value <= 0) throw new IllegalArgumentException("Use sequence");
        return value;
    }

    public static final class Capability implements IMessage {
        private UUID epoch;
        private boolean valid;
        public Capability() {}
        public Capability(UUID epoch) { this.epoch = Objects.requireNonNull(epoch, "epoch"); valid = true; }
        public UUID epoch() { return epoch; }
        public boolean isValid() { return valid; }
        public void fromBytes(ByteBuf in) {
            valid = false;
            try { epoch = readEpoch(in); requireEnd(in); valid = true; }
            catch (RuntimeException malformed) { epoch = null; }
        }
        public void toBytes(ByteBuf out) { writeEpoch(out, epoch); }
    }

    public static final class Intent implements IMessage {
        private UUID epoch;
        private long intent;
        private EnumHand hand;
        private ResourceLocation spell;
        private boolean valid;
        public Intent() {}
        public Intent(UUID epoch, long intent, EnumHand hand, ResourceLocation spell) {
            this.epoch = Objects.requireNonNull(epoch, "epoch"); this.intent = positive(intent);
            this.hand = Objects.requireNonNull(hand, "hand"); this.spell = Objects.requireNonNull(spell, "spell");
            valid = true;
        }
        public UUID epoch() { return epoch; }
        public long intent() { return intent; }
        public EnumHand hand() { return hand; }
        public ResourceLocation spell() { return spell; }
        public boolean isValid() { return valid; }
        public void fromBytes(ByteBuf in) {
            valid = false;
            try {
                epoch = readEpoch(in); intent = readPositive(in); hand = readHand(in); spell = readSpell(in);
                requireEnd(in); valid = true;
            } catch (RuntimeException malformed) { epoch = null; hand = null; spell = null; }
        }
        public void toBytes(ByteBuf out) {
            writeEpoch(out, epoch); out.writeLong(intent); out.writeByte(hand.ordinal()); writeSpell(out, spell);
        }
    }

    public static final class Release implements IMessage {
        private UUID epoch;
        private long intent;
        private boolean valid;
        public Release() {}
        public Release(UUID epoch, long intent) {
            this.epoch = Objects.requireNonNull(epoch, "epoch"); this.intent = positive(intent); valid = true;
        }
        public UUID epoch() { return epoch; }
        public long intent() { return intent; }
        public boolean isValid() { return valid; }
        public void fromBytes(ByteBuf in) {
            valid = false;
            try { epoch = readEpoch(in); intent = readPositive(in); requireEnd(in); valid = true; }
            catch (RuntimeException malformed) { epoch = null; }
        }
        public void toBytes(ByteBuf out) { writeEpoch(out, epoch); out.writeLong(intent); }
    }

    public static final class Close implements IMessage {
        private UUID epoch;
        private long intent;
        private long use;
        private long generation;
        private EnumHand hand;
        private ResourceLocation spell;
        private CloseCause cause;
        private boolean valid;
        public Close() {}
        public Close(UUID epoch, long intent, long use, EnumHand hand, ResourceLocation spell, CloseCause cause, long generation) {
            this.epoch = Objects.requireNonNull(epoch, "epoch"); this.intent = positive(intent);
            this.use = positive(use); this.hand = Objects.requireNonNull(hand, "hand");
            this.spell = Objects.requireNonNull(spell, "spell"); this.cause = Objects.requireNonNull(cause, "cause");
            this.generation = positive(generation);
            valid = true;
        }
        public UUID epoch() { return epoch; }
        public long intent() { return intent; }
        public long use() { return use; }
        public long generation() { return generation; }
        public EnumHand hand() { return hand; }
        public ResourceLocation spell() { return spell; }
        public CloseCause cause() { return cause; }
        public boolean isValid() { return valid; }
        public void fromBytes(ByteBuf in) {
            valid = false;
            try {
                epoch = readEpoch(in); intent = readPositive(in); use = readPositive(in); generation = readPositive(in);
                hand = readHand(in); spell = readSpell(in); int ordinal = in.readUnsignedByte();
                if (ordinal >= CloseCause.values().length) throw new IllegalArgumentException("Stop cause");
                cause = CloseCause.values()[ordinal]; requireEnd(in); valid = true;
            } catch (RuntimeException malformed) { epoch = null; hand = null; spell = null; cause = null; }
        }
        public void toBytes(ByteBuf out) {
            writeEpoch(out, epoch); out.writeLong(intent); out.writeLong(use); out.writeLong(generation); out.writeByte(hand.ordinal());
            writeSpell(out, spell); out.writeByte(cause.ordinal());
        }
    }

    public static final class CapabilityHandler implements IMessageHandler<Capability, IMessage> {
        public IMessage onMessage(Capability message, MessageContext context) {
            if (message.isValid()) FMLCommonHandler.instance().getWorldThread(context.netHandler)
                    .addScheduledTask(() -> JustAnotherWizardryManaSystemMod.proxy
                            .acceptParticipantUseCapability(message, context.netHandler));
            return null;
        }
    }
    public static final class IntentHandler implements IMessageHandler<Intent, IMessage> {
        public IMessage onMessage(Intent message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().player;
                long receipt = ParticipantUseService.networkReceipt();
                player.getServerWorld().addScheduledTask(() -> ParticipantUseService.intent(player, message, receipt));
            }
            return null;
        }
    }
    public static final class ReleaseHandler implements IMessageHandler<Release, IMessage> {
        public IMessage onMessage(Release message, MessageContext context) {
            if (message.isValid()) {
                EntityPlayerMP player = context.getServerHandler().player;
                long receipt = ParticipantUseService.networkReceipt();
                player.getServerWorld().addScheduledTask(() -> ParticipantUseService.release(player, message, receipt));
            }
            return null;
        }
    }
    public static final class CloseHandler implements IMessageHandler<Close, IMessage> {
        public IMessage onMessage(Close message, MessageContext context) {
            if (message.isValid()) FMLCommonHandler.instance().getWorldThread(context.netHandler)
                    .addScheduledTask(() -> JustAnotherWizardryManaSystemMod.proxy
                            .acceptParticipantUseClose(message, context.netHandler));
            return null;
        }
    }
}
