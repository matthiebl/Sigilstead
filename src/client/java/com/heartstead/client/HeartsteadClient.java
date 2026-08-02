package com.heartstead.client;

import com.heartstead.client.codex.CodexClientCache;
import com.heartstead.client.screen.CodexScreen;
import com.heartstead.client.screen.VaultScreen;
import com.heartstead.client.vault.VaultClientCache;
import com.heartstead.network.CodexSyncPayload;
import com.heartstead.network.VaultAnchorPayload;
import com.heartstead.network.VaultStatePayload;
import com.heartstead.network.VaultSyncPayload;
import com.heartstead.registry.HsMenuTypes;
import com.heartstead.vault.ClientVaultAnchorCache;
import com.heartstead.vault.ClientVaultUpgradeView;
import com.heartstead.vault.VaultUpgradeKind;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;

/**
 * Client entrypoint. Never loaded on a dedicated server.
 *
 * <p>Everything that only exists because a screen is being drawn lives under this source set:
 * screen classes, renderers, keybinds, tooltips, colour providers.
 *
 * <p>The split is not cosmetic — a class here that leaks into common code crashes the dedicated
 * server on load, and that failure shows up late. See {@code docs/CONVENTIONS.md} §3.
 */
public class HeartsteadClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(HsMenuTypes.VAULT, VaultScreen::new);
        MenuScreens.register(HsMenuTypes.CODEX, CodexScreen::new);
        ClientPlayNetworking.registerGlobalReceiver(CodexSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> CodexClientCache.update(payload)));
        ClientPlayNetworking.registerGlobalReceiver(VaultSyncPayload.TYPE,
                (payload, context) -> context.client().execute(() -> VaultClientCache.update(payload.entries())));
        ClientPlayNetworking.registerGlobalReceiver(VaultAnchorPayload.TYPE,
                (payload, context) -> context.client().execute(() -> ClientVaultAnchorCache.update(payload.anchorPos())));
        ClientPlayNetworking.registerGlobalReceiver(VaultStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    VaultClientCache.update(payload);
                    // Slot#isActive() runs client-side too, so the already-bought reach tiers have to
                    // reach the common-side view the upgrade slots consult (CONVENTIONS.md §3).
                    ClientVaultUpgradeView.update(satisfiedKinds(payload));
                }));
        // TODO Phase 3: Artisan extends the vanilla crafting menu (§7.1)
        // TODO Phase 4: core attunement progress in the item tooltip (§4.1)
    }

    /** The §2.3 reach tiers the synced state says are already bought. */
    private static java.util.Set<VaultUpgradeKind> satisfiedKinds(VaultStatePayload payload) {
        java.util.EnumSet<VaultUpgradeKind> set = java.util.EnumSet.noneOf(VaultUpgradeKind.class);
        for (VaultUpgradeKind kind : VaultUpgradeKind.values()) {
            if (kind.dimension() != null && payload.reachTiers().contains(kind.dimension())) {
                set.add(kind);
            }
        }
        return set;
    }
}
