package com.heartstead.client;

import net.fabricmc.api.ClientModInitializer;

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
        // TODO Phase 3: HsScreens.register();   // Vault screen — the real slot-based one
        // TODO Phase 5: Artisan / Foundry screens
        // TODO Phase 1: heart HUD overlay for the Frail indicator (DESIGN.md §5)
    }
}
