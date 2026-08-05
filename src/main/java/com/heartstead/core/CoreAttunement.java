package com.heartstead.core;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * DESIGN.md §4.1 — a core's per-stack state, registered as the {@code heartstead:attunement} data
 * component (CONVENTIONS.md §2.2, §4). Family is fixed at prime time; target and progress are written
 * by playing.
 *
 * <p>§4.1: "Progress lives on the stack. Drop it, chest it, die with it — the counter is a data
 * component, so it survives everything a stack survives. There is no player-side counter to desync."
 * That is the whole reason this is a component and not an attachment: the counter belongs to the
 * item, and two players can carry two half-attuned cores of the same family without interfering.
 *
 * <p>An <b>un-attuned</b> core has {@code target} empty and {@code progress} 0 — §4.1's "sitting in
 * the inventory it is inert" case, which only a main-hand or offhand event may lock. Once
 * {@code target} is present the core counts from anywhere in the inventory.
 *
 * <p>Carries a schema version from day one (CONVENTIONS.md §4). It reads as 1 when absent, so a
 * component written before a future field was added still loads.
 */
public record CoreAttunement(int version, CoreFamily family, Optional<Identifier> target, int progress, CoreTier tier) {

    public static final int CURRENT_VERSION = 1;

    public static final Codec<CoreAttunement> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.optionalFieldOf("version", CURRENT_VERSION).forGetter(CoreAttunement::version),
                    CoreFamily.CODEC.fieldOf("family").forGetter(CoreAttunement::family),
                    Identifier.CODEC.optionalFieldOf("target").forGetter(CoreAttunement::target),
                    Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("progress", 0).forGetter(CoreAttunement::progress),
                    Codec.intRange(1, 3).optionalFieldOf("tier", 1).xmap(CoreTier::ofLevel, CoreTier::level)
                            .forGetter(CoreAttunement::tier))
            .apply(instance, CoreAttunement::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreAttunement> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CoreAttunement::version,
            CoreFamily.STREAM_CODEC, CoreAttunement::family,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), CoreAttunement::target,
            ByteBufCodecs.VAR_INT, CoreAttunement::progress,
            ByteBufCodecs.VAR_INT.map(CoreTier::ofLevel, CoreTier::level), CoreAttunement::tier,
            CoreAttunement::new);

    /** §4.1 step 1 — what a prime recipe stamps onto its output: family fixed, target null, progress 0. */
    public static CoreAttunement primed(CoreFamily family) {
        return new CoreAttunement(CURRENT_VERSION, family, Optional.empty(), 0, CoreTier.ONE);
    }

    /** §4.1 — true while no qualifying event has locked a target yet. Only these lock from the hand. */
    public boolean unattuned() {
        return target.isEmpty();
    }

    /** §4.1 — the first qualifying event: writes the target and counts as progress 1. */
    public CoreAttunement lockTo(Identifier newTarget) {
        return new CoreAttunement(CURRENT_VERSION, family, Optional.of(newTarget), 1, tier);
    }

    /** §4.1 — a later event matching the locked target. */
    public CoreAttunement advanced() {
        return new CoreAttunement(CURRENT_VERSION, family, target, progress + 1, tier);
    }

    /** §4.3 — the same core one rate tier higher. Target and progress are untouched. */
    public CoreAttunement atTier(CoreTier newTier) {
        return new CoreAttunement(CURRENT_VERSION, family, target, progress, newTier);
    }

    /** True when {@code progress} has reached {@code threshold} and the Primed Core becomes a Core. */
    public boolean complete(int threshold) {
        return target.isPresent() && progress >= threshold;
    }

    /** §4.1 — whether {@code candidate} is the target this core already counts, for the parallel-progress rule. */
    public boolean targets(Identifier candidate) {
        return target.isPresent() && target.get().equals(candidate);
    }

    /** The §4.3 world-registry key this core claims when socketed, or empty while un-attuned. */
    public Optional<CoreKey> key() {
        return target.map(t -> new CoreKey(family, t));
    }
}
