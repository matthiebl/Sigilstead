package com.heartstead.economy;

import com.heartstead.config.HsConfigManager;
import com.heartstead.config.SigilConfig;
import com.heartstead.config.SigilDrop;
import com.heartstead.registry.HsItems;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemKilledByPlayerCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

/**
 * DESIGN.md §12.1 — the one drop table that matters. Injects the Sigil (§1) into structure chests,
 * trial chamber vaults, fishing treasure, hostile mobs and the four mini-bosses that carry a named
 * rate. The Ender Dragon is not here: it has an empty loot table and its first-kill / respawn split
 * cannot be expressed in one, so it lives in {@link SigilBossDrops}.
 *
 * <p>Uses {@code fabric-loot-api-v3}'s {@link LootTableEvents#MODIFY} rather than overriding
 * vanilla loot table JSON — an override replaces the table outright and stomps every other mod
 * touching the same one, where {@code MODIFY} composes.
 *
 * <p>None of this needs an entity predicate. {@code MODIFY} fires once per loot table key at load
 * time, not per kill, so "any hostile mob" is resolved by looking up each {@link EntityType}'s
 * {@link MobCategory} against its default loot table key, not by matching a live entity.
 *
 * <h2>The two load-bearing exclusions (§4.2, §12.1)</h2>
 *
 * Both stop the economy bootstrapping — cores are bought with Sigils, so a core that produced
 * Sigils would produce cores.
 *
 * <ol>
 *   <li><b>No mob killed by a core ever drops a Sigil.</b> Enforced by
 *       {@link #notProducedByACore()} on every mob pool. A §5 core rolls a mob's loot table with no
 *       {@code LAST_DAMAGE_PLAYER} in its context, so the condition fails structurally rather than
 *       relying on Phase 4 remembering a rule. <b>Phase 4 must never supply a player parameter when
 *       rolling a core's inherited loot table.</b></li>
 *   <li><b>The Wither is never a Sigil source</b>, even on a player kill — see
 *       {@link #NEVER_DROPS_SIGILS}. The §5 Wither Skull Core makes skulls passively, so
 *       skulls → Wither → Sigils is the same loop wearing a disguise.</li>
 * </ol>
 */
public final class SigilLoot {

    /**
     * §12.1 exclusion 2, plus the Ender Dragon, whose Sigils {@link SigilBossDrops} pays directly
     * so that a first kill and a respawn kill can differ.
     */
    private static final Set<ResourceKey<LootTable>> NEVER_DROPS_SIGILS = Set.of(
            EntityTypes.WITHER.getDefaultLootTable().orElseThrow(),
            EntityTypes.ENDER_DRAGON.getDefaultLootTable().orElseThrow()
    );

    /** §12.1 chests, each mapped to the config row that prices it. */
    private static final Map<ResourceKey<LootTable>, Function<SigilConfig, SigilDrop>> CHEST_TABLES =
            buildChestTableMap();

    /** §12.1 mini-bosses, each mapped to the config row that prices it. */
    private static final Map<ResourceKey<LootTable>, Function<SigilConfig, SigilDrop>> NAMED_MOB_TABLES =
            buildNamedMobTableMap();

    /** Maps each entity type's default loot table back to the entity, so MODIFY can check MobCategory. */
    private static final Map<ResourceKey<LootTable>, EntityType<?>> ENTITY_LOOT_TABLES = buildEntityLootTableMap();

