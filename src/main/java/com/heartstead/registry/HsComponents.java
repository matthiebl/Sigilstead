package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.codex.SealedTomeData;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * Data component registration (DESIGN.md §3.3, CONVENTIONS.md §2.2).
 *
 * <p>{@link #SEALED_TOME_DATA} is the Sealed Tome's per-stack payload — which enchantment (and at
 * what level) the Empower step chose. Registered before {@link HsItems}, so later systems (the
 * §4.1 attunement component a Core Sigil grows when primed) have somewhere to go without reshuffling
 * registration order.
 */
public final class HsComponents {

    public static DataComponentType<SealedTomeData> SEALED_TOME_DATA;

    private HsComponents() {
    }

    public static void register() {
        SEALED_TOME_DATA = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                key("sealed_tome_data"),
                DataComponentType.<SealedTomeData>builder()
                        .persistent(SealedTomeData.CODEC)
                        .networkSynchronized(SealedTomeData.STREAM_CODEC)
                        .build());
    }

    private static ResourceKey<DataComponentType<?>> key(String path) {
        return ResourceKey.create(Registries.DATA_COMPONENT_TYPE, Heartstead.id(path));
    }
}
