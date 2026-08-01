package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Every tuning dial in the pack (CONVENTIONS.md §5, DESIGN.md §10). One codec-backed record, loaded
 * from a JSON file in the Fabric config directory by {@link HsConfigManager}.
 *
 * <p>Server-authoritative: clients never decide balance, only render what the server tells them.
 *
 * <p>{@link VaultConfig} nests as its own record rather than nine flat fields here — a Mojang
 * {@code RecordCodecBuilder} group tops out at 16 entries, and this one was already at 8.
 */
public record HsConfig(
    double heartShardChestChance,
    int heartShardChestMinCount,
    int heartShardChestMaxCount,
    double heartShardHostileMobChance,
    double heartShardEliteMobChance,
    int heartCap,
    int heartFloor,
    int heartLossOnDeath,
    VaultConfig vault) {

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
                          .forGetter(HsConfig::heartShardEliteMobChance),
                      Codec.intRange(1, 40)
                          .fieldOf("heart_cap")
                          .forGetter(HsConfig::heartCap),
                      Codec.intRange(1, 40)
                          .fieldOf("heart_floor")
                          .forGetter(HsConfig::heartFloor),
                      Codec.intRange(1, 40)
                          .fieldOf("heart_loss_on_death")
                          .forGetter(HsConfig::heartLossOnDeath),
                      VaultConfig.CODEC.fieldOf("vault").forGetter(HsConfig::vault))
                  .apply(instance, HsConfig::new));

  /**
   * DESIGN.md §1: 30% for 1-2 in structure chests, 0.5% from hostile mobs, 10% from named elites.
   *
   * <p>DESIGN.md §6: 20-heart cap, floor of 5, -1 heart per death. Operators wanting the harder
   * mode described in §6 lower {@code heart_floor} to 3 or raise {@code heart_loss_on_death} to 2.
   */
  public static final HsConfig DEFAULT = new HsConfig(0.30, 1, 2, 0.005, 0.10, 20, 5, 1, VaultConfig.DEFAULT);
}
