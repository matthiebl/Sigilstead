package com.sigilstead.gametest;

import com.sigilstead.item.SealedTomeItem;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * DESIGN.md §3.3 Teach — exercises the real dispatch path, {@code Villager#mobInteract}, rather than
 * calling {@link SealedTomeItem#tryTeach} directly. That distinction matters here specifically: a
 * plain {@code Item#interactLivingEntity} override looked correct in isolation but was silently
 * unreachable because {@code Player#interactOn} calls {@code entity.interact(...)} — which resolves
 * to {@code mobInteract} opening the trade screen — before it ever tries the held item. Only a test
 * that goes through {@code mobInteract} itself, the way a real right-click does, would have caught
 * that; a test calling the item's own method directly would have passed while the feature stayed
 * broken in play. See {@link com.sigilstead.mixin.SealedTomeTeachMixin}.
 */
public class CodexTeachGameTests {

    @GameTest
    public void teachingThroughMobInteractSellsTheBookAndConsumesTomeAndEmerald(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, 1, 2, 1);
        villager.setVillagerData(
                villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(1));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, SealedTomeItem.create(Enchantments.MENDING, 1));
        player.getInventory().add(new ItemStack(Items.EMERALD, 5));

        // The exact call vanilla's own interaction dispatch makes — see the class javadoc.
        InteractionResult result = villager.mobInteract(player, InteractionHand.MAIN_HAND);

        boolean consumed = result.consumesAction();
        boolean tomeGone = player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
        int emeraldsLeft = countEmeralds(player);
        boolean sellsMending = offerSellsMending(villager);

        helper.succeedIf(() -> {
            if (!consumed) {
                throw new AssertionError("mobInteract returned " + result + " — the trade screen would have opened instead of teaching");
            }
            if (!tomeGone) {
                throw new AssertionError("the Sealed Tome was not consumed by a successful Teach");
            }
            if (emeraldsLeft != 4) {
                throw new AssertionError("expected 1 emerald spent (5 -> 4), player has " + emeraldsLeft);
            }
            if (!sellsMending) {
                throw new AssertionError("villager does not sell a Mending I enchanted book after being taught");
            }
        });
    }

    @GameTest
    public void mobInteractStillOpensTradingWhenNotHoldingASealedTome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, 1, 2, 1);
        villager.setVillagerData(
                villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.LIBRARIAN).withLevel(1));
        villager.getOffers().add(new MerchantOffer(
                new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 1), new ItemStack(Items.BOOK), 12, 2, 0.05f));

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        InteractionResult result = villager.mobInteract(player, InteractionHand.MAIN_HAND);

        helper.succeedIf(() -> {
            if (!result.consumesAction()) {
                throw new AssertionError("expected mobInteract to still consume and open trading for an empty hand, got " + result);
            }
        });
    }

    private static int countEmeralds(ServerPlayer player) {
        int total = 0;
        for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
            if (s.is(Items.EMERALD)) {
                total += s.getCount();
            }
        }
        return total;
    }

    private static boolean offerSellsMending(Villager villager) {
        for (MerchantOffer offer : villager.getOffers()) {
            if (offer.getResult().is(Items.ENCHANTED_BOOK)
                    && EnchantmentHelper.getEnchantmentsForCrafting(offer.getResult()).keySet().stream()
                            .anyMatch(holder -> holder.is(Enchantments.MENDING))) {
                return true;
            }
        }
        return false;
    }
}
