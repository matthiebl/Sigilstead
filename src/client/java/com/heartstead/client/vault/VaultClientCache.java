package com.heartstead.client.vault;

import com.heartstead.network.VaultSyncPayload;
import java.util.List;

/**
 * Client-side holder for the last {@link VaultSyncPayload} received. {@link
 * com.heartstead.client.screen.VaultScreen} reads from here rather than storing the snapshot on
 * itself, so the same sync path (REFERENCES.md) can back a second screen (Phase 3's Artisan) without
 * either one owning the data.
 */
public final class VaultClientCache {

    private static volatile List<VaultSyncPayload.Entry> entries = List.of();

    private VaultClientCache() {
    }

    public static void update(List<VaultSyncPayload.Entry> newEntries) {
        entries = newEntries;
    }

    public static List<VaultSyncPayload.Entry> entries() {
        return entries;
    }
}
