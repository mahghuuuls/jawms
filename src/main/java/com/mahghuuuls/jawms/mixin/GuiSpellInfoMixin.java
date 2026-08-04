package com.mahghuuuls.jawms.mixin;

import com.mahghuuuls.jawms.client.ClientSpellCostPresentation;
import com.mahghuuuls.jawms.client.WizardryIntegrationPresentation;
import electroblob.wizardry.client.gui.GuiSpellInfo;
import electroblob.wizardry.spell.Spell;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiSpellInfo.class, remap = false)
public abstract class GuiSpellInfoMixin {

    private static final String DRAW_FOREGROUND =
            "drawForegroundLayer(IIII)V";

    @Shadow
    public abstract Spell getSpell();

    @Redirect(
            method = DRAW_FOREGROUND,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/I18n;"
                            + "format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
                    ordinal = 3,
                    remap = true
            ),
            require = 1,
            expect = 1
    )
    private String jawms$replaceInstantBaseManaCost(
            String translationKey,
            Object[] parameters
    ) {
        return formatBaseManaCost(false);
    }

    @Redirect(
            method = DRAW_FOREGROUND,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/I18n;"
                            + "format(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;",
                    ordinal = 4,
                    remap = true
            ),
            require = 1,
            expect = 1
    )
    private String jawms$replaceContinuousBaseManaCost(
            String translationKey,
            Object[] parameters
    ) {
        return formatBaseManaCost(true);
    }

    private String formatBaseManaCost(boolean continuous) {
        String cost = ClientSpellCostPresentation.formatBaseCost(
                ClientSpellCostPresentation.getConfiguredBaseCost(getSpell())
        );
        return I18n.format(
                continuous
                        ? "gui.jawms.spell_book.base_mana_cost_continuous"
                        : "gui.jawms.spell_book.base_mana_cost",
                cost
        );
    }

    @Redirect(
            method = DRAW_FOREGROUND,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/spell/Spell;getDescription()Ljava/lang/String;",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private String jawms$appendIntegrationSpellInformation(Spell spell) {
        return WizardryIntegrationPresentation.appendSpellInformation(
                spell, spell.getDescription()
        );
    }
}
