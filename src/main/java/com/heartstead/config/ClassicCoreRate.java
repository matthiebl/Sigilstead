package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.5 — one classic core's tuning surface: how often it produces, how many qualifying
 * events its Primed Core needs before it finishes, and how much experience each cycle grants.
 * Separate from the period and the imprint count on purpose — a §5 core overrides both of §4's
 * generic numbers at once, and §12.5 prices them independently per entry rather than deriving one
 * from the other.
 *
 * <p>{@code xpPerCycle} is sized off what the vanilla action the core replaces would actually award
 * a player performing it themselves — an Enderman kill is 5 XP, so the Ender core's cycle is too;
 * bartering with a piglin or building an iron golem grants none, so those cores' cycles grant none
 * either. It scales with tier the same way item yield does: tier only changes how many cycles run
 * per hour, not how much one cycle is worth, so a tier III core earns 6× the XP for the same reason
 * it earns 6× the items.
 */
public record ClassicCoreRate(int periodTicks, int threshold, double xpPerCycle) {

    public static final Codec<ClassicCoreRate> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.intRange(1, 1728000).fieldOf("period_ticks").forGetter(ClassicCoreRate::periodTicks),
                    Codec.intRange(1, 100000).fieldOf("threshold").forGetter(ClassicCoreRate::threshold),
                    Codec.doubleRange(0.0, 1000.0).fieldOf("xp_per_cycle").forGetter(ClassicCoreRate::xpPerCycle))
            .apply(instance, ClassicCoreRate::new));
}
