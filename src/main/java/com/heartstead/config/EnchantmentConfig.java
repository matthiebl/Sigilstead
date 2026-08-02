package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §3.1/§3.2/§12.6 — Abundance and Kiln Touch. Both are treasure enchantments found in
 * structure loot rather than bought at a table (§3.3), so the only numbers worth exposing are the
 * per-level bonus chance and how often the books themselves turn up.
 */
public record EnchantmentConfig(
    double abundanceChancePerLevel, double abundanceBookChance, double kilnTouchBookChance) {

  public static final Codec<EnchantmentConfig> CODEC =
      RecordCodecBuilder.create(
          instance ->
              instance
                  .group(
                      Codec.doubleRange(0.0, 1.0)
                          .fieldOf("abundance_chance_per_level")
                          .forGetter(EnchantmentConfig::abundanceChancePerLevel),
                      Codec.doubleRange(0.0, 1.0)
                          .fieldOf("abundance_book_chance")
                          .forGetter(EnchantmentConfig::abundanceBookChance),
                      Codec.doubleRange(0.0, 1.0)
                          .fieldOf("kiln_touch_book_chance")
                          .forGetter(EnchantmentConfig::kilnTouchBookChance))
                  .apply(instance, EnchantmentConfig::new));

  /**
   * §12.6: 60% per level gives the ×1.6/×2.2/×2.8 averages at levels I–III. Mineshaft and trial
   * chamber loot rolls a 6% chance at an Abundance book (§3.1); nether fortress and bastion loot
   * rolls 6% at a Kiln Touch book (§3.2). Both book chances are starting guesses, flagged for
   * playtesting like §12.3's funnel throughput.
   */
  public static final EnchantmentConfig DEFAULT = new EnchantmentConfig(0.6, 0.06, 0.06);
}
