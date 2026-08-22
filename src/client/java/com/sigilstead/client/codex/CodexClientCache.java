package com.sigilstead.client.codex;

import com.sigilstead.network.CodexSyncPayload;
import java.util.List;

/**
 * Client-side holder for the last {@link CodexSyncPayload} received, the same shape as
 * {@code VaultClientCache}. {@code CodexScreen} reads from here rather than storing the snapshot on
 * itself.
 */
public final class CodexClientCache {

    private static volatile CodexSyncPayload state = new CodexSyncPayload(List.of(), 0, 8);

    private CodexClientCache() {
    }

    public static void update(CodexSyncPayload newState) {
        state = newState;
    }

    public static CodexSyncPayload state() {
        return state;
    }
}
