package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §2.3 — the Vault's capacity table, expressed as config so it can be tuned without a
 * recompile (CONVENTIONS.md §5). Stack depth is per distinct item type, in stacks; the caller
 * converts to a raw item-count cap using that item's own max stack size.
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
    int postTier3DistinctTypesPerSigil) {

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
                          .fieldOf("post_tier3_distinct_types_per_sigil")
                          .forGetter(VaultConfig::postTier3DistinctTypesPerSigil))
                  .apply(instance, VaultConfig::new));

  /** DESIGN.md §2.3's table verbatim: T1 0 Sigils/27 types/10 stacks, T2 3/108/64, T3 8/512/2048, +27 types per Sigil after. */
  public static final VaultConfig DEFAULT = new VaultConfig(27, 10, 3, 108, 64, 8, 512, 2048, 27);
}
