package com.heartstead.vault;

import com.heartstead.network.VaultSyncPayload;
import com.heartstead.registry.HsMenuTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §2.4 — the Vault screen handler. Establishes the block + block entity + screen handler
 * pattern the Codex and core screens (a later phase) copy.
 *
 * <p>Only holds real {@link net.minecraft.world.inventory.Slot}s for the player's own inventory —
 * the Vault side is not slot-backed. §2.3 allows hundreds of distinct types shown through a live
 * search filter, which doesn't map onto vanilla's fixed slot-click protocol, so the Vault grid is
 * client-drawn from a synced snapshot ({@link VaultSyncPayload}) and driven by intents
 * ({@link com.heartstead.network.VaultWithdrawPayload}) instead — "the screen sends intents, never
 * results" (docs/PROMPTS.md). All mutation happens here or in {@link Vault}, never on the client.
 */
public final class VaultMenu extends AbstractContainerMenu {

    /** Top-left of the player-inventory slot grid, shared with the client screen's layout. */
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 140;

    private final Inventory playerInventory;
    private int lastSyncedRevision = -1;

    public VaultMenu(int containerId, Inventory playerInventory) {
        super(HsMenuTypes.VAULT, containerId);
        this.playerInventory = playerInventory;
        addStandardInventorySlots(playerInventory, PLAYER_INV_X, PLAYER_INV_Y);

        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            syncTo(serverPlayer, true);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Shift-click deposit (DESIGN.md §2.4): a click that would normally move a stack between the
     * container and the player's own inventory instead deposits it into the Vault, since this menu
     * has no container side to move it to. Routes through {@link Vault#deposit}, the same
     * §2.5-tested transfer code the withdraw path uses — a second item-moving path is how the Vault
     * gets a dupe bug.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }
        var slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack original = slot.getItem();
        ItemStack copy = original.copy();
        ItemStack leftover = Vault.deposit(serverLevel, original);
        slot.set(leftover);
        slot.setChanged();

        // Vanilla's shift-click handler (AbstractContainerMenu#clicked) calls quickMoveStack in a
        // loop until it returns EMPTY or the slot stops matching the returned stack — that's its
        // only termination signal. A capped Vault refusing a deposit leaves the slot unchanged, so
        // returning a non-empty "copy" here regardless of progress spins that loop forever.
        boolean depositedNothing = leftover.getCount() == original.getCount();
        return depositedNothing ? ItemStack.EMPTY : copy;
    }

    /**
     * Server-side handling of a {@link com.heartstead.network.VaultWithdrawPayload} intent. Never
     * trusts the client's count: it's clamped to both live Vault stock and the player's free
     * inventory space by {@link Vault#withdrawInto}, so a full inventory or a stale client snapshot
     * can't lose or duplicate anything.
     */
    public void handleWithdraw(ServerPlayer player, Item item, int count) {
        if (count <= 0) {
            return;
        }
        ServerLevel level = player.level();
        int available = Vault.get(level).count(item);
        if (available <= 0) {
            return;
        }
        int withdrawn = Vault.withdrawInto(level, item, count, player.getInventory());
        if (withdrawn == 0) {
            player.sendSystemMessage(Component.translatable("gui.heartstead.vault.inventory_full"), true);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            syncTo(serverPlayer, false);
        }
    }

    private void syncTo(ServerPlayer serverPlayer, boolean force) {
        VaultData vault = Vault.get(serverPlayer.level());
        int revision = vault.revision();
        if (!force && revision == lastSyncedRevision) {
            return;
        }
        lastSyncedRevision = revision;
        ServerPlayNetworking.send(serverPlayer, VaultSyncPayload.of(vault.contents()));
    }
}
