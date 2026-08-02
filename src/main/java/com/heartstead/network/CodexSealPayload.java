package com.heartstead.network;

import com.heartstead.Heartstead;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * C2S — DESIGN.md §3.3 Tome: the archived enchantment the player picked off the empower list, the
 * way a stonecutter click picks one of its recipe alternatives. The server reads the tome slot's
 * actual contents and the player's own archive itself; this only carries which row was clicked.
 */
public record CodexSealPayload(ResourceKey<Enchantment> enchantment) implements CustomPacketPayload {

    public static final Type<CodexSealPayload> TYPE = new Type<>(Heartstead.id("codex_seal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CodexSealPayload> STREAM_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.ENCHANTMENT), CodexSealPayload::enchantment,
            CodexSealPayload::new);

    @Override
    public Type<CodexSealPayload> type() {
        return TYPE;
    }
}
