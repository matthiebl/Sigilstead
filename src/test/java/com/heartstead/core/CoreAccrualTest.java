package com.heartstead.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * DESIGN.md §4.2 — the offline accrual formula, tested before the block entity that uses it
 * (CONVENTIONS.md §8). §4.2 names two bugs this design invites; both are arithmetic, so both are
 * catchable here rather than in a world:
 *
 * <ul>
 *   <li><b>Double-counting</b> — settling twice must never pay twice. Every test that settles more
 *       than once feeds the previous result's {@code newLastSettled} back in, which is the contract
 *       the block entity relies on.</li>
 *   <li><b>Unbounded catch-up</b> — a chunk untouched for forty days must pay the cap, not the
 *       backlog.</li>
 * </ul>
 */
class CoreAccrualTest {

    private static final long CAP_24H = 24L * CoreAccrual.TICKS_PER_INGAME_HOUR;

    @Test
    void yieldsOneCyclePerPeriod() {
        CoreAccrual.Settlement result = CoreAccrual.settle(4000L, 0L, 400.0, CAP_24H);
        assertEquals(10, result.cycles());
        assertEquals(4000L, result.newLastSettled());
    }

    @Test
    void partialPeriodYieldsNothingAndKeepsItsRemainder() {
        CoreAccrual.Settlement result = CoreAccrual.settle(399L, 0L, 400.0, CAP_24H);
        assertEquals(0, result.cycles());
        assertEquals(0L, result.newLastSettled(), "an unpaid partial period must not be thrown away");
    }

    /**
     * The double-count guard in its simplest form: settling at the same instant a second time pays
     * nothing. This is the shape of "settle on chunk load AND tick while loaded" (§4.2) — if the
     * second call paid again, this fails.
     */
    @Test
    void settlingTwiceAtTheSameTimePaysOnce() {
        CoreAccrual.Settlement first = CoreAccrual.settle(4000L, 0L, 400.0, CAP_24H);
        CoreAccrual.Settlement second = CoreAccrual.settle(4000L, first.newLastSettled(), 400.0, CAP_24H);

        assertEquals(10, first.cycles());
        assertEquals(0, second.cycles());
    }

    /**
     * Many small settles must total exactly what one big settle would have paid. This is the real
     * double-count test: a housing loaded the whole time settles every {@code settle_interval_ticks},
     * and must not out-earn one that was unloaded for the same span.
     *
     * <p>The span stays under the cap on purpose. Above it the two <em>must</em> diverge — that is
     * what the cap is for, and {@link #backlogIsCappedAtTheConfiguredCeiling} covers that side.
     */
    @Test
    void manySmallSettlesEqualOneLargeSettle() {
        long span = 20_000L; // < CAP_24H
        double period = 437.0; // deliberately not a divisor of the interval, so remainders matter

        CoreAccrual.Settlement single = CoreAccrual.settle(span, 0L, period, CAP_24H);

        int total = 0;
        long last = 0L;
        for (long now = 20L; now <= span; now += 20L) {
            CoreAccrual.Settlement step = CoreAccrual.settle(now, last, period, CAP_24H);
            total += step.cycles();
            last = step.newLastSettled();
        }

        assertEquals(single.cycles(), total, "ticking while loaded must not out-earn one offline settle");
    }

    /** §4.2 — "a chunk untouched for forty in-game days" pays the cap, not the backlog. */
    @Test
    void backlogIsCappedAtTheConfiguredCeiling() {
        long fortyDays = 40L * 24L * CoreAccrual.TICKS_PER_INGAME_HOUR;
        CoreAccrual.Settlement result = CoreAccrual.settle(fortyDays, 0L, 400.0, CAP_24H);

        assertEquals((int) (CAP_24H / 400), result.cycles());
        assertTrue(result.newLastSettled() > fortyDays - CAP_24H,
                "the over-cap backlog must be discarded, not carried forward as credit");
    }

    /** After a capped settle the clock is caught up, so the very next settle starts from now. */
    @Test
    void aCappedSettleDoesNotLeaveResidualBacklog() {
        long fortyDays = 40L * 24L * CoreAccrual.TICKS_PER_INGAME_HOUR;
        CoreAccrual.Settlement first = CoreAccrual.settle(fortyDays, 0L, 400.0, CAP_24H);
        CoreAccrual.Settlement second = CoreAccrual.settle(fortyDays, first.newLastSettled(), 400.0, CAP_24H);

        assertEquals(0, second.cycles());
    }

    /** §12.4 tier III is 6× tier I, which must mean six times the cycles over the same span. */
    @Test
    void tierMultiplierScalesYieldLinearly() {
        double tierOne = CoreAccrual.effectivePeriodTicks(400, 1.0, 1.0);
        double tierThree = CoreAccrual.effectivePeriodTicks(400, 6.0, 1.0);

        int slow = CoreAccrual.settle(24_000L, 0L, tierOne, CAP_24H).cycles();
        int fast = CoreAccrual.settle(24_000L, 0L, tierThree, CAP_24H).cycles();

        assertEquals(slow * 6, fast);
    }

    /** {@code core_rate_multiplier} (§12.7) is global and multiplies on top of the tier multiplier. */
    @Test
    void globalRateMultiplierComposesWithTier() {
        assertEquals(400.0 / (2.5 * 0.5), CoreAccrual.effectivePeriodTicks(400, 2.5, 0.5), 1e-9);
    }

    /** A multiplier of zero switches cores off rather than dividing by zero. */
    @Test
    void aZeroRateMultiplierStopsProductionInsteadOfCrashing() {
        double period = CoreAccrual.effectivePeriodTicks(400, 1.0, 0.0);
        CoreAccrual.Settlement result = CoreAccrual.settle(1_000_000L, 0L, period, CAP_24H);
        assertEquals(0, result.cycles());
    }

    /** A clock that appears to run backwards (a restored backup, a /time set) must pay nothing, not a negative. */
    @Test
    void aBackwardsClockPaysNothing() {
        CoreAccrual.Settlement result = CoreAccrual.settle(100L, 5000L, 400.0, CAP_24H);
        assertEquals(0, result.cycles());
        assertEquals(100L, result.newLastSettled(), "the stamp should resync to now rather than stay in the future");
    }
}
