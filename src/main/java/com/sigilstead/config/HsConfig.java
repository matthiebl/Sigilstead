package com.sigilstead.config;

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
    boolean keepInventoryOnDeath,
    VaultConfig vault,
    boolean depositRequiresReach,
    EnchantmentConfig enchantment,
    CodexConfig codex,
    double coreRateMultiplier,
    int coreAccrualCapHours,
    AttunementConfig attunementThresholds,
    CoreConfig core,
    ClassicCoreConfig classicCores) {

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
                      // Optional, unlike its neighbours: it is the one field added after configs
                      // started existing on disk, and a required field would make every existing
                      // sigilstead.json malformed and silently reset every other dial with it.
                      Codec.BOOL
                          .optionalFieldOf("keep_inventory_on_death", true)
                          .forGetter(HsConfig::keepInventoryOnDeath),
                      VaultConfig.CODEC.fieldOf("vault").forGetter(HsConfig::vault),
                      Codec.BOOL
                          .fieldOf("deposit_requires_reach")
                          .forGetter(HsConfig::depositRequiresReach),
                      EnchantmentConfig.CODEC
                          .fieldOf("enchantment")
                          .forGetter(HsConfig::enchantment),
                      CodexConfig.CODEC.fieldOf("codex").forGetter(HsConfig::codex),
                      Codec.doubleRange(0.0, 1000.0)
                          .fieldOf("core_rate_multiplier")
                          .forGetter(HsConfig::coreRateMultiplier),
                      Codec.intRange(0, 8760)
                          .fieldOf("core_accrual_cap_hours")
                          .forGetter(HsConfig::coreAccrualCapHours),
                      AttunementConfig.CODEC
                          .fieldOf("attunement_thresholds")
                          .forGetter(HsConfig::attunementThresholds),
                      CoreConfig.CODEC.fieldOf("core").forGetter(HsConfig::core),
                      ClassicCoreConfig.CODEC.fieldOf("classic_cores").forGetter(HsConfig::classicCores))
                  .apply(instance, HsConfig::new));

  /**
   * DESIGN.md §12.1 for the Sigil table; §12.6 for lives — 20-heart cap, floor of 8, -1 heart per
   * death. Operators wanting §12.6's harder mode lower {@code heart_floor} to 3 or raise {@code
   * heart_loss_on_death} to 2.
   *
   * <p>{@code keep_inventory_on_death} ships {@code true} because §6's whole premise is that the
   * cost of dying moved to health: a world where you lose a heart <em>and</em> your inventory is
   * charging twice for one death. It is the switch for operators who want both.
   *
   * <p>{@code deposit_requires_reach} ships {@code false} per §2.0 and the OPEN-QUESTIONS decision
   * of 2026-08-01: free universal deposit is the pitch, and this is the switch that turns it off
   * rather than a rewrite.
   *
   * <p>§12.7's {@code bundle_slots} is deliberately absent: the §2.2 Bundle override it tunes is
   * not built, and a config field that visibly does nothing is worse than no field at all. It lands
   * with the override.
   *
   * <p>{@code core_rate_multiplier} ships at 1.0 per §12.7 and is expected to come down — it is the
   * one dial that moves every §4 and §5 rate at once, which is exactly why §10 ranks it first.
   */
  public static final HsConfig DEFAULT =
      new HsConfig(
          SigilConfig.DEFAULT, 20, 8, 1, true, VaultConfig.DEFAULT, false, EnchantmentConfig.DEFAULT,
          CodexConfig.DEFAULT, 1.0, 24, AttunementConfig.DEFAULT, CoreConfig.DEFAULT,
          ClassicCoreConfig.DEFAULT);
}
