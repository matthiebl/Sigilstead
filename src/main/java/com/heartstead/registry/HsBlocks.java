package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.block.VaultAnchorBlock;
import com.heartstead.item.VaultAnchorBlockItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Block registration (DESIGN.md §2.1, CONVENTIONS.md §2). The first real registered blocks in the
 * pack — no marker entities, no base-item table.
 */
public final class HsBlocks {

    private HsBlocks() {
    }

    public static final Block VAULT_ANCHOR = register(
            "vault_anchor",
            key -> new VaultAnchorBlock(
                    BlockBehaviour.Properties.of().setId(key).strength(5.0f, 1200.0f).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()),
            VaultAnchorBlockItem::new);

    private static Block register(String path, java.util.function.Function<ResourceKey<Block>, Block> factory) {
        return register(path, factory, BlockItem::new);
    }

    private static Block register(
            String path,
            java.util.function.Function<ResourceKey<Block>, Block> blockFactory,
            java.util.function.BiFunction<Block, Item.Properties, Item> itemFactory) {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Heartstead.id(path));
        Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, blockFactory.apply(blockKey));
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Heartstead.id(path));
        Registry.register(BuiltInRegistries.ITEM, itemKey, itemFactory.apply(block, new Item.Properties().setId(itemKey)));
        return block;
    }

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
