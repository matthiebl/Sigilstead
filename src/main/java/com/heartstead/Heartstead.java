package com.heartstead;

import com.heartstead.economy.HeartShardLoot;
import com.heartstead.registry.HsAttachments;
import com.heartstead.registry.HsComponents;
import com.heartstead.registry.HsCreativeTabs;
import com.heartstead.registry.HsItems;
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
public class Heartstead implements ModInitializer {

    public static final String MOD_ID = "heartstead";
    public static final Logger LOG = LoggerFactory.getLogger("Heartstead");

    /** Every id in the mod goes through here. Never build an Identifier by hand. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        HsComponents.register();
        HsItems.register();
        HsCreativeTabs.register();
        HsAttachments.register();
        HeartShardLoot.register();
        // TODO Phase 3: HsBlocks.register();
        // TODO Phase 3: HsBlockEntities.register();
        // TODO Phase 3: HsScreenHandlers.register();
        // TODO Phase 4: HsCoreTypes.register();

        LOG.info("Heartstead loaded.");
    }
}
