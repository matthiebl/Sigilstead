package com.heartstead.network;

import com.heartstead.Heartstead;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — the world's Vault Anchor position (DESIGN.md §2.1), broadcast on claim and on join so every
 * client can veto its own speculative placement of a second Anchor client-side, the same way it
 * already vetoes placing above the world height limit using data it holds locally.
 *
 * <p>Without this, {@link com.heartstead.block.VaultAnchorBlock#canSurvive} can only refuse on the
 * server (the claim lives in server-only {@code SavedData}), so the client optimistically predicts
 * a successful placement and then gets corrected — the "places for a split second and vanishes"
 * flicker. Syncing just this one position is cheap and lets the client refuse up front instead.
 */
public record VaultAnchorPayload(Optional<BlockPos> anchorPos) implements CustomPacketPayload {

    public static final Type<VaultAnchorPayload> TYPE = new Type<>(Heartstead.id("vault_anchor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultAnchorPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), VaultAnchorPayload::anchorPos,
            VaultAnchorPayload::new);

    @Override
    public Type<VaultAnchorPayload> type() {
        return TYPE;
    }
}
