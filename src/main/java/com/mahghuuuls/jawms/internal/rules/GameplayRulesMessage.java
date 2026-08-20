package com.mahghuuuls.jawms.internal.rules;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.ManaContribution;
import electroblob.wizardry.constants.Element;
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
import java.nio.charset.StandardCharsets;

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
    private List<ProviderPreviewEntry> providerPreviews = new ArrayList<>();
    private WizardryArmorProfile armorProfile = WizardryArmorProfile.DEFAULT;
    private List<ResourceLocation> generatedWizardryArmorIds = new ArrayList<>();
    private double efficiencyScale = GameplayRules.DEFAULT_EFFICIENCY_SCALE;
    private WizardryArmorEfficiencyProfile armorEfficiencyProfile = WizardryArmorEfficiencyProfile.DEFAULT;
    private WizardryIntegrationRules integrationRules = WizardryIntegrationRules.defaults(0L);
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
        providerPreviews = new ArrayList<>(rules.getProviderPreviews());
        armorProfile = rules.getArmorProfile();
        generatedWizardryArmorIds = new ArrayList<>(rules.getGeneratedWizardryArmorIds());
        efficiencyScale = rules.getEfficiencyScale();
        armorEfficiencyProfile = rules.getArmorEfficiencyProfile();
        integrationRules = rules.getWizardryIntegrationRules();
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
                        readContribution(buffer)
                ));
            }

            int previewCount = boundedCount(buffer.readInt());
            providerPreviews = new ArrayList<>();
            for (int index = 0; index < previewCount; index++) {
                ResourceLocation providerId = readIdentifier(buffer);
                ResourceLocation targetItemId = readIdentifier(buffer);
                int excludedCount = boundedCount(buffer.readInt());
                java.util.SortedSet<Integer> excludedMetadata = new java.util.TreeSet<>();
                int previousMetadata = -1;
                for (int metadataIndex = 0; metadataIndex < excludedCount; metadataIndex++) {
                    int metadata = buffer.readInt();
                    if (metadata <= previousMetadata || metadata > Short.MAX_VALUE) {
                        throw new IllegalArgumentException(
                                "Provider-preview metadata exclusions are not strictly ordered."
                        );
                    }
                    excludedMetadata.add(metadata);
                    previousMetadata = metadata;
                }
                providerPreviews.add(new ProviderPreviewEntry(
                        providerId,
                        targetItemId,
                        readContribution(buffer),
                        excludedMetadata
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
            int armorCount = boundedCount(buffer.readInt());
            generatedWizardryArmorIds = new ArrayList<>();
            for (int index = 0; index < armorCount; index++) {
                generatedWizardryArmorIds.add(readIdentifier(buffer));
            }
            efficiencyScale = buffer.readDouble();
            armorEfficiencyProfile = new WizardryArmorEfficiencyProfile(
                    buffer.readBoolean(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble()
            );
            integrationRules = readIntegrationRules(buffer);
        } catch (RuntimeException exception) {
            malformed = true;
            spellCosts.clear();
            itemRules.clear();
            providerPreviews.clear();
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
            writeContribution(buffer, rule.getContribution());
        }

        buffer.writeInt(providerPreviews.size());
        for (ProviderPreviewEntry preview : providerPreviews) {
            ByteBufUtils.writeUTF8String(buffer, preview.getProviderId().toString());
            ByteBufUtils.writeUTF8String(buffer, preview.getTargetItemId().toString());
            buffer.writeInt(preview.getExcludedMetadata().size());
            for (int metadata : preview.getExcludedMetadata()) {
                buffer.writeInt(metadata);
            }
            writeContribution(buffer, preview.getContribution());
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
        buffer.writeInt(generatedWizardryArmorIds.size());
        for (ResourceLocation id : generatedWizardryArmorIds) {
            ByteBufUtils.writeUTF8String(buffer, id.toString());
        }
        buffer.writeDouble(efficiencyScale);
        buffer.writeBoolean(armorEfficiencyProfile.isEnabled());
        buffer.writeDouble(armorEfficiencyProfile.getWizard());
        buffer.writeDouble(armorEfficiencyProfile.getSage());
        buffer.writeDouble(armorEfficiencyProfile.getBattlemage());
        buffer.writeDouble(armorEfficiencyProfile.getWarlock());
        buffer.writeDouble(armorEfficiencyProfile.getSageNonmatchingFullSet());
        writeIntegrationRules(buffer, integrationRules);
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
                || !Double.isFinite(efficiencyScale)
                || efficiencyScale <= 0.0D
                || spellCosts.size() > MAX_TABLE_ENTRIES
                || itemRules.size() > MAX_TABLE_ENTRIES
                || providerPreviews.size() > MAX_TABLE_ENTRIES
                || generatedWizardryArmorIds.size() > MAX_TABLE_ENTRIES) {
            return false;
        }
        for (double cost : spellCosts.values()) {
            if (!SpellCostCompiler.isSupportedCost(cost)) {
                return false;
            }
        }
        ResourceLocation previousProvider = null;
        for (ProviderPreviewEntry preview : providerPreviews) {
            if (preview.getExcludedMetadata().size() > MAX_TABLE_ENTRIES) {
                return false;
            }
            if (previousProvider != null
                    && previousProvider.compareTo(preview.getProviderId()) >= 0) {
                return false;
            }
            previousProvider = preview.getProviderId();
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

    public List<ProviderPreviewEntry> getProviderPreviews() {
        return new ArrayList<>(providerPreviews);
    }

    public WizardryArmorProfile getArmorProfile() {
        return armorProfile;
    }

    public List<ResourceLocation> getGeneratedWizardryArmorIds() {
        return new ArrayList<>(generatedWizardryArmorIds);
    }

    public double getEfficiencyScale() {
        return efficiencyScale;
    }

    public WizardryArmorEfficiencyProfile getArmorEfficiencyProfile() {
        return armorEfficiencyProfile;
    }

    public WizardryIntegrationRules getIntegrationRules() {
        return integrationRules;
    }

    private static void writeIntegrationRules(
            ByteBuf buffer, WizardryIntegrationRules rules
    ) {
        buffer.writeLong(rules.getRevision());
        buffer.writeBoolean(rules.isStorageEnabled());
        buffer.writeDouble(rules.getStorageManaIncreasePerLevel());
        buffer.writeBoolean(rules.isCondenserEnabled());
        buffer.writeDouble(rules.getCondenserRegenerationIncreasePerLevel());
        buffer.writeBoolean(rules.isSiphonEnabled());
        buffer.writeInt(rules.getSiphonManaPerLevel());
        buffer.writeByte(rules.getHeldWandAggregation().ordinal());
        buffer.writeBoolean(rules.isMeleeEnabled());
        buffer.writeDouble(rules.getMeleeDamagePerLevel());
        buffer.writeInt(rules.getMeleeManaPerLevel());
        buffer.writeBoolean(rules.isCondensingEnabled());
        buffer.writeDouble(rules.getCondensingRegenerationPerRing());
        buffer.writeBoolean(rules.isSiphoningEnabled());
        buffer.writeDouble(rules.getSiphoningIncrease());
        buffer.writeBoolean(rules.isExtractionEnabled());
        buffer.writeInt(rules.getExtractionMana());
        buffer.writeBoolean(rules.isPerfectionistEnabled());
        buffer.writeDouble(rules.getPerfectionistRefund());
        buffer.writeBoolean(rules.isDemonicSealEnabled());
        buffer.writeInt(rules.getDemonicSealManaPerHunger());
        buffer.writeInt(rules.getDemonicSealMinimumHunger());
        buffer.writeBoolean(rules.isDemonicSealContinuous());
        buffer.writeBoolean(rules.isFontEnabled());
        buffer.writeDouble(rules.getFontRegenerationPerLevel());
        buffer.writeBoolean(rules.isFontLockoutPermission());
        buffer.writeBoolean(rules.isFontContinuousPermission());
    }

    private static WizardryIntegrationRules readIntegrationRules(ByteBuf buffer) {
        long integrationRevision = buffer.readLong();
        boolean storageEnabled = buffer.readBoolean();
        double storageIncrease = buffer.readDouble();
        boolean condenserEnabled = buffer.readBoolean();
        double condenserIncrease = buffer.readDouble();
        boolean siphonEnabled = buffer.readBoolean();
        int siphonMana = buffer.readInt();
        int aggregationOrdinal = buffer.readUnsignedByte();
        if (aggregationOrdinal >= WizardryIntegrationRules.HeldWandAggregation.values().length) {
            throw new IllegalArgumentException("Invalid held-wand aggregation ordinal.");
        }
        return new WizardryIntegrationRules(
                integrationRevision,
                storageEnabled, storageIncrease,
                condenserEnabled, condenserIncrease,
                siphonEnabled, siphonMana,
                WizardryIntegrationRules.HeldWandAggregation.values()[aggregationOrdinal],
                buffer.readBoolean(), buffer.readDouble(), buffer.readInt(),
                buffer.readBoolean(), buffer.readDouble(),
                buffer.readBoolean(), buffer.readDouble(),
                buffer.readBoolean(), buffer.readInt(),
                buffer.readBoolean(), buffer.readDouble(),
                buffer.readBoolean(), buffer.readInt(), buffer.readInt(), buffer.readBoolean(),
                buffer.readBoolean(), buffer.readDouble(), buffer.readBoolean(), buffer.readBoolean()
        );
    }

    private static int boundedCount(int count) {
        if (count < 0 || count > MAX_TABLE_ENTRIES) {
            throw new IllegalArgumentException("Packet table count is out of bounds: " + count);
        }
        return count;
    }

    private static ResourceLocation readIdentifier(ByteBuf buffer) {
        int byteLength = ByteBufUtils.readVarInt(buffer, 2);
        if (byteLength < 1 || byteLength > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("Registry identifier is too long.");
        }
        byte[] bytes = new byte[byteLength];
        buffer.readBytes(bytes);
        String value = new String(bytes, StandardCharsets.UTF_8);
        if (value.length() > MAX_IDENTIFIER_LENGTH) {
            throw new IllegalArgumentException("Registry identifier is too long.");
        }
        return new ResourceLocation(value);
    }

    private static void writeContribution(ByteBuf buffer, ManaContribution contribution) {
        buffer.writeInt(contribution.getFlatMaximumMana());
        buffer.writeDouble(contribution.getMaximumManaIncrease());
        buffer.writeDouble(contribution.getMaximumManaReduction());
        buffer.writeDouble(contribution.getFlatRegeneration());
        buffer.writeDouble(contribution.getRegenerationIncrease());
        buffer.writeDouble(contribution.getRegenerationReduction());
        buffer.writeDouble(contribution.getFlatLockoutSeconds());
        buffer.writeDouble(contribution.getLockoutIncrease());
        buffer.writeDouble(contribution.getLockoutReduction());
        buffer.writeDouble(contribution.getGlobalSpellEfficiency());
        buffer.writeInt(contribution.getElementSpellEfficiency().size());
        for (Map.Entry<Element, Double> entry
                : contribution.getElementSpellEfficiency().entrySet()) {
            buffer.writeByte(entry.getKey().ordinal());
            buffer.writeDouble(entry.getValue());
        }
        buffer.writeDouble(contribution.getGlobalSpellCostMultiplier());
        buffer.writeInt(contribution.getElementSpellCostMultipliers().size());
        for (Map.Entry<Element, Double> entry
                : contribution.getElementSpellCostMultipliers().entrySet()) {
            buffer.writeByte(entry.getKey().ordinal());
            buffer.writeDouble(entry.getValue());
        }
        buffer.writeBoolean(contribution.canRegenerateDuringPostCastLockout());
        buffer.writeBoolean(contribution.canRegenerateDuringContinuousCasting());
    }

    private static ManaContribution readContribution(ByteBuf buffer) {
        ManaContribution.Builder builder = ManaContribution.builder()
                .flatMaximumMana(buffer.readInt())
                .maximumManaIncrease(buffer.readDouble())
                .maximumManaReduction(buffer.readDouble())
                .flatRegeneration(buffer.readDouble())
                .regenerationIncrease(buffer.readDouble())
                .regenerationReduction(buffer.readDouble())
                .flatLockoutSeconds(buffer.readDouble())
                .lockoutIncrease(buffer.readDouble())
                .lockoutReduction(buffer.readDouble())
                .globalSpellEfficiency(buffer.readDouble());
        int elementCount = buffer.readInt();
        if (elementCount < 0 || elementCount > Element.values().length) {
            throw new IllegalArgumentException("Element efficiency count is out of bounds.");
        }
        boolean[] seen = new boolean[Element.values().length];
        for (int index = 0; index < elementCount; index++) {
            int ordinal = buffer.readUnsignedByte();
            if (ordinal >= seen.length || seen[ordinal]) {
                throw new IllegalArgumentException("Invalid or duplicate element efficiency.");
            }
            seen[ordinal] = true;
            builder.elementSpellEfficiency(Element.values()[ordinal], buffer.readDouble());
        }
        builder.globalSpellCostMultiplier(buffer.readDouble());
        elementCount = buffer.readInt();
        if (elementCount < 0 || elementCount > Element.values().length) {
            throw new IllegalArgumentException("Element cost-multiplier count is out of bounds.");
        }
        seen = new boolean[Element.values().length];
        for (int index = 0; index < elementCount; index++) {
            int ordinal = buffer.readUnsignedByte();
            if (ordinal >= seen.length || seen[ordinal]) {
                throw new IllegalArgumentException("Invalid or duplicate element cost multiplier.");
            }
            seen[ordinal] = true;
            builder.elementSpellCostMultiplier(Element.values()[ordinal], buffer.readDouble());
        }
        return builder
                .regenerateDuringPostCastLockout(buffer.readBoolean())
                .regenerateDuringContinuousCasting(buffer.readBoolean())
                .build();
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
