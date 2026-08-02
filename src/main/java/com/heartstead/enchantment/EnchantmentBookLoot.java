package com.heartstead.enchantment;

import com.heartstead.config.EnchantmentConfig;
import com.heartstead.config.HsConfigManager;
import com.heartstead.registry.HsEnchantments;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * DESIGN.md §3.1/§3.2 — the only way to find these two enchantments before they're archived and
 * taught (§3.3): Abundance turns up in mineshaft and trial chamber loot, Kiln Touch in nether
 * fortress and bastion loot. Neither is in {@code #minecraft:in_enchanting_table} or
 * {@code #minecraft:tradeable}, so this is the sole source until a player teaches a librarian.
 *
 * <p>Uses {@link LootTableEvents#MODIFY} the same way {@link com.heartstead.economy.SigilLoot}
 * does, adding a pool rather than replacing the table.
 */
public final class EnchantmentBookLoot {

    private EnchantmentBookLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            EnchantmentConfig config = HsConfigManager.get().enchantment();

            if (key.equals(BuiltInLootTables.ABANDONED_MINESHAFT) || key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD)) {
                addBookPool(tableBuilder, registries, HsEnchantments.ABUNDANCE, UniformGenerator.between(1, 3), config.abundanceBookChance());
            }

            if (key.equals(BuiltInLootTables.NETHER_BRIDGE) || key.equals(BuiltInLootTables.BASTION_TREASURE)) {
                addBookPool(tableBuilder, registries, HsEnchantments.KILN_TOUCH, ConstantValue.exactly(1), config.kilnTouchBookChance());
            }
        });
    }

    private static void addBookPool(
            LootTable.Builder tableBuilder,
            HolderLookup.Provider registries,
            ResourceKey<Enchantment> enchantment,
            net.minecraft.world.level.storage.loot.providers.number.NumberProvider level,
            double chance) {
        if (chance <= 0.0) {
            return;
        }
        Holder<Enchantment> holder = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);

        tableBuilder.withPool(
                LootPool.lootPool()
                        .add(LootItem.lootTableItem(Items.BOOK)
                                .apply(new SetEnchantmentsFunction.Builder().withEnchantment(holder, level)))
                        .when(LootItemRandomChanceCondition.randomChance((float) chance)));
    }
}
