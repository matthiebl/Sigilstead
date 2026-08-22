package com.sigilstead.gametest;

import com.sigilstead.blockentity.CoreHousingBlockEntity;
import com.sigilstead.core.ActiveCores;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.core.CoreKey;
import com.sigilstead.core.CoreYield;
import com.sigilstead.registry.HsBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * DESIGN.md §4.2's offline accrual, in a real world. The arithmetic itself is covered by
 * {@code CoreAccrualTest} as pure logic (CONVENTIONS.md §8); what these add is the part that needs a
 * block entity and a save: that the one stored timestamp really is the only clock, that it survives
 * the NBT round trip a chunk unload/reload performs, and that the two bugs §4.2 names cannot happen
 * to a housing that actually exists.
 *
 * <p>Game time is not advanced by these tests — a GameTest cannot fast-forward the world clock
 * without also ticking everything in it. Instead the housing's stored stamp is pushed backwards,
 * which is indistinguishable from the world clock having moved forwards and is exactly what a chunk
 * reload after an absence presents to the settle path.
 */
public class CoreAccrualGameTests {

    /**
     * §4.2's double-counting bug: "settling on chunk load <em>and</em> ticking while loaded". Settle
     * once for a long span, then settle again immediately — the second must pay nothing at all.
     */
    @GameTest
    public void settlingTwiceDoesNotPayTwice(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CoreHousingBlockEntity housing = socketedSoulCage(helper, new BlockPos(1, 1, 1));

        backdate(housing, level, 20_000L);
        int first = housing.settle(level);
        int second = housing.settle(level);

        helper.succeedIf(() -> {
            if (first <= 0) {
                throw new AssertionError("a 20000-tick settle paid nothing, so this test asserts nothing");
            }
            if (second != 0) {
                throw new AssertionError("a second immediate settle paid " + second + " more cycles. "
                        + "§4.2: never accrue from two clocks.");
            }
        });
    }

    /**
     * §4.2's unbounded catch-up bug: "a chunk untouched for forty in-game days dumps its whole
     * backlog at once". Two housings, one backdated a day and one backdated forty, must settle to the
     * same capped yield.
     */
    @GameTest
    public void aFortyDayBacklogPaysNoMoreThanTheCap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long oneDay = 24L * 1000L;

        CoreHousingBlockEntity atCap = socketedSoulCage(helper, new BlockPos(1, 1, 1));
        CoreHousingBlockEntity wayOver = socketedSoulCage(helper, new BlockPos(3, 1, 1));

        backdate(atCap, level, oneDay);
        backdate(wayOver, level, 40 * oneDay);
        int capped = atCap.settle(level);
        int overrun = wayOver.settle(level);

