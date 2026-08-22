package com.sigilstead.villager;

import com.sigilstead.registry.HsAttachments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * DESIGN.md §3.3 Teach / §7.2 — a taught enchanted book stays sellable across vanilla's {@code Offers}
 * regeneration on level-up and restock. The taught set lives in
 * {@link HsAttachments#TAUGHT_ENCHANTMENTS}, a persistent, codec-backed attachment on the villager
 * entity (not derived from {@code Offers}), and {@link #ensureOfferPresent} re-applies every entry in
 * it whenever a reset is detected — the same mechanic the §7.2 proof of concept proved out with a
 * single fixed placeholder trade.
 */
public final class TaughtTradeInjector {

    private TaughtTradeInjector() {
    }

    /** §3.3 Teach — permanently adds {@code enchantment} at {@code level} to what this villager sells, for {@code emeralds}. */
    public static void teach(Villager villager, ResourceKey<Enchantment> enchantment, Holder<Enchantment> holder, int level, int emeralds) {
        TaughtEnchantments current = ((AttachmentTarget) villager)
                .getAttachedOrCreate(HsAttachments.TAUGHT_ENCHANTMENTS, TaughtEnchantments::initial);
        ((AttachmentTarget) villager).setAttached(HsAttachments.TAUGHT_ENCHANTMENTS, current.withTaught(enchantment, level));
        ensureOfferPresent(villager, holder, level, emeralds);
    }

    /**
     * Re-applies every offer this villager has been taught, using the currently attached
     * {@link Holder} for each enchantment. Called on every entry the villager already knows about,
     * so no price or level information needs to be looked up again.
     */
    public static void ensureOfferPresent(Villager villager) {
        TaughtEnchantments taught = ((AttachmentTarget) villager).getAttached(HsAttachments.TAUGHT_ENCHANTMENTS);
        if (taught == null || taught.taught().isEmpty()) {
            return;
        }
        var registries = villager.level().registryAccess();
        for (var entry : taught.taught().entrySet()) {
            Holder<Enchantment> holder = registries.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                    .getOrThrow(entry.getKey());
            int level = entry.getValue();
            int emeralds = com.sigilstead.codex.Codex.priceFor(holder, entry.getKey(), level);
            ensureOfferPresent(villager, holder, level, emeralds);
        }
    }

    private static void ensureOfferPresent(Villager villager, Holder<Enchantment> holder, int level, int emeralds) {
        ItemStack result = enchantedBook(holder, level);
        MerchantOffers offers = villager.getOffers();
        for (MerchantOffer offer : offers) {
            if (matches(offer.getResult(), holder, level)) {
                return;
            }
        }
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), result, 12, 2, 0.05f));
    }

    private static boolean matches(ItemStack stack, Holder<Enchantment> holder, int level) {
        return stack.is(Items.ENCHANTED_BOOK) && EnchantmentHelper.getItemEnchantmentLevel(holder, stack) == level;
    }

    private static ItemStack enchantedBook(Holder<Enchantment> holder, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, level));
        return book;
    }
}