    private SigilLoot() {
    }

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, holder) -> {
            SigilConfig config = HsConfigManager.get().sigil();

            Function<SigilConfig, SigilDrop> chest = CHEST_TABLES.get(key);
            if (chest != null) {
                addPool(tableBuilder, chest.apply(config), false);
                return;
            }

            if (NEVER_DROPS_SIGILS.contains(key)) {
                return;
            }

            Function<SigilConfig, SigilDrop> namedMob = NAMED_MOB_TABLES.get(key);
            if (namedMob != null) {
                addPool(tableBuilder, namedMob.apply(config), true);
                return;
            }

            EntityType<?> entityType = ENTITY_LOOT_TABLES.get(key);
            if (entityType != null && entityType.getCategory() == MobCategory.MONSTER) {
                addPool(tableBuilder, config.hostileMob(), true);
            }
        });
    }

    private static void addPool(LootTable.Builder tableBuilder, SigilDrop drop, boolean playerKillOnly) {
        if (drop.isDisabled()) {
            return;
        }

        LootPool.Builder pool = LootPool.lootPool()
                .add(LootItem.lootTableItem(HsItems.SIGIL)
                        .apply(SetItemCountFunction.setCount(UniformGenerator.between(drop.min(), drop.max()))))
                .when(LootItemRandomChanceCondition.randomChance((float) drop.chance()));

        if (playerKillOnly) {
            pool.when(notProducedByACore());
        }

        tableBuilder.withPool(pool);
    }

    /**
     * §4.2 / §12.1 exclusion 1 — the no-Sigils-from-cores rule, named for what it is rather than
     * for the vanilla condition that implements it.
     *
     * <p>A §5 core settles its yield by rolling the loot table of the thing it replaces, without a
     * killing player in the loot context. Requiring one is therefore an exclusion a loot-table
     * refactor cannot silently undo, which is exactly what §11 warns about. It also matches §12.1's
     * wording directly: the boss rates and the 0.15% hostile-mob rate "apply to player kills only".
     */
    private static LootItemCondition.Builder notProducedByACore() {
        return LootItemKilledByPlayerCondition.killedByPlayer();
    }

    private static Map<ResourceKey<LootTable>, Function<SigilConfig, SigilDrop>> buildChestTableMap() {
        Map<ResourceKey<LootTable>, Function<SigilConfig, SigilDrop>> map = new HashMap<>();

        // 12% for 1 — dungeon, mineshaft, desert temple, jungle temple, shipwreck treasure.
        for (ResourceKey<LootTable> key : Set.of(
                BuiltInLootTables.SIMPLE_DUNGEON,
                BuiltInLootTables.ABANDONED_MINESHAFT,
                BuiltInLootTables.DESERT_PYRAMID,
                BuiltInLootTables.JUNGLE_TEMPLE,
                BuiltInLootTables.SHIPWRECK_TREASURE)) {
            map.put(key, SigilConfig::structureChest);
        }

        // 25% for 1 — the stronghold. "Library / altar" in §12.1; the altar room's chests are
        // vanilla's stronghold crossing table, and the corridor chests are deliberately left out so
        // a single stronghold does not roll a dozen times.
        map.put(BuiltInLootTables.STRONGHOLD_LIBRARY, SigilConfig::strongholdChest);
        map.put(BuiltInLootTables.STRONGHOLD_CROSSING, SigilConfig::strongholdChest);

        // The two top-level vault reward tables — the ones the vault block actually rolls.
        map.put(BuiltInLootTables.TRIAL_CHAMBERS_REWARD, SigilConfig::trialVault);
        map.put(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS, SigilConfig::ominousTrialVault);

        map.put(BuiltInLootTables.BASTION_TREASURE, SigilConfig::bastionTreasure);
        map.put(BuiltInLootTables.ANCIENT_CITY, SigilConfig::ancientCity);
        map.put(BuiltInLootTables.END_CITY_TREASURE, SigilConfig::endCityTreasure);
        map.put(BuiltInLootTables.FISHING_TREASURE, SigilConfig::fishingTreasure);

        return Map.copyOf(map);
    }

    private static Map<ResourceKey<LootTable>, Function<SigilConfig, SigilDrop>> buildNamedMobTableMap() {
        Map<ResourceKey<LootTable>, Function<SigilConfig, SigilDrop>> map = new HashMap<>();
        map.put(EntityTypes.RAVAGER.getDefaultLootTable().orElseThrow(), SigilConfig::ravager);
        map.put(EntityTypes.EVOKER.getDefaultLootTable().orElseThrow(), SigilConfig::evoker);
        map.put(EntityTypes.ELDER_GUARDIAN.getDefaultLootTable().orElseThrow(), SigilConfig::elderGuardian);
        map.put(EntityTypes.WARDEN.getDefaultLootTable().orElseThrow(), SigilConfig::warden);
        return Map.copyOf(map);
    }

    private static Map<ResourceKey<LootTable>, EntityType<?>> buildEntityLootTableMap() {
        Map<ResourceKey<LootTable>, EntityType<?>> map = new HashMap<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            type.getDefaultLootTable().ifPresent(key -> map.put(key, type));
        }
        return map;
    }
}
