package com.sigilstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.1 — the notable tier: structures worth a deliberate detour but short of the
 * highest-value tier ({@link SigilHighValueStructureConfig}). Split out of
 * {@link SigilStructureConfig} for the same 16-field codec reason as its siblings.
 */
public record SigilNotableStructureConfig(
        SigilDrop strongholdChest,
        SigilDrop igloo,
        SigilDrop pillagerOutpost,
        SigilDrop netherFortress) {

    public static final Codec<SigilNotableStructureConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    SigilDrop.CODEC.fieldOf("stronghold_chest")
                            .forGetter(SigilNotableStructureConfig::strongholdChest),
                    SigilDrop.CODEC.fieldOf("igloo").forGetter(SigilNotableStructureConfig::igloo),
                    SigilDrop.CODEC.fieldOf("pillager_outpost")
                            .forGetter(SigilNotableStructureConfig::pillagerOutpost),
                    SigilDrop.CODEC.fieldOf("nether_fortress")
                            .forGetter(SigilNotableStructureConfig::netherFortress))
            .apply(instance, SigilNotableStructureConfig::new));

    /** DESIGN.md §12.1 verbatim. */
    public static final SigilNotableStructureConfig DEFAULT = new SigilNotableStructureConfig(
            SigilDrop.one(0.35),    // stronghold library / crossing
            SigilDrop.one(0.20),    // igloo
            SigilDrop.one(0.45),    // pillager outpost — several pillagers to fight through for it
            SigilDrop.one(0.25));   // nether fortress
}
