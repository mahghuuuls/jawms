package com.mahghuuuls.jawms.internal.integration.wizardry.effect;

import electroblob.wizardry.entity.projectile.EntityForceOrb;
import electroblob.wizardry.util.IElementalDamage;
import electroblob.wizardry.util.MagicDamage;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;

/** Conservative Wizardry sorcery attribution for bounded kill-time effects. */
public final class WizardrySorceryAttribution {

    public enum Category {
        FORCE_DAMAGE,
        FORCE_ORB,
        WRONG_PLAYER,
        AMBIGUOUS
    }

    private WizardrySorceryAttribution() {
    }

    public static Category classify(DamageSource source, EntityPlayer creditedPlayer) {
        if (source == null || creditedPlayer == null
                || source.getTrueSource() != creditedPlayer) {
            return Category.WRONG_PLAYER;
        }
        boolean forceDamage = source instanceof IElementalDamage
                && ((IElementalDamage) source).getType() == MagicDamage.DamageType.FORCE;
        Entity immediate = source.getImmediateSource();
        boolean forceOrb = immediate instanceof EntityForceOrb;
        boolean forceOrbOwnedByPlayer = forceOrb
                && ((EntityForceOrb) immediate).getThrower() == creditedPlayer;
        return classifyEvidence(true, forceDamage, forceOrb, forceOrbOwnedByPlayer);
    }

    static Category classifyEvidence(
            boolean playerCredited,
            boolean forceDamage,
            boolean forceOrb,
            boolean forceOrbOwnedByPlayer
    ) {
        if (!playerCredited) {
            return Category.WRONG_PLAYER;
        }
        if (forceDamage) {
            return Category.FORCE_DAMAGE;
        }
        if (forceOrb && forceOrbOwnedByPlayer) {
            return Category.FORCE_ORB;
        }
        return Category.AMBIGUOUS;
    }

    public static boolean isSorcery(Category category) {
        return category == Category.FORCE_DAMAGE || category == Category.FORCE_ORB;
    }
}
