package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.item.LifeHeartItem;
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
 */
public final class HsItems {

    private HsItems() {
    }

    public static final Item HEART_SHARD = register("heart_shard", new Item.Properties());
    public static final Item VITAL_HEART = register("vital_heart", new Item.Properties());
    public static final Item VAULT_SIGIL = register("vault_sigil", new Item.Properties().stacksTo(16));
    public static final Item LIFE_HEART =
            register("life_heart", new Item.Properties(), LifeHeartItem::new);

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
