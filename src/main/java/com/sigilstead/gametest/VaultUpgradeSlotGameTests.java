package com.sigilstead.gametest;

import com.sigilstead.registry.HsItems;
import com.sigilstead.vault.Vault;
import com.sigilstead.vault.VaultKey;
import com.sigilstead.vault.VaultAccess;
import com.sigilstead.vault.VaultData;
import com.sigilstead.vault.VaultUpgradeKind;
import com.sigilstead.vault.VaultMenu;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §2.3's upgrade slot — the tab-1 rework that replaced the buy buttons with a real slot
 * you place a Sigil into, armed but not spent until a confirm click (§2.4). It moves items, so it
 * gets its own tests (CONVENTIONS.md §8): a Sigil that vanishes without buying anything, or an
 * upgrade granted without consuming one, are both exactly the class of bug §2.6 exists to catch.
 *
 * <p>Runs against the shared singleton {@link VaultData} like the rest of the Vault suite, so each
 * test re-establishes the activation state it needs rather than depending on run order.
 */
public class VaultUpgradeSlotGameTests {

    /** Each plain Vault Sigil placed in the slot buys one capacity step (§12.3), and all of them are consumed. */
    @GameTest
    public void plainSigilsBuyCapacityAndAreConsumed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.activateForFree(level);

        int sigilsBefore = Vault.get(level).sigilsSpent();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VaultMenu menu = new VaultMenu(0, player.getInventory(), VaultAccess.ANCHOR);
        menu.handleSetTab(true);

        menu.slots.getFirst().set(new ItemStack(HsItems.VAULT_SIGIL, 3));
        menu.handleConfirmUpgrade(player, VaultUpgradeKind.CAPACITY);

        int sigilsAfter = Vault.get(level).sigilsSpent();
        ItemStack leftInSlot = menu.slots.getFirst().getItem();

