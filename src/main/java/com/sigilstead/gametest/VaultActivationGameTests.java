package com.sigilstead.gametest;

import com.sigilstead.registry.HsItems;
import com.sigilstead.vault.Vault;
import com.sigilstead.vault.VaultData;
import com.sigilstead.vault.VaultReach;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §2.6's three additional cases, all new in v0.4 and all written before the rework they
 * cover (CONVENTIONS.md §8): activation surviving a world reload, a withdrawal evaluated for a
 * player in a dimension the world has not bought, and deposit while the Anchor's chunk is unloaded.
 *
 * <p>Like {@code VaultGameTests}, these run against the real singleton {@link VaultData} shared by
 * every test in a {@code runGametest} invocation, so each one restores whatever global flag it
 * touched (activation, reach) before asserting — otherwise test order would decide the outcome.
 */
public class VaultActivationGameTests {

    /**
     * §2.1 — activation and the "capacity and reach are never lost" rule have to survive the
     * round trip through the versioned codec that a world save/reload actually performs. A flag
     * that lives only in memory would pass every in-session test and drop the Vault on restart.
     */
    @GameTest
    public void activationSurvivesWorldReload(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);
        Vault.claimAnchor(level, anchor);
        Vault.activateForFree(level);
        Vault.grantReach(level, Level.NETHER);
        int sigilsSpent = vault.sigilsSpent();

        VaultData reloaded = roundTrip(level);

        if (!wasActivated) {
            Vault.deactivate(level);
        }

