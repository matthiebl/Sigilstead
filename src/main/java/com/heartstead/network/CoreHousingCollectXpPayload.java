package com.heartstead.network;

import com.heartstead.Heartstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — the housing screen's "Collect" button. Carries nothing: which housing is asked follows the
 * same rule {@link VaultActivatePayload} does, from the sending player's currently open menu, not
 * from anything the packet names.
 */
public record CoreHousingCollectXpPayload() implements CustomPacketPayload {

    public static final Type<CoreHousingCollectXpPayload> TYPE =
            new Type<>(Heartstead.id("core_housing_collect_xp"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreHousingCollectXpPayload> STREAM_CODEC =
            StreamCodec.unit(new CoreHousingCollectXpPayload());

    @Override
    public Type<CoreHousingCollectXpPayload> type() {
        return TYPE;
    }
}
