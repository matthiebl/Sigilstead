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
        // TODO Phase 2: HsScreens.register();   // Vault screen (DESIGN.md §2.4)
        // TODO Phase 3: Codex screen (§3.3); Artisan extends the vanilla crafting menu (§7.1)
        // TODO Phase 4: core attunement progress in the item tooltip (§4.1)
    }
}
