package com.sigilstead.enchantment;

import com.sigilstead.config.HsConfigManager;
import com.sigilstead.registry.HsEnchantments;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * DESIGN.md §3.1 (Abundance) and §3.2 (Kiln Touch) — both act on block loot, so both hook {@link
 * LootTableEvents#MODIFY_DROPS}, which runs after a block's own loot table has already rolled. That
 * means silk touch's alternative branch (which drops the raw block instead of the usual item) and
 * Fortune's {@code apply_bonus} on the raw-ore entry have already run by the time either
 * enchantment looks at the drop list — so neither enchantment needs to know about silk touch or
 * Fortune explicitly. It just looks for the item it cares about; if silk touch swapped it out for
 * something else, there is nothing to find and both effects silently no-op, exactly matching the
 * mutual exclusion §3.1 and §3.2 describe in prose.
 *
 * <p>{@link #register()} is called once from {@link com.sigilstead.Sigilstead#onInitialize()}.
 */
public final class EnchantmentBlockLoot {

  private EnchantmentBlockLoot() {}

  public static void register() {
    LootTableEvents.MODIFY_DROPS.register(
        (table, context, drops) -> {
          if (!context.hasParameter(LootContextParams.BLOCK_STATE)
              || !context.hasParameter(LootContextParams.ORIGIN)) {
            return;
          }
          ItemInstance toolInstance = context.getOptionalParameter(LootContextParams.TOOL);
          if (!(toolInstance instanceof ItemStack tool) || tool.isEmpty()) {
            return;
          }
          BlockState state = context.getParameter(LootContextParams.BLOCK_STATE);

          applyAbundance(context, state, tool, drops);
          applyKilnTouch(context, state, tool, drops);
        });
  }

  // --- Abundance (§3.1) -------------------------------------------------------------------

  private static void applyAbundance(
      LootContext context, BlockState state, ItemStack tool, List<ItemStack> drops) {
    Item target = abundanceTarget(state);
    if (target == null) {
      return;
    }
    int level =
        HsEnchantments.level(context.getLevel().registryAccess(), tool, HsEnchantments.ABUNDANCE);
    if (level <= 0) {
      return;
    }

    double chance = HsConfigManager.get().enchantment().abundanceChancePerLevel();
    int extra = rollAbundanceExtra(level, chance, context.getRandom());
    if (extra <= 0) {
      return;
    }

    for (ItemStack stack : drops) {
      if (stack.is(target)) {
        stack.grow(extra);
        return;
      }
    }
    drops.add(new ItemStack(target, extra));
  }

  /**
   * §12.6: {@code level} independent trials at {@code chance} each, one extra item per success —
   * the same shape as vanilla's {@code binomial_with_bonus_count} loot formula (extra rounds = 0,
   * so only the enchantment level contributes trials). At 60%, levels I–III average +0.6/+1.2/+1.8,
   * i.e. the ×1.6/×2.2/×2.8 multipliers §12.6 specifies.
   */
  static int rollAbundanceExtra(int level, double chance, RandomSource random) {
    int extra = 0;
    for (int i = 0; i < level; i++) {
      if (random.nextFloat() < chance) {
        extra++;
      }
    }
    return extra;
  }

  /** §3.1's bulk-drop list: stone, deepslate, sand, gravel, clay, netherrack, terracotta, logs. */
  private static Item abundanceTarget(BlockState state) {
    Block block = state.getBlock();
    if (block == Blocks.STONE) {
      return Items.COBBLESTONE;
    }
    if (block == Blocks.DEEPSLATE) {
      return Items.COBBLED_DEEPSLATE;
    }
    if (block == Blocks.CLAY) {
      return Items.CLAY_BALL;
    }
    if (block == Blocks.NETHERRACK
        || block == Blocks.GRAVEL
        || block == Blocks.DIORITE
        || block == Blocks.GRANITE
        || block == Blocks.ANDESITE
        || state.is(BlockTags.SAND)
        || state.is(BlockTags.TERRACOTTA)
        || state.is(BlockTags.LOGS)) {
      return block.asItem();
    }
    return null;
  }

  // --- Kiln Touch (§3.2) -------------------------------------------------------------------

  private static void applyKilnTouch(
      LootContext context, BlockState state, ItemStack tool, List<ItemStack> drops) {
    Item source = kilnTouchSource(state);
    if (source == null) {
      return;
    }
    int level =
        HsEnchantments.level(context.getLevel().registryAccess(), tool, HsEnchantments.KILN_TOUCH);
    if (level <= 0) {
      return;
    }

    SingleRecipeInput input = new SingleRecipeInput(new ItemStack(source));
    Optional<RecipeHolder<SmeltingRecipe>> recipe =
        context
            .getLevel()
            .recipeAccess()
            .getRecipeFor(RecipeType.SMELTING, input, context.getLevel());
    if (recipe.isEmpty()) {
      return;
    }
    SmeltingRecipe smelting = recipe.get().value();
    Item result = smelting.assemble(input).getItem();

    int smelted = 0;
    for (ItemStack stack : drops) {
      if (stack.is(source)) {
        smelted += stack.getCount();
        stack.setCount(0);
      }
    }
    if (smelted <= 0) {
      return;
    }
    drops.add(new ItemStack(result, smelted));

    grantSmeltingXp(context, smelting.experience() * smelted);
  }

  /**
   * §3.2's furnace-XP grant, computed from the same {@link SmeltingRecipe} that decided the output
   * item, so a datapack that reprices smelting XP reprices Kiln Touch's break XP too. Fractional XP
   * (e.g. 0.7 per iron ingot) is resolved the way a furnace resolves it across many smelts: floor
   * the guaranteed amount, then roll the remainder as one extra point of XP.
   */
  private static void grantSmeltingXp(LootContext context, float xp) {
    int whole = (int) xp;
    float remainder = xp - whole;
    if (remainder > 0 && context.getRandom().nextFloat() < remainder) {
      whole++;
    }
    if (whole > 0) {
      net.minecraft.world.entity.ExperienceOrb.award(
          context.getLevel(), context.getParameter(LootContextParams.ORIGIN), whole);
    }
  }

  /** §3.2's autosmelt list: raw ore, sand, cobblestone, clay (as clay balls) and logs. */
  private static Item kilnTouchSource(BlockState state) {
    Block block = state.getBlock();
    if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
      return Items.RAW_IRON;
    }
    if (block == Blocks.GOLD_ORE
        || block == Blocks.DEEPSLATE_GOLD_ORE
        || block == Blocks.NETHER_GOLD_ORE) {
      return Items.RAW_GOLD;
    }
    if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) {
      return Items.RAW_COPPER;
    }
    if (block == Blocks.COBBLESTONE) {
      return Items.COBBLESTONE;
    }
    if (block == Blocks.CLAY) {
      return Items.CLAY_BALL;
    }
    if (state.is(BlockTags.SAND)) {
      return block.asItem();
    }
    if (state.is(BlockItemTags.LOGS_THAT_BURN.block())) {
      return block.asItem();
    }
    return null;
  }
}
