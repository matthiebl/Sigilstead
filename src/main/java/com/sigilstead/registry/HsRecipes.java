package com.sigilstead.registry;

import com.sigilstead.Sigilstead;
import com.sigilstead.core.CoreUpgradeRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * Recipe serializer registration. Only DESIGN.md §4.3's tier upgrade needs one — every other recipe
 * in the pack is ordinary shaped or shapeless JSON, which is where CONVENTIONS.md §6 wants them.
 */
public final class HsRecipes {

    private HsRecipes() {
    }

    /** DESIGN.md §4.3 / §12.4 — 4 Blaze Powder for tier II, 2 Netherite Scrap for tier III. */
    public static final RecipeSerializer<CoreUpgradeRecipe> CORE_UPGRADE = Registry.register(
            BuiltInRegistries.RECIPE_SERIALIZER,
            ResourceKey.create(Registries.RECIPE_SERIALIZER, Sigilstead.id("core_upgrade")),
            new RecipeSerializer<>(CoreUpgradeRecipe.CODEC, CoreUpgradeRecipe.STREAM_CODEC));

    /** Forces class load (and so the static registration above) at an explicit, ordered point. */
    public static void register() {
    }
}
