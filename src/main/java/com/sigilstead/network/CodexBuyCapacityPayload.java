package com.sigilstead.network;

import com.sigilstead.Sigilstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C2S — DESIGN.md §3.3/§12.6: confirms the capacity slot's Echo Shard or Nether Star. */
public record CodexBuyCapacityPayload() implements CustomPacketPayload {

    public static final Type<CodexBuyCapacityPayload> TYPE = new Type<>(Sigilstead.id("codex_buy_capacity"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CodexBuyCapacityPayload> STREAM_CODEC =
            StreamCodec.unit(new CodexBuyCapacityPayload());

    @Override
    public Type<CodexBuyCapacityPayload> type() {
        return TYPE;
    }
}
