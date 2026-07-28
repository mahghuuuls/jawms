package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameplayRulesMessage implements IMessage {

    private static final int MAX_TABLE_ENTRIES = 4096;
    private static final int MAX_IDENTIFIER_LENGTH = 256;

    private long revision;
    private int baseMaximum;
    private double baseRegeneration;
    private long lockoutTicks;
    private int smallFlask;
    private int mediumFlask;
    private int largeFlask;
    private Map<ResourceLocation, Double> spellCosts = new LinkedHashMap<>();
    private List<ConfiguredItemRule> itemRules = new ArrayList<>();
    private WizardryArmorProfile armorProfile = WizardryArmorProfile.DEFAULT;
    private boolean malformed;

    public GameplayRulesMessage() {
    }

    public GameplayRulesMessage(GameplayRules rules) {
        revision = rules.getRevision();
        baseMaximum = rules.getBaseMaximum();
        baseRegeneration = rules.getBaseRegeneration();
        lockoutTicks = rules.getRegenerationLockoutTicks();
        smallFlask = rules.getSmallFlask();
        mediumFlask = rules.getMediumFlask();
        largeFlask = rules.getLargeFlask();
        spellCosts = new LinkedHashMap<>(rules.getSpellCosts());
        itemRules = new ArrayList<>(rules.getConfiguredItemRules());
        armorProfile = rules.getArmorProfile();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            revision = buffer.readLong();
            baseMaximum = buffer.readInt();
            baseRegeneration = buffer.readDouble();
            lockoutTicks = buffer.readLong();
            smallFlask = buffer.readInt();
            mediumFlask = buffer.readInt();
            largeFlask = buffer.readInt();

            int spellCount = boundedCount(buffer.readInt());
            spellCosts = new LinkedHashMap<>();
            for (int index = 0; index < spellCount; index++) {
                ResourceLocation id = readIdentifier(buffer);
                double cost = buffer.readDouble();
                spellCosts.put(id, cost);
            }

            int itemCount = boundedCount(buffer.readInt());
            itemRules = new ArrayList<>();
            for (int index = 0; index < itemCount; index++) {
                itemRules.add(new ConfiguredItemRule(
                        readIdentifier(buffer),
                        buffer.readInt(),
                        buffer.readInt(),
                        buffer.readDouble(),
                        buffer.readDouble()
                ));
            }

            armorProfile = new WizardryArmorProfile(
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readDouble(),
                    buffer.readInt(),
                    buffer.readDouble(),
                    buffer.readInt(),
                    buffer.readDouble(),
                    buffer.readInt(),
                    buffer.readDouble()
            );
        } catch (RuntimeException exception) {
            malformed = true;
            spellCosts.clear();
            itemRules.clear();
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(revision);
        buffer.writeInt(baseMaximum);
        buffer.writeDouble(baseRegeneration);
        buffer.writeLong(lockoutTicks);
        buffer.writeInt(smallFlask);
        buffer.writeInt(mediumFlask);
        buffer.writeInt(largeFlask);

        buffer.writeInt(spellCosts.size());
        for (Map.Entry<ResourceLocation, Double> entry : spellCosts.entrySet()) {
            ByteBufUtils.writeUTF8String(buffer, entry.getKey().toString());
            buffer.writeDouble(entry.getValue());
        }

        buffer.writeInt(itemRules.size());
        for (ConfiguredItemRule rule : itemRules) {
            ByteBufUtils.writeUTF8String(buffer, rule.getItemId().toString());
            buffer.writeInt(rule.getMetadata());
            buffer.writeInt(rule.getMaximumMana());
            buffer.writeDouble(rule.getFlatRegeneration());
            buffer.writeDouble(rule.getPercentageRegeneration());
        }

        buffer.writeBoolean(armorProfile.isEnabled());
        buffer.writeInt(armorProfile.getHelmetMaximum());
        buffer.writeDouble(armorProfile.getHelmetRegeneration());
        buffer.writeInt(armorProfile.getChestMaximum());
        buffer.writeDouble(armorProfile.getChestRegeneration());
        buffer.writeInt(armorProfile.getLegsMaximum());
        buffer.writeDouble(armorProfile.getLegsRegeneration());
        buffer.writeInt(armorProfile.getBootsMaximum());
        buffer.writeDouble(armorProfile.getBootsRegeneration());
    }

    public boolean isValid() {
        if (malformed
                || revision < 0L
                || baseMaximum < 1
                || !Double.isFinite(baseRegeneration)
                || baseRegeneration < 0.0D
                || lockoutTicks < 0L
                || smallFlask < 0
                || mediumFlask < 0
                || largeFlask < 0
                || spellCosts.size() > MAX_TABLE_ENTRIES
                || itemRules.size() > MAX_TABLE_ENTRIES) {
            return false;
        }
        for (double cost : spellCosts.values()) {
            if (!SpellCostCompiler.isSupportedCost(cost)) {
                return false;
            }
        }
        return true;
    }

    public long getRevision() {
        return revision;
    }

    public int getBaseMaximum() {
        return baseMaximum;
    }

    public double getBaseRegeneration() {
        return baseRegeneration;
    }

    public long getLockoutTicks() {
        return lockoutTicks;
    }

    public int getSmallFlask() {
        return smallFlask;
    }

    public int getMediumFlask() {
        return mediumFlask;
    }

    public int getLargeFlask() {
        return largeFlask;
    }

    public Map<ResourceLocation, Double> getSpellCosts() {
        return new LinkedHashMap<>(spellCosts);
    }

    public List<ConfiguredItemRule> getItemRules() {
        return new ArrayList<>(itemRules);
    }

    public WizardryArmorProfile getArmorProfile() {
        return armorProfile;
    }

    private static int boundedCount(int count) {
        if (count < 0 || count > MAX_TABLE_ENTRIES) {
            throw new IllegalArgumentException("Packet table count is out of bounds: " + count);
        }
        return count;
    }

    private static ResourceLocation readIdentifier(ByteBuf buffer) {
        String value = ByteBufUtils.readUTF8String(buffer);
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("Registry identifier is too long.");
        }
        return new ResourceLocation(value);
    }

    public static final class Handler implements IMessageHandler<GameplayRulesMessage, IMessage> {

        @Override
        public IMessage onMessage(GameplayRulesMessage message, MessageContext context) {
            if (!message.isValid()) {
                JustAnotherWizardryManaSystemMod.LOGGER.warn(
                        "Discarded invalid gameplay-rules packet revision {}.",
                        message.revision
                );
                return null;
            }
            IThreadListener thread = FMLCommonHandler.instance().getWorldThread(context.netHandler);
            thread.addScheduledTask(
                    () -> JustAnotherWizardryManaSystemMod.proxy.acceptGameplayRules(message)
            );
            return null;
        }
    }
}
