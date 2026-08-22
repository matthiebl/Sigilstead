package com.sigilstead.lives;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Per-player heart count (DESIGN.md §6), stored on the player attachment (CONVENTIONS.md §2.2,
 * §4). Starts at the vanilla 10 hearts; {@link LivesSystem} derives the {@code max_health}
 * attribute modifier from {@link #hearts()} and clamps it to the configured cap and floor.
 *
 * <p>Carries a schema version per CONVENTIONS.md §4 so a future change to this shape has
 * somewhere to migrate from instead of guessing at an unversioned blob.
 */
public record HeartLevel(int version, int hearts) {

    public static final int CURRENT_VERSION = 1;
    public static final int STARTING_HEARTS = 10;

    public static final Codec<HeartLevel> CODEC =
            RecordCodecBuilder.create(instance -> instance
                    .group(
                            Codec.INT.fieldOf("version").forGetter(HeartLevel::version),
                            Codec.INT.fieldOf("hearts").forGetter(HeartLevel::hearts))
                    .apply(instance, HeartLevel::new));

    public static HeartLevel initial() {
        return new HeartLevel(CURRENT_VERSION, STARTING_HEARTS);
    }

    public HeartLevel withHearts(int newHearts) {
        return new HeartLevel(CURRENT_VERSION, newHearts);
    }
}
