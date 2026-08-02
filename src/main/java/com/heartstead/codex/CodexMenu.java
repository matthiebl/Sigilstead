package com.heartstead.codex;

import com.heartstead.network.CodexSyncPayload;
import com.heartstead.registry.HsItems;
import com.heartstead.registry.HsMenuTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.resources.ResourceKey;

/**
 * DESIGN.md §3.3 — the Codex screen handler. Establishes no new pattern beyond what
 * {@link com.heartstead.vault.VaultMenu} already set: real slots for everything a click can move,
 * with the two verbs that need a moment's confirmation (Archive, buy-capacity) gated behind an
 * explicit client intent the same way the Vault's upgrade slots are.
 *
 * <p>Four small slots, not a grid: an archive input, a capacity-upgrade input, a Tome input and a
 * Sealed Tome output. The list of archived enchantments a Tome can be sealed against is not
 * slot-backed — like the Vault's storage grid, it is synced ({@link CodexSyncPayload}) and drawn by
 * the client, because its length depends on how much a player has archived rather than on a fixed
 * layout.
 */
public final class CodexMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;

    private final SimpleContainer archiveContainer = new SimpleContainer(1);
    private final SimpleContainer capacityContainer = new SimpleContainer(1);
    private final SimpleContainer tomeContainer = new SimpleContainer(1);
    private final SimpleContainer sealedOutputContainer = new SimpleContainer(1);

    public CodexMenu(int containerId, Inventory playerInventory) {
        super(HsMenuTypes.CODEX, containerId);
        this.playerInventory = playerInventory;

        addSlot(new ArchiveSlot());
        addSlot(new CapacitySlot());
        addSlot(new TomeSlot());
        addSlot(new SealedTomeOutputSlot());
        addStandardInventorySlots(playerInventory, CodexLayout.PLAYER_INV_X, CodexLayout.PLAYER_INV_Y);

        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            sync(serverPlayer);
        }
    }

    private int firstPlayerSlot() {
        return 4;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, archiveContainer);
        clearContainer(player, capacityContainer);
        clearContainer(player, tomeContainer);
        clearContainer(player, sealedOutputContainer);
    }

    /** DESIGN.md §3.3 Archive — consumes whatever sits in the archive slot, if any of it fits. */
    public void handleArchive(ServerPlayer player) {
        ItemStack stack = archiveContainer.getItem(0);
        if (stack.isEmpty()) {
            return;
        }
        if (Codex.tryArchive(player, stack)) {
            archiveContainer.setItem(0, ItemStack.EMPTY);
            sync(player);
        }
    }

    /** DESIGN.md §3.3/§12.6 — confirms the capacity slot's Echo Shard or Nether Star. */
    public void handleBuyCapacity(ServerPlayer player) {
        ItemStack stack = capacityContainer.getItem(0);
        if (stack.isEmpty()) {
            return;
        }
        if (Codex.tryBuyCapacity(player, stack)) {
            capacityContainer.setItem(0, stack.isEmpty() ? ItemStack.EMPTY : stack);
            sync(player);
        }
    }

    /** DESIGN.md §3.3 Tome — seals the empty Tome in the input slot into a Sealed Tome for {@code enchantment}. */
    public void handleSeal(ServerPlayer player, ResourceKey<Enchantment> enchantment) {
        if (!sealedOutputContainer.getItem(0).isEmpty()) {
            return;
        }
        ItemStack tomeStack = tomeContainer.getItem(0);
        Codex.trySeal(player, tomeStack, enchantment).ifPresent(sealed -> {
            tomeContainer.setItem(0, tomeStack.isEmpty() ? ItemStack.EMPTY : tomeStack);
            sealedOutputContainer.setItem(0, sealed);
        });
    }

    private void sync(ServerPlayer player) {
        ServerPlayNetworking.send(player, CodexSyncPayload.of(Codex.get(player)));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();

        if (index < firstPlayerSlot()) {
            // Out of an input/output slot: back into the player's own inventory, like any container.
            if (!moveItemStackTo(original, firstPlayerSlot(), slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From the player's inventory: offer to whichever input slot will actually take it.
            if (!moveItemStackTo(original, 0, firstPlayerSlot(), false)) {
                return ItemStack.EMPTY;
            }
        }
        slot.setChanged();
        return copy;
    }

    /** Accepts anything carrying at least one enchantment — a book, or a found enchanted item. */
    private final class ArchiveSlot extends Slot {
        ArchiveSlot() {
            super(CodexMenu.this.archiveContainer, 0, CodexLayout.ARCHIVE_SLOT_X, CodexLayout.ARCHIVE_SLOT_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !EnchantmentHelper.getEnchantmentsForCrafting(stack).isEmpty();
        }
    }

    /** Accepts only the next capacity tier's upgrade item — Echo Shard, then Nether Star. */
    private final class CapacitySlot extends Slot {
        CapacitySlot() {
            super(CodexMenu.this.capacityContainer, 0, CodexLayout.CAPACITY_SLOT_X, CodexLayout.CAPACITY_SLOT_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Player player = playerInventory.player;
            return player instanceof ServerPlayer serverPlayer
                    && Codex.nextCapacityUpgradeItem(serverPlayer).map(stack::is).orElse(false);
        }
    }

    private final class TomeSlot extends Slot {
        TomeSlot() {
            super(CodexMenu.this.tomeContainer, 0, CodexLayout.TOME_SLOT_X, CodexLayout.TOME_SLOT_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(HsItems.TOME);
        }
    }

    private final class SealedTomeOutputSlot extends Slot {
        SealedTomeOutputSlot() {
            super(CodexMenu.this.sealedOutputContainer, 0, CodexLayout.SEALED_TOME_SLOT_X, CodexLayout.SEALED_TOME_SLOT_Y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
