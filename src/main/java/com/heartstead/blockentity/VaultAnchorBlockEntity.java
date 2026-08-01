package com.heartstead.blockentity;

import com.heartstead.registry.HsBlockEntities;
import com.heartstead.vault.VaultMenu;
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
 * DESIGN.md §2.1 / §2.4 — opens the shared, world-level {@link com.heartstead.vault.Vault} screen.
 * Holds no items itself: the Vault's contents live in {@code VaultData} (world {@code SavedData}),
 * not here, so this block entity carries nothing to persist beyond its position and state.
 *
 * <p>{@link com.heartstead.registry.HsMenuTypes#VAULT} is registered as a Fabric
 * {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuType}, which refuses to open (throwing at
 * runtime, not compile time — see the crash this class used to cause) unless its
 * {@link net.minecraft.world.MenuProvider} also implements {@link ExtendedMenuProvider}. The Vault
 * needs no real opening data, so this just hands back {@link Unit#INSTANCE}.
 */
public class VaultAnchorBlockEntity extends BlockEntity implements ExtendedMenuProvider<Unit> {

    public VaultAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(HsBlockEntities.VAULT_ANCHOR, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.heartstead.vault_anchor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VaultMenu(containerId, playerInventory);
    }

    @Override
    public Unit getScreenOpeningData(ServerPlayer player) {
        return Unit.INSTANCE;
    }
}
