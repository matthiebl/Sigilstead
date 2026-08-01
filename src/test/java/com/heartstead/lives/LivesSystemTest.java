package com.heartstead.lives;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Pure clamp math for DESIGN.md §6 — no world, no server, runs in milliseconds. */
class LivesSystemTest {

    @Test
    void deathLosesConfiguredHearts() {
        assertEquals(9, LivesSystem.heartsAfterDeath(10, 5, 1));
    }

    @Test
    void deathNeverGoesBelowFloor() {
        assertEquals(5, LivesSystem.heartsAfterDeath(5, 5, 1));
        assertEquals(5, LivesSystem.heartsAfterDeath(6, 5, 2));
    }

    @Test
    void deathRespectsHarderModeFloor() {
        assertEquals(3, LivesSystem.heartsAfterDeath(4, 3, 2));
    }

    @Test
    void consumeGainsOneHeart() {
        assertEquals(11, LivesSystem.heartsAfterConsume(10, 20));
    }

    @Test
    void consumeNeverExceedsCap() {
        assertEquals(20, LivesSystem.heartsAfterConsume(20, 20));
    }
}
