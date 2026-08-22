package com.sigilstead.mixin;

import com.sigilstead.villager.TaughtTradeInjector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN.md §7.2 — vanilla regenerates {@code Offers} on level-up ({@code updateTrades}) and on
 * restock. Both are hooked so a taught trade survives either.
 */
@Mixin(Villager.class)
abstract class VillagerTradePersistenceMixin {

    @Inject(method = "updateTrades", at = @At("TAIL"))
    private void sigilstead$reapplyOnUpdateTrades(ServerLevel level, CallbackInfo ci) {
        TaughtTradeInjector.ensureOfferPresent((Villager) (Object) this);
    }

    @Inject(method = "restock", at = @At("TAIL"))
    private void sigilstead$reapplyOnRestock(CallbackInfo ci) {
        TaughtTradeInjector.ensureOfferPresent((Villager) (Object) this);
    }
}
