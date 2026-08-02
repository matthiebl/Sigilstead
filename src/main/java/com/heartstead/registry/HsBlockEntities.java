package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.blockentity.CodexBlockEntity;
import com.heartstead.blockentity.LinkedFunnelBlockEntity;
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
            key("vault_anchor"),
            new BlockEntityType<>(VaultAnchorBlockEntity::new, java.util.Set.of(HsBlocks.VAULT_ANCHOR)));

    public static final BlockEntityType<LinkedFunnelBlockEntity> LINKED_FUNNEL = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            key("linked_funnel"),
            new BlockEntityType<>(LinkedFunnelBlockEntity::new, java.util.Set.of(HsBlocks.LINKED_FUNNEL)));

    public static final BlockEntityType<CodexBlockEntity> CODEX = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE,
            key("codex"),
            new BlockEntityType<>(CodexBlockEntity::new, java.util.Set.of(HsBlocks.CODEX)));

    private static ResourceKey<BlockEntityType<?>> key(String path) {
        return ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, Heartstead.id(path));
    }

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
