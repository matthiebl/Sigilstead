package com.heartstead.vault;

import java.util.Optional;
import net.minecraft.core.BlockPos;

/**
 * Client-side cache of the world's Vault Anchor position, kept in common code (it holds no
 * client-only types) so {@link com.heartstead.block.VaultAnchorBlock#canSurvive} can read it from
 * both sides. Only ever written from the client's {@code VaultAnchorPayload} receiver
 * (src/client, CONVENTIONS.md §3) and only ever meaningful when the reader is on the client — see
 * that method's javadoc for why this exists.
 */
public final class ClientVaultAnchorCache {

    private static volatile Optional<BlockPos> anchorPos = Optional.empty();

    private ClientVaultAnchorCache() {
    }

    public static void update(Optional<BlockPos> pos) {
        anchorPos = pos;
    }

    public static Optional<BlockPos> get() {
        return anchorPos;
    }
}
