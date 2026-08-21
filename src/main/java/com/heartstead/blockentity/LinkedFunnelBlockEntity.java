package com.heartstead.blockentity;

import com.heartstead.block.LinkedFunnelBlock;
import com.heartstead.config.HsConfigManager;
import com.heartstead.registry.HsBlockEntities;
import com.heartstead.vault.Vault;
import com.heartstead.vault.VaultKey;
import com.heartstead.vault.VaultReach;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * DESIGN.md §2.1 — the Linked Funnel's behaviour. Holds no inventory of its own: everything it moves
 * goes straight through the §2.6-tested {@link Vault} transfer path, which is the whole reason it is
 * safe to add a second automation entry point at all. A Funnel that buffered stacks would be a
 * second place items can be lost on a crash.
 *
 * <p>Runs on a cooldown rather than every tick so it lands at hopper speed (§2.1), and the cooldown
 * is the only per-block mutable state besides the output filter.
 */
public class LinkedFunnelBlockEntity extends BlockEntity {

    /** Vanilla's hopper transfer period, so "hopper-speed" (§2.1) is literally true rather than approximately. */
    private static final int TRANSFER_COOLDOWN = HopperBlockEntity.MOVE_ITEM_SPEED;

    private static final int CURRENT_VERSION = 1;

    private int cooldown;

    /**
     * Output mode's filter. A bare {@link Item}, not an {@link ItemStack}: the Funnel is configured
     * by right-clicking it with something, and a filter that also captured that item's components
     * would silently mean "only pull swords with exactly this enchantment and exactly this much
     * durability left" — a correctly configured Funnel doing nothing forever. It dispenses whichever
     * stored variants match the item, oldest deposit first.
     */
    private Item filter;

    public LinkedFunnelBlockEntity(BlockPos pos, BlockState state) {
        super(HsBlockEntities.LINKED_FUNNEL, pos, state);
    }

    public void setFilter(Item item) {
        this.filter = item;
        setChanged();
    }

    public Item filter() {
        return filter;
    }

    /**
     * How many items one transfer cycle moves (§12.7 {@code vault.funnel_items_per_transfer}). A
     * literal here would be a tuning dial hiding in code, which CLAUDE.md forbids — and this is one
     * of the numbers most likely to move after playtesting.
     */
    private static int transferBatch() {
        return HsConfigManager.get().vault().funnelItemsPerTransfer();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LinkedFunnelBlockEntity funnel) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (funnel.cooldown > 0) {
            funnel.cooldown--;
            return;
        }
        funnel.cooldown = TRANSFER_COOLDOWN;

        // §2.1 — "nothing linked works without an activated Anchor. Funnels go inert."
        if (!Vault.get(serverLevel).activated()) {
            return;
        }

