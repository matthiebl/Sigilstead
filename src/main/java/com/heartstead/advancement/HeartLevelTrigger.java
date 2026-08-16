package com.heartstead.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * DESIGN.md §7.3 — fires whenever §6 recomputes a player's heart count, in either direction.
 *
 * <p>Counted in <b>hearts</b>, not half-hearts, because that is the unit §6 and §12.6 are written in
 * and the unit the advancement text says out loud. The cap advancement is {@code {"min": 20}}.
 *
 * <p>Firing on losses too is deliberate: it costs nothing, and it means a future "lost a heart"
 * advancement needs no new trigger. Advancements are never revoked, so a criterion that once matched
 * stays matched — dying does not take the recognition back, which is the right behaviour for a
 * milestone.
 */
public class HeartLevelTrigger extends SimpleCriterionTrigger<HeartLevelTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, int hearts) {
        trigger(player, instance -> instance.matches(hearts));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, MinMaxBounds.Ints hearts)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        MinMaxBounds.Ints.CODEC
                                .optionalFieldOf("hearts", MinMaxBounds.Ints.ANY)
                                .forGetter(TriggerInstance::hearts))
                .apply(instance, TriggerInstance::new));

        boolean matches(int current) {
            return hearts.matches(current);
        }
    }
}
