package com.mahghuuuls.jawms.internal.integration.wizardry;

import com.mahghuuuls.jawms.JustAnotherWizardryManaSystemMod;
import com.mahghuuuls.jawms.api.IManaService;
import com.mahghuuuls.jawms.api.ManaApi;
import com.mahghuuuls.jawms.api.ManaCauses;
import com.mahghuuuls.jawms.api.ManaMutationResult;
import com.mahghuuuls.jawms.internal.rules.GameplayRules;
import electroblob.wizardry.item.IManaStoringItem;
import electroblob.wizardry.item.ItemManaFlask;
import electroblob.wizardry.item.ItemWizardArmour;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Version-pinned policy bridge for Wizardry 4.3.19 mana flasks. */
public final class WizardryFlaskBridge {

    private WizardryFlaskBridge() {
    }

    public static void refreshCapacities(GameplayRules rules) {
        if (rules == null) {
            throw new NullPointerException("rules");
        }
        refreshCapacities(
                rules.getSmallFlask(),
                rules.getMediumFlask(),
                rules.getLargeFlask()
        );
        JustAnotherWizardryManaSystemMod.LOGGER.info(
                "Refreshed Wizardry mana-flask capacities for JAWMS rules revision {}: {}/{}/{}.",
                rules.getRevision(), rules.getSmallFlask(), rules.getMediumFlask(), rules.getLargeFlask()
        );
    }

    public static void refreshCapacities(int small, int medium, int large) {
        if (small < 0 || medium < 0 || large < 0) {
            throw new IllegalArgumentException("Flask capacities must be non-negative.");
        }
        ItemManaFlask.Size.SMALL.capacity = small;
        ItemManaFlask.Size.MEDIUM.capacity = medium;
        ItemManaFlask.Size.LARGE.capacity = large;
    }

    public static boolean canRestore(EntityPlayer player, int budget) {
        if (player == null || budget <= 0) {
            return false;
        }
        int playerMissing;
        if (player.world.isRemote) {
            playerMissing = JustAnotherWizardryManaSystemMod.proxy.getClientMissingMana();
        } else {
            IManaService service = ManaApi.getManaService();
            playerMissing = Math.max(0, service.getMaximumMana(player) - service.getCurrentMana(player));
        }
        return buildPlan(player, budget, playerMissing).plan.hasDestination();
    }

    /** Applies one authoritative budget and returns whether anything changed. */
    public static boolean restore(EntityPlayer player, int budget) {
        if (player == null || player.world.isRemote || budget <= 0) {
            return false;
        }
        IManaService service = ManaApi.getManaService();
        int playerMissing = Math.max(0, service.getMaximumMana(player) - service.getCurrentMana(player));
        RuntimePlan runtime = buildPlan(player, budget, playerMissing);
        if (!runtime.plan.hasDestination()) {
            return false;
        }

        int playerRestored = 0;
        if (runtime.plan.playerAmount > 0) {
            ManaMutationResult result = service.restoreMana(
                    player, runtime.plan.playerAmount, ManaCauses.FLASK
            );
            playerRestored = result.getActualAmount();
        }

        int remaining = budget - playerRestored;
        int armorRestored = 0;
        if (remaining > 0 && runtime.plan.armorIndex >= 0) {
            ItemStack stack = runtime.armorStacks.get(runtime.plan.armorIndex);
            IManaStoringItem armor = (IManaStoringItem) stack.getItem();
            int before = armor.getMana(stack);
            armor.rechargeMana(stack, remaining);
            armorRestored = Math.max(0, armor.getMana(stack) - before);
        }
        return playerRestored > 0 || armorRestored > 0;
    }

    private static RuntimePlan buildPlan(EntityPlayer player, int budget, int playerMissing) {
        List<ItemStack> stacks = new ArrayList<>();
        List<FlaskAllocation.ArmorState> states = new ArrayList<>();
        for (ItemStack stack : player.inventory.armorInventory) {
            if (stack.isEmpty() || !(stack.getItem() instanceof ItemWizardArmour)
                    || !(stack.getItem() instanceof IManaStoringItem)) {
                continue;
            }
            IManaStoringItem armor = (IManaStoringItem) stack.getItem();
            int capacity = Math.max(0, armor.getManaCapacity(stack));
            int mana = Math.max(0, Math.min(armor.getMana(stack), capacity));
            stacks.add(stack);
            states.add(new FlaskAllocation.ArmorState(mana, capacity));
        }
        return new RuntimePlan(FlaskAllocation.plan(budget, playerMissing, states), stacks);
    }

    private static final class RuntimePlan {

        private final FlaskAllocation.Plan plan;
        private final List<ItemStack> armorStacks;

        private RuntimePlan(FlaskAllocation.Plan plan, List<ItemStack> armorStacks) {
            this.plan = plan;
            this.armorStacks = armorStacks;
        }
    }
}
