package com.sigilstead.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * DESIGN.md §4.3 — the identity the one-active-core-per-world rule is keyed on: a family plus a
 * target, not a core item. Two zombie Soul Cores collide; a zombie Soul Core and a skeleton Soul Core
 * do not, and neither do a zombie Soul Core and (were it possible) a zombie Pastoral Core.
 *
 * <p>Family is part of the key because §5 reuses the Soul Cage for eleven different cores: "breadth is
 * free and encouraged", and the only thing refused is the exact duplicate.
 */
public record CoreKey(CoreFamily family, Identifier target) {

    public static final Codec<CoreKey> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    CoreFamily.CODEC.fieldOf("family").forGetter(CoreKey::family),
                    Identifier.CODEC.fieldOf("target").forGetter(CoreKey::target))
            .apply(instance, CoreKey::new));
}
