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

    /** DESIGN.md §12.1 — dungeon chests roll a Sigil at 12% for 1. Statistical, not exact. */
    @GameTest
    public void sigilDungeonChestRate(GameTestHelper helper) {
        double rate = sigilRateInChestTable(helper.getLevel(), BuiltInLootTables.SIMPLE_DUNGEON, 6000);
        helper.succeedIf(() -> assertInRange("simple_dungeon sigil rate", rate, 0.08, 0.17));
    }

    /** DESIGN.md §12.1 — the ancient city is the richest chest source at 60% for 1-2. */
    @GameTest
    public void sigilAncientCityChestRate(GameTestHelper helper) {
        double rate = sigilRateInChestTable(helper.getLevel(), BuiltInLootTables.ANCIENT_CITY, 3000);
        helper.succeedIf(() -> assertInRange("ancient_city sigil rate", rate, 0.53, 0.67));
    }

    /** DESIGN.md §12.1 — 0.15% from an ordinary hostile mob, not a mini-boss rate. */
    @GameTest(maxTicks = 400)
    public void sigilOrdinaryHostileMobRate(GameTestHelper helper) {
        double rate = sigilRateInEntityTable(helper, EntityTypes.ZOMBIE, 40000, true);
        helper.succeedIf(() -> assertInRange("zombie sigil rate", rate, 0.0006, 0.0030));
    }

    /** DESIGN.md §12.1 — the Evoker is a mini-boss at 60%, well north of the general hostile rate. */
    @GameTest
    public void sigilEvokerMiniBossRate(GameTestHelper helper) {
        double rate = sigilRateInEntityTable(helper, EntityTypes.EVOKER, 3000, true);
        helper.succeedIf(() -> assertInRange("evoker sigil rate", rate, 0.53, 0.67));
    }

    /** DESIGN.md §12.1 — the Warden always pays, and pays 2. */
    @GameTest
    public void sigilWardenAlwaysPaysTwo(GameTestHelper helper) {
        LootParams params = entityParams(helper, EntityTypes.WARDEN, true);
        LootTable table = lootTable(helper.getLevel(), EntityTypes.WARDEN);
        for (int i = 0; i < 200; i++) {
            if (countSigils(table, params) != 2) {
                throw new AssertionError("warden drew " + countSigils(table, params) + " sigils, expected 2");
            }
        }
        helper.succeed();
    }

    /** DESIGN.md §12.1 — passive mobs are not a Sigil source at all. */
    @GameTest
    public void sigilNeverDropsFromPassiveMob(GameTestHelper helper) {
        double rate = sigilRateInEntityTable(helper, EntityTypes.COW, 2000, true);
        helper.succeedIf(() -> assertInRange("cow sigil rate", rate, 0.0, 0.0));
    }

    /**
     * DESIGN.md §12.1 exclusion 2 — the Wither is never a Sigil source, even on a player kill.
     * Skulls -> Wither -> Sigils would be the bootstrap loop the §5 Wither Skull Core opens.
     */
    @GameTest
    public void sigilNeverDropsFromWither(GameTestHelper helper) {
        double rate = sigilRateInEntityTable(helper, EntityTypes.WITHER, 3000, true);
        helper.succeedIf(() -> assertInRange("wither sigil rate", rate, 0.0, 0.0));
    }

    /**
     * DESIGN.md §4.2 / §12.1 exclusion 1 — no mob killed by a core ever drops a Sigil. A core rolls
     * an inherited loot table with no killing player in the context, so every mob pool must come up
     * empty without one. This is the invariant §11 warns a loot-table refactor will silently break;
     * Phase 4 must not start passing a player parameter to make it "work".
     */
    @GameTest
    public void sigilNeverDropsWithoutAPlayerKiller(GameTestHelper helper) {
        for (net.minecraft.world.entity.EntityType<?> type :
                java.util.List.of(EntityTypes.ZOMBIE, EntityTypes.EVOKER, EntityTypes.WARDEN, EntityTypes.RAVAGER)) {
            double rate = sigilRateInEntityTable(helper, type, 4000, false);
            if (rate != 0.0) {
                throw new AssertionError("core-rolled " + type.toShortString() + " produced sigils at " + rate);
            }
        }
        helper.succeed();
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

    private static double sigilRateInChestTable(ServerLevel level, net.minecraft.resources.ResourceKey<LootTable> key, int draws) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(key);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        return sigilRate(table, params, draws);
    }

    private static double sigilRateInEntityTable(
            GameTestHelper helper, net.minecraft.world.entity.EntityType<?> entityType, int draws, boolean killedByPlayer) {
        return sigilRate(lootTable(helper.getLevel(), entityType), entityParams(helper, entityType, killedByPlayer), draws);
    }

    private static LootTable lootTable(ServerLevel level, net.minecraft.world.entity.EntityType<?> entityType) {
        return level.getServer().reloadableRegistries().getLootTable(entityType.getDefaultLootTable().orElseThrow());
    }

    /**
     * Entity loot params. {@code killedByPlayer} is the whole point of the §12.1 exclusion tests:
     * with a player it is an ordinary kill, without one it is what a §5 core's roll looks like.
     */
    private static LootParams entityParams(
            GameTestHelper helper, net.minecraft.world.entity.EntityType<?> entityType, boolean killedByPlayer) {
        ServerLevel level = helper.getLevel();
        ItemEntity dummy = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.STICK));
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .withParameter(LootContextParams.THIS_ENTITY, dummy)
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().generic());
        if (killedByPlayer) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, helper.makeMockServerPlayerInLevel());
        }
        return builder.create(LootContextParamSets.ENTITY);
    }

    private static double sigilRate(LootTable table, LootParams params, int draws) {
        int hits = 0;
        for (int i = 0; i < draws; i++) {
            if (countSigils(table, params) > 0) {
                hits++;
            }
        }
        return (double) hits / draws;
    }

    private static int countSigils(LootTable table, LootParams params) {
        int count = 0;
        for (ItemStack stack : table.getRandomItems(params)) {
            if (stack.is(HsItems.SIGIL)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void assertInRange(String label, double value, double min, double max) {
        if (value < min || value > max) {
            throw new AssertionError(label + " = " + value + ", expected [" + min + ", " + max + "]");
        }
    }
}
