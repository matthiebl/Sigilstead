package com.sigilstead.advancement;

import com.sigilstead.vault.VaultUpgradeKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.predicates.ContextAwarePredicate;
import net.minecraft.advancements.predicates.entity.EntityPredicate;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * DESIGN.md §7.3 — fires when §2.3's confirm button actually spends a Sigil at the Anchor.
 *
 * <p>The {@code kind} field is what lets the three reach tiers be three separate advancements
 * without three separate triggers: §2.3's ladders differ only in which slot was confirmed, so the
 * criterion carries the slot rather than the tree carrying the distinction. Omitting it means "any
 * upgrade", which is what the capacity chain wants once it stops caring which.
 */
public class VaultUpgradedTrigger extends SimpleCriterionTrigger<VaultUpgradedTrigger.TriggerInstance> {

    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer player, VaultUpgradeKind kind) {
        trigger(player, instance -> instance.matches(kind));
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<VaultUpgradeKind> kind)
            implements SimpleCriterionTrigger.SimpleInstance {

        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance
                .group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player),
                        VaultUpgradeKind.CODEC.optionalFieldOf("kind").forGetter(TriggerInstance::kind))
                .apply(instance, TriggerInstance::new));

        boolean matches(VaultUpgradeKind bought) {
            return kind.isEmpty() || kind.get() == bought;
        }
    }
}
