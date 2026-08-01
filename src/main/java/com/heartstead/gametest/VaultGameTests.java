package com.heartstead.gametest;

import com.heartstead.vault.Vault;
import com.heartstead.vault.VaultCapacityTier;
import com.heartstead.vault.VaultData;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * DESIGN.md §2.5 — item-conservation GameTests for the Vault, written before the storage layer
 * itself (CONVENTIONS.md §8). The Vault is world state shared by every player on the server
 * (DESIGN.md §2), so these run against the real {@link Vault} facade rather than mocking it out.
 *
 * <p>The GameTest framework runs every method in this class against the same underlying overworld
 * within one {@code runGametest} invocation, and {@link VaultData} is a singleton keyed off that
 * overworld — so each test below uses its own dedicated item type (or computes capacity headroom
 * dynamically) rather than asserting on absolute vault state, to stay independent of run order.
 */
public class VaultGameTests {

    /** Deposit N, withdraw N: the vault's stock for that item must return to exactly where it started. */
    @GameTest
    public void depositThenWithdrawConservesExactCount(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.DIAMOND;
        ensureDistinctTypeHeadroom(level, item);
        int baseline = Vault.get(level).count(item);

        ItemStack leftover = Vault.deposit(level, new ItemStack(item, 40));
        boolean allDeposited = leftover.isEmpty();
        int afterDeposit = Vault.get(level).count(item);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int withdrawn = Vault.withdrawInto(level, item, 40, player.getInventory());
        int afterWithdraw = Vault.get(level).count(item);

        helper.succeedIf(() -> {
            if (!allDeposited) {
                throw new AssertionError("deposit of 40 diamonds left a nonempty remainder: " + leftover);
            }
            if (afterDeposit != baseline + 40) {
                throw new AssertionError("vault count after deposit = " + afterDeposit + ", expected " + (baseline + 40));
            }
            if (withdrawn != 40) {
                throw new AssertionError("withdrew " + withdrawn + " of 40 requested");
            }
            if (afterWithdraw != baseline) {
                throw new AssertionError("vault count after withdraw = " + afterWithdraw + ", expected baseline " + baseline);
            }
            if (countInInventory(player.getInventory(), item) != 40) {
                throw new AssertionError("player inventory holds " + countInInventory(player.getInventory(), item) + " diamonds, expected 40");
            }
        });
    }

    /** A withdraw that can't fit in the player's inventory must not remove anything from the Vault — no loss, no duplication. */
    @GameTest
    public void withdrawIntoFullInventoryLosesNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.EMERALD;
        ensureDistinctTypeHeadroom(level, item);

        Vault.deposit(level, new ItemStack(item, 5));
        int beforeAttempt = Vault.get(level).count(item);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        fillInventory(player.getInventory());

        int withdrawn = Vault.withdrawInto(level, item, 5, player.getInventory());
        int afterAttempt = Vault.get(level).count(item);

