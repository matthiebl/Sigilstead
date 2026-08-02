package com.heartstead.gametest;

import com.heartstead.registry.HsEnchantments;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DESIGN.md §3.2 — Kiln Touch moves items (raw ore into an ingot the player never mined) and grants
 * XP the block wouldn't otherwise drop, so it gets a GameTest rather than trusting the loot JSON by
 * eye (CONVENTIONS.md §8). §3.1's Abundance bonus is probabilistic and already covered by the
 * deterministic unit test in {@code EnchantmentBlockLootTest} — nothing here needs a live world.
 */
public class EnchantmentGameTests {

    /** An iron ore mined with Kiln Touch drops an ingot, never the raw ore or the block itself. */
    @GameTest
    public void kilnTouchAutosmeltsIronOre(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(Blocks.IRON_ORE.getLootTable().orElseThrow());
        LootParams params = blockParams(level, Blocks.IRON_ORE.defaultBlockState(), kilnTouchPickaxe(level));

        helper.succeedIf(() -> {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(Items.IRON_ORE) || stack.is(Items.RAW_IRON)) {
                    throw new AssertionError("Kiln Touch left an un-smelted " + stack + " in the drops");
                }
                if (!stack.is(Items.IRON_INGOT)) {
                    throw new AssertionError("unexpected drop " + stack + " from a Kiln Touch iron ore break");
                }
            }
        });
    }

    /** Kiln Touch grants the furnace's smelting XP on break, even though iron ore normally grants none. */
    @GameTest
    public void kilnTouchGrantsSmeltingXp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(Blocks.IRON_ORE.getLootTable().orElseThrow());
        Vec3 origin = helper.absoluteVec(new Vec3(0.5, 1.5, 0.5));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, origin)
                .withParameter(LootContextParams.BLOCK_STATE, Blocks.IRON_ORE.defaultBlockState())
                .withOptionalParameter(LootContextParams.TOOL, kilnTouchPickaxe(level))
                .create(LootContextParamSets.BLOCK);
        table.getRandomItems(params);

        boolean orbSpawned = !level.getEntitiesOfClass(ExperienceOrb.class, new AABB(origin, origin).inflate(2)).isEmpty();
        helper.succeedIf(() -> {
            if (!orbSpawned) {
                throw new AssertionError("Kiln Touch did not spawn an experience orb for the smelted ingot");
            }
        });
    }

    private static LootParams blockParams(ServerLevel level, BlockState state, ItemStack tool) {
        return new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.BLOCK_STATE, state)
                .withOptionalParameter(LootContextParams.TOOL, tool)
                .create(LootContextParamSets.BLOCK);
    }

    private static ItemStack kilnTouchPickaxe(ServerLevel level) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        Holder<Enchantment> kilnTouch = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(HsEnchantments.KILN_TOUCH);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(kilnTouch, 1);
        pickaxe.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        return pickaxe;
    }
}
