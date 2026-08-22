package com.sigilstead.registry;

import com.sigilstead.Sigilstead;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * DESIGN.md §3.1/§3.2 — Abundance and Kiln Touch are data-driven enchantments (JSON under
 * {@code data/sigilstead/enchantment/}), not Java-registered objects, so there is nothing to
 * register here. This just names their {@link ResourceKey}s so Java code that needs to check a
 * tool's enchantment level doesn't build the {@link net.minecraft.resources.Identifier} inline.
 */
public final class HsEnchantments {

    public static final ResourceKey<Enchantment> ABUNDANCE =
            ResourceKey.create(Registries.ENCHANTMENT, Sigilstead.id("abundance"));
    public static final ResourceKey<Enchantment> KILN_TOUCH =
            ResourceKey.create(Registries.ENCHANTMENT, Sigilstead.id("kiln_touch"));

    private HsEnchantments() {
    }

    /** The level of {@code key} on {@code stack}, or 0 if absent — resolves the holder from {@code registries} each call. */
    public static int level(HolderLookup.Provider registries, ItemStack stack, ResourceKey<Enchantment> key) {
        Holder<Enchantment> holder = registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }
}
