package com.sigilstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.1 — every structure that pays a Sigil, grouped into the three tiers
 * {@link SigilCommonStructureConfig}, {@link SigilNotableStructureConfig} and
 * {@link SigilHighValueStructureConfig}, plus {@code village} on its own.
 *
 * <p>{@code village} prices all thirteen profession/house loot tables uniformly rather than one
 * field per table — a village generates several of them at once, so a rate deliberately low enough
 * that a single visit averages under one Sigil, not a jackpot from walking through the middle of
 * town.
 */
public record SigilStructureConfig(
        SigilCommonStructureConfig common,
        SigilNotableStructureConfig notable,
        SigilHighValueStructureConfig highValue,
        SigilDrop village) {

    public static final Codec<SigilStructureConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    SigilCommonStructureConfig.CODEC.fieldOf("common").forGetter(SigilStructureConfig::common),
                    SigilNotableStructureConfig.CODEC.fieldOf("notable").forGetter(SigilStructureConfig::notable),
                    SigilHighValueStructureConfig.CODEC.fieldOf("high_value")
                            .forGetter(SigilStructureConfig::highValue),
                    SigilDrop.CODEC.fieldOf("village").forGetter(SigilStructureConfig::village))
            .apply(instance, SigilStructureConfig::new));

    public static final SigilStructureConfig DEFAULT = new SigilStructureConfig(
            SigilCommonStructureConfig.DEFAULT,
            SigilNotableStructureConfig.DEFAULT,
            SigilHighValueStructureConfig.DEFAULT,
            SigilDrop.one(0.05));
}
