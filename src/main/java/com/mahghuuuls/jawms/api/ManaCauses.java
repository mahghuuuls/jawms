package com.mahghuuuls.jawms.api;

import net.minecraft.util.ResourceLocation;

/** Stable built-in causes used by JAWMS mana-state transitions. */
public final class ManaCauses {

    public static final ResourceLocation SPELL_COST = cause("spell_cost");
    public static final ResourceLocation REGENERATION = cause("regeneration");
    public static final ResourceLocation FLASK = cause("flask");
    public static final ResourceLocation WIZARDRY_SIPHON = cause("wizardry_siphon");
    public static final ResourceLocation WIZARDRY_EXTRACTION = cause("wizardry_extraction");
    public static final ResourceLocation WIZARDRY_MELEE = cause("wizardry_melee");
    public static final ResourceLocation COMMAND = cause("command");
    public static final ResourceLocation RULES_UPDATE = cause("rules_update");
    public static final ResourceLocation STAT_CLAMP = cause("stat_clamp");
    public static final ResourceLocation LOCKOUT_START = cause("lockout_start");
    public static final ResourceLocation LOCKOUT_RESTART = cause("lockout_restart");
    public static final ResourceLocation LOCKOUT_END = cause("lockout_end");
    public static final ResourceLocation CONTINUOUS_START = cause("continuous_start");
    public static final ResourceLocation CONTINUOUS_END = cause("continuous_end");
    public static final ResourceLocation LIFECYCLE = cause("lifecycle");
    public static final ResourceLocation DEVELOPMENT_COMMAND = cause("development_command");

    private ManaCauses() {
    }

    private static ResourceLocation cause(String path) {
        return new ResourceLocation("jawms", path);
    }
}
