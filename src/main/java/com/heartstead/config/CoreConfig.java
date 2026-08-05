package com.heartstead.config;

import com.heartstead.core.CoreFamily;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.4 — the housing base rates and the §4.3 tier multipliers, plus the one structural
 * number §12 never gave: how many buffer slots a housing's "small internal storage" (§4.2) has.
 *
 * <p>Periods are in ticks per production cycle at tier I, before {@code core_rate_multiplier} and the
 * tier multiplier are applied. §12.4's table in ticks: Soul Cage 1 roll / 20s = 400, Verdant Planter
 * 1 harvest / 30s = 600, Paddock 1 yield / 45s = 900, Quarry Node 8 blocks / 20s = 400 with
 * {@code quarryBlocksPerCycle} = 8.
 *
 * <p>{@code housingSlots} has no §12 row — §4.2 says only "small internal storage". 9 is a starting
 * guess chosen so a housing left alone overnight fills and stalls (which is what §4.2 wants the cap to
 * mean) rather than voiding, and it is flagged for playtesting the same way
 * {@code vault.funnel_items_per_transfer} is. {@code settleIntervalTicks} is not a rate: it is how
 * often a loaded housing re-runs the same settle-from-timestamp calculation, and changing it cannot
 * change total yield (see {@link com.heartstead.core.CoreAccrual}).
 */
public record CoreConfig(
    int soulCagePeriodTicks,
    int verdantPlanterPeriodTicks,
    int paddockPeriodTicks,
    int quarryNodePeriodTicks,
    int quarryBlocksPerCycle,
    double tier2Multiplier,
    double tier3Multiplier,
    int housingSlots,
    int settleIntervalTicks) {

    public static final Codec<CoreConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.intRange(1, 1728000).fieldOf("soul_cage_period_ticks")
                            .forGetter(CoreConfig::soulCagePeriodTicks),
                    Codec.intRange(1, 1728000).fieldOf("verdant_planter_period_ticks")
                            .forGetter(CoreConfig::verdantPlanterPeriodTicks),
                    Codec.intRange(1, 1728000).fieldOf("paddock_period_ticks")
                            .forGetter(CoreConfig::paddockPeriodTicks),
                    Codec.intRange(1, 1728000).fieldOf("quarry_node_period_ticks")
                            .forGetter(CoreConfig::quarryNodePeriodTicks),
                    Codec.intRange(1, 64).fieldOf("quarry_blocks_per_cycle")
                            .forGetter(CoreConfig::quarryBlocksPerCycle),
                    Codec.doubleRange(0.0, 1000.0).fieldOf("tier2_multiplier")
                            .forGetter(CoreConfig::tier2Multiplier),
                    Codec.doubleRange(0.0, 1000.0).fieldOf("tier3_multiplier")
                            .forGetter(CoreConfig::tier3Multiplier),
                    Codec.intRange(1, 54).fieldOf("housing_slots")
                            .forGetter(CoreConfig::housingSlots),
                    Codec.intRange(1, 12000).fieldOf("settle_interval_ticks")
                            .forGetter(CoreConfig::settleIntervalTicks))
            .apply(instance, CoreConfig::new));

    /** DESIGN.md §12.4 verbatim, plus the two structural numbers §12 does not specify. */
    public static final CoreConfig DEFAULT = new CoreConfig(400, 600, 900, 400, 8, 2.5, 6.0, 9, 20);

    /** The tier-I period, in ticks, of the housing that hosts {@code family} (§12.4). */
    public int basePeriodTicks(CoreFamily family) {
        return switch (family) {
            case SOUL -> soulCagePeriodTicks;
            case VERDANT -> verdantPlanterPeriodTicks;
            case PASTORAL -> paddockPeriodTicks;
            case LITHIC -> quarryNodePeriodTicks;
        };
    }

    /**
     * How many loot rolls one production cycle performs. Only the Quarry Node produces in bulk —
     * §12.4 prices it as "8 blocks / 20s" where the other three are one roll per period.
     */
    public int rollsPerCycle(CoreFamily family) {
        return family == CoreFamily.LITHIC ? quarryBlocksPerCycle : 1;
    }
}
