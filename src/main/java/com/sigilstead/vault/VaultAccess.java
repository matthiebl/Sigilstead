package com.sigilstead.vault;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * DESIGN.md §2.2 — <em>which verbs a player has</em>, which after the v0.4 rework is the only part of
 * the Vault that is per-player. Capacity and reach belong to the world (§2.3); what a Satchel owner
 * lacks compared to a Pouch owner is not range, it is the withdrawal verb itself.
 *
 * <p>Sent to the client as the menu's opening data so the screen can grey out what it cannot do
 * rather than letting the player click and be silently refused. The server re-checks anyway — the
 * screen sends intents, never results.
 */
public enum VaultAccess {

    /** The Anchor block: both §2.4 tabs, and the only place activation and upgrades can happen. */
    ANCHOR(true, true),

    /** §2.2 T1 — "deposit, from anywhere, any dimension. No withdrawal." */
    SATCHEL(false, false),

    /** §2.2 T2 — adds withdrawal, still subject to the §2.3 reach tier for wherever you're standing. */
    POUCH(false, true);

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultAccess> STREAM_CODEC =
            ByteBufCodecs.<VaultAccess>idMapper(id -> values()[id], VaultAccess::ordinal).cast();

    private final boolean anchorTab;
    private final boolean canWithdraw;

    VaultAccess(boolean anchorTab, boolean canWithdraw) {
        this.anchorTab = anchorTab;
        this.canWithdraw = canWithdraw;
    }

    /** §2.4 — only the Anchor screen has tab 1. "The Vault Pouch opens tab 2 alone", and so does the Satchel. */
    public boolean hasAnchorTab() {
        return anchorTab;
    }

    /**
     * Whether this access item grants the withdrawal verb at all. Separate from — and evaluated
     * before — {@link VaultReach}, which then decides whether it works from where the player stands.
     * A Satchel is refused for the first reason and never reaches the second.
     */
    public boolean canWithdraw() {
        return canWithdraw;
    }
}
