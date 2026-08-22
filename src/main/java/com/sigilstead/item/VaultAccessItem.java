package com.sigilstead.item;

import com.sigilstead.vault.Vault;
import com.sigilstead.vault.VaultAccess;
import com.sigilstead.vault.VaultMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §2.2 — the access ladder as items: the Satchel (deposit only) and the Vault Pouch (adds
 * withdrawal). Both open §2.4's storage tab alone; which verbs the screen offers comes from the
 * {@link VaultAccess} they carry, and the server re-derives it from the menu rather than trusting
 * anything the client says.
 *
 * <p>Both refuse to open at all while the Anchor is dormant, per §2.1's "nothing linked works
 * without an activated Anchor. Funnels go inert, Satchels and Pouches refuse." Refusing with a
 * message beats opening an empty screen the player has to guess about.
 *
 * <p>{@link #canFitInsideContainerItems()} returns {@code false}, which is §2.5's nesting rule from
 * the other direction: a Satchel or Pouch cannot go inside a bundle or a shulker box, so there is no
 * exponential container even before the Vault's own deposit-side check runs.
 */
public class VaultAccessItem extends Item {

    private final VaultAccess access;

    public VaultAccessItem(Properties properties, VaultAccess access) {
        super(properties);
        this.access = access;
    }

    public VaultAccess access() {
        return access;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        if (!Vault.get(serverLevel).activated()) {
            serverPlayer.sendSystemMessage(Component.translatable("gui.sigilstead.vault.dormant"), true);
            return InteractionResult.FAIL;
        }
        serverPlayer.openMenu(new Provider(access));
        return InteractionResult.CONSUME;
    }

    /**
     * The menu type is a Fabric {@code ExtendedMenuType}, which refuses to open unless its provider
     * also implements {@link ExtendedMenuProvider} — so an item opening the Vault needs one of these
     * rather than a plain {@code SimpleMenuProvider}.
     */
    private record Provider(VaultAccess access) implements ExtendedMenuProvider<VaultAccess> {

        @Override
        public Component getDisplayName() {
            return Component.translatable("gui.sigilstead.vault.title");
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return new VaultMenu(containerId, playerInventory, access);
        }

        @Override
        public VaultAccess getScreenOpeningData(ServerPlayer player) {
            return access;
        }
    }
}
