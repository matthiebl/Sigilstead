package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.lives.HeartLevel;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Attachment registration (DESIGN.md §7.2, §6, CONVENTIONS.md §2.2). */
public final class HsAttachments {

    /**
     * Marks a villager as having been taught the DESIGN.md §7.2 proof-of-concept trade. Persistent
     * so it survives chunk unload/reload and world reload, unlike the vanilla {@code Offers} tag
     * which regenerates on level-up and restock.
     */
    public static AttachmentType<Boolean> TAUGHT_TRADE;

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
        TAUGHT_TRADE = AttachmentRegistry.createPersistent(Heartstead.id("taught_trade"), Codec.BOOL);
        HEART_LEVEL = AttachmentRegistry.<HeartLevel>builder()
                .persistent(HeartLevel.CODEC)
                .initializer(HeartLevel::initial)
                .buildAndRegister(Heartstead.id("heart_level"));
    }
}