        helper.succeedIf(() -> {
            if (sigilsAfter != sigilsBefore + 3) {
                throw new AssertionError("3 Vault Sigils bought " + (sigilsAfter - sigilsBefore) + " capacity steps, expected 3");
            }
            if (!leftInSlot.isEmpty()) {
                throw new AssertionError("the upgrade slot still holds " + leftInSlot + " after spending every Sigil in it");
            }
        });
    }

    /**
     * §2.4 — placing a Sigil only arms the slot. Nothing is spent and nothing is bought until the
     * confirm click, the same two-step shape as an enchanting table.
     */
    @GameTest
    public void placingASigilAloneBuysNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.activateForFree(level);

        int sigilsBefore = Vault.get(level).sigilsSpent();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VaultMenu menu = new VaultMenu(0, player.getInventory(), VaultAccess.ANCHOR);
        menu.handleSetTab(true);

        menu.slots.getFirst().set(new ItemStack(HsItems.VAULT_SIGIL, 3));

        int sigilsAfter = Vault.get(level).sigilsSpent();
        ItemStack stillInSlot = menu.slots.getFirst().getItem();

        helper.succeedIf(() -> {
            if (sigilsAfter != sigilsBefore) {
                throw new AssertionError("dropping a Sigil in the slot bought " + (sigilsAfter - sigilsBefore)
                        + " capacity steps before any confirm click");
            }
            if (stillInSlot.getCount() != 3) {
                throw new AssertionError("the armed slot holds " + stillInSlot.getCount() + " of 3 Sigils, expected all 3 untouched");
            }
        });
    }

    /**
     * A dimensional Sigil buys its one reach tier and consumes exactly one — the rest stay in the
     * slot rather than being silently eaten, because a second buys nothing.
     */
    @GameTest
    public void dimensionalSigilSpendsExactlyOneAndKeepsTheRest(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.activateForFree(level);
        Vault.revokeReach(level, Level.NETHER);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VaultMenu menu = new VaultMenu(0, player.getInventory(), VaultAccess.ANCHOR);
        menu.handleSetTab(true);

        int slot = VaultUpgradeKind.NETHER_REACH.ordinal();
        menu.slots.get(slot).set(new ItemStack(HsItems.NETHER_VAULT_SIGIL, 4));
        menu.handleConfirmUpgrade(player, VaultUpgradeKind.NETHER_REACH);

        boolean gotReach = Vault.get(level).reachTiers().contains(Level.NETHER);
        ItemStack leftInSlot = menu.slots.get(slot).getItem();

        helper.succeedIf(() -> {
            if (!gotReach) {
                throw new AssertionError("a Nether Vault Sigil in the upgrade slot did not buy Nether reach");
            }
            if (leftInSlot.getCount() != 3) {
                throw new AssertionError("upgrade slot holds " + leftInSlot.getCount() + " Sigils, expected 3 — "
                        + "a second dimensional Sigil buys nothing and must not be consumed");
            }
        });
    }

    /** Closing the screen must hand back whatever the slot still holds, not void it. */
    @GameTest
    public void closingTheMenuReturnsUnspentSigils(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.activateForFree(level);
        Vault.grantReach(level, Level.END);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        VaultMenu menu = new VaultMenu(0, player.getInventory(), VaultAccess.ANCHOR);
        menu.handleSetTab(true);

        // End reach is already owned, so nothing is spent and all four stay put.
        int slot = VaultUpgradeKind.END_REACH.ordinal();
        menu.slots.get(slot).set(new ItemStack(HsItems.END_VAULT_SIGIL, 4));
        menu.handleConfirmUpgrade(player, VaultUpgradeKind.END_REACH);
        menu.removed(player);

        int returned = countInInventory(player, HsItems.END_VAULT_SIGIL);
        ItemStack leftInSlot = menu.slots.get(slot).getItem();

        helper.succeedIf(() -> {
            if (returned != 4) {
                throw new AssertionError("player got back " + returned + " of 4 unspent Sigils on close");
            }
            if (!leftInSlot.isEmpty()) {
                throw new AssertionError("the upgrade slot still holds " + leftInSlot + " after the menu closed");
            }
        });
    }

    /**
     * §2.2 — a Pouch has no Anchor tab, so a crafted packet claiming otherwise must buy nothing.
     * The menu's own {@link VaultAccess} is the authority, never the client's claim.
     */
    @GameTest
    public void aPouchCannotBuyUpgradesEvenIfItClaimsTheAnchorTab(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.activateForFree(level);

        int sigilsBefore = Vault.get(level).sigilsSpent();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VaultMenu menu = new VaultMenu(0, player.getInventory(), VaultAccess.POUCH);
        menu.handleSetTab(true);

        menu.slots.getFirst().set(new ItemStack(HsItems.VAULT_SIGIL, 2));
        menu.handleConfirmUpgrade(player, VaultUpgradeKind.CAPACITY);

        int sigilsAfter = Vault.get(level).sigilsSpent();
        ItemStack leftInSlot = menu.slots.getFirst().getItem();


        helper.succeedIf(() -> {
            if (sigilsAfter != sigilsBefore) {
                throw new AssertionError("a Pouch bought " + (sigilsAfter - sigilsBefore) + " capacity steps");
            }
            if (leftInSlot.getCount() != 2) {
                throw new AssertionError("a Pouch consumed Sigils it could not spend: " + leftInSlot.getCount() + " left of 2");
            }
        });
    }

    /**
     * §2.4 — dropping the carried stack into the grid. A conservation case like any other transfer:
     * what leaves the cursor must equal what arrives in the Vault, and a right-click must move
     * exactly one.
     */
    @GameTest
    public void depositingTheCarriedStackConservesExactly(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.activateForFree(level);
        ensureDistinctTypeHeadroom(level, net.minecraft.world.item.Items.BRICK);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        VaultMenu menu = new VaultMenu(0, player.getInventory(), VaultAccess.ANCHOR);

        int baseline = Vault.get(level).count(net.minecraft.world.item.Items.BRICK);
        menu.setCarried(new ItemStack(net.minecraft.world.item.Items.BRICK, 30));

        menu.handleDepositCarried(player, true);
        int afterSingle = Vault.get(level).count(net.minecraft.world.item.Items.BRICK);
        int carriedAfterSingle = menu.getCarried().getCount();

        menu.handleDepositCarried(player, false);
        int afterAll = Vault.get(level).count(net.minecraft.world.item.Items.BRICK);
        ItemStack carriedAfterAll = menu.getCarried();

        helper.succeedIf(() -> {
            if (afterSingle != baseline + 1 || carriedAfterSingle != 29) {
                throw new AssertionError("a right-click deposited " + (afterSingle - baseline)
                        + " and left " + carriedAfterSingle + " on the cursor, expected 1 and 29");
            }
            if (afterAll != baseline + 30 || !carriedAfterAll.isEmpty()) {
                throw new AssertionError("a left-click left the Vault at " + (afterAll - baseline)
                        + " and the cursor holding " + carriedAfterAll + ", expected 30 and nothing");
            }
        });
    }

    /**
     * §2.0 / §2.2 pinned in one test, because "the Satchel's range check" is the easiest thing in §2
     * to get backwards: a Satchel deposits from <em>anywhere</em>, including far outside every reach
     * tier, and never withdraws — not out of reach, and not in reach either. Its limit is the verb it
     * lacks, not the distance.
     */
    @GameTest
    public void satchelDepositsOutOfReachAndNeverWithdraws(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Anchor far away with no dimensional tier bought: nothing here is within reach.
        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO).offset(4096, 0, 4096));
        Vault.activateForFree(level);
        Vault.revokeReach(level, Level.OVERWORLD);
        ensureDistinctTypeHeadroom(level, net.minecraft.world.item.Items.COAL);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        VaultMenu menu = new VaultMenu(0, player.getInventory(), VaultAccess.SATCHEL);

        int baseline = Vault.get(level).count(net.minecraft.world.item.Items.COAL);
        menu.setCarried(new ItemStack(net.minecraft.world.item.Items.COAL, 8));
        menu.handleDepositCarried(player, false);
        int afterDeposit = Vault.get(level).count(net.minecraft.world.item.Items.COAL);

        menu.handleWithdraw(player, VaultKey.of(net.minecraft.world.item.Items.COAL), 8);
        int afterWithdrawAttempt = Vault.get(level).count(net.minecraft.world.item.Items.COAL);
        int inInventory = countInInventory(player, net.minecraft.world.item.Items.COAL);

        helper.succeedIf(() -> {
            if (afterDeposit != baseline + 8) {
                throw new AssertionError("a Satchel deposited " + (afterDeposit - baseline)
                        + " of 8 from out of reach — §2.0 makes deposit unranged");
            }
            if (afterWithdrawAttempt != afterDeposit || inInventory != 0) {
                throw new AssertionError("a Satchel withdrew " + inInventory + " — it has no withdrawal verb at all");
            }
        });
    }

    private static void ensureDistinctTypeHeadroom(ServerLevel level, net.minecraft.world.item.Item item) {
        if (Vault.get(level).count(item) > 0) {
            return;
        }
        while (Vault.get(level).distinctTypeCount() >= Vault.capacityTier(level).distinctTypeCap()) {
            Vault.spendSigil(level);
        }
    }

    private static int countInInventory(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
