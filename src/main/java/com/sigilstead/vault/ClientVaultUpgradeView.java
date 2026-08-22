package com.sigilstead.vault;

import java.util.EnumSet;
import java.util.Set;

/**
 * Client-side view of which §2.3 upgrades the world has already bought, kept in common code (it
 * holds no client-only types) so {@code VaultMenu.UpgradeSlot#isActive()} can read it from both
 * sides. {@code Slot#isActive()} is evaluated on the client too, and the client has no access to the
 * server-only {@code SavedData} — without this, an already-bought reach slot would stay clickable
 * locally and then be refused, which reads as a broken button.
 *
 * <p>Only ever written from the client's {@code VaultStatePayload} receiver (src/client,
 * CONVENTIONS.md §3) and only ever meaningful when the reader is on the client. The server always
 * answers from {@link VaultData} directly, so nothing here can influence a real decision.
 */
public final class ClientVaultUpgradeView {

    private static volatile Set<VaultUpgradeKind> satisfied = EnumSet.noneOf(VaultUpgradeKind.class);

    private ClientVaultUpgradeView() {
    }

    public static void update(Set<VaultUpgradeKind> newSatisfied) {
        satisfied = newSatisfied.isEmpty() ? EnumSet.noneOf(VaultUpgradeKind.class) : EnumSet.copyOf(newSatisfied);
    }

    public static boolean isSatisfied(VaultUpgradeKind kind) {
        return satisfied.contains(kind);
    }
}
