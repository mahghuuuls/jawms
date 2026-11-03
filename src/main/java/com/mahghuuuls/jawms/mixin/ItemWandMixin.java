package com.mahghuuuls.jawms.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.internal.casting.ParticipantUseService;
import com.mahghuuuls.jawms.internal.casting.CastCostRegistry;
import com.mahghuuuls.jawms.internal.casting.FinishContinuation;
import com.mahghuuuls.jawms.internal.casting.WizardryWandBridge;
import electroblob.wizardry.item.ItemWand;
import electroblob.wizardry.spell.Spell;
import electroblob.wizardry.util.SpellModifiers;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ItemWand.class, remap = false)
public abstract class ItemWandMixin {

    /**
     * Wizardry stores wand mana as inverse item damage. Suppress only the
     * inherited durability presentation; the stored value remains untouched.
     */
    public boolean showDurabilityBar(ItemStack stack) {
        return false;
    }

    /** Suppresses only the wand-mana line in the arcane workbench tooltip. */
    public boolean showManaInWorkbench(EntityPlayer player, ItemStack stack) {
        return false;
    }

    private static final String CAN_CAST =
            "canCast(Lnet/minecraft/item/ItemStack;"
                    + "Lelectroblob/wizardry/spell/Spell;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;"
                    + "ILelectroblob/wizardry/util/SpellModifiers;)Z";
    private static final String CAST =
            "cast(Lnet/minecraft/item/ItemStack;"
                    + "Lelectroblob/wizardry/spell/Spell;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;"
                    + "ILelectroblob/wizardry/util/SpellModifiers;)Z";
    private static final String RIGHT_CLICK =
            "onItemRightClick(Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;"
                    + "Lnet/minecraft/util/EnumHand;)Lnet/minecraft/util/ActionResult;";
    private static final String USING_TICK =
            "onUsingTick(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/entity/EntityLivingBase;I)V";
    private static final String STOPPED_USING =
            "onPlayerStoppedUsing(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/entity/EntityLivingBase;I)V";
    private static final String GET_MANA =
            "getMana(Lnet/minecraft/item/ItemStack;)I";
    private static final String SET_MANA =
            "setMana(Lnet/minecraft/item/ItemStack;I)V";
    private static final String ATTRIBUTE_MODIFIERS =
            "getAttributeModifiers(Lnet/minecraft/inventory/EntityEquipmentSlot;"
                    + "Lnet/minecraft/item/ItemStack;)Lcom/google/common/collect/Multimap;";
    private static final String HIT_ENTITY =
            "hitEntity(Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/entity/EntityLivingBase;"
                    + "Lnet/minecraft/entity/EntityLivingBase;)Z";
    private static final String CREATIVE_BLOCK_BREAKING =
            "canDestroyBlockInCreative(Lnet/minecraft/world/World;"
                    + "Lnet/minecraft/util/math/BlockPos;"
                    + "Lnet/minecraft/item/ItemStack;"
                    + "Lnet/minecraft/entity/player/EntityPlayer;)Z";
    private static final String ATTACK_EVENT =
            "onAttackEntityEvent(Lnet/minecraftforge/event/entity/player/AttackEntityEvent;)V";

