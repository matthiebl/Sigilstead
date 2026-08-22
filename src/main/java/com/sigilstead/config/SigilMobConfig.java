package com.sigilstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.1 — every mob that pays a Sigil on a player kill. {@code hostileMob} is the one
 * source that pays from routine, near-base play rather than a deliberate trip to a structure, so it
 * is priced well below the minibosses despite the much larger boost off its old baseline (§10's
 * "common sources should not feel like they pay nothing at all").
 */
public record SigilMobConfig(
        SigilDrop hostileMob,
        SigilDrop ravager,
        SigilDrop evoker,
        SigilDrop elderGuardian,
        SigilDrop warden,
        SigilDrop enderDragonFirstKill,
        SigilDrop enderDragonRespawn) {

    public static final Codec<SigilMobConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    SigilDrop.CODEC.fieldOf("hostile_mob").forGetter(SigilMobConfig::hostileMob),
                    SigilDrop.CODEC.fieldOf("ravager").forGetter(SigilMobConfig::ravager),
                    SigilDrop.CODEC.fieldOf("evoker").forGetter(SigilMobConfig::evoker),
                    SigilDrop.CODEC.fieldOf("elder_guardian").forGetter(SigilMobConfig::elderGuardian),
                    SigilDrop.CODEC.fieldOf("warden").forGetter(SigilMobConfig::warden),
                    SigilDrop.CODEC.fieldOf("ender_dragon_first_kill")
                            .forGetter(SigilMobConfig::enderDragonFirstKill),
                    SigilDrop.CODEC.fieldOf("ender_dragon_respawn")
                            .forGetter(SigilMobConfig::enderDragonRespawn))
            .apply(instance, SigilMobConfig::new));

    /** DESIGN.md §12.1 verbatim. */
    public static final SigilMobConfig DEFAULT = new SigilMobConfig(
            SigilDrop.one(0.02),    // any hostile mob
            SigilDrop.one(0.35),    // ravager
            SigilDrop.one(0.80),    // evoker
            SigilDrop.one(0.80),    // elder guardian
            SigilDrop.always(2),    // warden
            SigilDrop.always(5),    // ender dragon, first kill
            SigilDrop.always(1));   // ender dragon, each respawn
}
