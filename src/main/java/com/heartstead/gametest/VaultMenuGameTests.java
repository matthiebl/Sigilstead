package com.heartstead.gametest;

import com.heartstead.vault.Vault;
import com.heartstead.vault.VaultMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * DESIGN.md §2.4 — item-conservation GameTests for the two new item-moving paths the Vault screen
 * adds on top of the already-tested {@link Vault} facade (CONVENTIONS.md §8): shift-click deposit
 * ({@link VaultMenu#quickMoveStack}) and the click-to-withdraw network intent
 * ({@link VaultMenu#handleWithdraw}). Both are exercised directly against {@link VaultMenu} rather
 * than over real networking, since a GameTest has no client connection to send packets through —
 * the payload handler in {@code HsPayloads} is a thin wrapper around {@code handleWithdraw} for
 * exactly this reason.
 */
public class VaultMenuGameTests {

    /** Shift-clicking a player-inventory slot must deposit it into the Vault, not just vanish it from the slot. */
    @GameTest
    public void shiftClickDepositsIntoVault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ensureActivatedWithReach(level);
        Item item = Items.LAPIS_LAZULI;
        ensureDistinctTypeHeadroom(level, item);
        int baseline = Vault.get(level).count(item);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(item, 20));

        VaultMenu menu = new VaultMenu(0, player.getInventory());
        Slot slot = findPlayerInventorySlot(menu, player, 0);
        ItemStack moved = menu.quickMoveStack(player, slot.index);

        int afterDeposit = Vault.get(level).count(item);
        boolean slotNowEmpty = !player.getInventory().getItem(0).is(item) || player.getInventory().getItem(0).isEmpty();

        helper.succeedIf(() -> {
            if (moved.getCount() != 20 || !moved.is(item)) {
                throw new AssertionError("quickMoveStack returned " + moved + ", expected a copy of 20 " + item);
            }
            if (afterDeposit != baseline + 20) {
                throw new AssertionError("vault count after shift-click deposit = " + afterDeposit + ", expected " + (baseline + 20));
            }
            if (!slotNowEmpty) {
                throw new AssertionError("player slot 0 still holds " + player.getInventory().getItem(0) + " after a full deposit");
            }
        });
    }

    /** A withdraw intent must move exactly the requested (and available) amount, with nothing lost or duplicated. */
    @GameTest
    public void withdrawIntentConservesExactCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ensureActivatedWithReach(level);
        Item item = Items.REDSTONE;
        ensureDistinctTypeHeadroom(level, item);

        Vault.deposit(level, new ItemStack(item, 12));
        int beforeWithdraw = Vault.get(level).count(item);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VaultMenu menu = new VaultMenu(0, player.getInventory());
        menu.handleWithdraw(player, item, 12);

        int afterWithdraw = Vault.get(level).count(item);
        int inInventory = countInInventory(player, item);

        helper.succeedIf(() -> {
            if (afterWithdraw != beforeWithdraw - 12) {
                throw new AssertionError("vault count after withdraw intent = " + afterWithdraw + ", expected " + (beforeWithdraw - 12));
            }
            if (inInventory != 12) {
                throw new AssertionError("player inventory holds " + inInventory + " redstone after withdrawing 12");
            }
        });
    }

    /**
     * The bug this guards against: vanilla's shift-click handler ({@code AbstractContainerMenu#clicked},
     * {@code ContainerInput.QUICK_MOVE}) calls {@link VaultMenu#quickMoveStack} in a loop with no
     * iteration cap of its own —
     * {@code while (!clicked.isEmpty() && ItemStack.isSameItem(slot.getItem(), clicked)) clicked = quickMoveStack(...);}
     * — and stops only when {@code quickMoveStack} returns {@link ItemStack#EMPTY}. A Vault that
     * refuses a deposit outright (distinct-type cap, or a type already at its per-type stack-depth
     * cap, DESIGN.md §2.3) leaves the slot's contents unchanged; returning anything non-empty in that
     * case spins the loop forever on the server thread — 100%+ CPU, no exception, looks exactly like
     * a hang because it is one. Bypassing this loop (a single direct {@code quickMoveStack} call, as
     * every other test here does) can never catch it, which is exactly how it shipped once already.
     */
    @GameTest
    public void shiftClickAgainstCappedTypeDoesNotHang(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ensureActivatedWithReach(level);
        Item item = Items.GOLDEN_APPLE;
        ensureDistinctTypeHeadroom(level, item);

        var tier = Vault.capacityTier(level);
        int perTypeCap = tier.stackDepthCap() * new ItemStack(item).getMaxStackSize();
        int alreadyStored = Vault.get(level).count(item);
        int toFill = Math.max(0, perTypeCap - alreadyStored);
        if (toFill > 0) {
            Vault.deposit(level, new ItemStack(item, toFill));
        }
        // The vault now holds exactly perTypeCap of `item` — any further deposit must be refused outright.

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(item, item.getDefaultMaxStackSize()));
        VaultMenu menu = new VaultMenu(0, player.getInventory());
        Slot slot = findPlayerInventorySlot(menu, player, 0);

        int iterations = 0;
        ItemStack clicked = menu.quickMoveStack(player, slot.index);
        iterations++;
        while (!clicked.isEmpty() && ItemStack.isSameItem(slot.getItem(), clicked) && iterations < 1000) {
            clicked = menu.quickMoveStack(player, slot.index);
            iterations++;
        }
        int iterationsFinal = iterations;

        helper.succeedIf(() -> {
            if (iterationsFinal >= 1000) {
                throw new AssertionError(
                        "quickMoveStack loop did not terminate within 1000 iterations against a Vault at its per-type cap — infinite loop");
            }
        });
    }

    /**
     * A synced snapshot with many entries (27, matching the T1 distinct-type cap) shouldn't itself
     * cause trouble across many ticks and a withdraw — a broader sanity check alongside the
     * loop-termination test above, which is what the actual reported hang traced back to.
     *
     * <p>Doesn't rely on {@link Vault#capacityTier}, deliberately — {@link VaultData} is a singleton
     * shared by every test in this run (see {@code VaultGameTests}' class javadoc), and other tests
     * push the *live* cap up by spending Sigils, which would make "fill to the current cap" deposit
     * thousands of items instead of the 27 this test is actually about.
     */
    @GameTest
    public void menuSurvivesVaultWithTwentySevenDistinctTypes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ensureActivatedWithReach(level);
        Item[] fillerTypes = distinctFillerItems(27);
        // The distinct-type cap is shared world state and other tests fill it; buy room for all 27
        // up front rather than depending on this test running early in the batch.
        ensureRoomForDistinctTypes(level, fillerTypes);
        for (Item filler : fillerTypes) {
            Vault.deposit(level, new ItemStack(filler, 3));
        }
        boolean allDeposited = true;
        for (Item filler : fillerTypes) {
            allDeposited &= Vault.get(level).count(filler) >= 3;
        }
        boolean allDepositedFinal = allDeposited;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VaultMenu menu = new VaultMenu(0, player.getInventory());
        for (int i = 0; i < 200; i++) {
            menu.broadcastChanges();
        }
        menu.handleWithdraw(player, fillerTypes[0], 3);
        for (int i = 0; i < 50; i++) {
            menu.broadcastChanges();
        }

        helper.succeedIf(() -> {
            if (!allDepositedFinal) {
                throw new AssertionError("not all 27 filler types made it into the vault at 3 each");
            }
        });
    }

    /**
     * A pool of distinct, mutually-unused vanilla item types, disjoint from {@code VaultGameTests}'
     * pool — the two used to share nineteen items (logs, stone variants, sand), which was invisible
     * while capacity only ever jumped in three big steps but started colliding once a fix made every
     * Sigil grow capacity by exactly +27, landing test runs on a much wider range of "room" sizes:
     * whichever of these two suites' tests happened to run first silently pre-filled the other's
     * "fresh" filler types, so the second test's deposit loop added fewer distinct types than it
     * asked for. Wool and concrete colours are used nowhere else in either pool.
     */
    private static Item[] distinctFillerItems(int count) {
        // 26.2 folds the sixteen dye-colour variants of wool and concrete into one ColorCollection
        // each rather than sixteen separate Items fields (REFERENCES.md) — asList() is the sixteen.
        java.util.List<Item> pool = new java.util.ArrayList<>(java.util.List.of(
                Items.OAK_PLANKS, Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.JUNGLE_PLANKS, Items.ACACIA_PLANKS,
                Items.DARK_OAK_PLANKS, Items.MANGROVE_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS, Items.CRIMSON_PLANKS,
                Items.WARPED_PLANKS));
        pool.addAll(Items.WOOL.asList());
        pool.add(Items.CONCRETE.white());
        pool.add(Items.CONCRETE.orange());
        pool.add(Items.CONCRETE.magenta());
        if (count > pool.size()) {
            throw new AssertionError("distinctFillerItems pool too small: needed " + count + ", have " + pool.size());
        }
        Item[] result = new Item[count];
        for (int i = 0; i < count; i++) {
            result[i] = pool.get(i);
        }
        return result;
    }

    /**
     * Matches on <b>container identity</b>, not on {@code getContainerSlot()}. The menu gained the
     * §2.3 upgrade slot, which is index 0 of its own one-slot container — so an index-only match
     * silently returned the upgrade slot instead of the player's first inventory slot, and the
     * deposit assertion failed against a slot it was never meant to touch.
     */
    private static Slot findPlayerInventorySlot(VaultMenu menu, ServerPlayer player, int containerSlotIndex) {
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == containerSlotIndex) {
                return slot;
            }
        }
        throw new AssertionError("VaultMenu has no slot for player inventory index " + containerSlotIndex);
    }

    private static int countInInventory(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * <b>New precondition in v0.4, and the reason this suite briefly went red during the §2 rework.</b>
     * DESIGN.md §2.1 says "nothing linked works without an activated Anchor", and §2.4 locks the
     * storage tab until activation — so {@link VaultMenu#quickMoveStack} now refuses a deposit into a
     * dormant Vault. These tests predate activation entirely and never set it up, so they were
     * asserting against a Vault that in v0.4 terms does not exist yet.
     *
     * <p>The same applies to withdrawal, for the second half of §2.0: it is now ranged, so a test that
     * withdraws has to establish reach. This grants the Overworld tier rather than parking the Anchor
     * next to the player, because {@code makeMockServerPlayerInLevel} does not put every mock player
     * in the same place and a local-reach setup would be quietly testing the mock's spawn position.
     *
     * <p>None of the assertions below changed. What changed is the world state a transfer needs, and
     * {@code VaultActivationGameTests} deliberately toggles both flags on the shared singleton, so
     * every test here re-establishes them rather than depending on run order.
     */
    private static void ensureActivatedWithReach(ServerLevel level) {
        if (!Vault.get(level).activated()) {
            Vault.activateForFree(level);
        }
        Vault.grantReach(level, net.minecraft.world.level.Level.OVERWORLD);
    }

    /** Spends Sigils until the shared Vault can hold every one of {@code items} as a new type. */
    private static void ensureRoomForDistinctTypes(ServerLevel level, Item[] items) {
        int needed = 0;
        for (Item item : items) {
            if (Vault.get(level).count(item) == 0) {
                needed++;
            }
        }
        while (Vault.get(level).distinctTypeCount() + needed > Vault.capacityTier(level).distinctTypeCap()) {
            Vault.spendSigil(level);
        }
    }

    private static void ensureDistinctTypeHeadroom(ServerLevel level, Item item) {
        if (Vault.get(level).count(item) > 0) {
            return;
        }
        while (Vault.get(level).distinctTypeCount() >= Vault.capacityTier(level).distinctTypeCap()) {
            Vault.spendSigil(level);
        }
    }
}
