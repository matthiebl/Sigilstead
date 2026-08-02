package com.heartstead.block;

import com.heartstead.blockentity.CodexBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * DESIGN.md §3.3 — the Codex block. Unlike the Vault Anchor (§2.1) there is nothing here to claim or
 * lose: every Codex opens the same per-player archive attachment
 * ({@link com.heartstead.registry.HsAttachments#CODEX_ARCHIVE}), so the block is a workbench, not a
 * container, and any number may exist at once.
 */
public class CodexBlock extends BaseEntityBlock {

    public static final MapCodec<CodexBlock> CODEC = simpleCodec(CodexBlock::new);

    public CodexBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CodexBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof MenuProvider provider) {
            player.openMenu(provider);
        }
        return InteractionResult.CONSUME;
    }
}
