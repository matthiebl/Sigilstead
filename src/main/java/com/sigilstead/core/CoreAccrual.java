package com.sigilstead.core;

/**
 * DESIGN.md §4.2 — offline accrual, as pure arithmetic with no world attached so it can be unit
 * tested (CONVENTIONS.md §8). {@link com.sigilstead.blockentity.CoreHousingBlockEntity} is the only
 * caller; everything it knows about time is in here.
 *
 * <h2>The one-clock rule</h2>
 *
 * §4.2: "Settle from the timestamp and update the timestamp in the same operation; never accrue from
 * two clocks." {@link #settle} is that operation. It takes the stored stamp and the current game
 * time, and returns both the yield <em>and</em> the stamp to store — there is no separate tick
 * counter anywhere, so a housing that is loaded the whole time and one that was unloaded for the same
 * span settle identically. That is what makes "never require a chunkloader" true rather than
 * approximately true.
 *
 * <p>It follows that a loaded housing may call this as often as it likes. It re-runs the same
 * subtraction, so calling it every tick and calling it once an hour pay exactly the same total — the
 * property {@code CoreAccrualTest.manySmallSettlesEqualOneLargeSettle} pins down. §4.2's phrase "in
 * one calculation rather than ticking" is about not keeping a second counter, not about refusing to
 * run while loaded.
 *
 * <h2>The remainder</h2>
 *
 * A settle that lands mid-period keeps the unpaid fraction by advancing the stamp only by what it
 * actually paid for, so nothing is lost to rounding across many small settles. The exception is a
 * settle that hit the cap: there the leftover really is discarded, because §4.2 wants the first chunk
 * load after a long absence not to be a jackpot.
 */
public final class CoreAccrual {

    /** Minecraft's day is 24000 ticks over 24 in-game hours, so an in-game hour is 1000. */
    public static final long TICKS_PER_INGAME_HOUR = 1000L;

    /**
     * Absorbs the last bit or two of floating-point error before flooring. Without it, an effective
     * period of {@code 400 / 6} turns an exact six tier-III cycles into five, because the division
     * lands at 359.999… rather than 360.
     */
    private static final double FLOOR_EPSILON = 1.0e-9;

    private CoreAccrual() {
    }

    /**
     * The result of one settle: how many production cycles to pay out, and the stamp the caller must
     * store. Storing {@code newLastSettled} is not optional — it is what stops the next settle
     * paying for the same span again.
     */
    public record Settlement(int cycles, long newLastSettled) {
    }

    /** §12.7 {@code core_accrual_cap_hours} expressed in ticks. */
    public static long capTicks(int accrualCapHours) {
        return Math.max(0L, (long) accrualCapHours * TICKS_PER_INGAME_HOUR);
    }

    /**
     * §12.4 — the tier-I period scaled by the core's tier multiplier and the global
     * {@code core_rate_multiplier}. A multiplier of zero yields an infinite period, which
     * {@link #settle} reads as "switched off" rather than dividing by zero.
     */
    public static double effectivePeriodTicks(int basePeriodTicks, double tierMultiplier, double rateMultiplier) {
        double speed = tierMultiplier * rateMultiplier;
        if (speed <= 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        return basePeriodTicks / speed;
    }

    /**
     * Settles everything owed between {@code lastSettled} and {@code now}, capped at {@code capTicks}.
     *
     * @param now          current game time in ticks
     * @param lastSettled  the stamp stored by the previous settle
     * @param periodTicks  ticks per production cycle, from {@link #effectivePeriodTicks}
     * @param capTicks     the §4.2 backlog ceiling, from {@link #capTicks}
     */
    public static Settlement settle(long now, long lastSettled, double periodTicks, long capTicks) {
        if (!Double.isFinite(periodTicks) || periodTicks <= 0.0) {
            // Production is switched off. Resync the stamp so switching it back on doesn't pay a
            // backlog for the time it spent disabled.
            return new Settlement(0, now);
        }

        long elapsed = now - lastSettled;
        if (elapsed <= 0L) {
            // Nothing owed — or the clock moved backwards (a restored backup, a /time set). Either
            // way, resync rather than leaving a stamp in the future that would suppress yield later.
            return new Settlement(0, now);
        }

        long capped = Math.min(elapsed, capTicks);
        long cycles = (long) Math.floor(capped / periodTicks + FLOOR_EPSILON);
        if (cycles <= 0L) {
            // Keep the unpaid partial period, unless the cap already swallowed the rest of the span.
            return new Settlement(0, elapsed > capTicks ? now - capped : lastSettled);
        }

        long consumed = Math.min(capped, (long) Math.floor(cycles * periodTicks + FLOOR_EPSILON));
        long remainder = capped - consumed;

        // Un-capped: advance by exactly what was paid for, keeping the remainder for next time.
        // Capped: jump to now minus the remainder, discarding the over-cap backlog entirely.
        long newLastSettled = elapsed > capTicks ? now - remainder : lastSettled + consumed;

        return new Settlement((int) Math.min(cycles, Integer.MAX_VALUE), newLastSettled);
    }
}
