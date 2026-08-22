package com.sigilstead.blockentity;

import com.sigilstead.codex.CodexMenu;
import com.sigilstead.registry.HsBlockEntities;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DESIGN.md §3.3 — opens the per-player {@link com.sigilstead.codex.Codex} screen. Holds no items and
 * no per-block state at all: the archive lives on the opening player's own attachment
 * ({@link com.sigilstead.registry.HsAttachments#CODEX_ARCHIVE}), so any Codex a player walks up to
 * shows the same thing, and breaking this block loses nothing.
 */
public class CodexBlockEntity extends BlockEntity implements ExtendedMenuProvider<Unit> {

    public CodexBlockEntity(BlockPos pos, BlockState state) {
        super(HsBlockEntities.CODEX, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.sigilstead.codex");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CodexMenu(containerId, playerInventory);
    }

    @Override
    public Unit getScreenOpeningData(ServerPlayer player) {
        return Unit.INSTANCE;
    }
}
