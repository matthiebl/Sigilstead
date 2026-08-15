package com.heartstead.mixin;

import com.heartstead.core.ClassicCore;
import com.heartstead.core.CoreFamily;
import com.heartstead.core.CoreImprint;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DESIGN.md §5 Barter Core — "Milestone: barter with a piglin". Fabric API has no bartering event,
 * so this hooks {@code PiglinAi#stopHoldingOffHandItem}, the one place vanilla itself knows a piglin
 * is about to pay out a gold-ingot barter (26.2 renamed the class but kept the shape; see
 * {@code getBarterResponseItems}, which this deliberately does not duplicate — it only credits the
 * player, the vanilla method still rolls and throws the loot).
 *
 * <p>Credits {@code NEAREST_VISIBLE_PLAYER}, the same memory {@code PiglinAi#throwItems} reads to
 * decide who the response items land in front of — the closest reading of "who this piglin just
 * bartered with" available without a dedicated event.
 */
@Mixin(PiglinAi.class)
abstract class PiglinBarterMixin {

    @Inject(method = "stopHoldingOffHandItem", at = @At("HEAD"))
    private static void heartstead$imprintBarter(ServerLevel level, Piglin body, boolean barteringEnabled, CallbackInfo ci) {
        ItemStack held = body.getItemInHand(InteractionHand.OFF_HAND);
        if (!barteringEnabled || !body.isAdult() || !held.is(PiglinAi.BARTERING_ITEM)) {
            return;
        }

        Optional<Player> nearest = body.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER);
        if (nearest.isPresent() && nearest.get() instanceof ServerPlayer player) {
            CoreImprint.offer(player, CoreFamily.SOUL, ClassicCore.BARTER.target());
        }
    }
}
