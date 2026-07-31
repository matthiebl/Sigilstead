package com.heartstead.registry;

/**
 * Data component registration (DESIGN.md §1, CONVENTIONS.md §2.2).
 *
 * <p>Empty for now — Heart Shard, Vital Heart and Vault Sigil are plain currency items with no
 * per-stack state, so nothing needs a custom {@code DataComponentType} yet. Kept as its own
 * registry class, registered before {@link HsItems}, so later systems (Codex enchantment records,
 * core attunement progress) have somewhere to go without reshuffling registration order.
 */
public final class HsComponents {

    private HsComponents() {
    }

    public static void register() {
        // No custom components yet.
    }
}
