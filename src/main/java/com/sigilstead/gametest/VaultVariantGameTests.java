package com.sigilstead.gametest;

import com.sigilstead.vault.Vault;
import com.sigilstead.vault.VaultData;
import com.sigilstead.vault.VaultKey;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * DESIGN.md §2.5 / §2.6 — conservation tests for stacks that carry components. The Vault used to key
 * its contents on the bare {@link Item}, so a half-used enchanted pickaxe went in and a factory-fresh
 * one came out, and ten differently-enchanted swords collapsed into one anonymous count. That is item
 * destruction, which §2.6 says is the one thing players will not forgive — so it gets its own suite.
 *
 * <p>Two invariants, and they pull in opposite directions on purpose (see {@link VaultKey}):
 * <b>components are preserved and addressable</b> — every distinct variant is its own row that keeps
 * its own data — while <b>capacity is still counted per item type</b>, so enchanting a sword never
 * costs one of §2.3's distinct-type slots.
 *
 * <p>As in {@link VaultGameTests}, every test uses its own item type and computes headroom rather
 * than asserting absolute Vault state, because the whole run shares one {@link VaultData}.
 */
public class VaultVariantGameTests {

    /** The bug in one test: deposit a damaged, enchanted, renamed tool and get exactly it back. */
    @GameTest
    public void enchantedAndDamagedToolSurvivesRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ensureDistinctTypeHeadroom(level, Items.DIAMOND_PICKAXE);

        ItemStack original = sharpened(level, Items.DIAMOND_PICKAXE, 3);
        original.setDamageValue(781);
        ItemStack deposited = original.copy();

        ItemStack leftover = Vault.deposit(level, deposited);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int withdrawn = Vault.withdrawInto(level, VaultKey.of(original), 1, player.getInventory());
        ItemStack returned = findInInventory(player.getInventory(), Items.DIAMOND_PICKAXE);

