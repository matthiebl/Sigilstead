package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.blockentity.VaultAnchorBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Block entity registration (CONVENTIONS.md §2). */
public final class HsBlockEntities {

    private HsBlockEntities() {
    }

    public static final BlockEntityType<VaultAnchorBlockEntity> VAULT_ANCHOR = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            key(),
            new BlockEntityType<>(VaultAnchorBlockEntity::new, java.util.Set.of(HsBlocks.VAULT_ANCHOR)));

    private static ResourceKey<BlockEntityType<?>> key() {
        return ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Heartstead.id("vault_anchor"));
    }

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
