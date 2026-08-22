package com.sigilstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.1 — the common tier of Sigil-bearing structures: the ones a player runs across
 * during ordinary early exploring rather than by deliberately seeking one out. Split out of
 * {@link SigilStructureConfig} because a Mojang {@code RecordCodecBuilder} group tops out at 16
 * entries (see {@link HsConfig}'s own note).
 *
 * <p>{@code dungeon} and {@code buriedTreasure} are the two guaranteed rows — both are single-chest
 * structures, so a 100% chance is a real floor rather than a statistical one.
 */
public record SigilCommonStructureConfig(
        SigilDrop dungeon,
        SigilDrop buriedTreasure,
        SigilDrop mineshaft,
        SigilDrop desertPyramid,
        SigilDrop jungleTemple,
        SigilDrop shipwreckTreasure,
        SigilDrop shipwreckMap,
        SigilDrop shipwreckSupply,
        SigilDrop ruinedPortal,
        SigilDrop underwaterRuinSmall,
        SigilDrop underwaterRuinBig) {

    public static final Codec<SigilCommonStructureConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    SigilDrop.CODEC.fieldOf("dungeon").forGetter(SigilCommonStructureConfig::dungeon),
                    SigilDrop.CODEC.fieldOf("buried_treasure")
                            .forGetter(SigilCommonStructureConfig::buriedTreasure),
                    SigilDrop.CODEC.fieldOf("mineshaft").forGetter(SigilCommonStructureConfig::mineshaft),
                    SigilDrop.CODEC.fieldOf("desert_pyramid")
                            .forGetter(SigilCommonStructureConfig::desertPyramid),
                    SigilDrop.CODEC.fieldOf("jungle_temple")
                            .forGetter(SigilCommonStructureConfig::jungleTemple),
                    SigilDrop.CODEC.fieldOf("shipwreck_treasure")
                            .forGetter(SigilCommonStructureConfig::shipwreckTreasure),
                    SigilDrop.CODEC.fieldOf("shipwreck_map")
                            .forGetter(SigilCommonStructureConfig::shipwreckMap),
                    SigilDrop.CODEC.fieldOf("shipwreck_supply")
                            .forGetter(SigilCommonStructureConfig::shipwreckSupply),
                    SigilDrop.CODEC.fieldOf("ruined_portal")
                            .forGetter(SigilCommonStructureConfig::ruinedPortal),
                    SigilDrop.CODEC.fieldOf("underwater_ruin_small")
                            .forGetter(SigilCommonStructureConfig::underwaterRuinSmall),
                    SigilDrop.CODEC.fieldOf("underwater_ruin_big")
                            .forGetter(SigilCommonStructureConfig::underwaterRuinBig))
            .apply(instance, SigilCommonStructureConfig::new));

    /** DESIGN.md §12.1 verbatim. */
    public static final SigilCommonStructureConfig DEFAULT = new SigilCommonStructureConfig(
            SigilDrop.always(1),    // dungeon — guaranteed
            SigilDrop.always(1),    // buried treasure — guaranteed
            SigilDrop.one(0.25),    // mineshaft
            SigilDrop.one(0.25),    // desert pyramid
            SigilDrop.one(0.25),    // jungle temple
            SigilDrop.one(0.25),    // shipwreck treasure
            SigilDrop.one(0.15),    // shipwreck map
            SigilDrop.one(0.15),    // shipwreck supply
            SigilDrop.one(0.15),    // ruined portal
            SigilDrop.one(0.15),    // underwater ruin (small)
            SigilDrop.one(0.30));   // underwater ruin (big)
}
