package com.sigilstead.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * DESIGN.md §3.3/§7.2 — the enchantments (and the level each was taught at) a librarian has
 * permanently learned to sell. Stored on the villager entity as a persistent, codec-backed
 * attachment (CONVENTIONS.md §2.2) rather than derived from its live {@code Offers} — vanilla
 * regenerates {@code Offers} on level-up and restock, which is exactly what this survives.
 *
 * <p>Generalises the DESIGN.md §7.2 proof of concept's single boolean flag into the real per-book
 * record the finished Teach step needs, keeping the same attachment + mixin persistence mechanic.
 */
public record TaughtEnchantments(int version, Map<ResourceKey<Enchantment>, Integer> taught) {

    public static final int CURRENT_VERSION = 1;

    public static final Codec<TaughtEnchantments> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.fieldOf("version").forGetter(TaughtEnchantments::version),
                    Codec.unboundedMap(ResourceKey.codec(Registries.ENCHANTMENT), Codec.intRange(1, 255))
                            .fieldOf("taught")
                            .forGetter(t -> Map.copyOf(t.taught)))
            .apply(instance, TaughtEnchantments::new));

    public static TaughtEnchantments initial() {
        return new TaughtEnchantments(CURRENT_VERSION, Map.of());
    }

    public TaughtEnchantments withTaught(ResourceKey<Enchantment> enchantment, int level) {
        if (taught.getOrDefault(enchantment, 0) >= level) {
            return this;
        }
        Map<ResourceKey<Enchantment>, Integer> next = new LinkedHashMap<>(taught);
        next.put(enchantment, level);
        return new TaughtEnchantments(CURRENT_VERSION, next);
    }
}
