package com.sigilstead.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * DESIGN.md §3.3 — the archive belongs to the player, not to any one Codex block. A codec-backed
 * player attachment (CONVENTIONS.md §2.2, §4) rather than block-entity state: any Codex a player
 * walks up to shows this same record, and breaking one loses nothing.
 *
 * <p>{@code archived} keys by enchantment and stores the highest level ever archived for it — a
 * second, weaker copy of the same book found later archives nothing new. {@code capacityTier} is
 * 0/1/2 for the three §12.6 tiers; the number of distinct enchantments each tier allows lives in
 * {@link CodexArchiveTier}, not here, so the tuning numbers stay in config (CONVENTIONS.md §5).
 *
 * <p>Carries a schema version per CONVENTIONS.md §4.
 */
public record CodexArchive(int version, Map<ResourceKey<Enchantment>, Integer> archived, int capacityTier) {

    public static final int CURRENT_VERSION = 1;

    public static final Codec<CodexArchive> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.fieldOf("version").forGetter(CodexArchive::version),
                    Codec.unboundedMap(ResourceKey.codec(Registries.ENCHANTMENT), Codec.intRange(1, 255))
                            .fieldOf("archived")
                            .forGetter(a -> Map.copyOf(a.archived)),
                    Codec.intRange(0, 2).optionalFieldOf("capacity_tier", 0).forGetter(CodexArchive::capacityTier))
            .apply(instance, CodexArchive::new));

    public static CodexArchive initial() {
        return new CodexArchive(CURRENT_VERSION, Map.of(), 0);
    }

    /** The archived level for {@code enchantment}, or 0 if it has never been archived. */
    public int levelOf(ResourceKey<Enchantment> enchantment) {
        return archived.getOrDefault(enchantment, 0);
    }

    /**
     * A copy with {@code enchantment} archived at {@code level} — or this unchanged, if the archive
     * already holds that enchantment at an equal or higher level.
     */
    public CodexArchive withArchived(ResourceKey<Enchantment> enchantment, int level) {
        if (levelOf(enchantment) >= level) {
            return this;
        }
        Map<ResourceKey<Enchantment>, Integer> next = new LinkedHashMap<>(archived);
        next.put(enchantment, level);
        return new CodexArchive(CURRENT_VERSION, next, capacityTier);
    }

    public CodexArchive withCapacityTier(int tier) {
        return new CodexArchive(CURRENT_VERSION, archived, tier);
    }
}
