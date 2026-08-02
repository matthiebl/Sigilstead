package com.heartstead.network;

import com.heartstead.Heartstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S — DESIGN.md §3.3 Archive: consume whatever currently sits in the archive slot. */
public record CodexArchivePayload() implements CustomPacketPayload {

    public static final Type<CodexArchivePayload> TYPE = new Type<>(Heartstead.id("codex_archive"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CodexArchivePayload> STREAM_CODEC =
            StreamCodec.unit(new CodexArchivePayload());

    @Override
    public Type<CodexArchivePayload> type() {
        return TYPE;
    }
}