        helper.succeedIf(() -> {
            if (withdrawn != 0) {
                throw new AssertionError("withdrew " + withdrawn + " into a full inventory, expected 0");
            }
            if (afterAttempt != beforeAttempt) {
                throw new AssertionError("vault count changed from " + beforeAttempt + " to " + afterAttempt
                        + " despite a failed withdraw — items were lost or duplicated");
            }
        });
    }

    /** Depositing past the per-type stack-depth cap (§2.3) must reject exactly the overflow, keeping the rest in the Vault, not voiding it. */
    @GameTest
    public void depositStopsAtStackDepthCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.IRON_INGOT;
        ensureDistinctTypeHeadroom(level, item);

        VaultCapacityTier tier = Vault.capacityTier(level);
        int perTypeCap = tier.stackDepthCap() * new ItemStack(item).getMaxStackSize();
        int existing = Vault.get(level).count(item);
        int room = Math.max(0, perTypeCap - existing);

        ItemStack overflowAttempt = new ItemStack(item, room + 100);
        ItemStack leftover = Vault.deposit(level, overflowAttempt);
        int afterDeposit = Vault.get(level).count(item);

        helper.succeedIf(() -> {
            if (leftover.getCount() != 100) {
                throw new AssertionError("depositing room+100 left a remainder of " + leftover.getCount() + ", expected 100");
            }
            if (afterDeposit != perTypeCap) {
                throw new AssertionError("vault count at the stack-depth boundary = " + afterDeposit + ", expected cap " + perTypeCap);
            }
        });
    }

    /**
     * Filling the Vault to its distinct-type cap (§2.3) and depositing one more, never-before-seen
     * type must be refused outright — the whole stack comes back, not a partial deposit.
     */
    @GameTest
    public void depositOfNewTypeRefusedAtDistinctTypeCapacity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VaultCapacityTier tier = Vault.capacityTier(level);
        int room = Math.max(0, tier.distinctTypeCap() - Vault.get(level).distinctTypeCount());
        // Fill exactly up to the cap using a private, never-reused pool of distinct fresh item types.
        Item[] fillerTypes = distinctFillerItems(room);
        for (Item filler : fillerTypes) {
            Vault.deposit(level, new ItemStack(filler, 1));
        }
        int distinctAtCap = Vault.get(level).distinctTypeCount();

        Item brandNewType = Items.NETHER_STAR;
        ItemStack attempt = new ItemStack(brandNewType, 1);
        ItemStack leftover = Vault.deposit(level, attempt);
        boolean vaultNowHoldsNewType = Vault.get(level).count(brandNewType) > 0;

        // This test deliberately fills the (shared, singleton) vault to its distinct-type cap;
        // reopen headroom afterward so later tests in the same run aren't starved by it.
        for (int i = 0; i < 50; i++) {
            Vault.spendSigil(level);
        }

        helper.succeedIf(() -> {
            if (distinctAtCap != tier.distinctTypeCap()) {
                throw new AssertionError("failed to fill to the distinct-type cap: at " + distinctAtCap + " of " + tier.distinctTypeCap());
            }
            if (leftover.getCount() != 1 || vaultNowHoldsNewType) {
                throw new AssertionError("a brand-new type was accepted past the distinct-type cap");
            }
        });
    }

    /**
     * DESIGN.md §2.5 "mid-transfer server shutdown" — round-tripping the Vault through its own
     * versioned codec (what a save-to-disk actually does) after a deposit and after a withdraw must
     * reproduce the exact same contents. Since all mutation here is a single synchronous method call
     * with no yield point, there is no in-between state a real shutdown could ever catch mid-write.
     */
    @GameTest
    public void codecRoundTripSurvivesAfterDepositAndWithdraw(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.GOLD_INGOT;
        ensureDistinctTypeHeadroom(level, item);

        Vault.deposit(level, new ItemStack(item, 12));
        int afterDeposit = Vault.get(level).count(item);
        boolean roundTripMatchesAfterDeposit = roundTripCount(level, item) == afterDeposit;

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vault.withdrawInto(level, item, 5, player.getInventory());
        int afterWithdraw = Vault.get(level).count(item);
        boolean roundTripMatchesAfterWithdraw = roundTripCount(level, item) == afterWithdraw;

        helper.succeedIf(() -> {
            if (!roundTripMatchesAfterDeposit) {
                throw new AssertionError("codec round trip after deposit did not match live vault state");
            }
            if (!roundTripMatchesAfterWithdraw) {
                throw new AssertionError("codec round trip after withdraw did not match live vault state");
            }
        });
    }

    /**
     * Two players both attempting to withdraw the Vault's last unit of a stack "at the same time" —
     * on a single-threaded server this means back-to-back calls in the same tick. Exactly one must
     * succeed; the total across both inventories and the remaining vault stock must still be 1.
     */
    @GameTest
    public void concurrentWithdrawByTwoPlayersNeverDuplicates(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.NETHERITE_INGOT;
        ensureDistinctTypeHeadroom(level, item);

        Vault.deposit(level, new ItemStack(item, 1));

        ServerPlayer playerA = helper.makeMockServerPlayerInLevel();
        ServerPlayer playerB = helper.makeMockServerPlayerInLevel();

        int gotA = Vault.withdrawInto(level, item, 1, playerA.getInventory());
        int gotB = Vault.withdrawInto(level, item, 1, playerB.getInventory());

        int total = gotA + gotB + Vault.get(level).count(item);

        helper.succeedIf(() -> {
            if (gotA + gotB != 1) {
                throw new AssertionError("two players withdrew a total of " + (gotA + gotB) + " from a single-item vault, expected exactly 1");
            }
            if (total != 1) {
                throw new AssertionError("total across both inventories and the vault = " + total + ", expected 1 (conservation violated)");
            }
        });
    }

    /**
     * {@link VaultData} is a singleton shared by every test in this run, and
     * {@link #depositOfNewTypeRefusedAtDistinctTypeCapacity} deliberately fills it to the
     * distinct-type cap — so any other test depositing a type the Vault doesn't already hold spends
     * Sigils first to guarantee room, regardless of what order the framework runs tests in.
     */
    private static void ensureDistinctTypeHeadroom(ServerLevel level, Item item) {
        if (Vault.get(level).count(item) > 0) {
            return;
        }
        while (Vault.get(level).distinctTypeCount() >= Vault.capacityTier(level).distinctTypeCap()) {
            Vault.spendSigil(level);
        }
    }

    private static int roundTripCount(ServerLevel level, Item item) {
        VaultData live = Vault.get(level);
        DataResult<net.minecraft.nbt.Tag> encoded = VaultData.TYPE.codec().encodeStart(NbtOps.INSTANCE, live);
        CompoundTag tag = (CompoundTag) encoded.getOrThrow();
        VaultData decoded = VaultData.TYPE.codec().parse(NbtOps.INSTANCE, tag).getOrThrow();
        return decoded.count(item);
    }

    private static int countInInventory(Inventory inventory, Item item) {
        int total = 0;
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void fillInventory(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
    }

    /** A pool of distinct, mutually-unused vanilla item types for filling the distinct-type cap in a test. */
    private static Item[] distinctFillerItems(int count) {
        Item[] pool = {
                Items.OAK_LOG, Items.SPRUCE_LOG, Items.BIRCH_LOG, Items.JUNGLE_LOG, Items.ACACIA_LOG,
                Items.DARK_OAK_LOG, Items.MANGROVE_LOG, Items.CHERRY_LOG, Items.CRIMSON_STEM, Items.WARPED_STEM,
                Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.DEEPSLATE,
                Items.SAND, Items.RED_SAND, Items.GRAVEL, Items.CLAY, Items.SNOW_BLOCK,
                Items.ICE, Items.PACKED_ICE, Items.BLUE_ICE, Items.OBSIDIAN, Items.CRYING_OBSIDIAN,
                Items.NETHERRACK, Items.SOUL_SAND, Items.SOUL_SOIL, Items.BASALT, Items.BLACKSTONE,
                Items.END_STONE, Items.PURPUR_BLOCK, Items.PRISMARINE, Items.DARK_PRISMARINE, Items.SEA_LANTERN,
                Items.GLOWSTONE, Items.REDSTONE_BLOCK, Items.LAPIS_BLOCK, Items.COAL_BLOCK, Items.IRON_BLOCK,
                Items.GOLD_BLOCK, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.QUARTZ_BLOCK, Items.BONE_BLOCK,
                Items.HAY_BLOCK, Items.MELON, Items.PUMPKIN, Items.MOSS_BLOCK, Items.MUD,
                Items.PACKED_MUD, Items.MUDDY_MANGROVE_ROOTS, Items.CALCITE, Items.TUFF, Items.DRIPSTONE_BLOCK,
                Items.AMETHYST_BLOCK, Items.SPONGE, Items.WET_SPONGE, Items.TERRACOTTA, Items.BRICKS,
                Items.MOSSY_COBBLESTONE, Items.MOSSY_STONE_BRICKS, Items.CHISELED_STONE_BRICKS, Items.CRACKED_STONE_BRICKS,
                Items.SMOOTH_STONE, Items.POLISHED_GRANITE, Items.POLISHED_DIORITE, Items.POLISHED_ANDESITE,
                Items.POLISHED_DEEPSLATE, Items.DEEPSLATE_BRICKS, Items.DEEPSLATE_TILES, Items.CHISELED_DEEPSLATE,
                Items.COBBLED_DEEPSLATE, Items.TUFF_BRICKS, Items.CHISELED_TUFF, Items.POLISHED_TUFF,
                Items.RESIN_BLOCK, Items.RESIN_BRICKS, Items.CHISELED_RESIN_BRICKS,
                Items.NETHER_BRICK, Items.RED_NETHER_BRICKS, Items.CHISELED_NETHER_BRICKS, Items.CRACKED_NETHER_BRICKS,
                Items.HONEYCOMB_BLOCK, Items.SLIME_BLOCK, Items.HONEY_BLOCK, Items.TARGET, Items.BEDROCK,
                Items.MAGMA_BLOCK, Items.SHROOMLIGHT, Items.WARPED_WART_BLOCK, Items.NETHER_WART_BLOCK,
                Items.POLISHED_BLACKSTONE, Items.POLISHED_BASALT, Items.SMOOTH_BASALT, Items.CHISELED_POLISHED_BLACKSTONE,
                Items.CRACKED_POLISHED_BLACKSTONE_BRICKS, Items.GILDED_BLACKSTONE, Items.POLISHED_BLACKSTONE_BRICKS,
                Items.SMOOTH_QUARTZ, Items.CHISELED_QUARTZ_BLOCK, Items.QUARTZ_PILLAR, Items.SMOOTH_SANDSTONE,
                Items.CHISELED_SANDSTONE, Items.CUT_SANDSTONE, Items.SANDSTONE, Items.RED_SANDSTONE,
                Items.CUT_RED_SANDSTONE, Items.CHISELED_RED_SANDSTONE, Items.SMOOTH_RED_SANDSTONE, Items.NETHER_QUARTZ_ORE,
                Items.ANCIENT_DEBRIS, Items.NETHERITE_BLOCK, Items.RAW_IRON_BLOCK, Items.RAW_GOLD_BLOCK, Items.RAW_COPPER_BLOCK,
        };
        if (count > pool.length) {
            throw new AssertionError("distinctFillerItems pool too small: needed " + count + ", have " + pool.length);
        }
        Item[] result = new Item[count];
        System.arraycopy(pool, 0, result, 0, count);
        return result;
    }
}