        helper.succeedIf(() -> {
            if (capped <= 0) {
                throw new AssertionError("a one-day settle paid nothing, so this test asserts nothing");
            }
            if (overrun != capped) {
                throw new AssertionError("a forty-day backlog paid " + overrun + " cycles against the 24h cap's "
                        + capped + " — §4.2's first chunk load after a long absence is a jackpot");
            }
        });
    }

    /**
     * §4.2 — the timestamp is block state and has to survive the NBT round trip a chunk unload and
     * reload performs. A stamp that reset to zero on load would make every reload pay a full capped
     * backlog, which is the same jackpot wearing a different hat.
     */
    @GameTest
    public void theSettledStampSurvivesAnUnloadReloadRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CoreHousingBlockEntity housing = socketedSoulCage(helper, new BlockPos(1, 1, 1));

        // Settle to now, so nothing at all is owed.
        housing.settle(level);

        CoreHousingBlockEntity reloaded = roundTrip(housing, level);
        int paidAfterReload = reloaded.settle(level);
        long stamp = reloaded.lastSettled();

        helper.succeedIf(() -> {
            if (stamp == 0L) {
                throw new AssertionError("the last-settled stamp came back as 0 from the round trip — "
                        + "every chunk reload would pay a full capped backlog");
            }
            if (paidAfterReload != 0) {
                throw new AssertionError("settling straight after a reload paid " + paidAfterReload
                        + " cycles that were already settled before the save");
            }
        });
    }

    /**
     * §4.2 — "walking away costs you nothing". A housing that was never ticked at all, then settled
     * once for the whole elapsed span, must earn what a continuously ticked one would have. This is
     * the no-chunkloader promise, stated as a test.
     */
    @GameTest
    public void anUnloadedHousingEarnsWhatALoadedOneWould(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        long span = 12_000L;

        CoreHousingBlockEntity ticked = socketedSoulCage(helper, new BlockPos(1, 1, 1));
        CoreHousingBlockEntity idle = socketedSoulCage(helper, new BlockPos(3, 1, 1));

        // The "loaded" one settles in many small steps of 137 ticks — deliberately not a divisor of
        // the 400-tick period, so an implementation that dropped the sub-period remainder would come
        // up short. The "unloaded" one settles once for the whole span.
        long remaining = span;
        int loaded = 0;
        while (remaining > 0) {
            long step = Math.min(137L, remaining);
            advanceBy(ticked, level, step);
            loaded += ticked.settle(level);
            remaining -= step;
        }
        backdate(idle, level, span);
        int unloaded = idle.settle(level);

        int loadedTotal = loaded;
        helper.succeedIf(() -> {
            if (unloaded <= 0) {
                throw new AssertionError("the unloaded housing earned nothing over " + span + " ticks");
            }
            if (loadedTotal != unloaded) {
                throw new AssertionError("a loaded housing earned " + loadedTotal + " cycles where an unloaded one "
                        + "earned " + unloaded + " — §4.2 requires a chunkloader to be worthless");
            }
        });
    }

    /** An empty housing banks nothing: socketing a core into one that stood idle must not pay a backlog. */
    @GameTest
    public void anEmptyHousingBanksNoTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, HsBlocks.SOUL_CAGE);
        CoreHousingBlockEntity housing = helper.getBlockEntity(pos, CoreHousingBlockEntity.class);

        // Stand empty across a long span.
        backdate(housing, level, 40L * 24L * 1000L);
        housing.settle(level);

        housing.setItem(CoreHousingBlockEntity.CORE_SLOT,
                CoreYieldGameTests.coreStack(CoreFamily.SOUL, "minecraft:zoglin"));
        int paid = housing.settle(level);

        helper.succeedIf(() -> {
            if (paid != 0) {
                throw new AssertionError("socketing into a long-idle housing paid " + paid
                        + " cycles of backlog it never earned");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    /**
     * A Soul Cage running a core, already settled to now so the caller starts from a clean slate.
     *
     * <p>Two things here exist purely because §4.3's rule is real and world-level. Each fixture
     * housing gets its own target, since two housings on the same one would leave the second refused
     * and earning nothing — which would look exactly like an accrual bug. And any claim left behind
     * by an earlier test in the same {@code runGametest} invocation is released first, so run order
     * cannot decide the outcome (the same defence {@code VaultGameTests} documents for the Vault's
     * shared singleton).
     */
    static CoreHousingBlockEntity socketedSoulCage(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        String target = fixtureTarget(helper, relative);
        CoreKey key = new CoreKey(CoreFamily.SOUL, Identifier.parse(target));
        ActiveCores.claimPos(level, key).ifPresent(held -> ActiveCores.release(level, key, held));

        helper.setBlock(relative, HsBlocks.SOUL_CAGE);
        CoreHousingBlockEntity housing = helper.getBlockEntity(relative, CoreHousingBlockEntity.class);
        housing.setItem(CoreHousingBlockEntity.CORE_SLOT, CoreYieldGameTests.coreStack(CoreFamily.SOUL, target));

        if (!housing.active()) {
            throw new AssertionError("fixture housing at " + relative + " did not start running (target "
                    + target + ", refused=" + housing.refused() + ") — the test below would measure nothing");
        }
        if (CoreYield.produce(level, CoreYieldGameTests.attuned(CoreFamily.SOUL, target),
                helper.absolutePos(relative), 100).isEmpty()) {
            throw new AssertionError("fixture target " + target + " yields nothing to a core, so every "
                    + "accrual assertion below would compare zero against zero");
        }

        housing.settle(level);
        clearBuffer(housing);
        return housing;
    }

    /**
     * Hostile mobs whose drops are <b>not</b> gated on {@code killed_by_player}, deliberately disjoint
     * from the ones {@code CoreSocketGameTests} uses so the two suites cannot claim the same target.
     *
     * <p>The gating matters more than it looks. A core rolls loot with no killing player — that is
     * exactly how §4.2's no-Sigils rule is enforced — so any mob whose drop requires one yields
     * literally nothing to a core. An Enderman Soul Core produces no pearls and a Blaze one no rods.
     * That is correct for §4, and it is why §12.5's Ender and Blaze-adjacent cores will have to supply
     * their own loot tables in Phase 5 rather than inherit the mob's, which §4.2 already anticipates:
     * "the core supplies the rate and the loot table".
     */
    private static final String[] FIXTURE_TARGETS = {
            "minecraft:zombie", "minecraft:skeleton", "minecraft:creeper", "minecraft:spider",
            "minecraft:zombified_piglin", "minecraft:wither_skeleton",
    };

    private static String fixtureTarget(GameTestHelper helper, BlockPos relative) {
        BlockPos abs = helper.absolutePos(relative);
        int hash = abs.getX() * 7919 + abs.getY() * 104729 + abs.getZ() * 1299709;
        return FIXTURE_TARGETS[Math.floorMod(hash, FIXTURE_TARGETS.length)];
    }

    /** Pushes the housing's stored stamp back to exactly {@code ticks} ago, discarding any remainder. */
    static void backdate(CoreHousingBlockEntity housing, ServerLevel level, long ticks) {
        housing.setLastSettledForTest(level.getGameTime() - ticks);
    }

    /**
     * Moves the housing {@code ticks} further from its <em>current</em> stamp, so the unpaid
     * sub-period remainder is preserved across steps. That distinction is the whole point of the
     * many-small-settles test: an implementation that threw the remainder away would pass a
     * {@link #backdate}-based version of it and fail this one.
     */
    static void advanceBy(CoreHousingBlockEntity housing, ServerLevel level, long ticks) {
        housing.setLastSettledForTest(housing.lastSettled() - ticks);
    }

    static int bufferCount(CoreHousingBlockEntity housing) {
        int total = 0;
        for (int slot = CoreHousingBlockEntity.FIRST_BUFFER_SLOT; slot < housing.getContainerSize(); slot++) {
            total += housing.getItem(slot).getCount();
        }
        return total;
    }

    static void clearBuffer(CoreHousingBlockEntity housing) {
        for (int slot = CoreHousingBlockEntity.FIRST_BUFFER_SLOT; slot < housing.getContainerSize(); slot++) {
            housing.setItem(slot, ItemStack.EMPTY);
        }
    }

    /**
     * Saves and reloads the housing's block entity through the same NBT path a chunk unload/reload
     * uses, then puts the reloaded state back into the world.
     */
    private static CoreHousingBlockEntity roundTrip(CoreHousingBlockEntity housing, ServerLevel level) {
        // Full metadata, not "without": loadStatic reads the block entity type out of the tag, and a
        // tag saved without it deserialises to null.
        CompoundTag tag = housing.saveWithFullMetadata(level.registryAccess());
        BlockEntity fresh = BlockEntity.loadStatic(
                housing.getBlockPos(), housing.getBlockState(), tag, level.registryAccess());
        fresh.setLevel(level);
        return (CoreHousingBlockEntity) fresh;
    }
}
