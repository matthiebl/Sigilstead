package com.sigilstead.mixin;

import com.sigilstead.item.SealedTomeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DESIGN.md §3.3 Teach — {@code Player#interactOn} calls {@code entity.interact(...)} before it ever
 * tries the held item's own {@code interactLivingEntity}, and for any adult villager with offers that
 * resolves to {@link Villager#mobInteract} opening the trade screen and returning a <em>consuming</em>
 * result. That means a plain {@code Item#interactLivingEntity} override on
 * {@link com.sigilstead.item.SealedTomeItem} is unreachable — {@code mobInteract} always wins the
 * race. Injecting at its head, cancellable, is what lets {@link SealedTomeItem#tryTeach} run first.
 */
@Mixin(Villager.class)
abstract class SealedTomeTeachMixin {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void sigilstead$tryTeach(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        SealedTomeItem.tryTeach(serverPlayer, (Villager) (Object) this, hand).ifPresent(cir::setReturnValue);
    }
}
