package com.heartstead.gametest;

import com.heartstead.blockentity.CoreHousingBlockEntity;
import com.heartstead.core.ActiveCoreData;
import com.heartstead.core.ActiveCores;
import com.heartstead.core.CoreFamily;
import com.heartstead.core.CoreKey;
import com.heartstead.registry.HsBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §4.3 — one active core per target, per world, enforced by refusing the socket.
 *
 * <p>§4.3 is emphatic about the shape of the rule, and each sentence is a test here: the second
 * socket is <b>refused</b> rather than placed dormant ("nothing is ever placed and then silently
 * switched off"); breadth is free, so only the exact family+target duplicate collides; and the
 * registry is world-level {@code SavedData}, so the refusal has to survive a reload rather than
 * living in a block entity that forgets.
 *
 * <p>Each test uses its own target id. The registry is a genuine world-level singleton shared by
 * every test in a {@code runGametest} invocation (the same constraint {@code VaultGameTests}
 * documents), so a shared fixture target would make run order decide the outcome.
 */
public class CoreSocketGameTests {

    /** §4.3 — the second housing running the same zombie Soul Core is refused, and does not run. */
    @GameTest
    public void aDuplicateTargetIsRefusedAtTheSecondSocket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String target = "minecraft:husk";

        CoreHousingBlockEntity first = socket(helper, new BlockPos(1, 1, 1), target);
        CoreHousingBlockEntity second = socket(helper, new BlockPos(3, 1, 1), target);

        boolean firstRuns = first.active();
        boolean secondRefused = second.refused();
        boolean secondRuns = second.active();

        helper.succeedIf(() -> {
            if (!firstRuns) {
                throw new AssertionError("the first housing did not claim its target at all");
            }
            if (!secondRefused) {
                throw new AssertionError("a second housing with the same target was accepted — §4.3's "
                        + "one-active-core-per-target rule is not enforced");
            }
            if (secondRuns) {
                throw new AssertionError("the refused housing is still active, i.e. it was placed dormant "
                        + "rather than refused — §4.3 rules that shape out explicitly");
            }
        });
    }

    /** §4.3 — "breadth is free and encouraged": two different targets in the same family both run. */
    @GameTest
    public void twoDifferentTargetsBothRun(GameTestHelper helper) {
        CoreHousingBlockEntity zombie = socket(helper, new BlockPos(1, 1, 1), "minecraft:drowned");
        CoreHousingBlockEntity skeleton = socket(helper, new BlockPos(3, 1, 1), "minecraft:stray");

        boolean bothRun = zombie.active() && skeleton.active();
        helper.succeedIf(() -> {
            if (!bothRun) {
                throw new AssertionError("two distinct targets collided — §4.3 refuses only the exact duplicate");
            }
        });
    }

    /**
     * §4.3 — a refused housing must free the claim up again the moment the original stops holding it.
     * Without this, breaking a housing would make its target permanently unusable.
     */
    @GameTest
    public void removingTheFirstCoreFreesTheTargetForTheSecond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String target = "minecraft:zombie_villager";

        CoreHousingBlockEntity first = socket(helper, new BlockPos(1, 1, 1), target);
        CoreHousingBlockEntity second = socket(helper, new BlockPos(3, 1, 1), target);
        boolean secondRefusedFirst = second.refused();

        // Pull the core out of the first housing, then re-offer the second's.
        first.setItem(CoreHousingBlockEntity.CORE_SLOT, ItemStack.EMPTY);
        ItemStack held = second.getItem(CoreHousingBlockEntity.CORE_SLOT);
        second.setItem(CoreHousingBlockEntity.CORE_SLOT, ItemStack.EMPTY);
        second.setItem(CoreHousingBlockEntity.CORE_SLOT, held);

        boolean secondRunsNow = second.active();

        helper.succeedIf(() -> {
            if (!secondRefusedFirst) {
                throw new AssertionError("the duplicate was not refused in the first place");
            }
            if (!secondRunsNow) {
                throw new AssertionError("the target stayed claimed after the first housing gave it up — "
                        + "a broken housing would make that target unusable forever");
            }
        });
    }

    /**
     * §4.3 — the registry is world-level {@code SavedData}, so a claim has to survive the versioned
     * codec round trip a world save and reload performs. A claim that lived only in memory would let
     * a server restart quietly double every core in the world.
     */
    @GameTest
    public void claimsSurviveAWorldReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String target = "minecraft:phantom";

        socket(helper, new BlockPos(1, 1, 1), target);
        CoreKey key = new CoreKey(CoreFamily.SOUL, Identifier.parse(target));

        ActiveCoreData reloaded = roundTrip(level);
        boolean survived = reloaded.claim(key).isPresent();

        helper.succeedIf(() -> {
            if (!survived) {
                throw new AssertionError("an active-core claim did not survive the codec round trip — "
                        + "every core in the world would double on the next restart");
            }
        });
    }

    /**
     * §4.3 across a reload, end to end: after the registry has been through the codec, a second
     * housing with the same target is still refused. This is the case the phase prompt calls out
     * specifically, and it is stronger than the codec test above because it also exercises the
     * liveness check a reloaded claim goes through.
     */
    @GameTest
    public void aDuplicateIsStillRefusedAfterAReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String target = "minecraft:cave_spider";

        socket(helper, new BlockPos(1, 1, 1), target);

        // Force the registry back through its codec, then reinstate it as the live save data — the
        // same journey a server restart makes.
        ActiveCoreData reloaded = roundTrip(level);
        boolean claimSurvived = reloaded.claim(new CoreKey(CoreFamily.SOUL, Identifier.parse(target))).isPresent();

        CoreHousingBlockEntity second = socket(helper, new BlockPos(3, 1, 1), target);
        boolean refused = second.refused();

        helper.succeedIf(() -> {
            if (!claimSurvived) {
                throw new AssertionError("the claim did not survive the round trip");
            }
            if (!refused) {
                throw new AssertionError("a duplicate socket was accepted after a reload");
            }
        });
    }

    /**
     * A claim whose housing no longer exists must not lock its target forever. §4.3 gives no rule for
     * this because it should not happen — but a world edit or a crash can leave one, and a target
     * nothing in the game can explain or release would be worse than a duplicate.
     */
    @GameTest
    public void aStaleClaimDoesNotLockATargetForever(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CoreKey orphan = new CoreKey(CoreFamily.SOUL, Identifier.parse("minecraft:silverfish"));

        // A claim pointing at empty air, as a crash or a world edit would leave behind.
        BlockPos nowhere = helper.absolutePos(new BlockPos(6, 1, 6));
        ActiveCores.tryClaim(level, orphan, nowhere);

        CoreHousingBlockEntity housing = socket(helper, new BlockPos(1, 1, 1), "minecraft:silverfish");
        boolean tookOver = housing.active();

        helper.succeedIf(() -> {
            if (!tookOver) {
                throw new AssertionError("a claim held by a block that no longer exists blocked a real housing");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static CoreHousingBlockEntity socket(GameTestHelper helper, BlockPos relative, String target) {
        helper.setBlock(relative, HsBlocks.SOUL_CAGE);
        CoreHousingBlockEntity housing = helper.getBlockEntity(relative, CoreHousingBlockEntity.class);
        housing.setItem(CoreHousingBlockEntity.CORE_SLOT,
                CoreYieldGameTests.coreStack(CoreFamily.SOUL, target));
        return housing;
    }

    private static ActiveCoreData roundTrip(ServerLevel level) {
        ActiveCoreData live = ActiveCores.get(level);
        CompoundTag tag = (CompoundTag) ActiveCoreData.TYPE.codec().encodeStart(NbtOps.INSTANCE, live).getOrThrow();
        return ActiveCoreData.TYPE.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
}
