package com.heartstead;

import com.heartstead.registry.HsItems;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
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

    /** Every id in the mod goes through here. Never build a ResourceLocation by hand. */
    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        // TODO Phase 0: HsComponents.register();
        HsItems.register();
        // TODO Phase 3: HsBlocks.register();
        // TODO Phase 3: HsBlockEntities.register();
        // TODO Phase 3: HsScreenHandlers.register();
        // TODO Phase 1: HsAttachments.register();   // player heart state
        // TODO Phase 4: HsCoreTypes.register();

        LOG.info("Heartstead loaded.");
    }
}
