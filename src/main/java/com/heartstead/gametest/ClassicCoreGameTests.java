package com.heartstead.gametest;

import com.heartstead.blockentity.CoreHousingBlockEntity;
import com.heartstead.core.ClassicCore;
import com.heartstead.core.CoreAttunement;
import com.heartstead.core.CoreFamily;
import com.heartstead.core.CoreImprint;
import com.heartstead.core.CoreTier;
import com.heartstead.core.CoreYield;
import com.heartstead.registry.HsBlocks;
import com.heartstead.registry.HsComponents;
import com.heartstead.registry.HsItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §5 / §12.5 — the eleven classic farm replacements' override surface: a socketed core
 * targeting one of {@link ClassicCore}'s entries must roll its own bespoke loot table and its own
 * §12.5 rate rather than the generic §4.2 numbers the housing and the mob would otherwise supply,
 * and a milestone core must finish on its first qualifying event, not the family's generic
 * attunement threshold.
 */
public class ClassicCoreGameTests {

    /**
     * §5 Golem — the bespoke {@code classic_cores/golem} table gives exactly 1 Iron Ingot per roll.
     * Iron Golem's own vanilla loot table would give 3-5 unconditionally, which would blow past
     * §12.5's "90/hr" the moment {@link CoreTargets} stopped overriding it.
     */
    @GameTest
    public void aGolemCoreYieldsExactlyOneIngotPerRoll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));

        CoreAttunement golem = attuned(CoreFamily.SOUL, ClassicCore.GOLEM);
        List<ItemStack> produced = CoreYield.produce(level, golem, pos, 1);

        helper.succeedIf(() -> {
            if (produced.size() != 1 || produced.get(0).getCount() != 1 || !produced.get(0).is(Items.IRON_INGOT)) {
                throw new AssertionError("one Golem Core roll produced " + produced
                        + ", expected exactly one Iron Ingot");
            }
        });
    }

    /** §5 Geode — "4 shards / 60s" is one roll with a set_count function, not four separate rolls. */
    @GameTest
    public void aGeodeCoreYieldsFourShardsPerRoll(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));

        CoreAttunement geode = attuned(CoreFamily.LITHIC, ClassicCore.GEODE);
        List<ItemStack> produced = CoreYield.produce(level, geode, pos, 1);

        int totalShards = produced.stream().mapToInt(ItemStack::getCount).sum();
        helper.succeedIf(() -> {
            if (totalShards != 4) {
                throw new AssertionError("one Geode Core roll produced " + totalShards
                        + " amethyst shards, expected exactly 4");
            }
        });
    }

    /**
     * DESIGN.md §4.2's no-Sigils rule extends unchanged to §5: heavy rolls across every one of the
     * eleven classic cores must never produce the currency they were bought with.
     */
    @GameTest
    public void noClassicCoreEverYieldsASigil(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));

        int sigils = 0;
        int total = 0;
        for (ClassicCore core : ClassicCore.values()) {
            List<ItemStack> produced = CoreYield.produce(level, attuned(core.family(), core), pos, 500);
            sigils += produced.stream().filter(stack -> stack.is(CoreYield.NEVER_FROM_CORES))
                    .mapToInt(ItemStack::getCount).sum();
            total += produced.size();
        }

        int foundSigils = sigils;
        int foundTotal = total;
        helper.succeedIf(() -> {
            if (foundSigils != 0) {
                throw new AssertionError("the eleven classic cores produced " + foundSigils + " Sigils between them");
            }
            if (foundTotal == 0) {
                throw new AssertionError("no classic core produced anything, so the zero-Sigil result proves nothing");
            }
        });
    }

    /**
     * §5's milestone shape — "doing the thing once is the attunement" — needs the per-target
     * threshold override to be 1, not the Soul family's generic 16. A Primed Core pre-targeted at
     * craft time (progress 0) must finish on the very first {@link CoreImprint#offer}.
     */
    @GameTest
    public void aMilestoneCoreFinishesOnItsFirstEvent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack preTargeted = new ItemStack(HsItems.primedCore(CoreFamily.SOUL));
        preTargeted.set(HsComponents.CORE_ATTUNEMENT,
                new CoreAttunement(CoreAttunement.CURRENT_VERSION, CoreFamily.SOUL,
                        Optional.of(ClassicCore.GUARDIAN.target()), 0, CoreTier.ONE));
        player.getInventory().setItem(15, preTargeted);

        CoreImprint.offer(player, CoreFamily.SOUL, ClassicCore.GUARDIAN.target());

        ItemStack after = player.getInventory().getItem(15);
        helper.succeedIf(() -> {
            if (!after.is(HsItems.core(CoreFamily.SOUL))) {
                throw new AssertionError("a Guardian Core did not finish on its first qualifying event: " + after);
            }
        });
    }

    /**
     * §5 — the core supplies the rate, overriding §4.2's generic Soul Cage period. Golem prices at
     * 40s (800 ticks); the Soul Cage's own §4.2 base is 20s (400 ticks). Settling exactly one Golem
     * period must yield exactly one cycle — two would mean the generic period leaked through.
     */
    @GameTest
    public void aClassicCoresPeriodOverridesTheHousingsGenericRate(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, HsBlocks.SOUL_CAGE);

        CoreHousingBlockEntity housing = helper.getBlockEntity(pos, CoreHousingBlockEntity.class);
        housing.setItem(CoreHousingBlockEntity.CORE_SLOT, coreStack(CoreFamily.SOUL, ClassicCore.GOLEM));

        long golemPeriodTicks = 800; // §12.5 — 40s at tier I, before any multiplier.
        housing.setLastSettledForTest(level.getGameTime() - golemPeriodTicks);

        int cycles = housing.settle(level);
        helper.succeedIf(() -> {
            if (cycles != 1) {
                throw new AssertionError("settling exactly one Golem period produced " + cycles
                        + " cycles, expected 1 — the Soul Cage's generic 20s period leaked through");
            }
        });
    }

    /**
     * §4.2's XP accrual — a settle grants {@code cycles * xpPerCycle}, the same way it grants
     * {@code cycles} loot rolls. Ender's classic-core override is 5 XP/cycle (§12.5); settling exactly
     * one Ender period (400 ticks, §12.5) must accrue exactly 5, not the Soul Cage's generic baseline.
     */
    @GameTest
    public void settlingAClassicCoreAccruesItsOwnXpPerCycle(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(4, 1, 4);
        helper.setBlock(pos, HsBlocks.SOUL_CAGE);

        CoreHousingBlockEntity housing = helper.getBlockEntity(pos, CoreHousingBlockEntity.class);
        housing.setItem(CoreHousingBlockEntity.CORE_SLOT, coreStack(CoreFamily.SOUL, ClassicCore.ENDER));

        long enderPeriodTicks = 400; // §12.5 — 20s at tier I.
        housing.setLastSettledForTest(level.getGameTime() - enderPeriodTicks);
        housing.settle(level);

        helper.succeedIf(() -> {
            if (housing.storedExperience() != 5.0) {
                throw new AssertionError("settling one Ender Core period accrued " + housing.storedExperience()
                        + " XP, expected exactly 5");
            }
        });
    }

    /**
     * {@link CoreHousingBlockEntity#collectExperience} hands the player whatever whole points have
     * accrued and never leaves a whole point uncollected — §4.2's "nothing owed is ever silently
     * dropped" rule, applied to XP the same way it already applies to items.
     */
    @GameTest
    public void collectingExperienceGrantsItAndClearsTheHousing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(6, 1, 6);
        helper.setBlock(pos, HsBlocks.SOUL_CAGE);

        CoreHousingBlockEntity housing = helper.getBlockEntity(pos, CoreHousingBlockEntity.class);
        housing.setItem(CoreHousingBlockEntity.CORE_SLOT, coreStack(CoreFamily.SOUL, ClassicCore.APIARY));
        housing.setLastSettledForTest(level.getGameTime() - 3600L); // 3 Apiary cycles (1200t each) at 3 XP each = 9.
        housing.settle(level);

        double accrued = housing.storedExperience();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int xpBefore = player.totalExperience;
        housing.collectExperience(player);

        helper.succeedIf(() -> {
            if (accrued <= 0.0) {
                throw new AssertionError("settle produced no XP to collect, so this test proves nothing");
            }
            if (housing.storedExperience() >= 1.0) {
                throw new AssertionError("collecting left " + housing.storedExperience()
                        + " whole XP uncollected in the housing");
            }
            if (player.totalExperience <= xpBefore) {
                throw new AssertionError("collecting experience did not grant the player any XP");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static CoreAttunement attuned(CoreFamily family, ClassicCore core) {
        return new CoreAttunement(CoreAttunement.CURRENT_VERSION, family,
                Optional.of(core.target()), 999, CoreTier.ONE);
    }

    private static ItemStack coreStack(CoreFamily family, ClassicCore core) {
        ItemStack stack = new ItemStack(HsItems.core(family));
        stack.set(HsComponents.CORE_ATTUNEMENT, attuned(family, core));
        return stack;
    }
}
