package com.heartstead.core;

import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * DESIGN.md §4.1 — turns a core's stored target id back into the thing it names. A target is stored
 * as a bare {@link Identifier} rather than a registry object so a core survives the mob or block it
 * targets being removed by a datapack: it becomes inert (§4.1's "wrong-target events are inert"
 * taken to its limit) instead of failing to load.
 *
 * <p>Which registry the id is read against comes from the family's {@link CoreFamily.Imprint}, which
 * is the single place that mapping is decided.
 */
public final class CoreTargets {

    private CoreTargets() {
    }

    /** The target's own display name — "Zombie", "Wheat" — for the §4.1 tooltip and the core's name. */
    public static Component displayName(CoreFamily family, Identifier target) {
        return switch (family.imprint()) {
            case ENTITY -> entityType(target).<Component>map(EntityType::getDescription)
                    .orElseGet(() -> Component.literal(target.toString()));
            case BLOCK -> block(target).<Component>map(Block::getName)
                    .orElseGet(() -> Component.literal(target.toString()));
        };
    }

    /**
     * The loot table a socketed core rolls to produce (§4.2). Empty when the target no longer exists,
     * or when it has no loot table at all — a housing holding such a core simply yields nothing.
     */
    public static Optional<ResourceKey<LootTable>> lootTable(CoreFamily family, Identifier target) {
        return switch (family.imprint()) {
            case ENTITY -> entityType(target).flatMap(EntityType::getDefaultLootTable);
            case BLOCK -> block(target).map(Block::getLootTable).flatMap(table -> table);
        };
    }

    public static Optional<EntityType<?>> entityType(Identifier target) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(target);
    }

    public static Optional<Block> block(Identifier target) {
        return BuiltInRegistries.BLOCK.getOptional(target);
    }
}
