package com.sigilstead.codex;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * DESIGN.md §3.3 — a Sealed Tome's per-stack payload: the one enchantment (and its archived level)
 * the Empower step chose. A {@code DataComponentType} (CONVENTIONS.md §2.2), registered as
 * {@link com.sigilstead.registry.HsComponents#SEALED_TOME_DATA}.
 */
public record SealedTomeData(ResourceKey<Enchantment> enchantment, int level) {

    public static final Codec<SealedTomeData> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ResourceKey.codec(Registries.ENCHANTMENT).fieldOf("enchantment").forGetter(SealedTomeData::enchantment),
                    Codec.intRange(1, 255).fieldOf("level").forGetter(SealedTomeData::level))
            .apply(instance, SealedTomeData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SealedTomeData> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.ENCHANTMENT), SealedTomeData::enchantment,
            ByteBufCodecs.VAR_INT, SealedTomeData::level,
            SealedTomeData::new);
}
