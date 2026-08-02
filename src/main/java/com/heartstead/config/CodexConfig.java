package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.resources.Identifier;

/**
 * DESIGN.md §3.3/§12.6 — the Codex archive's per-player capacity tiers and the librarian price table.
 * Tier 3 has no capacity field: it is structurally unlimited (§12.6's "unlimited" row is not a number
 * to tune, so {@link com.heartstead.codex.CodexArchiveTier} hardcodes it rather than reading a config
 * value that could only ever mean "as large as possible").
 *
 * <p>{@code prices} holds §12.6's four hand-tuned entries verbatim, checked first. Anything archived
 * that isn't one of those four — any other vanilla enchantment, or a mod's — still needs a price, so
 * {@link com.heartstead.codex.Codex#priceFor} falls back to {@code formulaEmeraldsPerRarityLevel} ×
 * level × the enchantment's own vanilla anvil-rarity multiplier (1/2/4/8), clamped to
 * {@code formulaMinEmeralds}/{@code formulaMaxEmeralds}. This is a deliberate widening of §3.3's
 * originally closed four-entry table: Teach works for anything you can archive, not just the four
 * named examples, while those four keep their exact hand-picked prices.
 */
public record CodexConfig(
        int tier1Capacity,
        int tier2Capacity,
        int teachEmeraldCost,
        int formulaEmeraldsPerRarityLevel,
        int formulaMinEmeralds,
        int formulaMaxEmeralds,
        List<CodexPrice> prices) {

    public static final Codec<CodexConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.intRange(0, 10000).fieldOf("tier1_capacity").forGetter(CodexConfig::tier1Capacity),
                    Codec.intRange(0, 10000).fieldOf("tier2_capacity").forGetter(CodexConfig::tier2Capacity),
                    Codec.intRange(0, 64).fieldOf("teach_emerald_cost").forGetter(CodexConfig::teachEmeraldCost),
                    Codec.intRange(1, 64)
                            .fieldOf("formula_emeralds_per_rarity_level")
                            .forGetter(CodexConfig::formulaEmeraldsPerRarityLevel),
                    Codec.intRange(1, 64).fieldOf("formula_min_emeralds").forGetter(CodexConfig::formulaMinEmeralds),
                    Codec.intRange(1, 64).fieldOf("formula_max_emeralds").forGetter(CodexConfig::formulaMaxEmeralds),
                    CodexPrice.CODEC.listOf().fieldOf("prices").forGetter(CodexConfig::prices))
            .apply(instance, CodexConfig::new));

    /**
     * §12.6 verbatim: T1 8, T2 16 (bought with an Echo Shard), T3 unlimited (bought with a Nether
     * Star). Teaching costs one emerald, always, on top of the Sealed Tome itself (§3.3). The four
     * fixed prices are Mending 32, Efficiency V 28, Silk Touch 24, Feather Falling IV 10; everything
     * else prices at {@code 3 × level × rarityMultiplier}, clamped to 4–64 emeralds — a common level-1
     * enchantment (rarity ×1) prices at the 4-emerald floor, a very-rare one (×8) at max level often
     * hits the 64-emerald ceiling.
     */
    public static final CodexConfig DEFAULT = new CodexConfig(8, 16, 1, 3, 4, 64, List.of(
            new CodexPrice(Identifier.withDefaultNamespace("mending"), 1, 32),
            new CodexPrice(Identifier.withDefaultNamespace("efficiency"), 5, 28),
            new CodexPrice(Identifier.withDefaultNamespace("silk_touch"), 1, 24),
            new CodexPrice(Identifier.withDefaultNamespace("feather_falling"), 4, 10)));
}
