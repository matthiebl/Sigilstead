package com.sigilstead.vault;

import com.sigilstead.config.HsConfigManager;
import com.sigilstead.network.VaultAnchorPayload;
import com.sigilstead.registry.HsItems;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §2 — the deposit/withdraw/capacity/activation/reach API over the shared, world-level
 * {@link VaultData}. All mutation happens here so §2.3's capacity rules and §2.5's container rules
 * are enforced in exactly one place; the Anchor block, the screen handler, the access items and the
 * Linked Funnel are all callers of this, not owners of the logic.
 *
 * <p>The Vault is one per world, not per dimension (DESIGN.md §2) — every method resolves the
 * overworld's save data regardless of which {@link ServerLevel} is passed in, so an Anchor built in
 * the Nether still reaches the same shared store.
 *
 * <p><b>Two deposit entry points, deliberately.</b> {@link #deposit} is the raw transfer and applies
 * only the §2.3 capacity and §2.5 container rules; {@link #depositThroughLink} adds §2.1's "nothing
 * linked works without an activated Anchor" on top. The Anchor's own screen and the GameTests use
 * the raw one — you are standing at the thing — while Satchels and Funnels use the linked one.
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

    // ---------------------------------------------------------------- §2.1 activation

    /**
     * §2.1 — activates for {@code player}, consuming a Vault Sigil unless this is the world's first
     * ever activation. Returns whether it happened.
     *
     * <p>The payment order is the anti-softlock rule stated exactly: a <b>held</b> Sigil is always
     * preferred, and only if the player has none does a Sigil sitting in the Vault pay instead. That
     * ordering matters on a server — without it, one player activating would silently drain the
     * shared pool while their own Sigil sat in their pocket.
     */
    public static boolean tryActivate(ServerLevel level, ServerPlayer player) {
        VaultData vault = get(level);
        if (vault.activated()) {
            return false;
        }
        if (!vault.everActivated()) {
            vault.setActivated(true);
            return true;
        }

        int cost = HsConfigManager.get().vault().reactivationSigils();
        if (cost <= 0) {
            vault.setActivated(true);
            return true;
        }
        if (takeFromInventory(player.getInventory(), HsItems.VAULT_SIGIL, cost)) {
            vault.setActivated(true);
            return true;
        }
        if (vault.count(HsItems.VAULT_SIGIL) >= cost) {
            // A Sigil is componentless, so the plain key is the only variant that can be stored.
            vault.remove(VaultKey.of(HsItems.VAULT_SIGIL), cost);
            vault.setActivated(true);
            return true;
        }
        return false;
    }

    /** True when {@link #tryActivate} would fall back to the Vault's own stock — what §2.4 shows a second button for. */
    public static boolean canActivateFromVaultStock(ServerLevel level) {
        VaultData vault = get(level);
        if (vault.activated() || !vault.everActivated()) {
            return false;
        }
        return vault.count(HsItems.VAULT_SIGIL) >= HsConfigManager.get().vault().reactivationSigils();
    }

    /** The world's first, free activation (§2.1). Used by tests and by {@link #tryActivate}'s free path. */
    public static void activateForFree(ServerLevel level) {
        get(level).setActivated(true);
    }

    /**
     * §2.1 — breaking an activated Anchor loses the activation. It never touches {@code sigilsSpent}
     * or the reach tiers: capacity and reach are bought progress that a broken block must not undo.
     */
    public static void deactivate(ServerLevel level) {
        get(level).setActivated(false);
    }

    /** Records that this world has had an Anchor without activating one — the "first is free" latch. */
    public static void markEverActivated(ServerLevel level) {
        get(level).markEverActivated();
    }

    // ---------------------------------------------------------------- §2.3 reach

    public static void grantReach(ServerLevel level, ResourceKey<Level> dimension) {
        get(level).grantReach(dimension);
    }

    public static void revokeReach(ServerLevel level, ResourceKey<Level> dimension) {
        get(level).revokeReach(dimension);
    }

    /**
     * §2.3 — buys the reach tier {@code dimension} with the matching dimensional Vault Sigil from
     * {@code player}'s inventory. The proof item baked into that Sigil's recipe (§12.2) is the real
     * gate: you cannot buy reach into a dimension you have never visited, which is what lets §1's
     * Sigil be craftable without breaking §2's economy.
     */
    public static boolean tryBuyReach(ServerLevel level, ServerPlayer player, ResourceKey<Level> dimension) {
        VaultData vault = get(level);
        if (!vault.activated() || vault.reachTiers().contains(dimension)) {
            return false;
        }
        Item sigil = reachSigilFor(dimension);
        if (sigil == null) {
            return false;
        }
        int cost = HsConfigManager.get().vault().reachTierSigils();
        if (!takeFromInventory(player.getInventory(), sigil, cost)) {
            return false;
        }
        vault.grantReach(dimension);
        return true;
    }

    /** §12.2 — the dimensional Vault Sigil that buys reach into {@code dimension}, or null if there isn't one. */
    public static Item reachSigilFor(ResourceKey<Level> dimension) {
        if (Level.OVERWORLD.equals(dimension)) {
            return HsItems.OVERWORLD_VAULT_SIGIL;
        }
        if (Level.NETHER.equals(dimension)) {
            return HsItems.NETHER_VAULT_SIGIL;
        }
        if (Level.END.equals(dimension)) {
            return HsItems.END_VAULT_SIGIL;
        }
        return null;
    }

    /** §2.3 — buys the next step of capacity with a plain Vault Sigil from {@code player}'s inventory. */
    public static boolean tryBuyCapacity(ServerLevel level, ServerPlayer player) {
        VaultData vault = get(level);
        if (!vault.activated()) {
            return false;
        }
        if (!takeFromInventory(player.getInventory(), HsItems.VAULT_SIGIL, 1)) {
            return false;
        }
        vault.spendSigil();
        return true;
    }

    // ---------------------------------------------------------------- §2.1 anchor position

    /**
     * Claims {@code pos} as the world's Vault Anchor. Callers (the block itself) are responsible for
     * enforcing the "one per world" rule before calling this — this just records the claim.
     *
     * <p>Broadcasts the new position to every connected player so each client can veto its own
     * speculative placement of a second Anchor locally — see {@link VaultAnchorPayload}'s javadoc.
     */
    public static void claimAnchor(ServerLevel level, BlockPos pos) {
        get(level).setAnchor(pos, level.dimension());
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, new VaultAnchorPayload(Optional.of(pos)));
        }
    }

    /**
     * §2.1's "one per world" check: true if a Vault Anchor block other than the one at {@code pos}
     * in {@code level} is currently standing anywhere in the world.
     *
     * <p>Resolves the claim's own dimension rather than assuming {@code level}'s — the Vault is one
     * per world, not per dimension, so an Anchor in the Nether must still block a second one in the
     * Overworld, and comparing bare {@link BlockPos} across dimensions would let the same coordinates
     * in two dimensions look like one Anchor.
     */
    public static boolean anchorStandsElsewhere(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.Block anchorBlock) {
        VaultData vault = get(level);
        Optional<BlockPos> anchor = vault.anchorPos();
        Optional<ResourceKey<Level>> dimension = vault.anchorDimension();
        if (anchor.isEmpty() || dimension.isEmpty()) {
            return false;
        }
        boolean sameSpot = anchor.get().equals(pos) && dimension.get().equals(level.dimension());
        if (sameSpot) {
            return false;
        }
        ServerLevel anchorLevel = level.getServer().getLevel(dimension.get());
        return anchorLevel != null && anchorLevel.getBlockState(anchor.get()).is(anchorBlock);
    }

    // ---------------------------------------------------------------- §2.5 / §2.3 transfer

    /**
     * §2.5 — whether the Vault will store {@code stack} at all. Any item carrying a non-empty
     * {@code container} or {@code bundle_contents} component is rejected outright: storing one would
     * launder capacity (a filled shulker is 27 free types hiding inside a single type) and hands the
     * transfer path a nested case it does not need. Empty shulkers and empty bundles store normally.
     */
    public static boolean isStorable(ItemStack stack) {
        var container = stack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItemCopyStream().findAny().isPresent()) {
            return false;
        }
        var bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle == null || bundle.isEmpty();
    }

    /**
     * Deposits as much of {@code stack} as §2.3 capacity allows and returns what didn't fit — empty
     * if all of it went in. This never mutates {@code stack} itself; the caller removes the
     * deposited portion (original count minus the returned remainder) from wherever it came from.
     *
     * <p>Applies the §2.5 container rule but <em>not</em> the §2.1 activation rule — see the class
     * javadoc for why there are two entry points.
     */
    public static ItemStack deposit(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty() || !isStorable(stack)) {
            return stack;
        }

        VaultData vault = get(level);
        Item item = stack.getItem();
        // Both caps are per item type, not per variant: an enchanted sword goes in beside the plain
        // one for free, and the two of them share the type's depth allowance (VaultKey's javadoc).
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

        vault.add(VaultKey.of(stack), toDeposit);

        ItemStack remainder = stack.copy();
        remainder.shrink(toDeposit);
        return remainder;
    }

    /**
     * §2.0 / §2.1 — deposit through a Satchel or a Linked Funnel: free from anywhere by default, but
     * inert while the Anchor is dormant, and subject to reach when {@code deposit_requires_reach}
     * (§12.7) is switched on.
     */
    public static ItemStack depositThroughLink(ServerLevel level, ItemStack stack, BlockPos at) {
        if (!VaultReach.canDepositAt(level, at)) {
            return stack;
        }
        return deposit(level, stack);
    }

    /** Removes up to {@code count} of one stored variant and returns how many actually came out. */
    public static int withdraw(ServerLevel level, VaultKey key, int count) {
        if (count <= 0) {
            return 0;
        }
        VaultData vault = get(level);
        int actual = Math.min(vault.count(key), count);
        if (actual > 0) {
            vault.remove(key, actual);
        }
        return actual;
    }

    /**
     * §2.0 — a withdrawal that first checks the player is somewhere the Vault's reach covers. This
     * is the gate every withdrawal path in the mod goes through; there is no second one.
     */
    public static int withdrawIntoIfInReach(ServerLevel level, Item item, int count, ServerPlayer player) {
        if (!VaultReach.canWithdrawAt(level, player.blockPosition())) {
            return 0;
        }
        return withdrawInto(level, item, count, player.getInventory());
    }

    /** As {@link #withdrawIntoIfInReach(ServerLevel, Item, int, ServerPlayer)}, for one exact variant. */
    public static int withdrawIntoIfInReach(ServerLevel level, VaultKey key, int count, ServerPlayer player) {
        if (!VaultReach.canWithdrawAt(level, player.blockPosition())) {
            return 0;
        }
        return withdrawInto(level, key, count, player.getInventory());
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
    public static int withdrawInto(ServerLevel level, VaultKey key, int count, Inventory inventory) {
        if (count <= 0) {
            return 0;
        }
        VaultData vault = get(level);
        int requested = Math.min(vault.count(key), count);
        if (requested <= 0) {
            return 0;
        }

        int actual = insertIntoMainSlots(inventory, key, requested);
        if (actual > 0) {
            vault.remove(key, actual);
        }
        return actual;
    }

    /**
     * Withdraws {@code count} of {@code item} without caring which variant it comes from, draining
     * stored variants in the order they were deposited. This is the convenience path for callers
     * that only know an item type — the Linked Funnel's output filter (§2.1) and the tests. Anything
     * a player clicks goes through the {@link VaultKey} overload instead, because a player pointed
     * at a specific stack and expects that stack.
     */
    public static int withdrawInto(ServerLevel level, Item item, int count, Inventory inventory) {
        if (count <= 0) {
            return 0;
        }
        int remaining = count;
        for (VaultKey key : get(level).variantsOf(item)) {
            remaining -= withdrawInto(level, key, remaining, inventory);
            if (remaining <= 0) {
                break;
            }
        }
        return count - remaining;
    }

    /**
     * The stored variant of {@code item} that a variant-blind caller should take from next, or empty
     * if the Vault holds none. Deposit order, so a Funnel eventually drains every variant rather
     * than jamming on one it can't place.
     */
    public static Optional<VaultKey> firstVariantOf(ServerLevel level, Item item) {
        List<VaultKey> variants = get(level).variantsOf(item);
        return variants.isEmpty() ? Optional.empty() : Optional.of(variants.getFirst());
    }

    /**
     * Takes exactly {@code count} of {@code item} out of {@code inventory}, or nothing at all if the
     * inventory doesn't hold that many. All-or-nothing on purpose: a partial take would charge a
     * player for an upgrade they didn't get.
     */
    private static boolean takeFromInventory(Inventory inventory, Item item, int count) {
        if (count <= 0) {
            return true;
        }
        var slots = inventory.getNonEquipmentItems();
        int available = 0;
        for (ItemStack stack : slots) {
            if (stack.is(item)) {
                available += stack.getCount();
            }
        }
        if (available < count) {
            return false;
        }

        int remaining = count;
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack stack = slots.get(i);
            if (!stack.is(item)) {
                continue;
            }
            int taken = Math.min(stack.getCount(), remaining);
            stack.shrink(taken);
            if (stack.isEmpty()) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
            remaining -= taken;
        }
        inventory.setChanged();
        return true;
    }

    private static int insertIntoMainSlots(Inventory inventory, VaultKey key, int amount) {
        var slots = inventory.getNonEquipmentItems();
        int maxStackSize = key.maxStackSize();
        int remaining = amount;

        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            ItemStack existing = slots.get(i);
            // Same item *and* same components — merging an enchanted sword into a plain one's slot
            // is exactly the data loss this whole path exists to prevent.
            if (existing.isEmpty() || !key.matches(existing)) {
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
            inventory.setItem(i, key.stack(toAdd));
            remaining -= toAdd;
        }

        if (remaining < amount) {
            inventory.setChanged();
        }
        return amount - remaining;
    }
}
