package com.sigilstead.blockentity;

import com.sigilstead.registry.HsBlockEntities;
import com.sigilstead.vault.VaultAccess;
import com.sigilstead.vault.VaultMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * DESIGN.md §2.1 / §2.4 — opens the shared, world-level {@link com.sigilstead.vault.Vault} screen.
 * Holds no items itself: the Vault's contents live in {@code VaultData} (world {@code SavedData}),
 * not here, so this block entity carries nothing to persist beyond its position and state.
 *
 * <p>{@link com.sigilstead.registry.HsMenuTypes#VAULT} is registered as a Fabric
 * {@link net.fabricmc.fabric.api.menu.v1.ExtendedMenuType}, which refuses to open (throwing at
 * runtime, not compile time — see the crash this class used to cause) unless its
 * {@link net.minecraft.world.MenuProvider} also implements {@link ExtendedMenuProvider}. The opening
 * data is {@link VaultAccess#ANCHOR} — the Anchor is the only place §2.4's tab 1 exists.
 */
public class VaultAnchorBlockEntity extends BlockEntity implements ExtendedMenuProvider<VaultAccess> {

    public VaultAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(HsBlockEntities.VAULT_ANCHOR, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.sigilstead.vault_anchor");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VaultMenu(containerId, playerInventory, VaultAccess.ANCHOR);
    }

    @Override
    public VaultAccess getScreenOpeningData(ServerPlayer player) {
        return VaultAccess.ANCHOR;
    }
}
