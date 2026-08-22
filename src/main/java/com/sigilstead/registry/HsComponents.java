package com.sigilstead.registry;

import com.sigilstead.Sigilstead;
import com.sigilstead.codex.SealedTomeData;
import com.sigilstead.core.CoreAttunement;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/**
 * Data component registration (DESIGN.md §3.3, CONVENTIONS.md §2.2).
 *
 * <p>{@link #SEALED_TOME_DATA} is the Sealed Tome's per-stack payload — which enchantment (and at
 * what level) the Empower step chose. {@link #CORE_ATTUNEMENT} is DESIGN.md §4.1's family / target /
 * progress triple, the reason §4 needs no player-side counter at all.
 *
 * <p>Both are {@code networkSynchronized}: the client draws the §4.1 attunement tooltip and the §4.3
 * tier line from the stack it already has, so neither needs a payload of its own.
 */
public final class HsComponents {

    public static DataComponentType<SealedTomeData> SEALED_TOME_DATA;

    /** DESIGN.md §4.1 — {@code sigilstead:attunement}, carried by every Primed Core and Core. */
    public static DataComponentType<CoreAttunement> CORE_ATTUNEMENT;

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

        CORE_ATTUNEMENT = Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                key("attunement"),
                DataComponentType.<CoreAttunement>builder()
                        .persistent(CoreAttunement.CODEC)
                        .networkSynchronized(CoreAttunement.STREAM_CODEC)
                        .build());
    }

    private static ResourceKey<DataComponentType<?>> key(String path) {
        return ResourceKey.create(Registries.DATA_COMPONENT_TYPE, Sigilstead.id(path));
    }
}
