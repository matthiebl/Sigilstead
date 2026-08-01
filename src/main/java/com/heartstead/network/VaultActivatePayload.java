package com.heartstead.network;

import com.heartstead.Heartstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — DESIGN.md §2.1 activation, as an intent with no arguments.
 *
 * <p>This is the <em>only</em> §2.4 tab-1 verb that is still a button, and deliberately so: the
 * capacity and reach ladders are bought by putting a Sigil in the upgrade slot, but the two
 * activation cases that button covers have no item to place. The world's first activation is free,
 * and the anti-softlock fallback spends a Sigil that is already <em>inside</em> the Vault — §2.4
 * asks for that one to be offered explicitly, "as a second button".
 *
 * <p>Carries nothing about how to pay. {@link com.heartstead.vault.Vault#tryActivate} decides
 * between free, held-Sigil and stored-Sigil, in that order — letting the client name a payment
 * source is how a shared pool gets drained by a modified client.
 */
public record VaultActivatePayload() implements CustomPacketPayload {

    public static final Type<VaultActivatePayload> TYPE = new Type<>(Heartstead.id("vault_activate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultActivatePayload> STREAM_CODEC =
            StreamCodec.unit(new VaultActivatePayload());

    @Override
    public Type<VaultActivatePayload> type() {
        return TYPE;
    }
}
