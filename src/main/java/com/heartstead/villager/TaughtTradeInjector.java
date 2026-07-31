package com.heartstead.villager;

import com.heartstead.registry.HsAttachments;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * DESIGN.md §7.2 proof of concept — a single fixed trade injected onto one villager, kept alive
 * across vanilla {@code Offers} regeneration.
 *
 * <p>The real feature (Codex "Teach", §3.3) will inject a taught enchanted book. That system doesn't
 * exist yet, so this POC uses a fixed placeholder offer (1 Diamond for 12 Emeralds) to isolate and
 * prove the persistence mechanic in {@link HsAttachments#TAUGHT_TRADE} — whether the villager has
 * been taught is stored on the entity, not derived from the {@code Offers} list, so it can be
 * re-applied whenever vanilla wipes or regenerates that list.
 */
public final class TaughtTradeInjector {

    private TaughtTradeInjector() {
    }

    /** Marks the villager as taught and ensures the offer is present immediately. */
    public static void teach(Villager villager) {
        ((AttachmentTarget) villager).setAttached(HsAttachments.TAUGHT_TRADE, Boolean.TRUE);
        ensureOfferPresent(villager);
    }

    /** Re-applies the taught offer if the villager was taught but vanilla regenerated {@code Offers}. */
    public static void ensureOfferPresent(Villager villager) {
        if (!Boolean.TRUE.equals(((AttachmentTarget) villager).getAttached(HsAttachments.TAUGHT_TRADE))) {
            return;
        }

        MerchantOffers offers = villager.getOffers();
        for (MerchantOffer offer : offers) {
            if (offer.getResult().is(TAUGHT_RESULT.getItem())) {
                return;
            }
        }

        offers.add(createOffer());
    }

    private static final ItemStack TAUGHT_RESULT = new ItemStack(Items.DIAMOND);

    private static MerchantOffer createOffer() {
        return new MerchantOffer(new ItemCost(Items.EMERALD, 12), TAUGHT_RESULT.copy(), 12, 2, 0.05f);
    }
}
