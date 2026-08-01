package com.heartstead.item;

import com.heartstead.lives.LivesSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

/**
 * DESIGN.md §6: right-click to raise max health by one heart, up to the configured cap. Instant —
 * no eating animation — matching Totem of Undying's use pattern rather than food.
 */
public class LifeHeartItem extends Item {

    public LifeHeartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        if (!LivesSystem.consumeLifeHeart(serverPlayer)) {
            return InteractionResult.FAIL;
        }

        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }
}
