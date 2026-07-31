package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Every tuning dial in the pack (CONVENTIONS.md §5, DESIGN.md §11). One codec-backed record, loaded
 * from a JSON file in the Fabric config directory by {@link HsConfigManager}.
 *
 * <p>Server-authoritative: clients never decide balance, only render what the server tells them.
 */
public record HsConfig(
    double heartShardChestChance,
    int heartShardChestMinCount,
    int heartShardChestMaxCount,
    double heartShardHostileMobChance,
    double heartShardEliteMobChance) {

  public static final Codec<HsConfig> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.doubleRange(0.0, 1.0)
                          .fieldOf("heart_shard_chest_chance")
                          .forGetter(HsConfig::heartShardChestChance),
                      Codec.intRange(0, 64)
                          .fieldOf("heart_shard_chest_min_count")
                          .forGetter(HsConfig::heartShardChestMinCount),
                      Codec.intRange(0, 64)
                          .fieldOf("heart_shard_chest_max_count")
                          .forGetter(HsConfig::heartShardChestMaxCount),
                      Codec.doubleRange(0.0, 1.0)
                          .fieldOf("heart_shard_hostile_mob_chance")
                          .forGetter(HsConfig::heartShardHostileMobChance),
                      Codec.doubleRange(0.0, 1.0)
                          .fieldOf("heart_shard_elite_mob_chance")
                          .forGetter(HsConfig::heartShardEliteMobChance))
                  .apply(instance, HsConfig::new));

  /**
   * DESIGN.md §1: 30% for 1-2 in structure chests, 0.5% from hostile mobs, 10% from named elites.
   */
  public static final HsConfig DEFAULT = new HsConfig(0.30, 1, 2, 0.005, 0.10);
}
