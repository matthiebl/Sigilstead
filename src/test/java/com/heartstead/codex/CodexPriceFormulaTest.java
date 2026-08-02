package com.heartstead.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.heartstead.config.CodexConfig;
import org.junit.jupiter.api.Test;

/**
 * Pure math for the DESIGN.md §3.3/§12.6 price fallback — no world, no server, runs in milliseconds.
 * {@link Codex#priceFor} itself needs a live {@link net.minecraft.core.Holder} and so is exercised by
 * {@code CodexGameTests} instead; this only pins down the clamp formula in isolation.
 */
class CodexPriceFormulaTest {

    private static int clampedFormula(int level, int rarityMultiplier) {
        CodexConfig config = CodexConfig.DEFAULT;
        int raw = config.formulaEmeraldsPerRarityLevel() * level * rarityMultiplier;
        return Math.max(config.formulaMinEmeralds(), Math.min(config.formulaMaxEmeralds(), raw));
    }

    @Test
    void commonLevelOneEnchantmentHitsTheFloor() {
        assertEquals(4, clampedFormula(1, 1));
    }

    @Test
    void midRangeEnchantmentScalesWithLevelAndRarity() {
        assertEquals(3 * 3 * 2, clampedFormula(3, 2));
    }

    @Test
    void veryRareMaxLevelEnchantmentHitsTheCeiling() {
        assertEquals(64, clampedFormula(5, 8));
    }
}
