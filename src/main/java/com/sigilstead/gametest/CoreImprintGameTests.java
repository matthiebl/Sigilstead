package com.sigilstead.gametest;

import com.sigilstead.core.CoreAttunement;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.core.CoreImprint;
import com.sigilstead.registry.HsComponents;
import com.sigilstead.registry.HsItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §4.1's hand rules, which are the part of attunement most likely to be got subtly wrong:
 * an un-attuned core locks a target <b>only</b> from a hand, but an attuned one counts from anywhere
 * and every one of them counts in parallel.
 *
 * <p>These drive {@link CoreImprint#offer} directly rather than killing real mobs. The event wiring
 * is three one-line registrations; the rules underneath are where the behaviour lives, and driving
 * them directly is what lets a test say "a core in the backpack must NOT lock" without needing a
 * backpack full of mobs to prove it.
 */
public class CoreImprintGameTests {

    private static final Identifier ZOMBIE = Identifier.parse("minecraft:zombie");
    private static final Identifier SKELETON = Identifier.parse("minecraft:skeleton");

    /** §4.1 — "an un-attuned Primed Core has to be in your main hand or offhand to take a target". */
    @GameTest
    public void anUnattunedCoreInTheBackpackNeverLocks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(20, primed(CoreFamily.SOUL));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        CoreAttunement after = attunementAt(player, 20);
        helper.succeedIf(() -> {
            if (after == null) {
                throw new AssertionError("the core lost its attunement component entirely");
            }
            if (!after.unattuned()) {
                throw new AssertionError("a core sitting in the inventory locked onto " + after.target()
                        + " — §4.1: sitting in the inventory it is inert");
            }
        });
    }

    /** §4.1 — "if un-attuned cores of the same family are in both hands, the main hand takes the lock". */
    @GameTest
    public void theMainHandWinsTheLockOverTheOffhand(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.MAINHAND, primed(CoreFamily.SOUL));
        player.setItemSlot(EquipmentSlot.OFFHAND, primed(CoreFamily.SOUL));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        CoreAttunement main = attunement(player.getItemBySlot(EquipmentSlot.MAINHAND));
        CoreAttunement off = attunement(player.getItemBySlot(EquipmentSlot.OFFHAND));

        helper.succeedIf(() -> {
            if (main == null || main.unattuned()) {
                throw new AssertionError("the main-hand core did not take the lock");
            }
            if (off == null || !off.unattuned()) {
                throw new AssertionError("the offhand core locked too — exactly one core locks per event");
            }
        });
    }

    /**
     * §4.1 — <b>one event advances one core</b>. Three cores locked to the same target must not all
     * tick off a single kill: that would make each extra duplicate core cheaper than the last, for a
     * target §4.3 will only ever let you run one of.
     */
    @GameTest
    public void onlyOneCoreAdvancesPerEvent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        for (int slot : new int[] {10, 11, 12}) {
            player.getInventory().setItem(slot, primedWithTarget(CoreFamily.SOUL, ZOMBIE, 3));
        }

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        int total = attunementAt(player, 10).progress()
                + attunementAt(player, 11).progress()
                + attunementAt(player, 12).progress();

        helper.succeedIf(() -> {
            if (total != 10) {
                throw new AssertionError("three zombie cores at 3/3/3 totalled " + total + " after one kill, "
                        + "expected 10 — exactly one core may advance per event");
            }
        });
    }

    /**
     * §4.1 — the credit goes to the core in your hand. With one event to give, the only order a
     * player can predict without opening their inventory is "the one I'm holding".
     */
    @GameTest
    public void theHeldCoreTakesTheCreditOverOneInTheBackpack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(13, primedWithTarget(CoreFamily.SOUL, ZOMBIE, 3));
        player.setItemSlot(EquipmentSlot.MAINHAND, primedWithTarget(CoreFamily.SOUL, ZOMBIE, 3));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        int held = attunement(player.getItemBySlot(EquipmentSlot.MAINHAND)).progress();
        int stowed = attunementAt(player, 13).progress();

        helper.succeedIf(() -> {
            if (held != 4 || stowed != 3) {
                throw new AssertionError("held core is at " + held + " and the stowed one at " + stowed
                        + ", expected 4 and 3 — the held core takes the credit");
            }
        });
    }

    /**
     * §4.1 — cores on <b>different</b> targets still advance independently. This is the half of
     * "nothing is queued and nothing waits its turn" that survives the one-core-per-event rule, and
     * it is the case the wiki's own example describes.
     */
    @GameTest
    public void coresOnDifferentTargetsAdvanceIndependently(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(18, primedWithTarget(CoreFamily.SOUL, ZOMBIE, 2));
        player.getInventory().setItem(19, primedWithTarget(CoreFamily.SOUL, SKELETON, 2));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);
        CoreImprint.offer(player, CoreFamily.SOUL, SKELETON);

        int zombie = attunementAt(player, 18).progress();
        int skeleton = attunementAt(player, 19).progress();

        helper.succeedIf(() -> {
            if (zombie != 3 || skeleton != 3) {
                throw new AssertionError("zombie core at " + zombie + ", skeleton core at " + skeleton
                        + ", expected 3 and 3 — different targets must not compete for one event");
            }
        });
    }

    /**
     * §4.1 — an un-attuned core in the hand does not steal an event that an already-attuned core
     * elsewhere could count. The kill advances the zombie core; the empty one is still waiting.
     */
    @GameTest
    public void anAttunedCoreTakesTheEventBeforeAnUnattunedOneLocks(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(21, primedWithTarget(CoreFamily.SOUL, ZOMBIE, 2));
        player.setItemSlot(EquipmentSlot.MAINHAND, primed(CoreFamily.SOUL));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        int attuned = attunementAt(player, 21).progress();
        CoreAttunement held = attunement(player.getItemBySlot(EquipmentSlot.MAINHAND));

        helper.succeedIf(() -> {
            if (attuned != 3) {
                throw new AssertionError("the already-attuned core is at " + attuned + ", expected 3");
            }
            if (!held.unattuned()) {
                throw new AssertionError("the held core locked on an event another core already counted — "
                        + "one event does one thing");
            }
        });
    }

    /** §4.1 — "wrong-target events are inert. No penalty, no reset — pillar 4." */
    @GameTest
    public void aWrongTargetEventChangesNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(14, primedWithTarget(CoreFamily.SOUL, ZOMBIE, 5));

        CoreImprint.offer(player, CoreFamily.SOUL, SKELETON);

        CoreAttunement after = attunementAt(player, 14);
        helper.succeedIf(() -> {
            if (after.progress() != 5) {
                throw new AssertionError("a wrong-target kill moved progress to " + after.progress()
                        + " — it must be completely inert");
            }
            if (!after.targets(ZOMBIE)) {
                throw new AssertionError("a wrong-target kill re-aimed the core to " + after.target());
            }
        });
    }

    /** §4.1 — a core of a different family is untouched by another family's event. */
    @GameTest
    public void anotherFamilysEventIsIgnored(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.MAINHAND, primed(CoreFamily.VERDANT));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        CoreAttunement after = attunement(player.getItemBySlot(EquipmentSlot.MAINHAND));
        helper.succeedIf(() -> {
            if (!after.unattuned()) {
                throw new AssertionError("a Verdant Core locked onto a mob kill");
            }
        });
    }

    /**
     * §4.1 — at the threshold "the stack converts into the finished Core, named for what it learned".
     * The conversion has to happen in the slot the Primed Core was in, keeping its target.
     */
    @GameTest
    public void reachingTheThresholdConvertsTheStackInPlace(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int threshold = com.sigilstead.config.HsConfigManager.get().attunementThresholds().forFamily(CoreFamily.SOUL);
        player.getInventory().setItem(16, primedWithTarget(CoreFamily.SOUL, ZOMBIE, threshold - 1));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        ItemStack converted = player.getInventory().getItem(16);
        CoreAttunement after = attunement(converted);

        helper.succeedIf(() -> {
            if (!converted.is(HsItems.core(CoreFamily.SOUL))) {
                throw new AssertionError("the stack at the threshold is " + converted
                        + ", expected the finished Soul Core");
            }
            if (after == null || !after.targets(ZOMBIE)) {
                throw new AssertionError("the finished core lost the target it learned");
            }
        });
    }

    /** A core one short of the threshold is still a Primed Core — the conversion is not early. */
    @GameTest
    public void oneShortOfTheThresholdStaysPrimed(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int threshold = com.sigilstead.config.HsConfigManager.get().attunementThresholds().forFamily(CoreFamily.SOUL);
        player.getInventory().setItem(17, primedWithTarget(CoreFamily.SOUL, ZOMBIE, threshold - 2));

        CoreImprint.offer(player, CoreFamily.SOUL, ZOMBIE);

        ItemStack stack = player.getInventory().getItem(17);
        helper.succeedIf(() -> {
            if (!stack.is(HsItems.primedCore(CoreFamily.SOUL))) {
                throw new AssertionError("a core converted one event early: " + stack);
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static ItemStack primed(CoreFamily family) {
        // The fresh attunement is a default component on the item, so a bare stack is already primed.
        return new ItemStack(HsItems.primedCore(family));
    }

    private static ItemStack primedWithTarget(CoreFamily family, Identifier target, int progress) {
        ItemStack stack = new ItemStack(HsItems.primedCore(family));
        stack.set(HsComponents.CORE_ATTUNEMENT, new CoreAttunement(
                CoreAttunement.CURRENT_VERSION, family, java.util.Optional.of(target), progress,
                com.sigilstead.core.CoreTier.ONE));
        return stack;
    }

    private static CoreAttunement attunement(ItemStack stack) {
        return stack.get(HsComponents.CORE_ATTUNEMENT);
    }

    private static CoreAttunement attunementAt(ServerPlayer player, int slot) {
        return attunement(player.getInventory().getItem(slot));
    }
}
