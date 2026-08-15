package com.heartstead.config;

import com.heartstead.core.ClassicCore;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.5 — the eleven classic farm replacements' rates and imprint thresholds, the numbers
 * §10 ranks third-highest impact ("aim at small hand-built farm, not mega-farm"). One
 * {@link ClassicCoreRate} per core rather than flattening to 22 fields — a Mojang
 * {@code RecordCodecBuilder} group tops out at 16 entries (see {@link HsConfig}'s own note).
 *
 * <p>Periods are ticks per production cycle at tier I, before {@code core_rate_multiplier} and the
 * §4.3 tier multiplier — same convention as {@link CoreConfig#basePeriodTicks}. Thresholds are the
 * qualifying-event count a Primed Core needs; the six milestone-shaped cores (§5) all price at 1,
 * because for them "doing the thing once is the attunement".
 */
public record ClassicCoreConfig(
    ClassicCoreRate golem,
    ClassicCoreRate ominous,
    ClassicCoreRate barter,
    ClassicCoreRate guardian,
    ClassicCoreRate tidal,
    ClassicCoreRate witherSkull,
    ClassicCoreRate ender,
    ClassicCoreRate shulker,
    ClassicCoreRate slime,
    ClassicCoreRate apiary,
    ClassicCoreRate geode) {

    public static final Codec<ClassicCoreConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ClassicCoreRate.CODEC.fieldOf("golem").forGetter(ClassicCoreConfig::golem),
                    ClassicCoreRate.CODEC.fieldOf("ominous").forGetter(ClassicCoreConfig::ominous),
                    ClassicCoreRate.CODEC.fieldOf("barter").forGetter(ClassicCoreConfig::barter),
                    ClassicCoreRate.CODEC.fieldOf("guardian").forGetter(ClassicCoreConfig::guardian),
                    ClassicCoreRate.CODEC.fieldOf("tidal").forGetter(ClassicCoreConfig::tidal),
                    ClassicCoreRate.CODEC.fieldOf("wither_skull").forGetter(ClassicCoreConfig::witherSkull),
                    ClassicCoreRate.CODEC.fieldOf("ender").forGetter(ClassicCoreConfig::ender),
                    ClassicCoreRate.CODEC.fieldOf("shulker").forGetter(ClassicCoreConfig::shulker),
                    ClassicCoreRate.CODEC.fieldOf("slime").forGetter(ClassicCoreConfig::slime),
                    ClassicCoreRate.CODEC.fieldOf("apiary").forGetter(ClassicCoreConfig::apiary),
                    ClassicCoreRate.CODEC.fieldOf("geode").forGetter(ClassicCoreConfig::geode))
            .apply(instance, ClassicCoreConfig::new));

    /**
     * DESIGN.md §12.5 verbatim. Periods: Golem 40s, Ominous 90s, Barter 30s, Guardian 25s, Tidal 30s,
     * Wither Skull 45s, Ender 20s, Shulker 360s (10/hr), Slime 30s, Apiary 60s, Geode 60s.
     * Thresholds: Golem 4, Tidal 16, Slime 16, Apiary 8, Geode 32, the other six (all milestone) 1.
     *
     * <p>{@code xp_per_cycle} defaults, per {@link ClassicCoreRate}'s "same as the vanilla action"
     * rule and flagged for playtesting the same way {@code housing_slots} is: Golem and Barter 0
     * (building and bartering grant no XP), Ominous 8 (a raid wave's worth of kills), Guardian 10,
     * Tidal 5, Wither Skull 5, Ender 5, Shulker 5 (each a single mob's vanilla XP reward), Slime 3,
     * Apiary 3 (a breed's XP), Geode 0 (mining amethyst clusters grants none in vanilla either).
     */
    public static final ClassicCoreConfig DEFAULT = new ClassicCoreConfig(
            new ClassicCoreRate(800, 4, 0.0),
            new ClassicCoreRate(1800, 1, 8.0),
            new ClassicCoreRate(600, 1, 0.0),
            new ClassicCoreRate(500, 1, 10.0),
            new ClassicCoreRate(600, 16, 5.0),
            new ClassicCoreRate(900, 1, 5.0),
            new ClassicCoreRate(400, 1, 5.0),
            new ClassicCoreRate(7200, 1, 5.0),
            new ClassicCoreRate(600, 16, 3.0),
            new ClassicCoreRate(1200, 8, 3.0),
            new ClassicCoreRate(1200, 32, 0.0));

    public ClassicCoreRate forCore(ClassicCore core) {
        return switch (core) {
            case GOLEM -> golem;
            case OMINOUS -> ominous;
            case BARTER -> barter;
            case GUARDIAN -> guardian;
            case TIDAL -> tidal;
            case WITHER_SKULL -> witherSkull;
            case ENDER -> ender;
            case SHULKER -> shulker;
            case SLIME -> slime;
            case APIARY -> apiary;
            case GEODE -> geode;
        };
    }
}
