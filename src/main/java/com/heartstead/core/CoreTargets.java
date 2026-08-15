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
        Optional<ClassicCore> classic = ClassicCore.forTarget(target);
        if (classic.isPresent() && classic.get().isSynthetic()) {
            // §5's six milestone cores name a thing (a raid, a barter) rather than a registry
            // object, so there is nothing for entityType()/block() below to resolve.
            return classic.get().displayName();
        }
        return switch (family.imprint()) {
            case ENTITY -> entityType(target).<Component>map(EntityType::getDescription)
                    .orElseGet(() -> Component.literal(target.toString()));
            case BLOCK -> block(target).<Component>map(Block::getName)
                    .orElseGet(() -> Component.literal(target.toString()));
        };
    }

    /**
     * §4.1's tooltip preview of what a core actually yields, so a synthetic classic core like Ender
     * ("mimics an Enderman, locked by killing the Ender Dragon") does not stay a mystery until you
     * watch a housing produce something. Classic cores get their own curated line — several either
     * name a thing rather than a mob ({@link ClassicCore#isSynthetic()}) or would otherwise describe
     * their bespoke loot table with the wrong mob's typical drops. Ordinary cores fall back to naming
     * whatever their target itself is known to drop.
     */
    public static Component yieldDescription(CoreFamily family, Identifier target) {
        Optional<ClassicCore> classic = ClassicCore.forTarget(target);
        if (classic.isPresent()) {
            return classic.get().yieldDescription();
        }
        return Component.translatable("item.heartstead.core.yield.generic", displayName(family, target));
    }

    /**
     * The loot table a socketed core rolls to produce (§4.2). Empty when the target no longer exists,
     * or when it has no loot table at all — a housing holding such a core simply yields nothing.
     *
     * <p>§5's eleven classic cores override this unconditionally rather than falling back to the
     * mob's or block's own table: §4.2 rolls with no killing player in the context, so a vanilla
     * table gated on {@code killed_by_player} would silently give a §5 core nothing at all for the
     * exact drop it exists to automate. See {@link ClassicCore}.
     */
    public static Optional<ResourceKey<LootTable>> lootTable(CoreFamily family, Identifier target) {
        Optional<ClassicCore> classic = ClassicCore.forTarget(target);
        if (classic.isPresent()) {
            return Optional.of(classic.get().lootTable());
        }
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
