package com.heartstead.gametest;

import com.heartstead.block.LinkedFunnelBlock;
import com.heartstead.blockentity.CoreHousingBlockEntity;
import com.heartstead.registry.HsBlocks;
import com.heartstead.vault.Vault;
import com.heartstead.vault.VaultData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

/**
 * DESIGN.md §4.2's payoff: "Output goes to small internal storage — <b>or straight into your Vault
 * if a Linked Funnel is below.</b> That is the integration payoff, and the moment the two flagship
 * systems click together."
 *
 * <p>These are §2.6-style conservation tests as much as §4 tests: a core is a new source of items
 * feeding the Vault, and CLAUDE.md is explicit that anything that moves items gets tested. The
 * housing deliberately reuses {@link Vault}'s existing transfer path rather than adding a second one,
 * so what is under test here is the routing decision, not a new transfer implementation.
 */
public class CoreVaultGameTests {

    /** §4.2 — with an input Funnel below, yield goes to the Vault instead of filling the buffer. */
    @GameTest
    public void yieldGoesStraightToTheVaultThroughAFunnel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(new BlockPos(0, 1, 0)));
        Vault.activateForFree(level);

        BlockPos housingPos = new BlockPos(2, 2, 2);
        helper.setBlock(housingPos.below(), HsBlocks.LINKED_FUNNEL);
        CoreHousingBlockEntity housing = CoreAccrualGameTests.socketedSoulCage(helper, housingPos);

        Item rottenFlesh = net.minecraft.world.item.Items.ROTTEN_FLESH;
        ensureDistinctTypeHeadroom(level, rottenFlesh);
        int vaultBefore = totalStored(level);

        CoreAccrualGameTests.backdate(housing, level, 20_000L);
        housing.settle(level);

        int vaultAfter = totalStored(level);
        int buffered = CoreAccrualGameTests.bufferCount(housing);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (vaultAfter <= vaultBefore) {
                throw new AssertionError("a housing over a Linked Funnel deposited nothing into the Vault "
                        + "(buffer holds " + buffered + ") — §4.2's integration payoff is not wired up");
            }
        });
    }

    /**
     * §2.1 — "nothing linked works without an activated Anchor. Funnels go inert." A housing over a
     * Funnel with a dormant Vault must fall back to its own buffer rather than voiding its yield.
     * This is the case that would silently destroy items if the fallback were missing.
     */
    @GameTest
    public void aDormantVaultMakesTheHousingFallBackToItsBuffer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(new BlockPos(0, 1, 0)));
        Vault.deactivate(level);

        BlockPos housingPos = new BlockPos(2, 2, 2);
        helper.setBlock(housingPos.below(), HsBlocks.LINKED_FUNNEL);
        CoreHousingBlockEntity housing = CoreAccrualGameTests.socketedSoulCage(helper, housingPos);

        int vaultBefore = totalStored(level);
        CoreAccrualGameTests.backdate(housing, level, 20_000L);
        housing.settle(level);

        int buffered = CoreAccrualGameTests.bufferCount(housing);
        int vaultAfter = totalStored(level);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (vaultAfter != vaultBefore) {
                throw new AssertionError("a dormant Vault still accepted " + (vaultAfter - vaultBefore) + " items");
            }
            if (buffered <= 0) {
                throw new AssertionError("yield went nowhere with the Vault dormant — items were voided rather "
                        + "than falling back to the housing's own buffer");
            }
        });
    }

    /** §4.2 — with no Funnel below, everything stays in the buffer and the Vault is untouched. */
    @GameTest
    public void withoutAFunnelTheYieldStaysInTheBuffer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(new BlockPos(0, 1, 0)));
        Vault.activateForFree(level);

        CoreHousingBlockEntity housing = CoreAccrualGameTests.socketedSoulCage(helper, new BlockPos(2, 2, 2));
        int vaultBefore = totalStored(level);

        CoreAccrualGameTests.backdate(housing, level, 20_000L);
        housing.settle(level);

        int buffered = CoreAccrualGameTests.bufferCount(housing);
        int vaultAfter = totalStored(level);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (buffered <= 0) {
                throw new AssertionError("a housing with no Funnel produced nothing into its buffer");
            }
            if (vaultAfter != vaultBefore) {
                throw new AssertionError("a housing with no Funnel below still reached the Vault");
            }
        });
    }

    /**
     * §4.2 — "an <em>output</em> Funnel is not a link." A Funnel set to dispense from the Vault must
     * not be mistaken for one feeding it, or the housing and the Funnel would fight over the same
     * items.
     */
    @GameTest
    public void anOutputFunnelIsNotALink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultData vault = Vault.get(level);
        boolean wasActivated = vault.activated();

        Vault.claimAnchor(level, helper.absolutePos(new BlockPos(0, 1, 0)));
        Vault.activateForFree(level);

        BlockPos housingPos = new BlockPos(2, 2, 2);
        helper.setBlock(housingPos.below(), HsBlocks.LINKED_FUNNEL.defaultBlockState()
                .setValue(LinkedFunnelBlock.MODE, LinkedFunnelBlock.Mode.OUTPUT));
        CoreHousingBlockEntity housing = CoreAccrualGameTests.socketedSoulCage(helper, housingPos);

        int vaultBefore = totalStored(level);
        CoreAccrualGameTests.backdate(housing, level, 20_000L);
        housing.settle(level);

        int buffered = CoreAccrualGameTests.bufferCount(housing);
        int vaultAfter = totalStored(level);

        restoreActivation(level, wasActivated);

        helper.succeedIf(() -> {
            if (vaultAfter != vaultBefore) {
                throw new AssertionError("an output Funnel was read as a Vault link");
            }
            if (buffered <= 0) {
                throw new AssertionError("yield vanished over an output Funnel instead of buffering");
            }
        });
    }

    // ---------------------------------------------------------------- helpers

    private static int totalStored(ServerLevel level) {
        return Vault.get(level).contents().values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void restoreActivation(ServerLevel level, boolean wasActivated) {
        if (wasActivated) {
            Vault.activateForFree(level);
        } else {
            Vault.deactivate(level);
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
