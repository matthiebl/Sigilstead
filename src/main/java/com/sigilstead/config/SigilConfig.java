package com.sigilstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.1 — the Sigil drop table, and the highest-impact dial in the pack (§10.1).
 *
 * <p>One found item funds three systems (§1), so every number here moves storage, farms and
 * survivability at once. Nested as its own record rather than flattened into {@link HsConfig}
 * because a {@code RecordCodecBuilder} group tops out at 16 entries — and nested further still into
 * {@link SigilStructureConfig} and {@link SigilMobConfig} for the same reason, now that §12.1 covers
 * every vanilla structure that can plausibly hold Sigil-worthy loot rather than a handful of them.
 *
 * <p>The Wither is absent by design, not by omission — §12.1 excludes it outright so that
 * skulls → Wither → Sigils cannot become the bootstrap loop the §5 Wither Skull Core would
 * otherwise open. There is deliberately no config field to turn it back on.
 */
public record SigilConfig(SigilStructureConfig structures, SigilMobConfig mobs) {

    public static final Codec<SigilConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    SigilStructureConfig.CODEC.fieldOf("structures").forGetter(SigilConfig::structures),
                    SigilMobConfig.CODEC.fieldOf("mobs").forGetter(SigilConfig::mobs))
            .apply(instance, SigilConfig::new));

    public static final SigilConfig DEFAULT = new SigilConfig(SigilStructureConfig.DEFAULT, SigilMobConfig.DEFAULT);
}
