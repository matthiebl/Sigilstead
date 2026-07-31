package com.heartstead.economy;

import com.heartstead.config.HsConfig;
import com.heartstead.config.HsConfigManager;
import com.heartstead.registry.HsItems;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * Heart Shard loot injection (DESIGN.md §1): structure chests, trial vaults, fishing treasure and
 * hostile mob drops.
 *
 * <p>Uses {@code fabric-loot-api-v3}'s {@link LootTableEvents#MODIFY} rather than overriding
 * vanilla loot table JSON — an override replaces the table outright and stomps every other mod
 * touching the same one, where {@code MODIFY} composes.
 *
 * <p>None of this needs an entity predicate. {@code MODIFY} fires once per loot table key at load
 * time, not per kill, so "any hostile mob" is resolved by looking up each {@link EntityType}'s
 * {@link MobCategory} against its default loot table key, not by matching a live entity.
 */
public final class HeartShardLoot {

    private static final Set<ResourceKey<LootTable>> CHEST_TABLES = Set.of(
            BuiltInLootTables.SIMPLE_DUNGEON,
            BuiltInLootTables.ABANDONED_MINESHAFT,
            BuiltInLootTables.DESERT_PYRAMID,
            BuiltInLootTables.JUNGLE_TEMPLE,
            BuiltInLootTables.SHIPWRECK_SUPPLY,
            BuiltInLootTables.SHIPWRECK_TREASURE,
            BuiltInLootTables.SHIPWRECK_MAP
    );

    /** Maps each entity type's default loot table back to the entity, so MODIFY can check MobCategory. */
    private static final Map<ResourceKey<LootTable>, EntityType<?>> ENTITY_LOOT_TABLES = buildEntityLootTableMap();

    private static final Set<ResourceKey<LootTable>> ELITE_MOB_TABLES = Set.of(
            EntityTypes.EVOKER.getDefaultLootTable().orElseThrow(),
            EntityTypes.ELDER_GUARDIAN.getDefaultLootTable().orElseThrow()
    );

    private HeartShardLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, holder) -> {
            HsConfig config = HsConfigManager.get();

            if (CHEST_TABLES.contains(key) || key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD)
                    || key.equals(BuiltInLootTables.FISHING_TREASURE)) {
                tableBuilder.withPool(chestPool(config));
                return;
            }

            if (ELITE_MOB_TABLES.contains(key)) {
                tableBuilder.withPool(mobPool(config.heartShardEliteMobChance()));
                return;
            }

            EntityType<?> entityType = ENTITY_LOOT_TABLES.get(key);
            if (entityType != null && entityType.getCategory() == MobCategory.MONSTER) {
                tableBuilder.withPool(mobPool(config.heartShardHostileMobChance()));
            }
        });
    }

    private static LootPool.Builder chestPool(HsConfig config) {
        return LootPool.lootPool()
                .add(LootItem.lootTableItem(HsItems.HEART_SHARD)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(
                                config.heartShardChestMinCount(), config.heartShardChestMaxCount()))))
                .when(LootItemRandomChanceCondition.randomChance((float) config.heartShardChestChance()));
    }

    private static LootPool.Builder mobPool(double chance) {
        return LootPool.lootPool()
                .add(LootItem.lootTableItem(HsItems.HEART_SHARD))
                .when(LootItemRandomChanceCondition.randomChance((float) chance));
    }

    private static Map<ResourceKey<LootTable>, EntityType<?>> buildEntityLootTableMap() {
        Map<ResourceKey<LootTable>, EntityType<?>> map = new HashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            type.getDefaultLootTable().ifPresent(key -> map.put(key, type));
        }
        return map;
    }
}
