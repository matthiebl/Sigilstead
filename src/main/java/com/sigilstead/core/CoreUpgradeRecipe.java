package com.sigilstead.core;

import com.sigilstead.item.CoreItem;
import com.sigilstead.registry.HsComponents;
import com.sigilstead.registry.HsRecipes;
import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Item;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §4.3 / §12.4 — upgrading a locked core's rate tier: 4 Blaze Powder for tier II, 2
 * Netherite Scrap for tier III.
 *
 * <p>A custom recipe rather than two JSON shapeless recipes per family, because the result depends on
 * the input's own state: the output is <em>this</em> core, target and all, one tier higher. A plain
 * recipe cannot read a component off an ingredient and copy it to the result, and the alternative —
 * an upgrade slot in the housing screen — would mean a core can only be upgraded while socketed,
 * which §4.3 never says and which would make the §4.3 refusal path harder, not easier.
 *
 * <p>It stays data-driven in the sense CONVENTIONS.md §6 means: the recipe is still declared by a
 * JSON file, still discovered by the recipe manager, and still removable by a datapack. Only its
 * assembly is Java, and only because the assembly is genuinely conditional.
 *
 * <p>The tier ladder stops at III — a tier III core in the grid with more Netherite Scrap simply does
 * not match, so it never consumes the materials.
 */
public class CoreUpgradeRecipe extends CustomRecipe {

    public static final MapCodec<CoreUpgradeRecipe> CODEC =
            MapCodec.unit(CoreUpgradeRecipe::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreUpgradeRecipe> STREAM_CODEC =
            StreamCodec.unit(new CoreUpgradeRecipe());

    /** The core and the payment found in a grid, and the tier they buy. */
    private record Match(ItemStack core, CoreAttunement attunement, CoreTier target) {
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return find(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Match match = find(input);
        if (match == null) {
            return ItemStack.EMPTY;
        }
        ItemStack result = match.core().copyWithCount(1);
        result.set(HsComponents.CORE_ATTUNEMENT, match.attunement().atTier(match.target()));
        return result;
    }

    /**
     * Finds exactly one locked core plus exactly the payment for its next tier, and nothing else.
     * Anything extra in the grid is not a match — an upgrade that quietly swallowed a stray item
     * would be a way to lose things in a crafting grid.
     */
    private Match find(CraftingInput input) {
        ItemStack core = ItemStack.EMPTY;
        CoreAttunement attunement = null;
        Map<Item, Integer> payment = new HashMap<>();

        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getItem() instanceof CoreItem) {
                if (!core.isEmpty()) {
                    return null; // two cores — ambiguous, refuse rather than pick one
                }
                attunement = stack.get(HsComponents.CORE_ATTUNEMENT);
                if (attunement == null || attunement.target().isEmpty()) {
                    return null; // §4.3 upgrades a *locked* core
                }
                core = stack;
                continue;
            }
            payment.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }

        if (core.isEmpty() || payment.isEmpty()) {
            return null;
        }
        CoreTier next = attunement.tier().next();
        if (next == null || !isExactPrice(payment, next)) {
            return null;
        }
        return new Match(core, attunement, next);
    }

    /**
     * The grid must hold exactly the next tier's price — every ingredient, at the right count, and
     * nothing else. Checking the map's size as well as its entries is what makes "and nothing else"
     * true: an upgrade that quietly swallowed a stray item would be a way to lose things in a
     * crafting grid, and one that ignored a missing ingredient would make §12.4's price a suggestion.
     */
    private static boolean isExactPrice(Map<Item, Integer> payment, CoreTier tier) {
        if (payment.size() != tier.costs().size()) {
            return false;
        }
        for (CoreTier.Cost cost : tier.costs()) {
            if (!Integer.valueOf(cost.count()).equals(payment.get(cost.item()))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<? extends CustomRecipe> getSerializer() {
        return HsRecipes.CORE_UPGRADE;
    }

    @Override
    public CraftingBookCategory category() {
        return CraftingBookCategory.MISC;
    }
}
