package com.sigilstead.config;

import com.sigilstead.core.CoreFamily;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * DESIGN.md §12.7 {@code attunement_thresholds.*} — how many qualifying events each §4.1 family needs
 * before a Primed Core becomes a finished Core. One field per family, defaults straight from §12.4.
 *
 * <p>These are the numbers §4.1 describes as "one session of the activity, not a grind", so they are
 * among the first things playtesting will move.
 */
public record AttunementConfig(int soul, int verdant, int pastoral, int lithic) {

    public static final Codec<AttunementConfig> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.intRange(1, 100000).fieldOf("soul").forGetter(AttunementConfig::soul),
                    Codec.intRange(1, 100000).fieldOf("verdant").forGetter(AttunementConfig::verdant),
                    Codec.intRange(1, 100000).fieldOf("pastoral").forGetter(AttunementConfig::pastoral),
                    Codec.intRange(1, 100000).fieldOf("lithic").forGetter(AttunementConfig::lithic))
            .apply(instance, AttunementConfig::new));

    /** DESIGN.md §12.4: 16 kills, 32 crops, 8 breeds, 64 blocks. */
    public static final AttunementConfig DEFAULT = new AttunementConfig(16, 32, 8, 64);

    public int forFamily(CoreFamily family) {
        return switch (family) {
            case SOUL -> soul;
            case VERDANT -> verdant;
            case PASTORAL -> pastoral;
            case LITHIC -> lithic;
        };
    }
}
