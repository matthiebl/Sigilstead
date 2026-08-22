package com.sigilstead.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sigilstead.config.VaultConfig;
import org.junit.jupiter.api.Test;

/** Pure tier math for DESIGN.md §2.3 — no world, no server, runs in milliseconds. */
class VaultCapacityTierTest {

    @Test
    void tier1IsTheStartingCapacity() {
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(0, VaultConfig.DEFAULT);
        assertEquals(27, tier.distinctTypeCap());
        assertEquals(10, tier.stackDepthCap());
    }

    @Test
    void tier2StartsExactlyAtItsSigilThreshold() {
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(3, VaultConfig.DEFAULT);
        assertEquals(108, tier.distinctTypeCap());
        assertEquals(64, tier.stackDepthCap());
    }

    @Test
    void eachSigilBelowTier2ThresholdStillGrowsDistinctTypes() {
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(2, VaultConfig.DEFAULT);
        assertEquals(27 + 2 * 27, tier.distinctTypeCap());
        assertEquals(10, tier.stackDepthCap());
    }

    @Test
    void eachSigilBetweenTier2AndTier3StillGrowsDistinctTypes() {
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(5, VaultConfig.DEFAULT);
        assertEquals(27 + 5 * 27, tier.distinctTypeCap());
        assertEquals(64, tier.stackDepthCap());
    }

    @Test
    void tier3StartsExactlyAtItsSigilThreshold() {
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(8, VaultConfig.DEFAULT);
        assertEquals(512, tier.distinctTypeCap());
        assertEquals(2048, tier.stackDepthCap());
    }

    @Test
    void pastTier3EachSigilOnlyAddsDistinctTypes() {
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(10, VaultConfig.DEFAULT);
        assertEquals(512 + 2 * 27, tier.distinctTypeCap());
        assertEquals(2048, tier.stackDepthCap());
    }
}
