package com.sigilstead.block;

import com.sigilstead.blockentity.CoreHousingBlockEntity;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.registry.HsBlockEntities;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * DESIGN.md §4.2 — the four housings: Soul Cage, Verdant Planter, Paddock, Quarry Node. One block
 * class, four registrations, because the only difference between them is the family they accept.
 *
 * <p>§4.2: "The housing supplies the family; the core supplies the rate and the loot table. A §5 core
 * overrides the §4.2 base rate with its own. That is why eleven classic farm replacements need no new
 * blocks." Keeping the family on the block — and everything else on the core — is what makes that
 * true rather than aspirational.
 *
 * <p>Housings are deliberately cheap and deliberately unhardened (§4.2: "the Core is the cost, and it
 * already ate a Sigil"), so unlike the Vault Anchor there is no explosion resistance, no push
 * reaction and no immunity tag here. Breaking one drops both the block and everything inside it.
 */
public class CoreHousingBlock extends BaseEntityBlock {

    public static final MapCodec<CoreHousingBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    propertiesCodec(),
                    CoreFamily.CODEC.fieldOf("family").forGetter(CoreHousingBlock::family))
            .apply(instance, CoreHousingBlock::new));

    private final CoreFamily family;

    public CoreHousingBlock(Properties properties, CoreFamily family) {
        super(properties);
        this.family = family;
    }

    /** §4.2 — the one core family this housing hosts. */
    public CoreFamily family() {
        return family;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoreHousingBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return createTickerHelper(type, HsBlockEntities.CORE_HOUSING, CoreHousingBlockEntity::serverTick);
    }

    /**
     * Opens the §4.2 socket screen. Settles first, so what a returning player sees in the buffer is
     * what their absence actually earned — §4.2's "on chunk load — and on any interaction".
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof CoreHousingBlockEntity housing) {
            if (level instanceof ServerLevel serverLevel) {
                housing.settle(serverLevel);
            }
            player.openMenu((MenuProvider) housing);
        }
        return InteractionResult.CONSUME;
    }
}
