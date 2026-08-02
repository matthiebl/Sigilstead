package com.heartstead.vault;

import com.heartstead.config.VaultConfig;

/**
 * DESIGN.md §2.3 — the distinct-type and per-type stack-depth caps that apply for a given number of
 * Sigils spent at the Anchor. Every Sigil adds {@code distinctTypesPerSigil} distinct types the
 * moment it's spent, not just at a tier boundary; the tier tables in §12.3 are the floor each range
 * guarantees once its threshold is reached, which only matters where that floor sits above the plain
 * linear total — T3's jump from 512 is real, T1→T2's isn't, since 27/Sigil already lands exactly on
 * 108 at the third Sigil. Stack depth has no such per-Sigil step: it only moves at a tier boundary.
 */
public record VaultCapacityTier(int distinctTypeCap, int stackDepthCap) {

    public static VaultCapacityTier forSigilsSpent(int sigilsSpent, VaultConfig config) {
        int perSigil = config.distinctTypesPerSigil();
        if (sigilsSpent >= config.tier3Sigils()) {
            int extraSigils = sigilsSpent - config.tier3Sigils();
            int distinctTypeCap = config.tier3DistinctTypes() + extraSigils * perSigil;
            return new VaultCapacityTier(distinctTypeCap, config.tier3StackDepth());
        }
        if (sigilsSpent >= config.tier2Sigils()) {
            int linear = config.tier1DistinctTypes() + sigilsSpent * perSigil;
            return new VaultCapacityTier(Math.max(config.tier2DistinctTypes(), linear), config.tier2StackDepth());
        }
        int distinctTypeCap = config.tier1DistinctTypes() + sigilsSpent * perSigil;
        return new VaultCapacityTier(distinctTypeCap, config.tier1StackDepth());
    }
}
