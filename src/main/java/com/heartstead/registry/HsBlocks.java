package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.block.LinkedFunnelBlock;
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
import net.minecraft.world.level.material.PushReaction;

/**
 * Block registration (DESIGN.md §2.1, CONVENTIONS.md §2). The first real registered blocks in the
 * pack — no marker entities, no base-item table.
 */
public final class HsBlocks {

    private HsBlocks() {
    }

    /**
     * §2.1 — the Anchor, with the half of its hardening that lives in code.
     * {@code PushReaction.BLOCK} stops a piston relocating it out from under the world's position
     * claim, and the 1200 explosion resistance (obsidian's) covers creepers, beds and TNT. The other
     * half — the {@code dragon_immune} and {@code wither_immune} tags — is JSON in
     * {@code data/minecraft/tags/block/}, because tags are data-driven content (CONVENTIONS.md §6).
     */
    public static final Block VAULT_ANCHOR = register(
            "vault_anchor",
            key -> new VaultAnchorBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .strength(5.0f, 1200.0f)
                            .sound(SoundType.AMETHYST)
                            .pushReaction(PushReaction.BLOCK)
                            .requiresCorrectToolForDrops()),
            VaultAnchorBlockItem::new);

    /**
     * §2.1 — the hopper-speed automation link. Deliberately ordinary: it holds nothing and claims
     * nothing, so losing one costs a hopper and the Vault is unaffected.
     */
    public static final Block LINKED_FUNNEL = register(
            "linked_funnel",
            key -> new LinkedFunnelBlock(
                    BlockBehaviour.Properties.of()
                            .setId(key)
                            .strength(3.0f, 8.0f)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()));

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
        Registry.register(BuiltInRegistries.ITEM, itemKey,
                itemFactory.apply(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
        return block;
    }

    /** Forces class load (and so the static registrations above) at an explicit, ordered point. */
    public static void register() {
    }
}
