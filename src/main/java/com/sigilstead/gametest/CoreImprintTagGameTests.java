package com.sigilstead.gametest;

import com.sigilstead.core.CoreImprint;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * DESIGN.md §4.1 — the four imprint allow-lists, which are the balance surface of the whole of §4:
 * whatever a Soul Core may lock onto is a loot table §4.2 will then farm forever.
 *
 * <p>These tests exist because the lists were originally category rules in Java ("any hostile mob"),
 * and a category rule cannot be read at a glance. Now that they are tags, the tests pin the two
 * things a tag file can silently get wrong: that it loads at all (a typo'd entry means a core that
 * can never be attuned, with no error anywhere), and that the entries which must <em>not</em> be on
 * it stay off.
 */
public class CoreImprintTagGameTests {

    /**
     * §4.2's economy guard, at the imprint end. Each of these drops something a core would then
     * produce every few seconds, far above the rate §12.5 prices the dedicated core at — an Evoker
     * Soul Core is a Totem of Undying every 20 seconds, against §12.5's 1.6 totems/hour.
     */
    private static final List<String> MUST_NOT_BE_SOUL_TARGETS = List.of(
            "minecraft:ender_dragon",   // the fight itself; §12.1 already keeps it out of the Sigil table
            "minecraft:wither",         // §12.1 excludes it explicitly — skulls are core-producible
            "minecraft:evoker",         // totem_of_undying, unconditional
            "minecraft:elder_guardian", // wet sponge and an armour trim template, unconditional
            "minecraft:warden",         // sculk catalyst, unconditional
            "minecraft:ravager"         // saddle, unconditional
    );

    /** Every §4.1 imprint tag must resolve to a non-empty set, or that family can never be attuned. */
    @GameTest
    public void allFourImprintTagsLoadAndAreNonEmpty(GameTestHelper helper) {
        int soul = countEntities(CoreImprint.SOUL_IMPRINTABLE);
        int pastoral = countEntities(CoreImprint.PASTORAL_IMPRINTABLE);
        int verdant = countBlocks(CoreImprint.VERDANT_IMPRINTABLE);
        int lithic = countBlocks(CoreImprint.LITHIC_IMPRINTABLE);

        helper.succeedIf(() -> {
            if (soul == 0) {
                throw new AssertionError("sigilstead:soul_imprintable is empty — no Soul Core could ever attune");
            }
            if (pastoral == 0) {
                throw new AssertionError("sigilstead:pastoral_imprintable is empty");
            }
            if (verdant == 0) {
                throw new AssertionError("sigilstead:verdant_imprintable is empty");
            }
            if (lithic == 0) {
                throw new AssertionError("sigilstead:lithic_imprintable is empty");
            }
        });
    }

    /** §4.2 — the bosses and §12.1 mini-bosses stay off the Soul list. */
    @GameTest
    public void bossesAndMiniBossesAreNotSoulTargets(GameTestHelper helper) {
        List<String> leaked = new ArrayList<>();
        for (String id : MUST_NOT_BE_SOUL_TARGETS) {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse(id)).orElse(null);
            if (type != null && CoreImprint.isImprintable(type, CoreImprint.SOUL_IMPRINTABLE)) {
                leaked.add(id);
            }
        }

        helper.succeedIf(() -> {
            if (!leaked.isEmpty()) {
                throw new AssertionError("these are Soul Core targets and must not be: " + leaked
                        + " — each farms a drop far above the rate §12.5 prices its dedicated core at");
            }
        });
    }

    /** The ordinary cases the lists exist to allow, so the guard above cannot pass by emptying them. */
    @GameTest
    public void theEverydayTargetsAreAllowed(GameTestHelper helper) {
        boolean zombie = isSoul("minecraft:zombie");
        boolean skeleton = isSoul("minecraft:skeleton");
        boolean cow = isPastoral("minecraft:cow");
        boolean bee = isPastoral("minecraft:bee");
        boolean wheat = Blocks.WHEAT.defaultBlockState().is(CoreImprint.VERDANT_IMPRINTABLE);
        boolean netherWart = Blocks.NETHER_WART.defaultBlockState().is(CoreImprint.VERDANT_IMPRINTABLE);
        boolean deepslate = Blocks.DEEPSLATE.defaultBlockState().is(CoreImprint.LITHIC_IMPRINTABLE);

        helper.succeedIf(() -> {
            if (!zombie || !skeleton) {
                throw new AssertionError("zombie/skeleton are not Soul targets (" + zombie + "/" + skeleton + ")");
            }
            if (!cow || !bee) {
                throw new AssertionError("cow/bee are not Pastoral targets (" + cow + "/" + bee + ")");
            }
            if (!wheat || !netherWart) {
                throw new AssertionError("wheat/nether wart are not Verdant targets (" + wheat + "/" + netherWart + ")");
            }
            if (!deepslate) {
                throw new AssertionError("deepslate is not a Lithic target");
            }
        });
    }

    /**
     * §5's classic-farm cores get their targets from their own prime recipes, not from a player
     * imprinting on a boss. A villager is the check that the Pastoral list is a list of *animals* —
     * villagers breed, and a villager core would be an emerald farm nobody specced.
     */
    @GameTest
    public void villagersAreNotPastoralTargets(GameTestHelper helper) {
        boolean villager = isPastoral("minecraft:villager");
        helper.succeedIf(() -> {
            if (villager) {
                throw new AssertionError("villagers are a Pastoral target — that is an unspecced emerald farm");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isSoul(String id) {
        return CoreImprint.isImprintable(
                BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)), CoreImprint.SOUL_IMPRINTABLE);
    }

    private static boolean isPastoral(String id) {
        return CoreImprint.isImprintable(
                BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)), CoreImprint.PASTORAL_IMPRINTABLE);
    }

    private static int countEntities(TagKey<EntityType<?>> tag) {
        int found = 0;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (CoreImprint.isImprintable(type, tag)) {
                found++;
            }
        }
        return found;
    }

    private static int countBlocks(TagKey<Block> tag) {
        int found = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block.defaultBlockState().is(tag)) {
                found++;
            }
        }
        return found;
    }
}