        helper.succeedIf(() -> {
            if (!reloaded.activated()) {
                throw new AssertionError("activation did not survive the codec round trip");
            }
            if (!reloaded.everActivated()) {
                throw new AssertionError("the ever-activated flag did not survive the codec round trip"
                        + " — the next Anchor would activate free a second time");
            }
            if (!reloaded.reachTiers().contains(Level.NETHER)) {
                throw new AssertionError("a purchased reach tier did not survive the codec round trip");
            }
            if (reloaded.sigilsSpent() != sigilsSpent) {
                throw new AssertionError("capacity Sigils spent changed across the round trip: "
                        + reloaded.sigilsSpent() + " vs " + sigilsSpent);
            }
            if (!reloaded.anchorDimension().map(Level.OVERWORLD::equals).orElse(false)) {
                throw new AssertionError("the Anchor's dimension did not survive the codec round trip");
            }
        });
    }

    /**
     * §2.1 — breaking an activated Anchor loses the activation but never the capacity or the reach
     * already bought. On a server one player must not be able to delete everyone else's progress.
     */
    @GameTest
    public void deactivationKeepsCapacityAndReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.activateForFree(level);
        Vault.grantReach(level, Level.END);
        Vault.spendSigil(level);
        int sigilsBefore = vault.sigilsSpent();

        Vault.deactivate(level);

        boolean stillActivated = vault.activated();
        boolean stillEverActivated = vault.everActivated();
        int sigilsAfter = vault.sigilsSpent();
        boolean keptReach = vault.reachTiers().contains(Level.END);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (stillActivated) {
                throw new AssertionError("the Vault stayed activated after the Anchor was broken");
            }
            if (!stillEverActivated) {
                throw new AssertionError("ever-activated was cleared by a break — the next activation would be free again");
            }
            if (sigilsAfter != sigilsBefore) {
                throw new AssertionError("capacity Sigils were refunded or lost on deactivation: "
                        + sigilsAfter + " vs " + sigilsBefore);
            }
            if (!keptReach) {
                throw new AssertionError("a purchased reach tier was lost when the Anchor was broken");
            }
        });
    }

    /**
     * §2.1 — the anti-softlock fallback. With no Vault Sigil in hand but one sitting in the Vault,
     * activation may consume the stored one; a held Sigil is always preferred so nobody silently
     * drains the shared pool.
     */
    @GameTest
    public void activationPrefersAHeldSigilOverTheStoredOne(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.markEverActivated(level);
        Vault.deactivate(level);

        // Both a held Sigil and a stored one are available.
        ensureStoredVaultSigils(level, 1);
        int storedBefore = vault.count(HsItems.VAULT_SIGIL);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().setItem(0, new ItemStack(HsItems.VAULT_SIGIL, 1));

        boolean activated = Vault.tryActivate(level, player);
        int heldAfter = countInInventory(player, HsItems.VAULT_SIGIL);
        int storedAfter = vault.count(HsItems.VAULT_SIGIL);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (!activated) {
                throw new AssertionError("activation with a held Vault Sigil was refused");
            }
            if (heldAfter != 0) {
                throw new AssertionError("the held Vault Sigil was not consumed (still holding " + heldAfter + ")");
            }
            if (storedAfter != storedBefore) {
                throw new AssertionError("the shared Vault pool was drained even though the player held a Sigil: "
                        + storedAfter + " vs " + storedBefore);
            }
        });
    }

    /** §2.1 — with nothing in hand, the Sigil sitting in the Vault pays instead. That is the softlock escape. */
    @GameTest
    public void activationFallsBackToASigilInsideTheVault(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.markEverActivated(level);
        Vault.deactivate(level);

        ensureStoredVaultSigils(level, 1);
        int storedBefore = vault.count(HsItems.VAULT_SIGIL);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();

        boolean activated = Vault.tryActivate(level, player);
        int storedAfter = vault.count(HsItems.VAULT_SIGIL);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (!activated) {
                throw new AssertionError("activation was refused despite a Vault Sigil sitting in the Vault");
            }
            if (storedAfter != storedBefore - 1) {
                throw new AssertionError("the stored Vault Sigil was not consumed exactly once: "
                        + storedAfter + " vs an expected " + (storedBefore - 1));
            }
        });
    }

    /**
     * §2.0 / §2.3 — a withdrawal attempted from a dimension the world has not bought reach into must
     * be refused, and refusing must not touch the Vault's contents.
     */
    @GameTest
    public void withdrawFromAnUnboughtDimensionIsRefusedAndLosesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        // The Anchor sits far from the test structure, so local reach cannot cover the player.
        BlockPos farAway = helper.absolutePos(BlockPos.ZERO).offset(4096, 0, 4096);
        Vault.claimAnchor(level, farAway);
        Vault.activateForFree(level);
        boolean hadNetherReach = vault.reachTiers().contains(Level.NETHER);
        Vault.revokeReach(level, Level.NETHER);
        Vault.revokeReach(level, Level.OVERWORLD);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        boolean reachOk = VaultReach.canWithdrawAt(level, player.blockPosition());
        int before = vault.count(Items.DIAMOND);
        int withdrawn = Vault.withdrawIntoIfInReach(level, Items.DIAMOND, 1, player);
        int after = vault.count(Items.DIAMOND);

        if (hadNetherReach) {
            Vault.grantReach(level, Level.NETHER);
        }
        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (reachOk) {
                throw new AssertionError("reach reported OK 4096 blocks from the Anchor with no dimensional tier bought");
            }
            if (withdrawn != 0) {
                throw new AssertionError("withdrew " + withdrawn + " from out of reach, expected 0");
            }
            if (after != before) {
                throw new AssertionError("a refused withdrawal changed the Vault from " + before + " to " + after);
            }
        });
    }

    /**
     * §2.0 — deposit is free from anywhere. Once the dimensional tier is bought, the same position
     * that was refused above must withdraw fine; that is the whole ladder in one assertion.
     */
    @GameTest
    public void buyingReachOpensWithdrawalAtTheSamePosition(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        BlockPos farAway = helper.absolutePos(BlockPos.ZERO).offset(4096, 0, 4096);
        Vault.claimAnchor(level, farAway);
        Vault.activateForFree(level);
        boolean hadOverworldReach = vault.reachTiers().contains(Level.OVERWORLD);
        Vault.revokeReach(level, Level.OVERWORLD);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        boolean refusedBefore = !VaultReach.canWithdrawAt(level, player.blockPosition());

        Vault.grantReach(level, Level.OVERWORLD);
        boolean allowedAfter = VaultReach.canWithdrawAt(level, player.blockPosition());

        if (!hadOverworldReach) {
            Vault.revokeReach(level, Level.OVERWORLD);
        }
        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (!refusedBefore) {
                throw new AssertionError("withdrawal was allowed before the Overworld tier was bought");
            }
            if (!allowedAfter) {
                throw new AssertionError("withdrawal was still refused after buying the Overworld tier");
            }
        });
    }

    /**
     * §2.6 — deposit while the Anchor's chunk is unloaded. The Vault lives in world {@code SavedData},
     * not in the Anchor's block entity, so this must work without the chunk ever being touched; if
     * it ever starts needing the block entity, this is the test that catches it.
     */
    @GameTest
    public void depositWorksWhileTheAnchorChunkIsUnloaded(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        // A position no test structure occupies and nothing is keeping loaded.
        BlockPos farAway = helper.absolutePos(BlockPos.ZERO).offset(8192, 0, 8192);
        Vault.claimAnchor(level, farAway);
        Vault.activateForFree(level);

        boolean chunkLoaded = level.hasChunk(farAway.getX() >> 4, farAway.getZ() >> 4);

        ensureDistinctTypeHeadroom(level, Items.BONE);
        int before = vault.count(Items.BONE);
        ItemStack leftover = Vault.deposit(level, new ItemStack(Items.BONE, 7));
        int after = vault.count(Items.BONE);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (chunkLoaded) {
                throw new AssertionError("the Anchor's chunk was loaded — this test asserts nothing as written");
            }
            if (!leftover.isEmpty()) {
                throw new AssertionError("deposit left a remainder of " + leftover.getCount()
                        + " with the Anchor's chunk unloaded");
            }
            if (after != before + 7) {
                throw new AssertionError("vault count after an unloaded-chunk deposit = " + after
                        + ", expected " + (before + 7));
            }
        });
    }

    /**
     * §2.5 — a container carrying contents is rejected at the deposit path outright. Storing one
     * would launder capacity: a filled shulker is 27 free types hiding inside a single type.
     */
    @GameTest
    public void filledContainersAreRefusedAndEmptyOnesAreNot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        ensureDistinctTypeHeadroom(level, Items.SHULKER_BOX);

        ItemStack filled = new ItemStack(Items.SHULKER_BOX, 1);
        filled.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(
                        java.util.List.of(new ItemStack(Items.DIAMOND, 64))));

        int filledBefore = vault.count(Items.SHULKER_BOX);
        ItemStack filledLeftover = Vault.deposit(level, filled);
        int filledAfter = vault.count(Items.SHULKER_BOX);

        ItemStack empty = new ItemStack(Items.SHULKER_BOX, 1);
        ItemStack emptyLeftover = Vault.deposit(level, empty);
        int emptyAfter = vault.count(Items.SHULKER_BOX);

        helper.succeedIf(() -> {
            if (filledLeftover.getCount() != 1 || filledAfter != filledBefore) {
                throw new AssertionError("a shulker box holding a stack of diamonds was accepted into the Vault");
            }
            if (!emptyLeftover.isEmpty() || emptyAfter != filledAfter + 1) {
                throw new AssertionError("an empty shulker box was refused; §2.5 stores those normally");
            }
        });
    }

    /** §2.1 — nothing linked works without an activated Anchor: deposit itself must refuse. */
    @GameTest
    public void depositIsRefusedWhileTheAnchorIsDormant(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(BlockPos.ZERO));
        Vault.deactivate(level);

        int before = vault.count(Items.STICK);
        ItemStack leftover = Vault.depositThroughLink(level, new ItemStack(Items.STICK, 4), helper.absolutePos(BlockPos.ZERO));
        int after = vault.count(Items.STICK);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (leftover.getCount() != 4 || after != before) {
                throw new AssertionError("a dormant Vault accepted a linked deposit — §2.1 says Funnels go inert");
            }
        });
    }

    /** Puts the shared singleton back the way this test found it, so run order can't decide outcomes. */
    private static void restoreActivation(ServerLevel level, boolean wasActivated) {
        if (wasActivated) {
            Vault.activateForFree(level);
        } else {
            Vault.deactivate(level);
        }
    }

    private static VaultData roundTrip(ServerLevel level) {
        VaultData live = Vault.get(level);
        CompoundTag tag = (CompoundTag) VaultData.TYPE.codec().encodeStart(NbtOps.INSTANCE, live).getOrThrow();
        return VaultData.TYPE.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static void ensureStoredVaultSigils(ServerLevel level, int atLeast) {
        ensureDistinctTypeHeadroom(level, HsItems.VAULT_SIGIL);
        int missing = atLeast - Vault.get(level).count(HsItems.VAULT_SIGIL);
        if (missing > 0) {
            Vault.deposit(level, new ItemStack(HsItems.VAULT_SIGIL, missing));
        }
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
