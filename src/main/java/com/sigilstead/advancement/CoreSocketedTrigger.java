package com.sigilstead.advancement;

import com.sigilstead.core.CoreFamily;
import com.sigilstead.core.CoreTier;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.MinMaxBounds;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * DESIGN.md §7.3 — fires when a §4.2 housing accepts an attuned core, carrying which family and
 * which §4.3 tier went in.
 *
 * <p>Tier is {@link MinMaxBounds.Ints} over the tier's <em>level</em> rather than an enum, because
 * the advancement that matters ("a tier III core") is a threshold, and a threshold written as
 * {@code {"min": 3}} keeps working if §4.3 ever grows a fourth rung.
 */
public class CoreSocketedTrigger extends SimpleCriterionTrigger<CoreSocketedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, CoreFamily family, CoreTier tier) {
        trigger(player, instance -> instance.matches(family, tier));
    }

    public record TriggerInstance(
            Optional<ContextAwarePredicate> player, Optional<CoreFamily> family, MinMaxBounds.Ints tier)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        CoreFamily.CODEC.optionalFieldOf("family").forGetter(TriggerInstance::family),
                        MinMaxBounds.Ints.CODEC
                                .optionalFieldOf("tier", MinMaxBounds.Ints.ANY)
                                .forGetter(TriggerInstance::tier))
                .apply(instance, TriggerInstance::new));

        boolean matches(CoreFamily socketed, CoreTier socketedTier) {
            if (family.isPresent() && family.get() != socketed) {
                return false;
            }
            return tier.matches(socketedTier.level());
        }
    }
}
