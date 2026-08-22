package com.sigilstead.gametest;

import com.sigilstead.blockentity.CoreHousingBlockEntity;
import com.sigilstead.core.CoreAttunement;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.core.CoreTier;
import com.sigilstead.core.CoreYield;
import com.sigilstead.registry.HsBlocks;
import com.sigilstead.registry.HsComponents;
import com.sigilstead.registry.HsItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * DESIGN.md §4.2's load-bearing invariant: <b>no core ever yields a Sigil</b>.
 *
 * <p>§4.2 calls this out as "exactly the sort of invariant a loot-table refactor reintroduces
 * silently", and it is the reason the economy does not bootstrap — cores are bought with Sigils, so a
 * core that produced Sigils would produce cores. These tests socket real cores into real housings and
 * assert on what actually comes out, rather than trusting either of the two layers
 * {@link CoreYield} enforces it at.
 *
 * <p>The zombie case is the sharp one: {@code SigilLoot} really does inject a Sigil pool into the
 * zombie loot table (§12.1's 0.15% hostile-mob rate), so if the {@code killedByPlayer} guard were
 * ever dropped from that pool, or a core ever supplied a killing player, this test would start
 * failing rather than the economy quietly inflating.
 */
public class CoreYieldGameTests {

    /** Enough rolls that a 0.15% per-kill Sigil rate would show up hundreds of times over. */
    private static final int HEAVY_ROLLS = 20_000;

    /**
     * §4.2 — a socketed Soul Core rolls the zombie loot table many thousands of times and produces
     * exactly zero Sigils, while still producing the mob's ordinary drops.
     */
    @GameTest
    public void aSocketedSoulCoreNeverYieldsSigils(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));

        CoreAttunement zombie = attuned(CoreFamily.SOUL, "minecraft:zombie");
        List<ItemStack> produced = CoreYield.produce(level, zombie, pos, HEAVY_ROLLS);

        int sigils = countSigils(produced);
        boolean producedAnything = !produced.isEmpty();

        helper.succeedIf(() -> {
            if (sigils != 0) {
                throw new AssertionError("a Soul Core produced " + sigils + " Sigils over " + HEAVY_ROLLS
                        + " rolls — §4.2's no-Sigils-from-cores rule is broken and the economy bootstraps");
            }
            if (!producedAnything) {
                throw new AssertionError("a zombie Soul Core produced nothing at all over " + HEAVY_ROLLS
                        + " rolls, so the zero-Sigil result above proves nothing");
            }
        });
    }

    /**
     * §4.2 — the rule is "not at any housing, any tier, or through any loot table it inherits", so
     * every family is checked, not just the mob one.
     */
    @GameTest
    public void noFamilyEverYieldsSigils(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));

        int sigils = 0;
        int total = 0;
        for (CoreAttunement core : List.of(
                attuned(CoreFamily.SOUL, "minecraft:skeleton"),
                attuned(CoreFamily.VERDANT, "minecraft:wheat"),
                attuned(CoreFamily.PASTORAL, "minecraft:cow"),
                attuned(CoreFamily.LITHIC, "minecraft:deepslate"))) {
            List<ItemStack> produced = CoreYield.produce(level, core, pos, 2000);
            sigils += countSigils(produced);
            total += produced.size();
        }

        int foundSigils = sigils;
        int foundTotal = total;
        helper.succeedIf(() -> {
            if (foundSigils != 0) {
                throw new AssertionError("cores produced " + foundSigils + " Sigils across the four families");
            }
            if (foundTotal == 0) {
                throw new AssertionError("no family produced anything, so this test asserts nothing");
            }
        });
    }

    /**
     * §4.1 — a Verdant Core targets "a mature crop", so its yield must be the mature drop. A default
     * (age 0) wheat block drops a single seed, which would make every Verdant Core worthless without
     * anyone noticing it was broken rather than merely weak.
     */
    @GameTest
    public void aVerdantCoreYieldsTheMatureHarvest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));

        List<ItemStack> produced = CoreYield.produce(level, attuned(CoreFamily.VERDANT, "minecraft:wheat"), pos, 200);
        boolean anyWheat = produced.stream().anyMatch(stack -> stack.is(Items.WHEAT));

        helper.succeedIf(() -> {
            if (!anyWheat) {
                throw new AssertionError("a wheat Verdant Core produced no wheat over 200 harvests — "
                        + "it is rolling the immature crop's loot table");
            }
        });
    }

    /** A core whose target no longer exists produces nothing rather than throwing on the settle path. */
    @GameTest
    public void aCoreWithAnUnknownTargetProducesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(0, 1, 0));

        List<ItemStack> produced =
                CoreYield.produce(level, attuned(CoreFamily.SOUL, "sigilstead:no_such_mob"), pos, 10);

        helper.succeedIf(() -> {
            if (!produced.isEmpty()) {
                throw new AssertionError("a core targeting a nonexistent mob produced " + produced.size() + " stacks");
            }
        });
    }

    /**
     * The end-to-end version of the same rule: a real core in a real Soul Cage, settled over a long
     * span, must never put a Sigil into its buffer. This is the path a player actually exercises, and
     * it covers the delivery code as well as {@link CoreYield}.
     */
    @GameTest
    public void aSocketedHousingNeverBuffersASigil(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, HsBlocks.SOUL_CAGE);

        CoreHousingBlockEntity housing = helper.getBlockEntity(pos, CoreHousingBlockEntity.class);
        housing.setItem(CoreHousingBlockEntity.CORE_SLOT,
                coreStack(CoreFamily.SOUL, "minecraft:zombie"));

        // Settle repeatedly so the buffer fills, empties and fills again many times over.
        int sigils = 0;
        for (int round = 0; round < 40; round++) {
            housing.settle(level);
            for (int slot = CoreHousingBlockEntity.FIRST_BUFFER_SLOT; slot < housing.getContainerSize(); slot++) {
                if (housing.getItem(slot).is(CoreYield.NEVER_FROM_CORES)) {
                    sigils++;
                }
                housing.setItem(slot, ItemStack.EMPTY);
            }
        }

        int found = sigils;
        helper.succeedIf(() -> {
            if (found != 0) {
                throw new AssertionError("a socketed Soul Cage buffered " + found + " excluded currency stacks");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    static CoreAttunement attuned(CoreFamily family, String target) {
        return new CoreAttunement(CoreAttunement.CURRENT_VERSION, family,
                Optional.of(Identifier.parse(target)), 999, CoreTier.ONE);
    }

    static ItemStack coreStack(CoreFamily family, String target) {
        ItemStack stack = new ItemStack(HsItems.core(family));
        stack.set(HsComponents.CORE_ATTUNEMENT, attuned(family, target));
        return stack;
    }

    private static int countSigils(List<ItemStack> produced) {
        int found = 0;
        for (ItemStack stack : produced) {
            if (stack.is(CoreYield.NEVER_FROM_CORES)) {
                found += stack.getCount();
            }
        }
        return found;
    }

    /** Unused registry guard: fails loudly if a fixture id stops resolving after a version bump. */
    static void assertTargetExists(String target) {
        Identifier id = Identifier.parse(target);
        if (BuiltInRegistries.ENTITY_TYPE.getOptional(id).isEmpty()
                && BuiltInRegistries.BLOCK.getOptional(id).isEmpty()) {
            throw new AssertionError("test fixture target no longer exists: " + target);
        }
    }
}
