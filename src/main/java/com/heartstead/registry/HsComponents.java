package com.heartstead.registry;

/**
 * Data component registration (DESIGN.md §1, CONVENTIONS.md §2.2).
 *
 * <p>Empty for now — every §1 Sigil is a plain currency item with no per-stack state, so nothing
 * needs a custom {@code DataComponentType} yet. Kept as its own registry class, registered before
 * {@link HsItems}, so later systems (Codex enchantment records, the §4.1 {@code attunement}
 * component a Core Sigil grows when it is primed) have somewhere to go without reshuffling
 * registration order.
 */
public final class HsComponents {

    private HsComponents() {
    }

    public static void register() {
        // No custom components yet.
    }
}
