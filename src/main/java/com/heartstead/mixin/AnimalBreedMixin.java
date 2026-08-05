package com.heartstead.mixin;

import com.heartstead.core.CoreImprint;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN.md §4.1 Pastoral — "breed two animals". Fabric API has no breeding event in 26.2, so the
 * imprint hooks {@code Animal#finalizeSpawnChildFromBreeding}, which is the narrow window where both
 * facts this needs are still true: a child has definitely been produced (the caller returns early if
 * {@code getBreedOffspring} yielded nothing), and the love cause has not yet been cleared — clearing
 * it is the first thing that method goes on to do, which is why the later
 * {@code spawnChildFromBreeding} tail is too late to read it.
 *
 * <p>Credit goes to the player who fed the parents, checking the partner as a fallback because only
 * one of the two carries the cause when a single player fed both. A breed with no love cause at all
 * (a datapack, a command) imprints nothing — §4.1 rewards the activity, and nobody did it.
 */
@Mixin(Animal.class)
abstract class AnimalBreedMixin {

    @Inject(method = "finalizeSpawnChildFromBreeding", at = @At("HEAD"))
    private void heartstead$imprintPastoral(ServerLevel level, Animal partner, AgeableMob child, CallbackInfo ci) {
        Animal self = (Animal) (Object) this;
        ServerPlayer breeder = self.getLoveCause() != null ? self.getLoveCause() : partner.getLoveCause();
        if (breeder != null) {
            CoreImprint.onBred(breeder, self);
        }
    }
}
