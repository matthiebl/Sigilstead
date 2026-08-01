package com.heartstead.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * One row of the DESIGN.md §12.1 Sigil drop table: a chance and the amount rolled when it hits.
 *
 * <p>A single shape for chests and mobs alike, so §12.1 reads as one table in the config file
 * instead of a chance field and a count field per source (CONVENTIONS.md §5).
 */
public record SigilDrop(double chance, int min, int max) {

    public static final Codec<SigilDrop> CODEC =
            RecordCodecBuilder.create(
                    instance ->
                            instance
                                    .group(
                                            Codec.doubleRange(0.0, 1.0)
                                                    .fieldOf("chance")
                                                    .forGetter(SigilDrop::chance),
                                            Codec.intRange(0, 64)
                                                    .fieldOf("min")
                                                    .forGetter(SigilDrop::min),
                                            Codec.intRange(0, 64)
                                                    .fieldOf("max")
                                                    .forGetter(SigilDrop::max))
                                    .apply(instance, SigilDrop::new));

    /** A row that always rolls exactly {@code amount}. */
    public static SigilDrop always(int amount) {
        return new SigilDrop(1.0, amount, amount);
    }

    /** A row that rolls exactly one Sigil at {@code chance}. */
    public static SigilDrop one(double chance) {
        return new SigilDrop(chance, 1, 1);
    }

    /** Never rolls. Used to switch a source off without deleting the field. */
    public boolean isDisabled() {
        return chance <= 0.0 || max <= 0;
    }
}
