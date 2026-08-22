package com.sigilstead.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sigilstead.config.CodexConfig;
import org.junit.jupiter.api.Test;

/** Pure tier math for DESIGN.md §3.3/§12.6 — no world, no server, runs in milliseconds. */
class CodexArchiveTierTest {

    @Test
    void tier1IsEightByDefault() {
        CodexArchiveTier tier = CodexArchiveTier.forTier(0, CodexConfig.DEFAULT);
        assertEquals(8, tier.capacity());
    }

    @Test
    void tier2IsSixteenByDefault() {
        CodexArchiveTier tier = CodexArchiveTier.forTier(1, CodexConfig.DEFAULT);
        assertEquals(16, tier.capacity());
    }

    @Test
    void tier3IsUnlimited() {
        CodexArchiveTier tier = CodexArchiveTier.forTier(2, CodexConfig.DEFAULT);
        assertEquals(CodexArchiveTier.UNLIMITED, tier.capacity());
    }

    @Test
    void hasRoomForIsStrictlyBelowCapacity() {
        CodexArchiveTier tier = new CodexArchiveTier(8);
        assertTrue(tier.hasRoomFor(7));
        assertFalse(tier.hasRoomFor(8));
    }
}
