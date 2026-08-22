package com.sigilstead;

import com.sigilstead.advancement.HsTriggers;
import com.sigilstead.core.CoreImprint;
import com.sigilstead.economy.SigilBossDrops;
import com.sigilstead.economy.SigilLoot;
import com.sigilstead.enchantment.EnchantmentBlockLoot;
import com.sigilstead.enchantment.EnchantmentBookLoot;
import com.sigilstead.lives.LivesSystem;
import com.sigilstead.registry.HsAttachments;
import com.sigilstead.registry.HsBlockEntities;
import com.sigilstead.registry.HsBlocks;
import com.sigilstead.registry.HsComponents;
import com.sigilstead.registry.HsCreativeTabs;
import com.sigilstead.registry.HsItems;
import com.sigilstead.registry.HsMenuTypes;
import com.sigilstead.registry.HsPayloads;
import com.sigilstead.registry.HsRecipes;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common entrypoint. Runs on both client and dedicated server.
 *
 * <p>Registration order matters and is asserted here rather than left to class-load timing:
 * components before items (items reference component types), items before blocks (block items),
 * blocks before block entities.
 *
 * <p>Anything touching rendering, screens or keybinds belongs in {@code src/client}, not here.
 * See {@code docs/CONVENTIONS.md}.
 */
public class Sigilstead implements ModInitializer {

    public static final String MOD_ID = "sigilstead";
    public static final Logger LOG = LoggerFactory.getLogger("Sigilstead");

    /** Every id in the mod goes through here. Never build an Identifier by hand. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        HsComponents.register();
        HsItems.register();
        HsBlocks.register();
        HsBlockEntities.register();
        HsMenuTypes.register();
        HsRecipes.register();
        HsPayloads.register();
        HsCreativeTabs.register();
        HsAttachments.register();
        HsTriggers.register();
        SigilLoot.register();
        SigilBossDrops.register();
        EnchantmentBlockLoot.register();
        EnchantmentBookLoot.register();
        LivesSystem.register();
        CoreImprint.register();

        LOG.info("Sigilstead loaded.");
    }
}
