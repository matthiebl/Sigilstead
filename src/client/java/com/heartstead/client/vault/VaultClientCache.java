package com.heartstead.client.vault;

import com.heartstead.network.VaultStatePayload;
import com.heartstead.network.VaultSyncPayload;
import java.util.List;

/**
 * Client-side holder for the last {@link VaultSyncPayload} and {@link VaultStatePayload} received.
 * {@link com.heartstead.client.screen.VaultScreen} reads from here rather than storing the snapshot
 * on itself, so the same sync path (REFERENCES.md) can back a second screen (Phase 3's Artisan)
 * without either one owning the data.
 *
 * <p>Purely for rendering. Nothing here decides anything — every click still goes to the server as
 * an intent and is re-validated there (CONVENTIONS.md §5). The state copy exists so §2.4's tab 1 can
 * grey a button out rather than letting the player click into a refusal.
 */
public final class VaultClientCache {

    private static volatile List<VaultSyncPayload.Entry> entries = List.of();
    private static volatile VaultStatePayload state = VaultStatePayload.empty();

    private VaultClientCache() {
    }

    public static void update(List<VaultSyncPayload.Entry> newEntries) {
        entries = newEntries;
    }

    public static void update(VaultStatePayload newState) {
        state = newState;
    }

    public static List<VaultSyncPayload.Entry> entries() {
        return entries;
    }

    public static VaultStatePayload state() {
        return state;
    }
}
