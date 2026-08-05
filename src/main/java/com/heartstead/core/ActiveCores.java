package com.heartstead.core;

import com.heartstead.blockentity.CoreHousingBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * DESIGN.md §4.3 — the one-active-core-per-target rule, enforced by <b>refusing the socket</b>.
 *
 * <p>§4.3: "the socket is refused at the point of insertion, with the reason shown to the player.
 * Nothing is ever placed and then silently switched off, so there is no dormancy, no wake-up
 * ordering, and no 'which of my four does the game pick' question to answer." So there is exactly one
 * verb here — {@link #tryClaim} — and it either succeeds or hands back the reason. There is no
 * dormant state to represent, anywhere in §4.
 *
 * <p>Claims resolve against the overworld's save data regardless of which level is passed, matching
 * {@link com.heartstead.vault.Vault}: §4.3's rule is per <em>world</em>, so a zombie Soul Core in the
 * Nether must refuse one in the Overworld.
 *
 * <h2>Stale claims</h2>
 *
 * A claim can outlive its housing — a world edit, a crash between the block being removed and the
 * save, a chunk that was deleted. {@link #tryClaim} therefore verifies that the claiming block is
 * still a housing still holding that same key before it refuses, and takes the claim over if it is
 * not. Without that check one lost block would make a target permanently unusable, with nothing in
 * the game able to explain why.
 */
public final class ActiveCores {

    private ActiveCores() {
    }

    /** Why a socket was refused, or that it wasn't. */
    public enum Result {
        /** The claim is now held by the asking housing. */
        CLAIMED,
        /** §4.3 — another live housing already runs this exact family + target. */
        REFUSED_DUPLICATE
    }

    public static ActiveCoreData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(ActiveCoreData.TYPE);
    }

    /**
     * §4.3 — claims {@code key} for the housing at {@code pos}, or refuses because another live
     * housing already holds it. Re-claiming a key this same housing already holds succeeds, so a
     * chunk reload is never mistaken for a duplicate.
     */
    public static Result tryClaim(ServerLevel level, CoreKey key, BlockPos pos) {
        ActiveCoreData data = get(level);
        Optional<ActiveCoreData.Claim> existing = data.claim(key);

        if (existing.isPresent()) {
            ActiveCoreData.Claim claim = existing.get();
            if (claim.isAt(pos, level.dimension())) {
                return Result.CLAIMED;
            }
            if (isClaimLive(level, claim)) {
                return Result.REFUSED_DUPLICATE;
            }
        }

        data.put(new ActiveCoreData.Claim(key, pos, level.dimension()));
        return Result.CLAIMED;
    }

    /**
     * Releases {@code key}, but only if {@code pos} is the housing that holds it. The guard matters:
     * a housing being unloaded or broken must never release a claim a different housing has since
     * taken over.
     */
    public static void release(ServerLevel level, CoreKey key, BlockPos pos) {
        ActiveCoreData data = get(level);
        data.claim(key)
                .filter(claim -> claim.isAt(pos, level.dimension()))
                .ifPresent(claim -> data.remove(key));
    }

    /** Where the live claim on {@code key} is, if there is one — what a refusal message names. */
    public static Optional<BlockPos> claimPos(ServerLevel level, CoreKey key) {
        return get(level).claim(key).filter(claim -> isClaimLive(level, claim)).map(ActiveCoreData.Claim::pos);
    }

    /**
     * §4.3 — whether {@code key} is live somewhere other than {@code pos}, i.e. whether a housing at
     * {@code pos} would be refused. This is the question the socket slot asks, and asking it
     * <em>before</em> anything moves is what makes §4.3's "refused at the point of insertion" literal
     * rather than "accepted and then undone".
     */
    public static boolean claimedElsewhere(ServerLevel level, CoreKey key, BlockPos pos) {
        return claimPos(level, key).filter(held -> !held.equals(pos)).isPresent();
    }

    /**
     * Every target of {@code family} currently claimed by a housing other than the one at {@code pos}.
     * Sent to the client when a housing screen opens so its core slot can refuse a duplicate directly,
     * with no server round trip and so no moment where the core is visibly in the slot.
     */
    public static List<Identifier> claimedTargets(ServerLevel level, CoreFamily family, BlockPos pos) {
        List<Identifier> targets = new ArrayList<>();
        for (ActiveCoreData.Claim claim : get(level).claims()) {
            if (claim.key().family() == family && !claim.isAt(pos, level.dimension()) && isClaimLive(level, claim)) {
                targets.add(claim.key().target());
            }
        }
        return targets;
    }

    /**
     * Whether a recorded claim still corresponds to a housing that is really there and really holding
     * that key. An unloaded chunk is treated as live: {@link ServerLevel#isLoaded} being false says
     * nothing about whether the block exists, and forcing the chunk in to find out would be a socket
     * attempt that loads arbitrary chunks.
     */
    private static boolean isClaimLive(ServerLevel level, ActiveCoreData.Claim claim) {
        ServerLevel claimLevel = levelOf(level, claim.dimension());
        if (claimLevel == null) {
            return false;
        }
        if (!claimLevel.isLoaded(claim.pos())) {
            return true;
        }
        return claimLevel.getBlockEntity(claim.pos()) instanceof CoreHousingBlockEntity housing
                && housing.socketedKey().map(claim.key()::equals).orElse(false);
    }

    private static ServerLevel levelOf(ServerLevel level, net.minecraft.resources.ResourceKey<Level> dimension) {
        return level.dimension().equals(dimension) ? level : level.getServer().getLevel(dimension);
    }
}
