package com.sigilstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.3 — the Vault's capacity, reach and activation table, expressed as config so it can
 * be tuned without a recompile (CONVENTIONS.md §5). Stack depth is per distinct item type, in
 * stacks; the caller converts to a raw item-count cap using that item's own max stack size.
 */
public record VaultConfig(
    int tier1DistinctTypes,
    int tier1StackDepth,
    int tier2Sigils,
    int tier2DistinctTypes,
    int tier2StackDepth,
    int tier3Sigils,
    int tier3DistinctTypes,
    int tier3StackDepth,
    int distinctTypesPerSigil,
    int reactivationSigils,
    int localReachChunks,
    int reachTierSigils,
    int funnelItemsPerTransfer) {

  public static final Codec<VaultConfig> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.intRange(0, 10000)
                          .fieldOf("tier1_distinct_types")
                          .forGetter(VaultConfig::tier1DistinctTypes),
                      Codec.intRange(0, 100000)
                          .fieldOf("tier1_stack_depth")
                          .forGetter(VaultConfig::tier1StackDepth),
                      Codec.intRange(0, 1000)
                          .fieldOf("tier2_sigils")
                          .forGetter(VaultConfig::tier2Sigils),
                      Codec.intRange(0, 10000)
                          .fieldOf("tier2_distinct_types")
                          .forGetter(VaultConfig::tier2DistinctTypes),
                      Codec.intRange(0, 100000)
                          .fieldOf("tier2_stack_depth")
                          .forGetter(VaultConfig::tier2StackDepth),
                      Codec.intRange(0, 1000)
                          .fieldOf("tier3_sigils")
                          .forGetter(VaultConfig::tier3Sigils),
                      Codec.intRange(0, 10000)
                          .fieldOf("tier3_distinct_types")
                          .forGetter(VaultConfig::tier3DistinctTypes),
                      Codec.intRange(0, 100000)
                          .fieldOf("tier3_stack_depth")
                          .forGetter(VaultConfig::tier3StackDepth),
                      Codec.intRange(0, 10000)
                          .fieldOf("distinct_types_per_sigil")
                          .forGetter(VaultConfig::distinctTypesPerSigil),
                      Codec.intRange(0, 100)
                          .fieldOf("reactivation_sigils")
                          .forGetter(VaultConfig::reactivationSigils),
                      Codec.intRange(1, 1024)
                          .fieldOf("local_reach_chunks")
                          .forGetter(VaultConfig::localReachChunks),
                      Codec.intRange(0, 100)
                          .fieldOf("reach_tier_sigils")
                          .forGetter(VaultConfig::reachTierSigils),
                      Codec.intRange(1, 64)
                          .fieldOf("funnel_items_per_transfer")
                          .forGetter(VaultConfig::funnelItemsPerTransfer))
                  .apply(instance, VaultConfig::new));

  /**
   * DESIGN.md §12.3's tables verbatim: T1 0 Sigils/27 types/10 stacks, T2 3/108/64, T3 8/512/2048,
   * +27 types per Sigil throughout (stack depth only moves at a tier boundary); 1 Vault Sigil to
   * re-anchor, a 5×5-chunk local reach square, and 1 dimensional Sigil per reach tier.
   *
   * <p>{@code funnel_items_per_transfer} has no §12 row yet — the Linked Funnel's throughput was
   * never specced beyond "hopper-speed" (§2.1), and a hopper's one-item-per-cycle would be uselessly
   * slow against a Vault. 16 per cycle is a starting guess and is flagged for playtesting.
   */
  public static final VaultConfig DEFAULT =
      new VaultConfig(27, 10, 3, 108, 64, 8, 512, 2048, 27, 1, 5, 1, 16);
}
