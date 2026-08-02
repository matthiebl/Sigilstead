package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Every tuning dial in the pack (CONVENTIONS.md §5, DESIGN.md §10). One codec-backed record, loaded
 * from a JSON file in the Fabric config directory by {@link HsConfigManager}.
 *
 * <p>Server-authoritative: clients never decide balance, only render what the server tells them.
 *
 * <p>{@link SigilConfig} and {@link VaultConfig} nest as their own records rather than flattening
 * to two dozen fields here — a Mojang {@code RecordCodecBuilder} group tops out at 16 entries.
 */
public record HsConfig(
    SigilConfig sigil,
    int heartCap,
    int heartFloor,
    int heartLossOnDeath,
    VaultConfig vault,
    boolean depositRequiresReach,
    EnchantmentConfig enchantment) {

  public static final Codec<HsConfig> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      SigilConfig.CODEC.fieldOf("sigil").forGetter(HsConfig::sigil),
                      Codec.intRange(1, 40).fieldOf("heart_cap").forGetter(HsConfig::heartCap),
                      Codec.intRange(1, 40).fieldOf("heart_floor").forGetter(HsConfig::heartFloor),
                      Codec.intRange(1, 40)
                          .fieldOf("heart_loss_on_death")
                          .forGetter(HsConfig::heartLossOnDeath),
                      VaultConfig.CODEC.fieldOf("vault").forGetter(HsConfig::vault),
                      Codec.BOOL
                          .fieldOf("deposit_requires_reach")
                          .forGetter(HsConfig::depositRequiresReach),
                      EnchantmentConfig.CODEC
                          .fieldOf("enchantment")
                          .forGetter(HsConfig::enchantment))
                  .apply(instance, HsConfig::new));

  /**
   * DESIGN.md §12.1 for the Sigil table; §12.6 for lives — 20-heart cap, floor of 5, -1 heart per
   * death. Operators wanting §12.6's harder mode lower {@code heart_floor} to 3 or raise {@code
   * heart_loss_on_death} to 2.
   *
   * <p>{@code deposit_requires_reach} ships {@code false} per §2.0 and the OPEN-QUESTIONS decision
   * of 2026-08-01: free universal deposit is the pitch, and this is the switch that turns it off
   * rather than a rewrite.
   *
   * <p>§12.7's {@code bundle_slots} is deliberately absent: the §2.2 Bundle override it tunes is
   * not built, and a config field that visibly does nothing is worse than no field at all. It lands
   * with the override.
   */
  public static final HsConfig DEFAULT =
      new HsConfig(
          SigilConfig.DEFAULT, 20, 5, 1, VaultConfig.DEFAULT, false, EnchantmentConfig.DEFAULT);
}
