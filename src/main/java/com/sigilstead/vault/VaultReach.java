package com.sigilstead.vault;

import com.sigilstead.config.HsConfigManager;
import com.sigilstead.config.VaultConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §2.0 and §2.3 — the one rule the whole storage system hangs on, in one place:
 *
 * <blockquote><b>Deposit is free from anywhere. Withdrawal is ranged.</b></blockquote>
 *
 * <p>There is deliberately no per-item range table and no "field" concept. A thing either deposits,
 * which is always allowed, or withdraws, which needs the Anchor's reach tier to cover where the
 * player is standing — and that is the same check for the Satchel, the Pouch, the Anchor screen and
 * the Linked Funnel's output mode alike. Anything that wants to move items out of the Vault asks
 * {@link #canWithdrawAt}; nothing else gets a say.
 *
 * <p>Reach tiers are independent, not cumulative (§2.3): each names the dimension it unlocks, so an
 * Anchor standing in the Nether still needs the Nether tier for Nether-wide withdrawal. Local reach
 * is the one that comes free with activation, and it only applies in the Anchor's own dimension.
 */
public final class VaultReach {

    private VaultReach() {
    }

    /**
     * Whether the Vault will hand items out to something standing at {@code pos} in {@code level}.
     *
     * <p>A dormant Vault refuses outright — §2.1's "nothing linked works without an activated
     * Anchor" — before either reach test is even considered.
     */
    public static boolean canWithdrawAt(ServerLevel level, BlockPos pos) {
        VaultData vault = Vault.get(level);
        if (!vault.activated()) {
            return false;
        }
        return withinLocalReach(vault, level, pos) || vault.reachTiers().contains(level.dimension());
    }

    /**
     * §2.0 — deposit ignores the reach table entirely by default. {@code deposit_requires_reach}
     * (§12.7, default {@code false}) is the switch that makes deposit obey the same rule, so the
     * largest unvalidated convenience in the pack is a config flip rather than a rewrite.
     */
    public static boolean canDepositAt(ServerLevel level, BlockPos pos) {
        if (!Vault.get(level).activated()) {
            return false;
        }
        if (!HsConfigManager.get().depositRequiresReach()) {
            return true;
        }
        return canWithdrawAt(level, pos);
    }

    /**
     * §12.3 — local reach covers a {@code local_reach_chunks} square of chunks centred on the
     * Anchor's own chunk, in the Anchor's own dimension. A 5-chunk square means two chunks either
     * side of centre, which is why the radius is {@code (n - 1) / 2} rather than {@code n}.
     */
    private static boolean withinLocalReach(VaultData vault, ServerLevel level, BlockPos pos) {
        Optional<BlockPos> anchor = vault.anchorPos();
        Optional<ResourceKey<Level>> anchorDimension = vault.anchorDimension();
        if (anchor.isEmpty() || anchorDimension.isEmpty() || !anchorDimension.get().equals(level.dimension())) {
            return false;
        }

        VaultConfig config = HsConfigManager.get().vault();
        int radius = (config.localReachChunks() - 1) / 2;
        // Chessboard (Chebyshev) distance is a square, which is the shape §12.3 describes — a
        // Euclidean radius would clip the corners of the 5×5 the table promises.
        return ChunkPos.containing(anchor.get()).getChessboardDistance(ChunkPos.containing(pos)) <= radius;
    }
}
