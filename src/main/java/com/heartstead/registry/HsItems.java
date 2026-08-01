package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.item.HeartSigilItem;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

/**
 * Item registration (DESIGN.md §1, §6, CONVENTIONS.md §2).
 *
 * <p>Real registered items with their own ids — no {@code custom_data} identity markers and no
 * base-item table.
 *
 * <p>This is the whole §1 progression spine: one found {@link #SIGIL}, its three children, and the
 * three dimensional Vault Sigils §2.3 buys withdrawal reach with. Nothing else in the pack is
 * currency, and only the Sigil itself is uncraftable.
 */
public final class HsItems {

    private HsItems() {
    }

    /** §1 — the only currency. Found, never craftable; see {@link com.heartstead.economy.SigilLoot}. */
    public static final Item SIGIL = register("sigil", new Item.Properties());

    /** §1 / §4.1 — primed into a farm core. Cheapest of the three surcharges, per the §1.1 guardrail. */
    public static final Item CORE_SIGIL = register("core_sigil", new Item.Properties());

    /** §1 / §6 — consumed for +1 max heart. */
    public static final Item HEART_SIGIL =
            register("heart_sigil", new Item.Properties(), HeartSigilItem::new);

    /** §1 / §2.3 — Vault capacity, the Vault Pouch, and re-anchoring. */
    public static final Item VAULT_SIGIL = register("vault_sigil", new Item.Properties());

    /** §2.3 — overworld-wide withdrawal. Echo Shard is the proof item (§12.2). */
    public static final Item OVERWORLD_VAULT_SIGIL =
            register("overworld_vault_sigil", new Item.Properties());

    /** §2.3 — Nether-wide withdrawal. Ghast Tear + Blaze Powder are the proof items (§12.2). */
    public static final Item NETHER_VAULT_SIGIL =
            register("nether_vault_sigil", new Item.Properties());

    /** §2.3 — End-wide withdrawal. Dragon's Breath is the proof item (§12.2). */
    public static final Item END_VAULT_SIGIL = register("end_vault_sigil", new Item.Properties());

    private static Item register(String path, Item.Properties properties) {
        return register(path, properties, Item::new);
    }

    private static Item register(String path, Item.Properties properties, Function<Item.Properties, Item> factory) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Heartstead.id(path));
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
    }

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
