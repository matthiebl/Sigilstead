package com.sigilstead.gametest;

import com.sigilstead.core.CoreAttunement;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.core.CoreTier;
import com.sigilstead.core.CoreUpgradeRecipe;
import com.sigilstead.registry.HsComponents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;

/**
 * DESIGN.md §4.3 / §12.4 — the rate-tier upgrade recipe.
 *
 * <p>Written after the fact, which is the reason it exists: §12.4's tier II was repriced from one
 * ingredient to two, and the recipe had no test to notice that its matcher refused mixed payments
 * outright. A price the recipe cannot match is an item players simply cannot craft, with no error
 * anywhere — so the multi-ingredient case is the first thing asserted here.
 *
 * <p>These read the price off {@link CoreTier} rather than hardcoding Blaze Powder and Diamonds, so
 * repricing §12.4 again cannot silently invalidate the suite. What is pinned is the <em>shape</em> of
 * the rule: the exact price upgrades, anything else does not.
 */
public class CoreUpgradeGameTests {

    /** §12.4 — the exact price of the next tier upgrades a locked core, however many ingredients it has. */
    @GameTest
    public void theExactPriceUpgradesALockedCore(GameTestHelper helper) {
        ItemStack core = CoreYieldGameTests.coreStack(CoreFamily.SOUL, "minecraft:zombie");
        ItemStack result = assemble(grid(core, priceOf(CoreTier.TWO)));

        helper.succeedIf(() -> {
            if (result.isEmpty()) {
                throw new AssertionError("tier II's exact price (" + CoreTier.TWO.costs()
                        + ") did not match the upgrade recipe — the tier is uncraftable");
            }
            CoreAttunement after = result.get(HsComponents.CORE_ATTUNEMENT);
            if (after == null || after.tier() != CoreTier.TWO) {
                throw new AssertionError("upgraded core came out at " + (after == null ? "no tier" : after.tier()));
            }
            if (!after.targets(net.minecraft.resources.Identifier.parse("minecraft:zombie"))) {
                throw new AssertionError("the upgrade lost the core's target");
            }
        });
    }

    /** Tier II → III, so the whole §12.4 ladder is walked rather than just its first rung. */
    @GameTest
    public void theLadderRunsAllTheWayToTierThree(GameTestHelper helper) {
        ItemStack core = CoreYieldGameTests.coreStack(CoreFamily.SOUL, "minecraft:skeleton");
        core.set(HsComponents.CORE_ATTUNEMENT, core.get(HsComponents.CORE_ATTUNEMENT).atTier(CoreTier.TWO));

        ItemStack result = assemble(grid(core, priceOf(CoreTier.THREE)));

        helper.succeedIf(() -> {
            if (result.isEmpty()) {
                throw new AssertionError("tier III's price (" + CoreTier.THREE.costs() + ") did not match");
            }
            if (result.get(HsComponents.CORE_ATTUNEMENT).tier() != CoreTier.THREE) {
                throw new AssertionError("a tier II core did not reach tier III");
            }
        });
    }

    /** §12.4 is a price, not a minimum: one ingredient short must not upgrade. */
    @GameTest
    public void anIncompletePriceDoesNotUpgrade(GameTestHelper helper) {
        ItemStack core = CoreYieldGameTests.coreStack(CoreFamily.SOUL, "minecraft:creeper");
        List<ItemStack> short1 = new ArrayList<>(priceOf(CoreTier.TWO));
        short1.removeLast();

        ItemStack result = assemble(grid(core, short1));

        helper.succeedIf(() -> {
            if (!result.isEmpty()) {
                throw new AssertionError("a core upgraded without paying the full §12.4 price");
            }
        });
    }

    /** A stray extra item is not quietly consumed — that would be a way to lose things in a grid. */
    @GameTest
    public void aStrayExtraIngredientDoesNotUpgrade(GameTestHelper helper) {
        ItemStack core = CoreYieldGameTests.coreStack(CoreFamily.SOUL, "minecraft:spider");
        List<ItemStack> withStray = new ArrayList<>(priceOf(CoreTier.TWO));
        withStray.add(new ItemStack(Items.DIRT, 1));

        ItemStack result = assemble(grid(core, withStray));

        helper.succeedIf(() -> {
            if (!result.isEmpty()) {
                throw new AssertionError("an upgrade swallowed a stray item that was not part of the price");
            }
        });
    }

    /** §4.3 upgrades a *locked* core. A finished core with no target should not exist, but must not upgrade. */
    @GameTest
    public void anUnlockedCoreDoesNotUpgrade(GameTestHelper helper) {
        ItemStack core = new ItemStack(com.sigilstead.registry.HsItems.core(CoreFamily.SOUL));
        core.set(HsComponents.CORE_ATTUNEMENT, CoreAttunement.primed(CoreFamily.SOUL));

        ItemStack result = assemble(grid(core, priceOf(CoreTier.TWO)));

        helper.succeedIf(() -> {
            if (!result.isEmpty()) {
                throw new AssertionError("a core with no target was upgraded");
            }
        });
    }

    /** §12.4's ladder stops at III: more scrap must not consume itself against a maxed core. */
    @GameTest
    public void aTierThreeCoreCannotBeUpgradedFurther(GameTestHelper helper) {
        ItemStack core = CoreYieldGameTests.coreStack(CoreFamily.SOUL, "minecraft:husk");
        core.set(HsComponents.CORE_ATTUNEMENT, core.get(HsComponents.CORE_ATTUNEMENT).atTier(CoreTier.THREE));

        ItemStack result = assemble(grid(core, priceOf(CoreTier.THREE)));

        helper.succeedIf(() -> {
            if (!result.isEmpty()) {
                throw new AssertionError("a tier III core matched an upgrade and ate the materials for nothing");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    /** The next tier's price as loose stacks, straight from §12.4 via {@link CoreTier}. */
    private static List<ItemStack> priceOf(CoreTier tier) {
        List<ItemStack> stacks = new ArrayList<>();
        for (CoreTier.Cost cost : tier.costs()) {
            stacks.add(new ItemStack(cost.item(), cost.count()));
        }
        return stacks;
    }

    /** A 3×3 grid holding the core and the payment, padded with empties. */
    private static CraftingInput grid(ItemStack core, List<ItemStack> payment) {
        List<ItemStack> slots = new ArrayList<>();
        slots.add(core);
        slots.addAll(payment);
        while (slots.size() < 9) {
            slots.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, slots);
    }

    private static ItemStack assemble(CraftingInput input) {
        CoreUpgradeRecipe recipe = new CoreUpgradeRecipe();
        return recipe.matches(input, null) ? recipe.assemble(input) : ItemStack.EMPTY;
    }
}