        if (LinkedFunnelBlock.isOutput(state)) {
            funnel.pushOutOfVault(serverLevel, pos);
        } else {
            funnel.pullIntoVault(serverLevel, pos);
        }
    }

    /**
     * §2.0's free verb: takes from the container (or loose items) directly above and deposits. No
     * distance check, no dimension check — a Funnel under a farm at the far end of the Nether feeds
     * the same Vault as one at your base.
     */
    private void pullIntoVault(ServerLevel level, BlockPos pos) {
        Container above = HopperBlockEntity.getContainerAt(level, pos.above());
        if (above != null) {
            for (int slot = 0; slot < above.getContainerSize(); slot++) {
                ItemStack stack = above.getItem(slot);
                if (stack.isEmpty() || !Vault.isStorable(stack)) {
                    continue;
                }
                if (above instanceof WorldlyContainer worldly && !worldly.canTakeItemThroughFace(slot, stack, Direction.DOWN)) {
                    continue;
                }
                int toMove = Math.min(transferBatch(), stack.getCount());
                ItemStack offered = stack.copyWithCount(toMove);
                ItemStack leftover = Vault.depositThroughLink(level, offered, pos);
                int moved = toMove - leftover.getCount();
                if (moved > 0) {
                    above.removeItem(slot, moved);
                    above.setChanged();
                    return;
                }
            }
        }

        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos.above()))) {
            ItemStack stack = entity.getItem();
            if (stack.isEmpty() || !Vault.isStorable(stack)) {
                continue;
            }
            ItemStack leftover = Vault.depositThroughLink(level, stack, pos);
            if (leftover.getCount() == stack.getCount()) {
                continue;
            }
            if (leftover.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(leftover);
            }
            return;
        }
    }

    /**
     * §2.0's powerful verb: dispenses the filtered item into the container below, but only if the
     * Vault's reach covers where this Funnel stands. Same rule as the Pouch, same code path.
     *
     * <p>Measures the destination's free space <em>before</em> touching the Vault, so a full
     * destination is a no-op rather than a withdrawal that has nowhere to go. The
     * {@code depositThroughLink} at the end is a belt-and-braces return path that should never
     * trigger; if the room calculation is ever wrong, items go back to the Vault rather than onto
     * the floor.
     */
    private void pushOutOfVault(ServerLevel level, BlockPos pos) {
        if (filter == null || !VaultReach.canWithdrawAt(level, pos)) {
            return;
        }
        Container below = HopperBlockEntity.getContainerAt(level, pos.below());
        if (below == null) {
            return;
        }

        // One variant per cycle: mixing variants into a single insert would need one destination
        // check per variant anyway, and the next cycle picks up whatever is left.
        VaultKey key = Vault.firstVariantOf(level, filter).orElse(null);
        if (key == null) {
            return;
        }
        int stored = Vault.get(level).count(key);
        if (stored <= 0) {
            return;
        }
        int room = roomFor(below, key.stack(), Math.min(transferBatch(), stored));
        if (room <= 0) {
            return;
        }

        int withdrawn = Vault.withdraw(level, key, room);
        if (withdrawn <= 0) {
            return;
        }
        ItemStack leftover = insertInto(below, key.stack(withdrawn));
        if (!leftover.isEmpty()) {
            Vault.deposit(level, leftover);
        }
        below.setChanged();
    }

    /** How many of {@code probe}'s exact variant the container would accept, capped at {@code limit}. */
    private static int roomFor(Container container, ItemStack probe, int limit) {
        int maxStackSize = Math.min(container.getMaxStackSize(), probe.getMaxStackSize());
        int room = 0;
        for (int slot = 0; slot < container.getContainerSize() && room < limit; slot++) {
            if (!container.canPlaceItem(slot, probe)) {
                continue;
            }
            if (container instanceof WorldlyContainer worldly && !worldly.canPlaceItemThroughFace(slot, probe, Direction.UP)) {
                continue;
            }
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                room += maxStackSize;
            } else if (ItemStack.isSameItemSameComponents(existing, probe)) {
                room += Math.max(0, maxStackSize - existing.getCount());
            }
        }
        return Math.min(room, limit);
    }

    /** Inserts what it can and returns the remainder. Mirrors {@link #roomFor}'s slot rules exactly. */
    private static ItemStack insertInto(Container container, ItemStack stack) {
        int maxStackSize = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());

        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }
            int room = maxStackSize - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int toAdd = Math.min(room, stack.getCount());
            existing.grow(toAdd);
            stack.shrink(toAdd);
        }

        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (!container.getItem(slot).isEmpty() || !container.canPlaceItem(slot, stack)) {
                continue;
            }
            int toAdd = Math.min(maxStackSize, stack.getCount());
            container.setItem(slot, stack.copyWithCount(toAdd));
            stack.shrink(toAdd);
        }

        return stack;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("version", CURRENT_VERSION);
        output.putInt("cooldown", cooldown);
        if (filter != null) {
            output.store("filter", BuiltInRegistries.ITEM.byNameCodec(), filter);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        cooldown = input.getIntOr("cooldown", 0);
        filter = input.read("filter", BuiltInRegistries.ITEM.byNameCodec()).orElse(null);
    }
}
