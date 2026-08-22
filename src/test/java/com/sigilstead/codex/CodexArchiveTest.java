package com.sigilstead.codex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.enchantment.Enchantment;
import org.junit.jupiter.api.Test;

/** Pure archive math for DESIGN.md §3.3 — no world, no server, runs in milliseconds. */
class CodexArchiveTest {

    private static final ResourceKey<Enchantment> MENDING =
            ResourceKey.create(Registries.ENCHANTMENT, Identifier.withDefaultNamespace("mending"));

    @Test
    void archivingRecordsTheLevel() {
        CodexArchive archive = CodexArchive.initial().withArchived(MENDING, 1);
        assertEquals(1, archive.levelOf(MENDING));
    }

    @Test
    void archivingALowerLevelLaterDoesNothing() {
        CodexArchive archive = CodexArchive.initial().withArchived(MENDING, 3);
        CodexArchive unchanged = archive.withArchived(MENDING, 1);
        assertSame(archive, unchanged);
        assertEquals(3, unchanged.levelOf(MENDING));
    }

    @Test
    void archivingAHigherLevelLaterUpgradesIt() {
        CodexArchive archive = CodexArchive.initial().withArchived(MENDING, 1).withArchived(MENDING, 2);
        assertEquals(2, archive.levelOf(MENDING));
    }

    @Test
    void unarchivedEnchantmentIsLevelZero() {
        assertEquals(0, CodexArchive.initial().levelOf(MENDING));
    }

    @Test
    void capacityTierStartsAtZero() {
        assertEquals(0, CodexArchive.initial().capacityTier());
    }
}
