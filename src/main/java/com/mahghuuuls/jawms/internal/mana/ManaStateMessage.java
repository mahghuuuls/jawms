package com.mahghuuuls.jawms.internal.mana;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import io.netty.buffer.ByteBuf;
import electroblob.wizardry.constants.Element;
import java.util.EnumMap;
import java.util.Map;
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
    private double globalSpellEfficiency;
    private final EnumMap<Element, Double> elementSpellEfficiency = new EnumMap<>(Element.class);
    private double globalSpellCostMultiplier = 1.0D;
    private final EnumMap<Element, Double> elementSpellCostMultiplier = new EnumMap<>(Element.class);

    public ManaStateMessage() {
    }

    public ManaStateMessage(int currentMana, int maximumMana, double effectiveRegeneration,
                            long revision) {
        this(currentMana, maximumMana, effectiveRegeneration, revision, 0.0D,
                java.util.Collections.emptyMap(), 1.0D,
                java.util.Collections.emptyMap());
    }

    public ManaStateMessage(
            int currentMana,
            int maximumMana,
            double effectiveRegeneration,
            long revision,
            double globalSpellEfficiency,
            Map<Element, Double> elementSpellEfficiency,
            double globalSpellCostMultiplier,
            Map<Element, Double> elementSpellCostMultiplier
    ) {
        this.currentMana = currentMana;
        this.maximumMana = maximumMana;
        this.effectiveRegeneration = effectiveRegeneration;
        this.revision = revision;
        this.globalSpellEfficiency = globalSpellEfficiency;
        this.elementSpellEfficiency.putAll(elementSpellEfficiency);
        this.globalSpellCostMultiplier = globalSpellCostMultiplier;
        this.elementSpellCostMultiplier.putAll(elementSpellCostMultiplier);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        currentMana = buffer.readInt();
        maximumMana = buffer.readInt();
        effectiveRegeneration = buffer.readDouble();
        revision = buffer.readLong();
        globalSpellEfficiency = buffer.readDouble();
        elementSpellEfficiency.clear();
        int count = buffer.readUnsignedByte();
        if (count > Element.values().length) {
            throw new IllegalArgumentException("Element efficiency count is out of bounds.");
        }
        for (int index = 0; index < count; index++) {
            int ordinal = buffer.readUnsignedByte();
            if (ordinal >= Element.values().length || elementSpellEfficiency.containsKey(Element.values()[ordinal])) {
                throw new IllegalArgumentException("Invalid or duplicate element efficiency.");
            }
            elementSpellEfficiency.put(Element.values()[ordinal], buffer.readDouble());
        }
        globalSpellCostMultiplier = buffer.readDouble();
        elementSpellCostMultiplier.clear();
        count = buffer.readUnsignedByte();
        if (count > Element.values().length) {
            throw new IllegalArgumentException("Element cost-multiplier count is out of bounds.");
        }
        for (int index = 0; index < count; index++) {
            int ordinal = buffer.readUnsignedByte();
            if (ordinal >= Element.values().length
                    || elementSpellCostMultiplier.containsKey(Element.values()[ordinal])) {
                throw new IllegalArgumentException("Invalid or duplicate element cost multiplier.");
            }
            elementSpellCostMultiplier.put(Element.values()[ordinal], buffer.readDouble());
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(currentMana);
        buffer.writeInt(maximumMana);
        buffer.writeDouble(effectiveRegeneration);
        buffer.writeLong(revision);
        buffer.writeDouble(globalSpellEfficiency);
        buffer.writeByte(elementSpellEfficiency.size());
        for (Map.Entry<Element, Double> entry : elementSpellEfficiency.entrySet()) {
            buffer.writeByte(entry.getKey().ordinal());
            buffer.writeDouble(entry.getValue());
        }
        buffer.writeDouble(globalSpellCostMultiplier);
        buffer.writeByte(elementSpellCostMultiplier.size());
        for (Map.Entry<Element, Double> entry : elementSpellCostMultiplier.entrySet()) {
            buffer.writeByte(entry.getKey().ordinal());
            buffer.writeDouble(entry.getValue());
        }
    }

    boolean isValid() {
        return maximumMana > 0
                && currentMana >= 0
                && currentMana <= maximumMana
                && Double.isFinite(effectiveRegeneration)
                && effectiveRegeneration >= 0.0D
                && Double.isFinite(globalSpellEfficiency)
                && globalSpellEfficiency >= 0.0D
                && elementSpellEfficiency.values().stream().allMatch(value -> Double.isFinite(value) && value >= 0.0D)
                && Double.isFinite(globalSpellCostMultiplier)
                && globalSpellCostMultiplier > 0.0D
                && elementSpellCostMultiplier.values().stream()
                .allMatch(value -> Double.isFinite(value) && value > 0.0D)
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

    double getGlobalSpellCostMultiplier() {
        return globalSpellCostMultiplier;
    }

    Map<Element, Double> getElementSpellCostMultipliers() {
        return java.util.Collections.unmodifiableMap(elementSpellCostMultiplier);
    }

    public void deliver() {
        JustAnotherWizardryManaSystemMod.proxy.acceptManaState(
                currentMana, maximumMana, effectiveRegeneration, revision, globalSpellEfficiency,
                elementSpellEfficiency, globalSpellCostMultiplier, elementSpellCostMultiplier);
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
            thread.addScheduledTask(() ->
                    JustAnotherWizardryManaSystemMod.proxy.acceptLegacyMana(message, context.netHandler));
            return null;
        }
    }
}