    @Inject(
            method = GET_MANA,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$reportWandManaFull(
            ItemStack stack, CallbackInfoReturnable<Integer> callback
    ) {
        callback.setReturnValue(((ItemWand) (Object) this).getManaCapacity(stack));
    }

    @Inject(
            method = SET_MANA,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$preserveHistoricalWandMana(
            ItemStack stack, int mana, CallbackInfo callback
    ) {
        callback.cancel();
    }

    @Redirect(
            method = ATTRIBUTE_MODIFIERS,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/util/WandHelper;"
                            + "getUpgradeLevel(Lnet/minecraft/item/ItemStack;"
                            + "Lnet/minecraft/item/Item;)I",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private int jawms$disableMeleeAttributes(ItemStack stack, Item upgrade) {
        return 0;
    }

    @Inject(
            method = HIT_ENTITY,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = true
    )
    private void jawms$disableMeleeManaConsumption(
            ItemStack stack,
            EntityLivingBase target,
            EntityLivingBase wielder,
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(true);
    }

    @Inject(
            method = CREATIVE_BLOCK_BREAKING,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$allowOrdinaryCreativeBlockBreaking(
            World world,
            BlockPos position,
            ItemStack stack,
            EntityPlayer player,
            CallbackInfoReturnable<Boolean> callback
    ) {
        callback.setReturnValue(true);
    }

    @Inject(
            method = ATTACK_EVENT,
            at = @At("HEAD"),
            cancellable = true,
            require = 1,
            expect = 1,
            remap = false
    )
    private static void jawms$disableMeleeFeedback(
            AttackEntityEvent event, CallbackInfo callback
    ) {
        callback.cancel();
    }

    @ModifyVariable(
            method = CAN_CAST,
            at = @At(value = "STORE", ordinal = 0),
            index = 7,
            require = 1,
            expect = 1,
            remap = false
    )
    private int jawms$replaceCanCastCost(
            int wizardryCost,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        return WizardryWandBridge.replaceCalculatedCost(
                wizardryCost,
                spell,
                caster,
                hand,
                modifiers
        );
    }

    @Redirect(
            method = CAN_CAST,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;"
                            + "getMana(Lnet/minecraft/item/ItemStack;)I",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private int jawms$replaceWandAffordability(
            ItemWand wand,
            ItemStack queriedStack,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        return WizardryWandBridge.getAvailableManaForPrecheck(
                wand.getMana(queriedStack),
                spell,
                caster,
                hand,
                castingTick,
                modifiers
        );
    }

    @ModifyVariable(
            method = CAST,
            at = @At(value = "STORE", ordinal = 0),
            index = 8,
            require = 1,
            expect = 1,
            remap = false
    )
    private int jawms$replaceSuccessfulCastCost(
            int wizardryCost,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        return WizardryWandBridge.replaceCalculatedCost(
                wizardryCost,
                spell,
                caster,
                hand,
                modifiers
        );
    }

    @Redirect(
            method = CAST,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/spell/Spell;"
                            + "cast(Lnet/minecraft/world/World;"
                            + "Lnet/minecraft/entity/player/EntityPlayer;"
                            + "Lnet/minecraft/util/EnumHand;"
                            + "ILelectroblob/wizardry/util/SpellModifiers;)Z",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private boolean jawms$castWithReservation(
            Spell spell,
            World world,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers,
            ItemStack owningStack,
            Spell owningSpell,
            EntityPlayer owningCaster,
            EnumHand owningHand,
            int owningCastingTick,
            SpellModifiers owningModifiers
    ) {
        return WizardryWandBridge.castWithReservation(
                owningStack,
                spell,
                world,
                caster,
                hand,
                castingTick,
                modifiers
        );
    }

    @Redirect(
            method = CAST,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;"
                            + "consumeMana(Lnet/minecraft/item/ItemStack;"
                            + "ILnet/minecraft/entity/EntityLivingBase;)V",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$commitSuccessfulDeduction(
            ItemWand wand,
            ItemStack consumedStack,
            int wizardryCost,
            EntityLivingBase wielder,
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers
    ) {
        WizardryWandBridge.commitFromSuccessfulDeduction(
                wand,
                consumedStack,
                wizardryCost,
                wielder,
                spell
        );
    }

    @Inject(
            method = CAST,
            at = @At(
                    value = "FIELD",
                    target = "Lelectroblob/wizardry/spell/Spell;isContinuous:Z",
                    ordinal = 3,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$commitZeroCostSuccess(
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers,
            CallbackInfoReturnable<Boolean> callback
    ) {
        WizardryWandBridge.commitZeroCostIfPending(caster, spell);
    }

    @WrapMethod(method = CAST, require = 1, expect = 1, remap = false)
    private boolean jawms$cleanupReservation(
            ItemStack stack,
            Spell spell,
            EntityPlayer caster,
            EnumHand hand,
            int castingTick,
            SpellModifiers modifiers,
            Operation<Boolean> original
    ) {
        int initialDepth = WizardryWandBridge.captureAttemptDepth();
        if (!caster.world.isRemote && !WizardryWandBridge.isCastAllowed()) return false;
        ParticipantUseService.Scope scope = caster.world.isRemote || CastCostRegistry.instance().isEmpty()
                ? null : ParticipantUseService.enterCast(caster, stack, spell, hand);
        Throwable primary = null;
        try {
            if (scope != null && !ParticipantUseService.supportedDepth()) return false;
            return original.call(stack, spell, caster, hand, castingTick, modifiers);
        } catch (RuntimeException | Error fault) {
            primary = fault;
            throw fault;
        } finally {
            try {
                try { if (scope != null) ParticipantUseService.exitCast(scope); }
                finally {
                    try { WizardryWandBridge.cleanupToDepth(caster, initialDepth); }
                    finally { WizardryWandBridge.endDiscreteCast(caster, hand, spell); }
                }
            } catch (RuntimeException | Error fault) {
                if (primary == null) throw fault;
                if (primary != fault) primary.addSuppressed(fault);
            }
        }
    }

    @Inject(method = CAN_CAST, at = @At("HEAD"), cancellable = true, require = 1, expect = 1, remap = false)
    private void jawms$guardParticipantRetry(ItemStack stack, Spell spell, EntityPlayer player,
            EnumHand hand, int tick, SpellModifiers modifiers, CallbackInfoReturnable<Boolean> callback) {
        if ((player.world.isRemote && JustAnotherWizardryManaSystemMod.proxy.isParticipantInputBlocked())
                || (!player.world.isRemote && ParticipantUseService.retryBlocked(player))) callback.setReturnValue(false);
    }

    @WrapMethod(method = RIGHT_CLICK, require = 1, expect = 1, remap = true)
    private ActionResult<ItemStack> jawms$physicalOrigin(World world, EntityPlayer player, EnumHand hand,
            Operation<ActionResult<ItemStack>> original) {
        if (!world.isRemote && ParticipantUseService.retryBlocked(player)) {
            return new ActionResult<>(net.minecraft.util.EnumActionResult.FAIL, player.getHeldItem(hand));
        }
        if (world.isRemote) {
            ActionResult<ItemStack> result = original.call(world, player, hand);
            if (result.getType() == net.minecraft.util.EnumActionResult.SUCCESS)
                JustAnotherWizardryManaSystemMod.proxy.participantInputStarted(player, hand);
            return result;
        }
        ParticipantUseService.enterOrigin(player, player.getHeldItem(hand), hand, true);
        try {
            ActionResult<ItemStack> result = original.call(world, player, hand);
            if (result.getType() == net.minecraft.util.EnumActionResult.SUCCESS) ParticipantUseService.bindStarted(player, hand);
            return result;
        } finally { ParticipantUseService.exitOrigin(); }
    }

    @WrapMethod(method = USING_TICK, require = 1, expect = 1, remap = false)
    private void jawms$usingOrigin(ItemStack stack, EntityLivingBase user, int count, Operation<Void> original) {
        if (user instanceof EntityPlayer && user.world.isRemote)
            JustAnotherWizardryManaSystemMod.proxy.observeParticipantUseArgument((EntityPlayer) user, stack);
        if (!(user instanceof EntityPlayer) || user.world.isRemote) { original.call(stack, user, count); return; }
        EntityPlayer player = (EntityPlayer) user;
        if (ParticipantUseService.retryBlocked(player)) { player.resetActiveHand(); return; }
        ParticipantUseService.enterOrigin(player, stack, player.getActiveHand(), false);
        try { original.call(stack, user, count); }
        finally { ParticipantUseService.exitOrigin(); }
    }

    @Inject(method = RIGHT_CLICK, at = @At("HEAD"), require = 1, expect = 1, remap = true)
    private void jawms$beginPhysicalCast(
            World world, EntityPlayer player, EnumHand hand,
            CallbackInfoReturnable<ActionResult<ItemStack>> callback
    ) {
        WizardryWandBridge.beginPhysicalCast(player, hand);
    }

    @Inject(
            method = RIGHT_CLICK,
            at = @At("RETURN"),
            require = 4,
            expect = 4,
            remap = true
    )
    private void jawms$beginContinuousCast(
            World world,
            EntityPlayer player,
            EnumHand hand,
            CallbackInfoReturnable<ActionResult<ItemStack>> callback
    ) {
        WizardryWandBridge.beginContinuousCast(
                player,
                hand,
                player.getHeldItem(hand),
                callback.getReturnValue()
        );
    }

    @Redirect(
            method = USING_TICK,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/EntityPlayer;stopActiveHand()V",
                    ordinal = 0,
                    remap = true
            ),
            require = 1,
            expect = 1,
            remap = false
    )
    private void jawms$interruptContinuousCast(
            EntityPlayer player,
            ItemStack stack,
            EntityLivingBase user,
            int count
    ) {
        Spell spell = electroblob.wizardry.util.WandHelper.getCurrentSpell(stack);
        WizardryWandBridge.interruptContinuousCast(
                player,
                spell,
                player.getActiveHand()
        );
        player.stopActiveHand();
    }

    @Redirect(
            method = STOPPED_USING,
            at = @At(
                    value = "INVOKE",
                    target = "Lelectroblob/wizardry/item/ItemWand;"
                            + "getMana(Lnet/minecraft/item/ItemStack;)I",
                    ordinal = 0,
                    remap = false
            ),
            require = 1,
            expect = 1,
            remap = true
    )
    private int jawms$replaceContinuousFinishAffordability(
            ItemWand wand,
            ItemStack queriedStack,
            ItemStack stack,
            World world,
            EntityLivingBase user,
            int timeLeft
    ) {
        if (!(user instanceof EntityPlayer)) {
            return wand.getMana(queriedStack);
        }
        Spell spell = electroblob.wizardry.util.WandHelper.getCurrentSpell(stack);
        return WizardryWandBridge.getManaForContinuousFinish(
                wand.getMana(queriedStack),
                (EntityPlayer) user,
                spell,
                user.getActiveHand()
        );
    }

    @WrapMethod(method = STOPPED_USING, require = 1, expect = 1, remap = true)
    private void jawms$finishContinuousCast(
            ItemStack stack,
            World world,
            EntityLivingBase user,
            int timeLeft,
            Operation<Void> original
    ) {
        if (!(user instanceof EntityPlayer)) {
            original.call(stack, world, user, timeLeft);
            return;
        }
        EntityPlayer player = (EntityPlayer) user;
        Spell spell = electroblob.wizardry.util.WandHelper.getCurrentSpell(stack);
        EnumHand hand = player.getActiveHand();
        if (!world.isRemote && (ParticipantUseService.suppressesFinish(player, hand, spell)
                || !ParticipantUseService.permitsFinish(player, stack, hand, spell))) return;
        FinishContinuation.Capture capture = world.isRemote
                ? JustAnotherWizardryManaSystemMod.proxy.beginParticipantFinish(player, stack, hand, spell)
                : FinishContinuation.Capture.LEGACY;
        if (capture == FinishContinuation.Capture.SUPPRESSED) return;
        boolean completed = false;
        try {
            original.call(stack, world, user, timeLeft);
            completed = true;
        } finally {
            try {
                if (!world.isRemote) ParticipantUseService.endFinish(player);
                WizardryWandBridge.finishContinuousCast(player, spell, hand);
            } finally {
                if (capture == FinishContinuation.Capture.CAPTURED)
                    JustAnotherWizardryManaSystemMod.proxy.endParticipantFinish(completed);
            }
        }
    }

    @ModifyVariable(method = STOPPED_USING, at = @At("STORE"), index = 7, require = 2, expect = 2, remap = true)
    private SpellModifiers jawms$copyReleaseModifiers(SpellModifiers modifiers) {
        return JustAnotherWizardryManaSystemMod.proxy.isParticipantFinishCaptureActive() ? modifiers.copy() : modifiers;
    }

    @WrapOperation(method = STOPPED_USING, at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/fml/common/eventhandler/EventBus;post(Lnet/minecraftforge/fml/common/eventhandler/Event;)Z", remap = false),
            require = 1, expect = 1, remap = true)
    private boolean jawms$deferFinishEvent(net.minecraftforge.fml.common.eventhandler.EventBus bus,
            net.minecraftforge.fml.common.eventhandler.Event event, Operation<Boolean> original) {
        if (JustAnotherWizardryManaSystemMod.proxy.deferParticipantFinishAction(() -> original.call(bus, event))) return false;
        return original.call(bus, event);
    }

    @WrapOperation(method = STOPPED_USING, at = @At(value = "INVOKE",
            target = "Lelectroblob/wizardry/spell/Spell;finishCasting(Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;DDDLnet/minecraft/util/EnumFacing;ILelectroblob/wizardry/util/SpellModifiers;)V", remap = false),
            require = 1, expect = 1, remap = true)
    private void jawms$deferFinishBody(Spell spell, World world, EntityLivingBase user, double x, double y, double z,
            net.minecraft.util.EnumFacing facing, int tick, SpellModifiers modifiers, Operation<Void> original,
            @Local(argsOnly = true) ItemStack stack) {
        Runnable action = () -> original.call(spell, world, user, x, y, z, facing, tick, modifiers);
        if (world.isRemote && JustAnotherWizardryManaSystemMod.proxy.deferParticipantFinishAction(action)) return;
        action.run();
        if (!world.isRemote && user instanceof EntityPlayer) ParticipantUseService.finishedBody((EntityPlayer) user, stack, spell);
    }

    @WrapOperation(method = STOPPED_USING, at = @At(value = "INVOKE", target = "Lelectroblob/wizardry/spell/Spell;getCooldown()I", remap = false),
            require = 1, expect = 1, remap = true)
    private int jawms$captureBaseCooldown(Spell spell, Operation<Integer> original) {
        int value = original.call(spell);
        JustAnotherWizardryManaSystemMod.proxy.captureParticipantBaseCooldown(value);
        return value;
    }

    @WrapOperation(method = STOPPED_USING, at = @At(value = "INVOKE", target = "Lelectroblob/wizardry/util/WandHelper;setCurrentCooldown(Lnet/minecraft/item/ItemStack;I)V", remap = false),
            require = 1, expect = 1, remap = true)
    private void jawms$deferFinishCooldown(ItemStack stack, int value, Operation<Void> original,
            @Local SpellModifiers modifiers) {
        if (!JustAnotherWizardryManaSystemMod.proxy.deferParticipantCooldown(value, modifiers,
                (representative, cooldown) -> original.call(representative, cooldown))) original.call(stack, value);
    }
}
