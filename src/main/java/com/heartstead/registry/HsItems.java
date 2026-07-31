package com.heartstead.registry;

/**
 * Item registration.
 *
 * <p>Deliberately empty — this is Phase 0.1 work, see {@code docs/PROMPTS.md}.
 *
 * <p><b>Before writing anything here, verify the {@code Item.Properties} API against the
 * decompiled 26.2 sources.</b> Item construction changed repeatedly across 1.21.x — most
 * notably items now require their {@code ResourceKey} to be set on the properties at
 * construction time rather than being inferred from the registry call. Every tutorial
 * older than 1.21.5 is wrong, and the compiler error it produces is not obvious.
 *
 * <p>Run {@code ./gradlew genSources} and read the real signature first.
 *
 * <p>Items to register (docs/DESIGN.md §1):
 * <ul>
 *   <li>{@code heart_shard} — the base currency</li>
 *   <li>{@code vital_heart} — 4 shards + golden apple</li>
 *   <li>{@code vault_sigil} — structure-only, never craftable</li>
 * </ul>
 *
 * <p>These are now <b>real registered items</b> with their own ids. The data-pack-era
 * {@code custom_data} marker convention and the "pick an obscure base item" workaround are
 * both gone — see {@code docs/CONVENTIONS.md} §2.
 */
public final class HsItems {

    private HsItems() {
    }

    public static void register() {
        // TODO Phase 0.1 — see docs/PROMPTS.md
    }
}
