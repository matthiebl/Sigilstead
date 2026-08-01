package com.heartstead.item;

import com.heartstead.registry.HsBlocks;
import com.heartstead.vault.Vault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/**
 * DESIGN.md §2.1 — refuses to place a second Vault Anchor with a clear message, the same shape as
 * vanilla's build-height check (see {@code ServerPlayerGameMode}/{@code ServerPlayer#sendBuildLimitMessage}):
 * checked and messaged *before* the placement pipeline runs, so nothing is consumed and there's an
 * explicit reason shown rather than a silent refusal.
 *
 * <p>{@link com.heartstead.block.VaultAnchorBlock#canSurvive} still vetoes at the block-state level
 * too — that's the correctness guarantee (covers dispensers, other mods, anything that doesn't go
 * through this item), this class is purely the player-facing message layered on top of it. A
 * one-frame "places then vanishes" flicker can still happen from the client's own placement
 * prediction, which has no visibility into the world's Vault Anchor position (it's save data, not
 * block state) to predict the refusal in advance — the same thing happens placing any vanilla block
 * somewhere it can't survive.
 */
public class VaultAnchorBlockItem extends BlockItem {

    public VaultAnchorBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel serverLevel
                && otherAnchorStands(serverLevel, context.getClickedPos())) {
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendSystemMessage(Component.translatable("item.heartstead.vault_anchor.already_exists"), true);
            }
            return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    private static boolean otherAnchorStands(ServerLevel level, BlockPos placingAt) {
        return Vault.anchorStandsElsewhere(level, placingAt, HsBlocks.VAULT_ANCHOR);
    }
}
