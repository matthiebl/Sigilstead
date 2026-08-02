package com.heartstead.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

/** DESIGN.md §12.6 — Abundance's per-level bonus roll, no world required. */
class EnchantmentBlockLootTest {

    @Test
    void zeroChanceNeverAddsAnExtraItem() {
        RandomSource random = RandomSource.create(1);
        for (int level = 1; level <= 3; level++) {
            assertEquals(0, EnchantmentBlockLoot.rollAbundanceExtra(level, 0.0, random));
        }
    }

    @Test
    void certainChanceAlwaysAddsOnePerLevel() {
        RandomSource random = RandomSource.create(1);
        assertEquals(1, EnchantmentBlockLoot.rollAbundanceExtra(1, 1.0, random));
        assertEquals(2, EnchantmentBlockLoot.rollAbundanceExtra(2, 1.0, random));
        assertEquals(3, EnchantmentBlockLoot.rollAbundanceExtra(3, 1.0, random));
    }

    @Test
    void averagesMatchTheDesignedMultipliers() {
        RandomSource random = RandomSource.create(42);
        int trials = 200_000;

        for (int level = 1; level <= 3; level++) {
            long total = 0;
            for (int i = 0; i < trials; i++) {
                total += EnchantmentBlockLoot.rollAbundanceExtra(level, 0.6, random);
            }
            double average = (double) total / trials;
            // §12.6: level I averages +0.6, II +1.2, III +1.8 (×1.6/×2.2/×2.8 including the base item).
            assertEquals(level * 0.6, average, 0.02);
        }
    }
}
