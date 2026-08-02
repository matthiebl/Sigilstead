package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * DESIGN.md §3.3/§12.6 — one row of the fixed librarian price table: enchantment id, the level it is
 * taught at, and the flat emerald price. No rerolling — the price is this number every time, though
 * vanilla discounts and Hero of the Village still apply on top of it (§3.3).
 */
public record CodexPrice(Identifier enchantment, int level, int emeralds) {

    public static final Codec<CodexPrice> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Identifier.CODEC.fieldOf("enchantment").forGetter(CodexPrice::enchantment),
                    Codec.intRange(1, 255).fieldOf("level").forGetter(CodexPrice::level),
                    Codec.intRange(1, 64).fieldOf("emeralds").forGetter(CodexPrice::emeralds))
            .apply(instance, CodexPrice::new));
}
