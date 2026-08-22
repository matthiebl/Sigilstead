package com.sigilstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.1 — the high-value tier: the structures that gate the game's toughest fights and
 * longest journeys. Split out of {@link SigilStructureConfig} for the same 16-field codec reason
 * as its siblings.
 *
 * <p>{@code bastionSecondary} prices {@code chests/bastion_other}, {@code chests/bastion_bridge}
 * and {@code chests/bastion_hoglin_stable} together — the rooms outside the treasure room, which
 * (unlike the treasure room) spawn several times per bastion as the structure generates. Paired
 * with {@code bastionTreasure}, a full clear averages comfortably more than 2 Sigils without the
 * treasure room being the only room that pays.
 */
public record SigilHighValueStructureConfig(
        SigilDrop trialVault,
        SigilDrop ominousTrialVault,
        SigilDrop bastionTreasure,
        SigilDrop bastionSecondary,
        SigilDrop ancientCity,
        SigilDrop endCityTreasure,
        SigilDrop woodlandMansion,
        SigilDrop fishingTreasure) {

    public static final Codec<SigilHighValueStructureConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    SigilDrop.CODEC.fieldOf("trial_vault")
                            .forGetter(SigilHighValueStructureConfig::trialVault),
                    SigilDrop.CODEC.fieldOf("ominous_trial_vault")
                            .forGetter(SigilHighValueStructureConfig::ominousTrialVault),
                    SigilDrop.CODEC.fieldOf("bastion_treasure")
                            .forGetter(SigilHighValueStructureConfig::bastionTreasure),
                    SigilDrop.CODEC.fieldOf("bastion_secondary")
                            .forGetter(SigilHighValueStructureConfig::bastionSecondary),
                    SigilDrop.CODEC.fieldOf("ancient_city")
                            .forGetter(SigilHighValueStructureConfig::ancientCity),
                    SigilDrop.CODEC.fieldOf("end_city_treasure")
                            .forGetter(SigilHighValueStructureConfig::endCityTreasure),
                    SigilDrop.CODEC.fieldOf("woodland_mansion")
                            .forGetter(SigilHighValueStructureConfig::woodlandMansion),
                    SigilDrop.CODEC.fieldOf("fishing_treasure")
                            .forGetter(SigilHighValueStructureConfig::fishingTreasure))
            .apply(instance, SigilHighValueStructureConfig::new));

    /** DESIGN.md §12.1 verbatim. */
    public static final SigilHighValueStructureConfig DEFAULT = new SigilHighValueStructureConfig(
            SigilDrop.one(0.28),               // trial chamber vault
            SigilDrop.one(0.68),               // trial chamber vault (ominous)
            new SigilDrop(0.68, 1, 2),         // bastion treasure room
            SigilDrop.one(0.25),                // bastion secondary rooms
            new SigilDrop(0.80, 1, 2),         // ancient city
            new SigilDrop(0.68, 1, 2),         // end city treasure
            new SigilDrop(0.75, 1, 2),         // woodland mansion
            SigilDrop.one(0.07));              // fishing treasure
}
