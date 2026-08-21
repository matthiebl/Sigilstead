package com.heartstead.vault;

import com.heartstead.advancement.HsTriggers;
import com.heartstead.config.HsConfigManager;
import com.heartstead.network.VaultStatePayload;
import com.heartstead.network.VaultSyncPayload;
import com.heartstead.registry.HsMenuTypes;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §2.4 — the Vault screen handler. Establishes the block + block entity + screen handler
 * pattern the Codex and core screens (a later phase) copy.
 *
 * <p>The Vault grid is not slot-backed: §2.3 allows hundreds of distinct types shown through a live
 * search filter, which doesn't map onto vanilla's fixed slot-click protocol, so it is client-drawn
 * from a synced snapshot ({@link VaultSyncPayload}) and driven by intents. The player's own
 * inventory and the four §2.3 upgrade slots <em>are</em> real slots, at {@link VaultLayout}'s
 * coordinates.
 *
 * <p><b>Two tabs (§2.4), tracked server-side.</b> The client asks to switch and the server records
 * it, because the upgrade slots' {@link Slot#isActive()} depends on it — slots left active under the
 * storage grid would swallow clicks meant for a stored stack. Tab 2 is the default: storage is what
 * a player opens the Vault for, and tab 1 is where they go deliberately.
 *
 * <p><b>Upgrades are slots, not buttons</b> — one per {@link VaultUpgradeKind}, the same shape as
 * every interactive block in vanilla.
 */
public final class VaultMenu extends AbstractContainerMenu {

    private final Inventory playerInventory;
    private final VaultAccess access;

    /** §2.3's upgrade slots, one per {@link VaultUpgradeKind}, in declaration order. */
    private final SimpleContainer upgradeContainer = new SimpleContainer(VaultUpgradeKind.values().length);

    private boolean anchorTabOpen;
    private int lastSyncedRevision = -1;
    private boolean lastSyncedReach;

