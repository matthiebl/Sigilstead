package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.codex.CodexArchive;
import com.heartstead.lives.HeartLevel;
import com.heartstead.villager.TaughtEnchantments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Attachment registration (DESIGN.md §3.3, §7.2, §6, CONVENTIONS.md §2.2). */
public final class HsAttachments {

    /**
     * DESIGN.md §3.3/§7.2 — the enchantments a librarian has been taught to sell. Persistent so it
     * survives chunk unload/reload and world reload, unlike the vanilla {@code Offers} tag which
     * regenerates on level-up and restock. Generalises the §7.2 proof of concept's single boolean
     * flag into the real record {@link com.heartstead.villager.TaughtTradeInjector} re-applies.
     */
    public static AttachmentType<TaughtEnchantments> TAUGHT_ENCHANTMENTS;

    /** DESIGN.md §3.3 — the player's own archive, capacity tier included. Never per-block (see the class this backs). */
    public static AttachmentType<CodexArchive> CODEX_ARCHIVE;

    /**
     * Current heart count for a player (DESIGN.md §6). Source of truth for the {@code max_health}
     * attribute modifier applied by {@link com.heartstead.lives.LivesSystem} — not
     * {@code copyOnDeath}, since death must decrement the value rather than carry it over
     * unchanged; {@link com.heartstead.lives.LivesSystem} handles that transfer itself.
     */
    public static AttachmentType<HeartLevel> HEART_LEVEL;

    private HsAttachments() {
    }

    public static void register() {
        TAUGHT_ENCHANTMENTS = AttachmentRegistry.createPersistent(Heartstead.id("taught_enchantments"), TaughtEnchantments.CODEC);
        CODEX_ARCHIVE = AttachmentRegistry.createPersistent(Heartstead.id("codex_archive"), CodexArchive.CODEC);
        HEART_LEVEL = AttachmentRegistry.<HeartLevel>builder()
                .persistent(HeartLevel.CODEC)
                .initializer(HeartLevel::initial)
                .buildAndRegister(Heartstead.id("heart_level"));
    }
}
