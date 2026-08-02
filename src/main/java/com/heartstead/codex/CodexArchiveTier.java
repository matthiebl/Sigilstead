package com.heartstead.codex;

import com.heartstead.config.CodexConfig;

/**
 * DESIGN.md §3.3/§12.6 — the three Codex capacity tiers: T1 (free) and T2 (an Echo Shard) come from
 * config, T3 (a Nether Star) is structurally unlimited rather than a number worth tuning.
 */
public record CodexArchiveTier(int capacity) {

    /** Stands in for "no ceiling" without a sentinel every caller has to special-case. */
    public static final int UNLIMITED = Integer.MAX_VALUE;

    public static CodexArchiveTier forTier(int tier, CodexConfig config) {
        return switch (tier) {
            case 0 -> new CodexArchiveTier(config.tier1Capacity());
            case 1 -> new CodexArchiveTier(config.tier2Capacity());
            default -> new CodexArchiveTier(UNLIMITED);
        };
    }

    public boolean hasRoomFor(int distinctCount) {
        return distinctCount < capacity;
    }
}
