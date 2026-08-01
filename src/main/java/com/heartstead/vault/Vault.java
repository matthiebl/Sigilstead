package com.heartstead.vault;

import com.heartstead.config.HsConfigManager;
import com.heartstead.network.VaultAnchorPayload;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * DESIGN.md §2 — the deposit/withdraw/capacity API over the shared, world-level {@link VaultData}.
 * All mutation happens here so the §2.3 capacity rules are enforced in exactly one place; the Vault
 * Anchor block and screen handler (a later phase) are callers of this, not owners of the logic.
 *
 * <p>The Vault is one per world, not per dimension (DESIGN.md §2) — every method resolves the
 * overworld's save data regardless of which {@link ServerLevel} is passed in, so an Anchor built in
 * the Nether still reaches the same shared store.
 */
public final class Vault {

    private Vault() {
    }

    public static VaultData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(VaultData.TYPE);
    }

    public static VaultCapacityTier capacityTier(ServerLevel level) {
        return VaultCapacityTier.forSigilsSpent(get(level).sigilsSpent(), HsConfigManager.get().vault());
    }

    /** Consumes one Vault Sigil at the Anchor, advancing capacity per §2.3. */
    public static void spendSigil(ServerLevel level) {
        get(level).spendSigil();
    }

    /** Position of the world's one Vault Anchor (DESIGN.md §2.1), if one has been placed. */
    public static Optional<BlockPos> anchorPos(ServerLevel level) {
        return get(level).anchorPos();
    }

    /**
     * Claims {@code pos} as the world's Vault Anchor. Callers (the block itself) are responsible for
     * enforcing the "one per world" rule before calling this — this just records the claim.
     *
     * <p>Broadcasts the new position to every connected player so each client can veto its own
     * speculative placement of a second Anchor locally — see {@link VaultAnchorPayload}'s javadoc.
     */
    public static void claimAnchor(ServerLevel level, BlockPos pos) {
        get(level).setAnchorPos(pos);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new VaultAnchorPayload(Optional.of(pos)));
        }
    }

    /**
     * Deposits as much of {@code stack} as §2.3 capacity allows and returns what didn't fit — empty
     * if all of it went in. This never mutates {@code stack} itself; the caller removes the
     * deposited portion (original count minus the returned remainder) from wherever it came from.
     */
    public static ItemStack deposit(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        VaultData vault = get(level);
        Item item = stack.getItem();
        int existing = vault.count(item);
        VaultCapacityTier tier = VaultCapacityTier.forSigilsSpent(vault.sigilsSpent(), HsConfigManager.get().vault());

        if (existing == 0 && vault.distinctTypeCount() >= tier.distinctTypeCap()) {
            return stack;
        }

        int perTypeCap = tier.stackDepthCap() * stack.getMaxStackSize();
        int room = Math.max(0, perTypeCap - existing);
        int toDeposit = Math.min(stack.getCount(), room);
        if (toDeposit <= 0) {
            return stack;
        }

        vault.add(item, toDeposit);

        ItemStack remainder = stack.copy();
        remainder.shrink(toDeposit);
        return remainder;
    }

    /** Removes up to {@code count} of {@code item} from the Vault and returns how many actually came out. */
    public static int withdraw(ServerLevel level, Item item, int count) {
        if (count <= 0) {
            return 0;
        }
        VaultData vault = get(level);
        int actual = Math.min(vault.count(item), count);
        if (actual > 0) {
            vault.remove(item, actual);
        }
        return actual;
    }

    /**
     * Withdraws up to {@code count} of {@code item} directly into {@code inventory}, constrained by
     * both Vault stock and free inventory space. Only removes from the Vault what actually fit, so a
     * full inventory never loses items — the DESIGN.md §2.5 "full inventory on withdraw" case.
     *
     * <p>Inserts by hand rather than via {@link Inventory#add}, which reports success and silently
     * discards the stack for any player with {@code hasInfiniteMaterials()} (creative/spectator) even
     * when there's no room — exactly the kind of item-voiding bug §2.5 exists to catch, and one this
     * survival-facing system can't inherit just because a player happens to be in creative.
     */
    public static int withdrawInto(ServerLevel level, Item item, int count, Inventory inventory) {
        if (count <= 0) {
            return 0;
        }
        VaultData vault = get(level);
        int requested = Math.min(vault.count(item), count);
        if (requested <= 0) {
            return 0;
        }

        int actual = insertIntoMainSlots(inventory, item, requested);
        if (actual > 0) {
            vault.remove(item, actual);
        }
        return actual;
    }

    private static int insertIntoMainSlots(Inventory inventory, Item item, int amount) {
        var slots = inventory.getNonEquipmentItems();
        int maxStackSize = new ItemStack(item).getMaxStackSize();
        int remaining = amount;

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack existing = slots.get(i);
            if (existing.isEmpty() || !existing.is(item)) {
                continue;
            }
            int room = maxStackSize - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int toAdd = Math.min(room, remaining);
            existing.grow(toAdd);
            remaining -= toAdd;
        }

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            if (!slots.get(i).isEmpty()) {
                continue;
            }
            int toAdd = Math.min(maxStackSize, remaining);
            inventory.setItem(i, new ItemStack(item, toAdd));
            remaining -= toAdd;
        }

        if (remaining < amount) {
            inventory.setChanged();
        }
        return amount - remaining;
    }
}
