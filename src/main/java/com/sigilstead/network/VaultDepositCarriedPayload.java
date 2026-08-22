package com.sigilstead.network;

import com.sigilstead.Sigilstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — DESIGN.md §2.4: drop the stack on the cursor into the Vault by clicking anywhere in the
 * grid, the same gesture as dropping it into a chest slot.
 *
 * <p>The Vault grid has no real slots for vanilla's click protocol to address (§2.3 allows hundreds
 * of types behind a live filter), so this intent stands in for the slot click that would otherwise
 * carry it. {@code single} is the right-click variant, matching vanilla's place-one behaviour.
 *
 * <p>Names no amount and no item: the server reads the carried stack it already holds, so a crafted
 * packet can only ever deposit what the player is genuinely carrying.
 */
public record VaultDepositCarriedPayload(boolean single) implements CustomPacketPayload {

    public static final Type<VaultDepositCarriedPayload> TYPE = new Type<>(Sigilstead.id("vault_deposit_carried"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultDepositCarriedPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, VaultDepositCarriedPayload::single, VaultDepositCarriedPayload::new);

    @Override
    public Type<VaultDepositCarriedPayload> type() {
        return TYPE;
    }
}
