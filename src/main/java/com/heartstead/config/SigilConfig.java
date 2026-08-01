package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.1 — the Sigil drop table, and the highest-impact dial in the pack (§10.1).
 *
 * <p>One found item funds three systems (§1), so every number here moves storage, farms and
 * survivability at once. Nested as its own record rather than flattened into {@link HsConfig}
 * because a {@code RecordCodecBuilder} group tops out at 16 entries.
 *
 * <p>The Wither is absent by design, not by omission — §12.1 excludes it outright so that
 * skulls → Wither → Sigils cannot become the bootstrap loop the §5 Wither Skull Core would
 * otherwise open. There is deliberately no config field to turn it back on.
 */
public record SigilConfig(
        SigilDrop structureChest,
        SigilDrop strongholdChest,
        SigilDrop trialVault,
        SigilDrop ominousTrialVault,
        SigilDrop bastionTreasure,
        SigilDrop ancientCity,
        SigilDrop endCityTreasure,
        SigilDrop fishingTreasure,
        SigilDrop hostileMob,
        SigilDrop ravager,
        SigilDrop evoker,
        SigilDrop elderGuardian,
        SigilDrop warden,
        SigilDrop enderDragonFirstKill,
        SigilDrop enderDragonRespawn) {

    public static final Codec<SigilConfig> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance
                                    .group(
                                            SigilDrop.CODEC.fieldOf("structure_chest")
                                                    .forGetter(SigilConfig::structureChest),
                                            SigilDrop.CODEC.fieldOf("stronghold_chest")
                                                    .forGetter(SigilConfig::strongholdChest),
                                            SigilDrop.CODEC.fieldOf("trial_vault")
                                                    .forGetter(SigilConfig::trialVault),
                                            SigilDrop.CODEC.fieldOf("ominous_trial_vault")
                                                    .forGetter(SigilConfig::ominousTrialVault),
                                            SigilDrop.CODEC.fieldOf("bastion_treasure")
                                                    .forGetter(SigilConfig::bastionTreasure),
                                            SigilDrop.CODEC.fieldOf("ancient_city")
                                                    .forGetter(SigilConfig::ancientCity),
                                            SigilDrop.CODEC.fieldOf("end_city_treasure")
                                                    .forGetter(SigilConfig::endCityTreasure),
                                            SigilDrop.CODEC.fieldOf("fishing_treasure")
                                                    .forGetter(SigilConfig::fishingTreasure),
                                            SigilDrop.CODEC.fieldOf("hostile_mob")
                                                    .forGetter(SigilConfig::hostileMob),
                                            SigilDrop.CODEC.fieldOf("ravager")
                                                    .forGetter(SigilConfig::ravager),
                                            SigilDrop.CODEC.fieldOf("evoker")
                                                    .forGetter(SigilConfig::evoker),
                                            SigilDrop.CODEC.fieldOf("elder_guardian")
                                                    .forGetter(SigilConfig::elderGuardian),
                                            SigilDrop.CODEC.fieldOf("warden")
                                                    .forGetter(SigilConfig::warden),
                                            SigilDrop.CODEC.fieldOf("ender_dragon_first_kill")
                                                    .forGetter(SigilConfig::enderDragonFirstKill),
                                            SigilDrop.CODEC.fieldOf("ender_dragon_respawn")
                                                    .forGetter(SigilConfig::enderDragonRespawn))
                                    .apply(instance, SigilConfig::new));

    /** The §12.1 table verbatim. Target: ~1 Sigil per 20–25 min of active T1 exploring. */
    public static final SigilConfig DEFAULT = new SigilConfig(
            SigilDrop.one(0.12),                  // dungeon / mineshaft / temples / shipwreck treasure
            SigilDrop.one(0.25),                  // stronghold library and altar
            SigilDrop.one(0.20),                  // trial chamber vault
            SigilDrop.one(0.50),                  // ominous trial chamber vault
            new SigilDrop(0.50, 1, 2),            // bastion treasure
            new SigilDrop(0.60, 1, 2),            // ancient city
            new SigilDrop(0.50, 1, 2),            // end city treasure
            SigilDrop.one(0.05),                  // fishing treasure
            SigilDrop.one(0.0015),                // any hostile mob
            SigilDrop.one(0.25),                  // ravager
            SigilDrop.one(0.60),                  // evoker
            SigilDrop.one(0.60),                  // elder guardian
            SigilDrop.always(2),                  // warden
            SigilDrop.always(5),                  // ender dragon, first kill
            SigilDrop.always(1));                 // ender dragon, each respawn
}
