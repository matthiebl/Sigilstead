package com.heartstead.registry;

import com.heartstead.Heartstead;
import com.heartstead.core.CoreFamily;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * Creative mode tab registration. All of Heartstead's items live in one dedicated tab rather than
 * being scattered across vanilla's Ingredients/Tools tabs.
 */
public final class HsCreativeTabs {

    private HsCreativeTabs() {
    }

    public static final ResourceKey<CreativeModeTab> MAIN =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Heartstead.id("main"));

    public static void register() {
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, MAIN, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.heartstead.main"))
                .icon(() -> new ItemStack(HsItems.SIGIL))
                .displayItems((parameters, output) -> {
                    // DESIGN.md §1, in spine order: the found Sigil, its three children, then the
                    // three dimensional Vault Sigils.
                    output.accept(HsItems.SIGIL);
                    output.accept(HsItems.CORE_SIGIL);
                    output.accept(HsItems.HEART_SIGIL);
                    output.accept(HsItems.VAULT_SIGIL);
                    output.accept(HsItems.OVERWORLD_VAULT_SIGIL);
                    output.accept(HsItems.NETHER_VAULT_SIGIL);
                    output.accept(HsItems.END_VAULT_SIGIL);
                    // §2 — the Vault's blocks and the §2.2 access ladder, in tier order.
                    output.accept(HsBlocks.VAULT_ANCHOR);
                    output.accept(HsBlocks.LINKED_FUNNEL);
                    output.accept(HsItems.SATCHEL);
                    output.accept(HsItems.VAULT_POUCH);
                    // §3.3 — the Codex and its Tome. The Sealed Tome is deliberately absent: it only
                    // ever exists pre-filled by the Empower step, so a blank creative one means nothing.
                    output.accept(HsBlocks.CODEX);
                    output.accept(HsItems.TOME);
                    // §4 — the four housings, then the Primed Cores in §12.4's table order. Finished
                    // Cores are deliberately absent: a Core's whole identity is the target it learned
                    // by playing (§4.1), so a creative one with no target would be a nameless item
                    // that no housing accepts. Prime one and imprint it, exactly like everyone else.
                    for (var housing : HsBlocks.HOUSINGS) {
                        output.accept(housing);
                    }
                    for (CoreFamily family : CoreFamily.values()) {
                        output.accept(HsItems.primedCore(family));
                    }
                })
                .build());
    }
}