        helper.succeedIf(() -> {
            if (!leftover.isEmpty()) {
                throw new AssertionError("the pickaxe did not fit: " + leftover);
            }
            if (withdrawn != 1) {
                throw new AssertionError("withdrew " + withdrawn + " pickaxes, expected 1");
            }
            if (returned.isEmpty()) {
                throw new AssertionError("nothing came back out of the vault");
            }
            if (!ItemStack.isSameItemSameComponents(returned, original)) {
                throw new AssertionError("withdrawn stack lost its components: got " + returned
                        + " with " + returned.getComponents() + ", expected " + original.getComponents());
            }
            if (returned.getDamageValue() != 781) {
                throw new AssertionError("durability reset: damage " + returned.getDamageValue() + ", expected 781");
            }
        });
    }

    /**
     * The §2.3 half of the rule: ten differently-enchanted swords are ten addressable rows but a
     * single distinct type, and their counts pool against that one type's depth allowance.
     */
    @GameTest
    public void tenEnchantedSwordsAreTenRowsButOneDistinctType(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.DIAMOND_SWORD;
        ensureDistinctTypeHeadroom(level, item);

        int distinctBefore = Vault.get(level).distinctTypeCount();
        int rowsBefore = Vault.get(level).contents().size();

        for (int level_ = 1; level_ <= 10; level_++) {
            ItemStack leftover = Vault.deposit(level, sharpened(level, item, level_));
            if (!leftover.isEmpty()) {
                throw new AssertionError("sword " + level_ + " did not fit: " + leftover);
            }
        }

        int distinctAfter = Vault.get(level).distinctTypeCount();
        int rowsAfter = Vault.get(level).contents().size();
        int pooledCount = Vault.get(level).count(item);
        int oneVariantCount = Vault.get(level).count(VaultKey.of(sharpened(level, item, 4)));

        helper.succeedIf(() -> {
            if (distinctAfter != distinctBefore + 1) {
                throw new AssertionError("ten sword variants spent " + (distinctAfter - distinctBefore)
                        + " distinct types, expected 1");
            }
            if (rowsAfter != rowsBefore + 10) {
                throw new AssertionError("ten sword variants produced " + (rowsAfter - rowsBefore)
                        + " grid rows, expected 10");
            }
            if (pooledCount != 10) {
                throw new AssertionError("per-item total = " + pooledCount + ", expected 10");
            }
            if (oneVariantCount != 1) {
                throw new AssertionError("one variant's own count = " + oneVariantCount + ", expected 1");
            }
        });
    }

    /** Withdrawing one variant must not touch another, and must never hand back the wrong one. */
    @GameTest
    public void withdrawingOneVariantLeavesTheOthersAlone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.IRON_AXE;
        ensureDistinctTypeHeadroom(level, item);

        ItemStack plain = new ItemStack(item);
        ItemStack enchanted = sharpened(level, item, 2);
        Vault.deposit(level, plain.copy());
        Vault.deposit(level, enchanted.copy());

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        int withdrawn = Vault.withdrawInto(level, VaultKey.of(enchanted), 1, player.getInventory());
        ItemStack returned = findInInventory(player.getInventory(), item);

        int plainLeft = Vault.get(level).count(VaultKey.of(plain));
        int enchantedLeft = Vault.get(level).count(VaultKey.of(enchanted));

        helper.succeedIf(() -> {
            if (withdrawn != 1 || !ItemStack.isSameItemSameComponents(returned, enchanted)) {
                throw new AssertionError("asked for the enchanted axe, got " + returned);
            }
            if (enchantedLeft != 0) {
                throw new AssertionError("enchanted variant still holds " + enchantedLeft + ", expected 0");
            }
            if (plainLeft != 1) {
                throw new AssertionError("plain variant holds " + plainLeft + ", expected 1 — untouched");
            }
        });
    }

    /**
     * Two identical enchanted stacks are one row with a count of two, not two rows. This is what
     * keeps the §2.4 grid from filling with duplicate cells for genuinely interchangeable items.
     */
    @GameTest
    public void identicalVariantsStackIntoOneRow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.GOLDEN_SHOVEL;
        ensureDistinctTypeHeadroom(level, item);

        int rowsBefore = Vault.get(level).contents().size();
        Vault.deposit(level, sharpened(level, item, 1));
        Vault.deposit(level, sharpened(level, item, 1));
        int rowsAfter = Vault.get(level).contents().size();
        int count = Vault.get(level).count(VaultKey.of(sharpened(level, item, 1)));

        helper.succeedIf(() -> {
            if (rowsAfter != rowsBefore + 1) {
                throw new AssertionError("identical variants made " + (rowsAfter - rowsBefore) + " rows, expected 1");
            }
            if (count != 2) {
                throw new AssertionError("row count = " + count + ", expected 2");
            }
        });
    }

    /** §2.6's persistence case, for a variant: components must survive the save/load codec. */
    @GameTest
    public void componentsSurviveTheCodecRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Item item = Items.IRON_SWORD;
        ensureDistinctTypeHeadroom(level, item);

        ItemStack stack = sharpened(level, item, 5);
        stack.setDamageValue(11);
        Vault.deposit(level, stack.copy());

        VaultData decoded = roundTrip(level);
        int decodedCount = decoded.count(VaultKey.of(stack));

        helper.succeedIf(() -> {
            if (decodedCount != 1) {
                throw new AssertionError("the enchanted, damaged sword did not survive the codec: count "
                        + decodedCount + ", expected 1");
            }
        });
    }

    /**
     * A v2 blob — contents keyed on bare item ids — must still load, as componentless variants. Old
     * worlds cannot carry components anyway, since the map they were saved in had nowhere to put any.
     */
    @GameTest
    public void legacyContentsBlobStillLoads(GameTestHelper helper) {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("version", 2);
        legacy.putInt("sigils_spent", 4);
        CompoundTag contents = new CompoundTag();
        contents.putInt("minecraft:diamond", 17);
        contents.putInt("minecraft:oak_log", 3);
        legacy.put("contents", contents);

        VaultData decoded = VaultData.TYPE.codec().parse(NbtOps.INSTANCE, legacy).getOrThrow();

        helper.succeedIf(() -> {
            if (decoded.count(VaultKey.of(Items.DIAMOND)) != 17 || decoded.count(Items.DIAMOND) != 17) {
                throw new AssertionError("v2 diamonds did not migrate: " + decoded.count(Items.DIAMOND));
            }
            if (decoded.distinctTypeCount() != 2) {
                throw new AssertionError("v2 blob loaded " + decoded.distinctTypeCount() + " types, expected 2");
            }
            if (decoded.sigilsSpent() != 4) {
                throw new AssertionError("v2 sigils_spent lost: " + decoded.sigilsSpent());
            }
        });
    }

    private static ItemStack sharpened(ServerLevel level, Item item, int enchantmentLevel) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS), enchantmentLevel);
        return stack;
    }

    /**
     * Encodes and decodes through a <em>registry-aware</em> ops, which is what
     * {@code DimensionDataStorage} actually saves with. Plain {@link NbtOps} cannot write an
     * enchantment — the component's codec needs the registry to resolve the holder — so a bare
     * {@code NbtOps} round trip here would fail on correct code.
     */
    private static VaultData roundTrip(ServerLevel level) {
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        DataResult<Tag> encoded = VaultData.TYPE.codec().encodeStart(ops, Vault.get(level));
        return VaultData.TYPE.codec().parse(ops, encoded.getOrThrow()).getOrThrow();
    }

    /** See {@link VaultGameTests#ensureDistinctTypeHeadroom} — one shared Vault across the whole run. */
    private static void ensureDistinctTypeHeadroom(ServerLevel level, Item item) {
        if (Vault.get(level).count(item) > 0) {
            return;
        }
        while (Vault.get(level).distinctTypeCount() >= Vault.capacityTier(level).distinctTypeCap()) {
            Vault.spendSigil(level);
        }
    }

    private static ItemStack findInInventory(Inventory inventory, Item item) {
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
