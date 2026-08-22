package com.sigilstead.blockentity;

import com.sigilstead.block.CoreHousingBlock;
import com.sigilstead.block.LinkedFunnelBlock;
import com.sigilstead.config.CoreConfig;
import com.sigilstead.config.HsConfigManager;
import com.sigilstead.core.ActiveCores;
import com.sigilstead.core.CoreAccrual;
import com.sigilstead.core.CoreAttunement;
import com.sigilstead.core.CoreFamily;
import com.sigilstead.core.CoreHousingOpenData;
import com.sigilstead.core.CoreHousingMenu;
import com.sigilstead.core.CoreKey;
import com.sigilstead.core.CoreYield;
import com.sigilstead.item.CoreItem;
import com.sigilstead.registry.HsBlockEntities;
import com.sigilstead.registry.HsBlocks;
import com.sigilstead.registry.HsComponents;
import com.sigilstead.vault.Vault;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * DESIGN.md §4.2 — the shared behaviour of all four housings. One class, not four: §4.2 says "the
 * housing supplies the family; the core supplies the rate and the loot table", so the only thing that
 * differs between a Soul Cage and a Quarry Node is the family its block declares, and that is why §5's
 * eleven classic-farm cores need no new blocks at all.
 *
 * <h2>Slot 0 is the core; the rest is the §4.2 buffer</h2>
 *
 * The block entity is a {@link WorldlyContainer} so that a Linked Funnel (or an ordinary hopper)
 * placed beneath it drains the buffer with no new item-moving code — §2.1's Funnel already knows how
 * to take from a container above it, and CLAUDE.md is explicit that a second transfer path is how the
 * Vault gets a dupe bug. Automation sees the buffer slots only: {@link #canTakeItemThroughFace}
 * refuses slot 0, so no hopper can ever steal a socketed core, and {@link #canPlaceItemThroughFace}
 * refuses everything, because a housing has no input.
 *
 * <h2>Accrual (§4.2)</h2>
 *
 * All time arithmetic is {@link CoreAccrual}'s, and this class stores exactly one timestamp. It
 * settles on its first server tick after chunk load (26.2 has no {@code BlockEntity#onLoad}, and the
 * first tick is the same moment for this purpose) and every {@code settle_interval_ticks} thereafter.
 * Because every settle recomputes from the stored stamp rather than incrementing a counter, settling
 * often and settling once pay identically — that is the §4.2 double-counting bug, and it is closed by
 * construction rather than by remembering not to tick.
 *
 * <h2>Where the yield goes</h2>
 *
 * §4.2's payoff: "Output goes to small internal storage — or straight into your Vault if a Linked
 * Funnel is below." Both, in that order of preference — a linked Funnel below takes the yield
 * directly, so a housing feeding the Vault keeps producing while its chunk is unloaded instead of
 * stalling on a full buffer the Funnel was not running to drain.
 *
 * <p>Production stops when there is nowhere left to put output. Items that exist in the world are
 * never destroyed; what is discarded, at the moment both the Vault and the buffer are full, is at
 * most one cycle's worth of loot that was never materialised. That is deliberate — §4.2 wants
 * "internal storage limits to be meaningful", which they are not if a full housing keeps banking.
 */
public class CoreHousingBlockEntity extends BlockEntity implements WorldlyContainer, ExtendedMenuProvider<CoreHousingOpenData> {

    private static final int CURRENT_VERSION = 1;

    /** Slot 0 holds the socketed core; buffer slots start at 1. */
    public static final int CORE_SLOT = 0;
    public static final int FIRST_BUFFER_SLOT = 1;

    private final CoreFamily family;

    private ItemStack core = ItemStack.EMPTY;
    private NonNullList<ItemStack> buffer;

    /** §4.2 — the one stored clock. Everything owed is the span between this and the current game time. */
    private long lastSettled;

    /**
     * Accrued experience, waiting to be collected from the screen. Kept as a {@code double} so a
     * fractional remainder from a low-XP core (or a partial tier multiplier) is never silently
     * dropped between settles — only {@link #collectExperience} rounds down, and only what it hands
     * the player is subtracted.
     */
    private double storedExperience;

    /** The §4.3 claim this housing currently holds, so it can be released precisely when it changes. */
    private CoreKey claimedKey;

    /** True when §4.3 refused this core because a duplicate is already running somewhere. */
    private boolean refused;

    private int sinceLastSettle;

    public CoreHousingBlockEntity(BlockPos pos, BlockState state) {
        super(HsBlockEntities.CORE_HOUSING, pos, state);
        this.family = state.getBlock() instanceof CoreHousingBlock housing ? housing.family() : CoreFamily.SOUL;
        this.buffer = NonNullList.withSize(HsConfigManager.get().core().housingSlots(), ItemStack.EMPTY);
    }

    public CoreFamily family() {
        return family;
    }

    /** The §4.3 key this housing's core occupies, or empty when nothing valid is socketed. */
    public Optional<CoreKey> socketedKey() {
        return attunement().flatMap(CoreAttunement::key);
    }

    /** True when a core is socketed and running — neither refused nor targetless. */
    public boolean active() {
        return !refused && socketedKey().isPresent();
    }

    /** §4.3 — true when the socketed core was refused because a duplicate is already active. */
    public boolean refused() {
        return refused;
    }

    /** §4.2 — the one stored clock. Everything owed is the span between this and the current game time. */
    public long lastSettled() {
        return lastSettled;
    }

    /**
     * Moves the stored stamp, which is only meaningful to the accrual GameTests: a test cannot
     * fast-forward the world clock without ticking everything in the world, so it backdates the
     * stamp instead — indistinguishable, from {@link #settle}'s point of view, from time passing.
     * Nothing in the mod calls this.
     */
    public void setLastSettledForTest(long gameTime) {
        this.lastSettled = gameTime;
    }

    /** The XP accrued and waiting to be collected — what the screen's counter and button read. */
    public double storedExperience() {
        return storedExperience;
    }

    /**
     * Hands the player whatever whole XP points have accrued, keeping any fractional remainder for
     * next time — the same "nothing owed is ever silently dropped" rule {@link #settle} follows for
     * items. A no-op below one point, so the button has nothing to send for.
     */
    public void collectExperience(ServerPlayer player) {
        int whole = (int) Math.floor(storedExperience);
        if (whole <= 0) {
            return;
        }
        player.giveExperiencePoints(whole);
        storedExperience -= whole;
        setChanged();
    }

    private Optional<CoreAttunement> attunement() {
        if (core.isEmpty() || !(core.getItem() instanceof CoreItem coreItem) || coreItem.family() != family) {
            return Optional.empty();
        }
        return Optional.ofNullable(core.get(HsComponents.CORE_ATTUNEMENT));
    }

    // ---------------------------------------------------------------- §4.3 socketing

    /**
     * Re-evaluates which §4.3 claim this housing holds. Called whenever slot 0 changes, and on load,
     * so the registry and the block never disagree about who is running what.
     */
    private void refreshClaim() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        CoreKey wanted = socketedKey().orElse(null);

        if (claimedKey != null && !claimedKey.equals(wanted)) {
            ActiveCores.release(serverLevel, claimedKey, worldPosition);
            claimedKey = null;
        }
        if (wanted == null) {
            refused = false;
            return;
        }
        if (claimedKey != null) {
            return;
        }

        boolean claimed = ActiveCores.tryClaim(serverLevel, wanted, worldPosition) == ActiveCores.Result.CLAIMED;
        refused = !claimed;
        claimedKey = claimed ? wanted : null;
        if (claimed) {
            // Start the clock now, not at whatever the last core left behind — otherwise socketing a
            // core into a housing that stood empty for a week would pay a week's backlog instantly.
            lastSettled = serverLevel.getGameTime();
        }
    }

    // ---------------------------------------------------------------- §4.2 accrual

    public static void serverTick(Level level, BlockPos pos, BlockState state, CoreHousingBlockEntity housing) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        int interval = HsConfigManager.get().core().settleIntervalTicks();
        // sinceLastSettle starts at 0, so the very first tick after a chunk load settles the whole
        // elapsed offline delta — §4.2's "on chunk load … it settles the whole elapsed delta".
        if (housing.sinceLastSettle > 0 && housing.sinceLastSettle < interval) {
            housing.sinceLastSettle++;
            return;
        }
        housing.sinceLastSettle = 1;
        housing.settle(serverLevel);
    }

    /**
     * §4.2 — settles everything owed since {@link #lastSettled} in one calculation and stores the new
     * stamp. Also called on interaction (opening the screen), so a player who walks up sees the yield
     * their absence earned without waiting for a tick interval.
     *
     * @return how many production cycles this settle paid for. This is the accrual answer, and it is
     *     what the GameTests assert on — the <em>items</em> a cycle produces are a random loot roll,
     *     so counting them could never test the arithmetic. Delivery may place fewer items than these
     *     cycles rolled if both the Vault and the buffer fill.
     */
    public int settle(ServerLevel level) {
        long now = level.getGameTime();

        Optional<CoreAttunement> attunement = attunement();
        if (attunement.isEmpty() || refused) {
            // Nothing is running, so nothing accrues. Keeping the stamp current is what stops an
            // empty housing banking time it never earned.
            lastSettled = now;
            return 0;
        }

        CoreConfig config = HsConfigManager.get().core();
        CoreAttunement core = attunement.get();
        int basePeriodTicks = core.target()
                .flatMap(com.sigilstead.core.ClassicCore::forTarget)
                .map(classic -> HsConfigManager.get().classicCores().forCore(classic).periodTicks())
                .orElseGet(() -> config.basePeriodTicks(family));
        double period = CoreAccrual.effectivePeriodTicks(
                basePeriodTicks,
                core.tier().rateMultiplier(config),
                HsConfigManager.get().coreRateMultiplier());
        long cap = CoreAccrual.capTicks(HsConfigManager.get().coreAccrualCapHours());

        CoreAccrual.Settlement settlement = CoreAccrual.settle(now, lastSettled, period, cap);
        lastSettled = settlement.newLastSettled();
        if (settlement.cycles() <= 0) {
            setChanged();
            return 0;
        }

        // XP scales with tier exactly the way item yield does — tier only changes cycles/hour, not
        // what one cycle is worth — so no separate tier multiplier is needed here.
        double xpPerCycle = core.target()
                .flatMap(com.sigilstead.core.ClassicCore::forTarget)
                .map(classic -> HsConfigManager.get().classicCores().forCore(classic).xpPerCycle())
                .orElseGet(() -> config.xpPerCycle(family));
        storedExperience += settlement.cycles() * xpPerCycle;

        // §5's classic cores price their whole cycle's yield into the loot table itself (Geode's "4
        // shards" is one roll with a set_count function, not four rolls) — the Quarry Node's 8-blocks-
        // per-cycle bulk rate is a §4.2 base-rate detail Geode's own §12.5 rate replaces outright.
        int rolls = core.target().flatMap(com.sigilstead.core.ClassicCore::forTarget).isPresent()
                ? 1
                : config.rollsPerCycle(family);
        for (int cycle = 0; cycle < settlement.cycles(); cycle++) {
            if (!hasAnywhereToPut(level)) {
                break;
            }
            if (!deliver(level, CoreYield.produce(level, core, worldPosition, rolls))) {
                break;
            }
        }
        setChanged();
        return settlement.cycles();
    }

    /** Cheap pre-check so a full housing stops before rolling loot it could not accept. */
    private boolean hasAnywhereToPut(ServerLevel level) {
        if (linkedFunnelBelow(level)) {
            return true;
        }
        for (int slot = 0; slot < buffer.size(); slot++) {
            if (buffer.get(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * §4.2's Vault integration. Returns false once output has nowhere left to go, which stops
     * production for this settle.
     */
    private boolean deliver(ServerLevel level, List<ItemStack> produced) {
        boolean funnel = linkedFunnelBelow(level);
        List<ItemStack> remaining = new ArrayList<>();

        for (ItemStack stack : produced) {
            ItemStack left = funnel ? Vault.depositThroughLink(level, stack, worldPosition.below()) : stack;
            if (!left.isEmpty()) {
                remaining.add(left);
            }
        }

        boolean allPlaced = true;
        for (ItemStack stack : remaining) {
            if (!insertIntoBuffer(stack).isEmpty()) {
                allPlaced = false;
            }
        }
        return allPlaced;
    }

    /**
     * §4.2 — "or straight into your Vault if a Linked Funnel is below". An <em>output</em> Funnel is
     * deliberately not a link: it dispenses from the Vault rather than into it, and reading it as a
     * link would make a housing and a Funnel below it fight over the same items.
     */
    private boolean linkedFunnelBelow(ServerLevel level) {
        BlockState below = level.getBlockState(worldPosition.below());
        return below.is(HsBlocks.LINKED_FUNNEL) && !LinkedFunnelBlock.isOutput(below);
    }

    /** Inserts what fits into the buffer and returns the remainder. Mirrors ordinary container merge rules. */
    private ItemStack insertIntoBuffer(ItemStack stack) {
        ItemStack remaining = stack.copy();

        for (int slot = 0; slot < buffer.size() && !remaining.isEmpty(); slot++) {
            ItemStack existing = buffer.get(slot);
            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int room = Math.min(existing.getMaxStackSize(), getMaxStackSize()) - existing.getCount();
            if (room <= 0) {
                continue;
            }
            int moved = Math.min(room, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
        }

        for (int slot = 0; slot < buffer.size() && !remaining.isEmpty(); slot++) {
            if (!buffer.get(slot).isEmpty()) {
                continue;
            }
            int moved = Math.min(Math.min(remaining.getMaxStackSize(), getMaxStackSize()), remaining.getCount());
            buffer.set(slot, remaining.copyWithCount(moved));
            remaining.shrink(moved);
        }

        return remaining;
    }

    // ---------------------------------------------------------------- container

    @Override
    public int getContainerSize() {
        return FIRST_BUFFER_SLOT + buffer.size();
    }

    @Override
    public boolean isEmpty() {
        if (!core.isEmpty()) {
            return false;
        }
        for (ItemStack stack : buffer) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot == CORE_SLOT) {
            return core;
        }
        int index = slot - FIRST_BUFFER_SLOT;
        return index >= 0 && index < buffer.size() ? buffer.get(index) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot == CORE_SLOT) {
            ItemStack taken = core.split(amount);
            if (!taken.isEmpty()) {
                setChanged();
            }
            return taken;
        }
        ItemStack taken = ContainerHelper.removeItem(buffer, slot - FIRST_BUFFER_SLOT, amount);
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot == CORE_SLOT) {
            ItemStack taken = core;
            core = ItemStack.EMPTY;
            setChanged();
            return taken;
        }
        return ContainerHelper.takeItem(buffer, slot - FIRST_BUFFER_SLOT);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == CORE_SLOT) {
            core = stack;
        } else {
            int index = slot - FIRST_BUFFER_SLOT;
            if (index >= 0 && index < buffer.size()) {
                buffer.set(index, stack);
            }
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Only the core slot takes anything, and only a finished core of this housing's family that
        // has actually locked a target. §4.2: housings hold Cores, not Primed Cores.
        return slot == CORE_SLOT
                && stack.getItem() instanceof CoreItem coreItem
                && coreItem.family() == family
                && Optional.ofNullable(stack.get(HsComponents.CORE_ATTUNEMENT))
                        .map(attunement -> attunement.target().isPresent())
                        .orElse(false);
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        core = ItemStack.EMPTY;
        buffer.clear();
        buffer = NonNullList.withSize(buffer.size(), ItemStack.EMPTY);
        setChanged();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        refreshClaim();
    }

    // ---------------------------------------------------------------- automation faces

    @Override
    public int[] getSlotsForFace(Direction direction) {
        int[] slots = new int[buffer.size()];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = FIRST_BUFFER_SLOT + i;
        }
        return slots;
    }

    /** A housing has no input. Nothing may ever be pushed in by automation — including a core. */
    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return false;
    }

    /** Output only, and never the core: a hopper must not be able to steal what a player socketed. */
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
        return slot != CORE_SLOT;
    }

    // ---------------------------------------------------------------- menu

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new CoreHousingMenu(containerId, playerInventory, this, openData());
    }

    @Override
    public CoreHousingOpenData getScreenOpeningData(ServerPlayer player) {
        return openData();
    }

    /**
     * §4.3 — the snapshot the client's core slot refuses duplicates against. Computed at open time,
     * which is enough: the server re-checks live on every click, so a claim made while the screen is
     * open is still refused, just one round trip later.
     */
    private CoreHousingOpenData openData() {
        List<net.minecraft.resources.Identifier> claimed = level instanceof ServerLevel serverLevel
                ? ActiveCores.claimedTargets(serverLevel, family, worldPosition)
                : List.of();
        return new CoreHousingOpenData(family, buffer.size(), claimed);
    }

    // ---------------------------------------------------------------- lifecycle

    /**
     * Drops the core and the buffer, and releases the §4.3 claim. Runs while the block entity still
     * exists, which {@code affectNeighborsAfterRemoval} does not — by the time that fires the
     * contents would already be unreachable.
     */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel && claimedKey != null) {
            ActiveCores.release(serverLevel, claimedKey, worldPosition);
            claimedKey = null;
        }
        if (level != null) {
            Containers.dropContents(level, pos, this);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("version", CURRENT_VERSION);
        output.putLong("last_settled", lastSettled);
        output.putDouble("stored_experience", storedExperience);
        output.putInt("buffer_size", buffer.size());
        if (!core.isEmpty()) {
            output.store("core", ItemStack.CODEC, core);
        }
        ContainerHelper.saveAllItems(output.child("buffer"), buffer, true);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        lastSettled = input.getLongOr("last_settled", 0L);
        storedExperience = input.getDoubleOr("stored_experience", 0.0);
        core = input.read("core", ItemStack.CODEC).orElse(ItemStack.EMPTY);

        // Sized to whichever is larger, the config or what this housing was saved with. Lowering
        // housing_slots on an existing world must not silently delete whatever was in the slots it
        // removes; the extra slots survive until the player empties them.
        int configured = HsConfigManager.get().core().housingSlots();
        int saved = input.getIntOr("buffer_size", configured);
        NonNullList<ItemStack> loaded = NonNullList.withSize(Math.max(configured, saved), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input.childOrEmpty("buffer"), loaded);
        buffer = loaded;

        // The claim is world state and the housing is block state; a load is the moment they have to
        // be reconciled, and refreshClaim is the only thing that does it.
        claimedKey = null;
        refreshClaim();
    }
}
