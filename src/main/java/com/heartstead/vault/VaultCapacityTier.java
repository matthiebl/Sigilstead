package com.heartstead.vault;

import com.heartstead.config.VaultConfig;

/**
 * DESIGN.md §2.3 — the distinct-type and per-type stack-depth caps that apply for a given number of
 * Sigils spent at the Anchor. Past T3, each further Sigil only ever buys more distinct types.
 */
public record VaultCapacityTier(int distinctTypeCap, int stackDepthCap) {

    public static VaultCapacityTier forSigilsSpent(int sigilsSpent, VaultConfig config) {
        if (sigilsSpent >= config.tier3Sigils()) {
            int extraSigils = sigilsSpent - config.tier3Sigils();
            int distinctTypeCap = config.tier3DistinctTypes() + extraSigils * config.postTier3DistinctTypesPerSigil();
            return new VaultCapacityTier(distinctTypeCap, config.tier3StackDepth());
        }
        if (sigilsSpent >= config.tier2Sigils()) {
            return new VaultCapacityTier(config.tier2DistinctTypes(), config.tier2StackDepth());
        }
        return new VaultCapacityTier(config.tier1DistinctTypes(), config.tier1StackDepth());
    }
}
