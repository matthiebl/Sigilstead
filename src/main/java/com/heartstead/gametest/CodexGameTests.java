package com.heartstead.gametest;

import com.heartstead.codex.Codex;
import com.heartstead.codex.CodexMenu;
import com.heartstead.registry.HsAttachments;
import com.heartstead.registry.HsComponents;
import com.heartstead.registry.HsItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * DESIGN.md §3.3 — the Archive, Tome and capacity flows all move or consume items, so they get
 * GameTests written first (CONVENTIONS.md §8): a book that survives archiving, or a capacity item
 * that buys an upgrade without being spent, are exactly the class of bug the Vault suite already
 * catches for §2.
 */
public class CodexGameTests {

    private static final ResourceKey<Enchantment> MENDING = Enchantments.MENDING;

    @GameTest
    public void archivingAnEnchantedBookConsumesItAndRecordsTheEnchantment(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CodexMenu menu = new CodexMenu(0, player.getInventory());
        menu.slots.getFirst().set(mendingBook(helper));

        menu.handleArchive(player);

        int archivedLevel = Codex.get(player).levelOf(MENDING);
        ItemStack leftInSlot = menu.slots.getFirst().getItem();

        helper.succeedIf(() -> {
            if (archivedLevel != 1) {
                throw new AssertionError("expected Mending I to be archived, archive has level " + archivedLevel);
            }
            if (!leftInSlot.isEmpty()) {
                throw new AssertionError("the archive slot still holds " + leftInSlot + " after a successful Archive");
            }
        });
    }

    @GameTest
    public void archivingAPlainBookDoesNothingAndLeavesTheItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CodexMenu menu = new CodexMenu(0, player.getInventory());
        menu.slots.getFirst().set(new ItemStack(Items.BOOK));

        menu.handleArchive(player);

        ItemStack leftInSlot = menu.slots.getFirst().getItem();
        helper.succeedIf(() -> {
            if (leftInSlot.isEmpty()) {
                throw new AssertionError("a plain Book with no enchantments was consumed by Archive");
            }
        });
    }

    @GameTest
    public void buyingCapacityConsumesTheEchoShardAndAdvancesTheTier(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CodexMenu menu = new CodexMenu(0, player.getInventory());
        menu.slots.get(1).set(new ItemStack(Items.ECHO_SHARD));

        menu.handleBuyCapacity(player);

        int tierAfter = Codex.get(player).capacityTier();
        ItemStack leftInSlot = menu.slots.get(1).getItem();

        helper.succeedIf(() -> {
            if (tierAfter != 1) {
                throw new AssertionError("expected capacity tier 1 after an Echo Shard, got " + tierAfter);
            }
            if (!leftInSlot.isEmpty()) {
                throw new AssertionError("the capacity slot still holds " + leftInSlot + " after a successful upgrade");
            }
        });
    }

    @GameTest
    public void sealingAnArchivedEnchantmentConsumesTheTomeAndProducesASealedTome(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setAttached(HsAttachments.CODEX_ARCHIVE, Codex.get(player).withArchived(MENDING, 1));

        CodexMenu menu = new CodexMenu(0, player.getInventory());
        menu.slots.get(2).set(new ItemStack(HsItems.TOME));

        menu.handleSeal(player, MENDING);

        ItemStack tomeSlot = menu.slots.get(2).getItem();
        ItemStack outputSlot = menu.slots.get(3).getItem();

        helper.succeedIf(() -> {
            if (!tomeSlot.isEmpty()) {
                throw new AssertionError("the Tome slot still holds " + tomeSlot + " after a successful Seal");
            }
            if (!outputSlot.is(HsItems.SEALED_TOME)) {
                throw new AssertionError("expected a Sealed Tome in the output slot, got " + outputSlot);
            }
            var data = outputSlot.get(HsComponents.SEALED_TOME_DATA);
            if (data == null || !data.enchantment().equals(MENDING) || data.level() != 1) {
                throw new AssertionError("Sealed Tome did not carry Mending I, got " + data);
            }
        });
    }

    @GameTest
    public void sealingAnUnarchivedEnchantmentDoesNothing(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        CodexMenu menu = new CodexMenu(0, player.getInventory());
        menu.slots.get(2).set(new ItemStack(HsItems.TOME));

        menu.handleSeal(player, MENDING);

        ItemStack tomeSlot = menu.slots.get(2).getItem();
        helper.succeedIf(() -> {
            if (tomeSlot.isEmpty()) {
                throw new AssertionError("a Tome was consumed sealing an enchantment that was never archived");
            }
        });
    }

    private static ItemStack mendingBook(GameTestHelper helper) {
        Holder<Enchantment> mending = helper.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(MENDING);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(mending, 1));
        return book;
    }
}
