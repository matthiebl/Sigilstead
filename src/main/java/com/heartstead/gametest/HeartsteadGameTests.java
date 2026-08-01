package com.heartstead.gametest;

import com.heartstead.config.HsConfig;
import com.heartstead.config.HsConfigManager;
import com.heartstead.lives.HeartLevel;
import com.heartstead.registry.HsAttachments;
import com.heartstead.registry.HsItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * GameTest entrypoint — headless, in-world, automated verification.
 *
 * <p>This is the single biggest practical win from being a mod rather than a data pack. Run with:
 *
 * <pre>./gradlew runGametest</pre>
 *
 * <h2>How the API works in 26.2</h2>
 *
 * This is a <b>plain class</b> registered under the {@code fabric-gametest} entrypoint in
 * {@code fabric.mod.json}. There is no interface to implement — the old {@code FabricGameTest}
 * interface is gone. Tests are public methods annotated with
 * {@link net.fabricmc.fabric.api.gametest.v1.GameTest}, which carries {@code structure},
 * {@code maxTicks}, {@code setupTicks}, {@code rotation}, {@code skyAccess}, {@code maxAttempts}
 * and friends.
 *
 * <h2>What belongs here</h2>
 *
 * In rough priority order (see {@code docs/DESIGN.md}):
 * <ul>
 *   <li><b>§2.5 Vault item conservation</b> — the highest-value tests in the project. Deposit N,
 *       withdraw N, assert nothing was created or destroyed. Include the nasty cases: server
 *       shutdown mid-transfer, full inventory on withdraw, capacity boundary, concurrent access.
 *       The Vault is world state shared by every player, so concurrent access is a first-class
 *       case here, not an edge case.</li>
 *   <li><b>§4.2 core offline accrual</b> — advance the world clock, unload and reload the chunk,
 *       assert yield matches the elapsed-time formula, does not double-count, and respects the
 *       configured catch-up ceiling.</li>
 *   <li><b>§4.3 one active core per target</b> — assert a duplicate core is refused at the point of
 *       socketing, and that the refusal survives a world reload.</li>
 *   <li><b>§7.2 villager trade persistence</b> — level up, restock, reload, assert the taught offer
 *       survives all three.</li>
 *   <li><b>§6 lives floor</b> — die repeatedly, assert health never drops below the configured floor.</li>
 * </ul>
 *
 * <p>Pure logic with no world dependency belongs in {@code src/test} as plain JUnit instead — it
 * runs in milliseconds and does not need a server.
 */
public class HeartsteadGameTests {

    // TODO Phase 3: vault conservation suite — write these BEFORE the Vault itself.

    /** DESIGN.md §1 — dungeon chests roll Heart Shard at 30% for 1-2. Statistical, not exact. */
    @GameTest
    public void heartShardDungeonChestRate(GameTestHelper helper) {
        double rate = heartShardRateInChestTable(helper.getLevel(), BuiltInLootTables.SIMPLE_DUNGEON, 4000);
        helper.succeedIf(() -> assertInRange("simple_dungeon shard rate", rate, 0.20, 0.40));
    }

    /** DESIGN.md §1 — 0.5% from an ordinary hostile mob (zombie), not the 10% elite rate. */
    @GameTest
    public void heartShardOrdinaryHostileMobRate(GameTestHelper helper) {
        double rate = heartShardRateInEntityTable(helper.getLevel(), EntityTypes.ZOMBIE, 6000);
        helper.succeedIf(() -> assertInRange("zombie shard rate", rate, 0.001, 0.02));
    }

    /** DESIGN.md §1 — Evoker gets the 10% elite rate, not the 0.5% general hostile-mob rate. */
    @GameTest
    public void heartShardEvokerEliteRate(GameTestHelper helper) {
        double rate = heartShardRateInEntityTable(helper.getLevel(), EntityTypes.EVOKER, 3000);
        helper.succeedIf(() -> assertInRange("evoker shard rate", rate, 0.06, 0.16));
    }

    /** DESIGN.md §1 — passive mobs never drop Heart Shards. */
    @GameTest
    public void heartShardNeverDropsFromPassiveMob(GameTestHelper helper) {
        double rate = heartShardRateInEntityTable(helper.getLevel(), EntityTypes.COW, 1000);
        helper.succeedIf(() -> assertInRange("cow shard rate", rate, 0.0, 0.0));
    }

    /**
     * DESIGN.md §6 — die repeatedly and assert hearts never drop below the configured floor, in
     * either the tracked attachment value or the live {@code max_health}/current health.
     */
    @GameTest
    public void livesFloorNeverBreached(GameTestHelper helper) {
        HsConfig config = HsConfigManager.get();
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        for (int i = 0; i < 8; i++) {
            player = level.getServer().getPlayerList().respawn(player, false, Entity.RemovalReason.KILLED);

            int hearts = player.getAttachedOrCreate(HsAttachments.HEART_LEVEL, HeartLevel::initial).hearts();
            if (hearts < config.heartFloor()) {
                throw new AssertionError("hearts dropped below floor after death " + i + ": " + hearts);
            }
            if (player.getMaxHealth() < config.heartFloor() * 2.0f) {
                throw new AssertionError("max_health dropped below floor after death " + i + ": " + player.getMaxHealth());
            }
            if (player.getHealth() < config.heartFloor() * 2.0f) {
                throw new AssertionError("current health dropped below floor after death " + i + ": " + player.getHealth());
            }
        }

        helper.succeed();
    }

    private static double heartShardRateInChestTable(ServerLevel level, net.minecraft.resources.ResourceKey<LootTable> key, int draws) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        return heartShardRate(table, params, draws);
    }

    private static double heartShardRateInEntityTable(ServerLevel level, net.minecraft.world.entity.EntityType<?> entityType, int draws) {
        net.minecraft.resources.ResourceKey<LootTable> key = entityType.getDefaultLootTable().orElseThrow();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        ItemEntity dummy = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.STICK));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.THIS_ENTITY, dummy)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic())
                .create(LootContextParamSets.ENTITY);
        return heartShardRate(table, params, draws);
    }

    private static double heartShardRate(LootTable table, LootParams params, int draws) {
        int hits = 0;
        for (int i = 0; i < draws; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(HsItems.HEART_SHARD)) {
                    hits++;
                    break;
                }
            }
        }
        return (double) hits / draws;
    }

    private static void assertInRange(String label, double value, double min, double max) {
        if (value < min || value > max) {
            throw new AssertionError(label + " = " + value + ", expected [" + min + ", " + max + "]");
        }
    }
}
