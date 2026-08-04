package com.mahghuuuls.jawms.client;

import com.mahghuuuls.jawms.internal.rules.ConfiguredItemRule;
import com.mahghuuuls.jawms.internal.rules.GameplayRulesMessage;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorProfile;
import com.mahghuuuls.jawms.internal.rules.WizardryArmorEfficiencyProfile;
import com.mahghuuuls.jawms.internal.rules.WizardryIntegrationRules;
import com.mahghuuuls.jawms.internal.integration.wizardry.WizardryFlaskBridge;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.item.ItemStack;

public final class ClientGameplayRules {

    private static long revision = Long.MIN_VALUE;
    private static int baseMaximum;
    private static double baseRegeneration;
    private static long lockoutTicks;
    private static int smallFlask;
    private static int mediumFlask;
    private static int largeFlask;
    private static Map<ResourceLocation, Double> spellCosts = Collections.emptyMap();
    private static List<ConfiguredItemRule> itemRules = Collections.emptyList();
    private static WizardryArmorProfile armorProfile = WizardryArmorProfile.DEFAULT;
    private static Set<ResourceLocation> generatedWizardryArmorIds = Collections.emptySet();
    private static double efficiencyScale;
    private static WizardryArmorEfficiencyProfile armorEfficiencyProfile = WizardryArmorEfficiencyProfile.DEFAULT;
    private static WizardryIntegrationRules integrationRules = WizardryIntegrationRules.defaults(0L);

    public ClientGameplayRules() {
    }

    public static void accept(GameplayRulesMessage message) {
        if (message.getRevision() <= revision) {
            return;
        }
        revision = message.getRevision();
        baseMaximum = message.getBaseMaximum();
        baseRegeneration = message.getBaseRegeneration();
        lockoutTicks = message.getLockoutTicks();
        smallFlask = message.getSmallFlask();
        mediumFlask = message.getMediumFlask();
        largeFlask = message.getLargeFlask();
        spellCosts = Collections.unmodifiableMap(new LinkedHashMap<>(message.getSpellCosts()));
        itemRules = Collections.unmodifiableList(message.getItemRules());
        armorProfile = message.getArmorProfile();
        generatedWizardryArmorIds = Collections.unmodifiableSet(
                new LinkedHashSet<>(message.getGeneratedWizardryArmorIds())
        );
        efficiencyScale = message.getEfficiencyScale();
        armorEfficiencyProfile = message.getArmorEfficiencyProfile();
        integrationRules = message.getIntegrationRules();
        WizardryFlaskBridge.refreshCapacities(smallFlask, mediumFlask, largeFlask);
    }

    public static long getRevision() {
        return revision;
    }

    public static int getBaseMaximum() {
        return baseMaximum;
    }

    public static double getBaseRegeneration() {
        return baseRegeneration;
    }

    public static long getLockoutTicks() {
        return lockoutTicks;
    }

    public static int getSmallFlask() {
        return smallFlask;
    }

    public static int getMediumFlask() {
        return mediumFlask;
    }

    public static int getLargeFlask() {
        return largeFlask;
    }

    public static Map<ResourceLocation, Double> getSpellCosts() {
        return spellCosts;
    }

    public static double getSpellCost(ResourceLocation spellId, double fallback) {
        if (spellId == null) {
            return fallback;
        }
        Double configured = spellCosts.get(spellId);
        return configured == null ? fallback : configured;
    }

    public static List<ConfiguredItemRule> getItemRules() {
        return itemRules;
    }

    public static WizardryArmorProfile getArmorProfile() {
        return armorProfile;
    }

    public static double getEfficiencyScale() {
        return efficiencyScale;
    }

    public static WizardryArmorEfficiencyProfile getArmorEfficiencyProfile() {
        return armorEfficiencyProfile;
    }

    public static WizardryIntegrationRules getIntegrationRules() {
        return integrationRules;
    }

    public static boolean isGeneratedWizardryArmor(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.getItem().getRegistryName() != null
                && generatedWizardryArmorIds.contains(stack.getItem().getRegistryName());
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clear();
    }

    static void clear() {
        revision = Long.MIN_VALUE;
        baseMaximum = 0;
        baseRegeneration = 0.0D;
        lockoutTicks = 0L;
        smallFlask = 0;
        mediumFlask = 0;
        largeFlask = 0;
        spellCosts = Collections.emptyMap();
        itemRules = Collections.emptyList();
        armorProfile = WizardryArmorProfile.DEFAULT;
        generatedWizardryArmorIds = Collections.emptySet();
        efficiencyScale = 0.0D;
        armorEfficiencyProfile = WizardryArmorEfficiencyProfile.DEFAULT;
        integrationRules = WizardryIntegrationRules.defaults(0L);
    }
}
