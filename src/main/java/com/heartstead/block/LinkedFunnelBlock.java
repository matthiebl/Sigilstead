package com.heartstead.block;

import com.heartstead.blockentity.LinkedFunnelBlockEntity;
import com.heartstead.registry.HsBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * DESIGN.md §2.1 — the Linked Funnel: a hopper-speed automation link that either feeds the Vault
 * from whatever sits above it, or dispenses one configured item out of the Vault into whatever sits
 * below it. Which of the two is a block state, so the mode is visible in the world and known to the
 * client without a sync packet.
 *
 * <p><b>The §2.0 rule applies here unchanged.</b> Input mode is deposit and works from anywhere;
 * output mode is a withdrawal and needs the reach tier covering where the Funnel itself stands. That
 * falls straight out of {@link com.heartstead.vault.VaultReach} and needed no separate concept — the
 * Funnel is just another caller.
 *
 * <p><b>No screen, deliberately.</b> Right-clicking with an empty hand cycles the mode and
 * right-clicking with an item sets the output filter to it. CLAUDE.md's "capability is not a
 * mandate" applies: a two-field configuration does not earn a screen handler, and the chat feedback
 * tells the player what happened.
 */
public class LinkedFunnelBlock extends BaseEntityBlock {

    public static final MapCodec<LinkedFunnelBlock> CODEC = simpleCodec(LinkedFunnelBlock::new);

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    public LinkedFunnelBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(MODE, Mode.INPUT));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(MODE);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkedFunnelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, HsBlockEntities.LINKED_FUNNEL, LinkedFunnelBlockEntity::serverTick);
    }

    /** Empty hand cycles the mode; see the class javadoc for why there is no screen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        Mode next = state.getValue(MODE) == Mode.INPUT ? Mode.OUTPUT : Mode.INPUT;
        level.setBlockAndUpdate(pos, state.setValue(MODE, next));
        tell(player, Component.translatable("block.heartstead.linked_funnel.mode." + next.getSerializedName()));
        return InteractionResult.CONSUME;
    }

    /**
     * Right-clicking with an item sets the output filter to that item's type. Nothing is consumed —
     * the Funnel stores a type, not a stack, so charging the player a real item for naming one would
     * be a tax with no purpose.
     */
    @Override
    protected InteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof LinkedFunnelBlockEntity funnel)) {
            return InteractionResult.PASS;
        }
        funnel.setFilter(stack.getItem());
        tell(player, Component.translatable("block.heartstead.linked_funnel.filter_set", stack.getHoverName()));
        return InteractionResult.CONSUME;
    }

    /** Action-bar feedback, which is the Funnel's entire UI. */
    private static void tell(Player player, Component message) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    /** Which direction the Funnel moves items relative to the Vault. */
    public enum Mode implements StringRepresentable {
        /** Pulls from the container above and deposits — §2.0's free verb, works anywhere. */
        INPUT("input"),
        /** Withdraws the filtered item and pushes into the container below — reach-gated. */
        OUTPUT("output");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    /** Convenience for the block entity, which needs to know whether it is running as a source or a sink. */
    public static boolean isOutput(BlockState state) {
        return state.hasProperty(MODE) && state.getValue(MODE) == Mode.OUTPUT;
    }

}