    public VaultMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, VaultAccess.ANCHOR);
    }

    public VaultMenu(int containerId, Inventory playerInventory, VaultAccess access) {
        super(HsMenuTypes.VAULT, containerId);
        this.playerInventory = playerInventory;
        this.access = access;

        VaultUpgradeKind[] kinds = VaultUpgradeKind.values();
        for (int i = 0; i < kinds.length; i++) {
            addSlot(new UpgradeSlot(upgradeContainer, i, kinds[i]));
        }
        addStandardInventorySlots(playerInventory, VaultLayout.PLAYER_INV_X, VaultLayout.PLAYER_INV_Y);

        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            syncTo(serverPlayer, true);
        }
    }

    public VaultAccess access() {
        return access;
    }

    public boolean anchorTabOpen() {
        return anchorTabOpen;
    }

    /**
     * Client intent — see the class javadoc for why the server needs to know which tab is showing.
     * Also called directly on the client so its own copy of the menu agrees; {@code isActive()} is
     * evaluated on both sides, and a client that only told the server would draw upgrade slots it
     * refused to let the player click.
     */
    public void handleSetTab(boolean anchorTab) {
        this.anchorTabOpen = anchorTab && access.hasAnchorTab();
    }

    /** The index of the first player-inventory slot, i.e. one past the upgrade slots. */
    private int firstPlayerSlot() {
        return VaultUpgradeKind.values().length;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Anything left in an upgrade slot goes back to the player rather than evaporating on close. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, upgradeContainer);
    }

    /**
     * §2.3 / §2.4 — the upgrade slots' whole behaviour. Dropping a Sigil into a slot only <em>arms</em>
     * it; nothing is spent and nothing is bought until {@link #handleConfirmUpgrade} fires, the same
     * two-step shape as an enchanting table's confirm click. Nothing needs to happen here on its own —
     * placement is just a real slot filling in, which vanilla's own slot sync already broadcasts to
     * the client.
     *
     * <p>Plain Vault Sigils are spent greedily on confirm because every one buys something (activation
     * first if the Vault is dormant, then capacity, which §12.3 gives no ceiling). A dimensional Sigil
     * spends exactly one, because a second buys nothing — the rest stay in the slot rather than being
     * silently eaten.
     */
    public void handleConfirmUpgrade(ServerPlayer player, VaultUpgradeKind kind) {
        if (!access.hasAnchorTab() || !anchorTabOpen) {
            return;
        }
        int index = kind.ordinal();
        ItemStack stack = upgradeContainer.getItem(index);
        if (stack.isEmpty() || !stack.is(kind.sigil())) {
            return;
        }

        ServerLevel level = player.level();
        if (kind == VaultUpgradeKind.CAPACITY) {
            boolean wasActivated = Vault.get(level).activated();
            int spentBefore = Vault.get(level).sigilsSpent();
            spendPlainSigils(level, stack);
            // §7.3 recognition only. Split so a Sigil that only paid for reactivation doesn't
            // report a capacity purchase it did not make.
            if (!wasActivated && Vault.get(level).activated()) {
                HsTriggers.VAULT_ACTIVATED.trigger(player);
            }
            if (Vault.get(level).sigilsSpent() > spentBefore) {
                HsTriggers.VAULT_UPGRADED.trigger(player, kind);
            }
        } else if (!kind.isSatisfied(Vault.get(level)) && Vault.get(level).activated()) {
            Vault.grantReach(level, kind.dimension());
            stack.shrink(1);
            HsTriggers.VAULT_UPGRADED.trigger(player, kind);
        }
        upgradeContainer.setItem(index, stack.isEmpty() ? ItemStack.EMPTY : stack);
    }

    private void spendPlainSigils(ServerLevel level, ItemStack stack) {
        VaultData vault = Vault.get(level);

        if (!vault.activated()) {
            int cost = vault.everActivated() ? HsConfigManager.get().vault().reactivationSigils() : 0;
            if (stack.getCount() < cost) {
                return;
            }
            stack.shrink(cost);
            Vault.activateForFree(level);
        }

        // Capacity has no ceiling (§12.3), so every remaining Sigil in the slot buys a step.
        int remaining = stack.getCount();
        for (int i = 0; i < remaining; i++) {
            vault.spendSigil();
        }
        stack.shrink(remaining);
    }

    /**
     * §2.1 — the one tab-1 verb that is still a button, because the two cases it covers have no item
     * to put in a slot: the world's free first activation, and the anti-softlock fallback that
     * spends a Sigil already inside the Vault. Refused unless this menu was opened at the Anchor.
     */
    public void handleActivate(ServerPlayer player) {
        if (!access.hasAnchorTab()) {
            return;
        }
        if (Vault.tryActivate(player.level(), player)) {
            HsTriggers.VAULT_ACTIVATED.trigger(player);
        } else {
            player.sendSystemMessage(Component.translatable("gui.heartstead.vault.need_vault_sigil"), true);
        }
    }

    /**
     * §2.4 — deposit the stack the player is holding on the cursor by clicking anywhere in the Vault
     * grid, the way you would drop it into a chest slot. The grid has no real slots to click, so this
     * is the intent that stands in for that gesture; right-click deposits one, matching vanilla.
     *
     * <p>Goes through {@link Vault#deposit} like every other transfer, and only shrinks the carried
     * stack by what the Vault actually took.
     */
    public void handleDepositCarried(ServerPlayer player, boolean single) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        if (!VaultReach.canDepositAt(level, player.blockPosition()) || !Vault.isStorable(carried)) {
            return;
        }

        int offered = single ? 1 : carried.getCount();
        ItemStack leftover = Vault.deposit(level, carried.copyWithCount(offered));
        int moved = offered - leftover.getCount();
        if (moved <= 0) {
            return;
        }
        carried.shrink(moved);
        setCarried(carried.isEmpty() ? ItemStack.EMPTY : carried);
        HsTriggers.VAULT_DEPOSITED.trigger(player);
    }

    /**
     * Shift-click deposit (DESIGN.md §2.4): a click that would normally move a stack between the
     * container and the player's own inventory instead deposits it into the Vault. Routes through
     * {@link Vault#deposit}, the same §2.6-tested transfer code the withdraw path uses — a second
     * item-moving path is how the Vault gets a dupe bug.
     *
     * <p>Refuses while dormant and refuses anything §2.5 won't store, and in both cases returns
     * {@link ItemStack#EMPTY} so vanilla's retry loop terminates — see
     * {@code VaultMenuGameTests#shiftClickAgainstCappedTypeDoesNotHang}.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        // Shift-clicking an upgrade slot returns its contents to the player, as any input slot does.
        if (slot.container == upgradeContainer) {
            ItemStack contents = slot.getItem();
            ItemStack copy = contents.copy();
            if (!moveItemStackTo(contents, firstPlayerSlot(), slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return copy;
        }

        ItemStack original = slot.getItem();

        // On tab 1, a Sigil one of the upgrade slots would accept goes there rather than into
        // storage — the same courtesy a furnace does with fuel. Depositing it instead would be
        // technically correct and quietly infuriating, since those slots are why that tab is open.
        if (anchorTabOpen && access.hasAnchorTab()) {
            int target = upgradeSlotFor(serverLevel, original.getItem());
            if (target >= 0) {
                ItemStack copy = original.copy();
                if (!moveItemStackTo(original, target, target + 1, false)) {
                    return ItemStack.EMPTY;
                }
                slot.setChanged();
                return copy;
            }
        }

        if (!VaultReach.canDepositAt(serverLevel, player.blockPosition()) || !Vault.isStorable(original)) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = original.copy();
        ItemStack leftover = Vault.deposit(serverLevel, original);
        slot.set(leftover);
        slot.setChanged();

        // Vanilla's shift-click handler (AbstractContainerMenu#clicked) calls quickMoveStack in a
        // loop until it returns EMPTY or the slot stops matching the returned stack — that's its
        // only termination signal. A capped Vault refusing a deposit leaves the slot unchanged, so
        // returning a non-empty "copy" here regardless of progress spins that loop forever.
        boolean depositedNothing = leftover.getCount() == original.getCount();
        if (!depositedNothing && player instanceof ServerPlayer serverPlayer) {
            HsTriggers.VAULT_DEPOSITED.trigger(serverPlayer);
        }
        return depositedNothing ? ItemStack.EMPTY : copy;
    }

    /** The empty upgrade slot that would take {@code item}, or -1 if none would. */
    private int upgradeSlotFor(ServerLevel level, Item item) {
        VaultUpgradeKind[] kinds = VaultUpgradeKind.values();
        for (int i = 0; i < kinds.length; i++) {
            if (kinds[i].sigil() == item && !kinds[i].isSatisfied(Vault.get(level))
                    && upgradeContainer.getItem(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Server-side handling of a {@link com.heartstead.network.VaultWithdrawPayload} intent. Never
     * trusts the client's count: it's clamped to both live Vault stock and the player's free
     * inventory space by {@link Vault#withdrawInto}, so a full inventory or a stale client snapshot
     * can't lose or duplicate anything.
     *
     * <p>The intent names a {@link VaultKey}, not an item: click one of ten enchanted swords and you
     * get that sword back, components intact.
     *
     * <p>Two gates ahead of that, in the order §2 defines them: does this access item grant the
     * withdrawal verb at all (§2.2), and does the Vault's reach cover where the player is standing
     * (§2.0, §2.3). The first is silent — the screen already draws a Satchel's grid as disabled
     * sockets, so a message would be telling the player something they can see.
     */
    public void handleWithdraw(ServerPlayer player, VaultKey key, int count) {
        if (count <= 0 || !access.canWithdraw()) {
            return;
        }
        ServerLevel level = player.level();
        if (!VaultReach.canWithdrawAt(level, player.blockPosition())) {
            player.sendSystemMessage(Component.translatable("gui.heartstead.vault.out_of_reach"), true);
            return;
        }
        // A key the Vault doesn't hold is a stale or forged snapshot; silently nothing.
        if (Vault.get(level).count(key) <= 0) {
            return;
        }
        int withdrawn = Vault.withdrawInto(level, key, count, player.getInventory());
        if (withdrawn == 0) {
            player.sendSystemMessage(Component.translatable("gui.heartstead.vault.inventory_full"), true);
        } else {
            HsTriggers.VAULT_WITHDREW.trigger(player);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            syncTo(serverPlayer, false);
        }
    }

    /**
     * Pushes both the contents snapshot and the §2.4 tab-1 state whenever the Vault's revision
     * counter has moved. One revision counter drives both so the two can never disagree — the tab-1
     * numbers and the grid are always from the same instant.
     */
    private void syncTo(ServerPlayer serverPlayer, boolean force) {
        ServerLevel level = serverPlayer.level();
        VaultData vault = Vault.get(level);
        int revision = vault.revision();

        // Reach depends on where the player is standing, not on the Vault's contents — so walking out
        // of range changes nothing the revision counter would notice. Without this second trigger the
        // client keeps drawing a usable grid until something unrelated happens to dirty the Vault.
        boolean reach = VaultReach.canWithdrawAt(level, serverPlayer.blockPosition());
        if (!force && revision == lastSyncedRevision && reach == lastSyncedReach) {
            return;
        }
        lastSyncedRevision = revision;
        lastSyncedReach = reach;
        ServerPlayNetworking.send(serverPlayer, VaultSyncPayload.of(vault.contents()));
        ServerPlayNetworking.send(serverPlayer, stateFor(level, serverPlayer, vault));
    }

    private static VaultStatePayload stateFor(ServerLevel level, ServerPlayer player, VaultData vault) {
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(vault.sigilsSpent(), HsConfigManager.get().vault());
        return new VaultStatePayload(
                vault.activated(),
                vault.everActivated(),
                vault.sigilsSpent(),
                tier.distinctTypeCap(),
                tier.stackDepthCap(),
                vault.distinctTypeCount(),
                java.util.List.copyOf(vault.reachTiers()),
                Vault.canActivateFromVaultStock(level),
                VaultReach.canWithdrawAt(level, player.blockPosition()),
                HsConfigManager.get().vault().reactivationSigils());
    }

    /**
     * One §2.3 upgrade. Accepts only its own Sigil (§12.2), so a misdrop bounces instead of sitting
     * in a slot that will never spend it, and goes inactive once its tier is bought — which is what
     * draws it as filled-and-disabled rather than as an empty socket.
     */
    private final class UpgradeSlot extends Slot {

        private final VaultUpgradeKind kind;

        UpgradeSlot(Container container, int index, VaultUpgradeKind kind) {
            super(container, index, VaultLayout.upgradeSlotX(index), VaultLayout.UPGRADE_SLOT_Y);
            this.kind = kind;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(kind.sigil());
        }

        @Override
        public boolean isActive() {
            if (!anchorTabOpen || !access.hasAnchorTab()) {
                return false;
            }
            Player player = playerInventory.player;
            if (player.level() instanceof ServerLevel serverLevel) {
                return !kind.isSatisfied(Vault.get(serverLevel));
            }
            // Client side: the synced state is the only view of what's been bought.
            return !ClientVaultUpgradeView.isSatisfied(kind);
        }
    }
}
