package com.heartstead.block;

import com.heartstead.blockentity.VaultAnchorBlockEntity;
import com.heartstead.vault.ClientVaultAnchorCache;
import com.heartstead.vault.Vault;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * DESIGN.md §2.1 — creates the world's Vault and opens its screen (§2.4). Exactly one may stand at
 * a time: {@link #canSurvive} refuses to let a second Anchor's placement ever commit while a
 * different one already stands, and {@link #onPlace} claims the {@link Vault}'s anchor position once
 * placement succeeds. Breaking the standing Anchor and placing a fresh one re-claims the position —
 * there is no separate "unclaim" step, so this is checked live against the world rather than a flag
 * that could drift out of sync.
 *
 * <p>The veto lives in {@code canSurvive}, not in {@code onPlace} calling
 * {@code Level#destroyBlock} on itself — mutating the world for the position a block is still in the
 * middle of being placed at is reentrant and, in testing, hung the server on the next save. Vetoing
 * before the placement commits is the standard, safe pattern (the same one torches and saplings use).
 */
public class VaultAnchorBlock extends BaseEntityBlock {

    public static final MapCodec<VaultAnchorBlock> CODEC = simpleCodec(VaultAnchorBlock::new);

    public VaultAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VaultAnchorBlockEntity(pos, state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel && otherAnchorStands(serverLevel, pos)) {
            return false;
        }
        // The client has no view of the server-only SavedData claim, so without this it always
        // predicts a successful placement and gets corrected afterward — the "places then vanishes"
        // flicker. ClientVaultAnchorCache (synced via VaultAnchorPayload) lets it veto up front
        // instead, the same way it already does for the world height limit using local level data.
        if (level.isClientSide()) {
            Optional<BlockPos> anchor = ClientVaultAnchorCache.get();
            if (anchor.isPresent() && !anchor.get().equals(pos)) {
                return false;
            }
        }
        return super.canSurvive(state, level, pos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (oldState.is(state.getBlock()) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!otherAnchorStands(serverLevel, pos)) {
            Vault.claimAnchor(serverLevel, pos);
        }
    }

    /** True if a Vault Anchor other than the one at {@code pos} is currently standing in the world. */
    private boolean otherAnchorStands(ServerLevel level, BlockPos pos) {
        Optional<BlockPos> anchor = Vault.anchorPos(level);
        return anchor.isPresent() && !anchor.get().equals(pos) && level.getBlockState(anchor.get()).is(this);
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
