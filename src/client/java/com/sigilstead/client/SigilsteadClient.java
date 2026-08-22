package com.sigilstead.client;

import com.sigilstead.client.codex.CodexClientCache;
import com.sigilstead.client.screen.CodexScreen;
import com.sigilstead.client.screen.CoreHousingScreen;
import com.sigilstead.client.screen.VaultScreen;
import com.sigilstead.client.vault.VaultClientCache;
import com.sigilstead.network.CodexSyncPayload;
import com.sigilstead.network.VaultAnchorPayload;
import com.sigilstead.network.VaultStatePayload;
import com.sigilstead.network.VaultSyncPayload;
import com.sigilstead.registry.HsMenuTypes;
import com.sigilstead.vault.ClientVaultAnchorCache;
import com.sigilstead.vault.ClientVaultUpgradeView;
import com.sigilstead.vault.VaultUpgradeKind;
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
public class SigilsteadClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        MenuScreens.register(HsMenuTypes.VAULT, VaultScreen::new);
        MenuScreens.register(HsMenuTypes.CODEX, CodexScreen::new);
        MenuScreens.register(HsMenuTypes.CORE_HOUSING, CoreHousingScreen::new);
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
        //
        // §4.1's attunement tooltip needs nothing here: the attunement component is
        // networkSynchronized, so the stack the client already holds carries family, target and
        // progress, and PrimedCoreItem#appendHoverText reads them off it.
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
