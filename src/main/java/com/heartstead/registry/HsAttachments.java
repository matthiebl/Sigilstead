package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

/** Attachment registration (DESIGN.md §7.5, CONVENTIONS.md §2.2). */
public final class HsAttachments {

    /**
     * Marks a villager as having been taught the DESIGN.md §7.5 proof-of-concept trade. Persistent
     * so it survives chunk unload/reload and world reload, unlike the vanilla {@code Offers} tag
     * which regenerates on level-up and restock.
     */
    public static AttachmentType<Boolean> TAUGHT_TRADE;

    private HsAttachments() {
    }

    public static void register() {
        TAUGHT_TRADE = AttachmentRegistry.createPersistent(Heartstead.id("taught_trade"), Codec.BOOL);
    }
}
