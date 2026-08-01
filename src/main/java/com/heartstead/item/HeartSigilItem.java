package com.heartstead.item;

import com.heartstead.lives.LivesSystem;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;

/**
 * DESIGN.md §1 / §6 — the Heart Sigil. Right-click to raise max health by one heart, up to the
 * configured cap (§12.6). Instant — no eating animation — matching Totem of Undying's use pattern
 * rather than food.
 */
public class HeartSigilItem extends Item {

    public HeartSigilItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.CONSUME;
        }

        if (!LivesSystem.consumeHeartSigil(serverPlayer)) {
            return InteractionResult.FAIL;
        }

        player.getItemInHand(hand).consume(1, player);
        return InteractionResult.SUCCESS_SERVER;
    }
}
