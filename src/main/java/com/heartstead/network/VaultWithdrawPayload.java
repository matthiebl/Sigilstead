package com.heartstead.network;

import com.heartstead.Heartstead;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * C2S — DESIGN.md §2.4 "click-to-withdraw" as an intent, not a slot click: the Vault grid isn't
 * backed by real {@link net.minecraft.world.inventory.Slot}s (§2.3 allows hundreds of distinct
 * types, filtered live by search), so there's nothing for vanilla's slot-click protocol to address.
 * The server ({@link com.heartstead.vault.VaultMenu}) re-validates the request against live Vault
 * stock and the player's actual inventory space before moving anything — this payload only names
 * what the player asked for, never how much is safe to give them.
 */
public record VaultWithdrawPayload(Item item, int count) implements CustomPacketPayload {

    public static final Type<VaultWithdrawPayload> TYPE = new Type<>(Heartstead.id("vault_withdraw"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultWithdrawPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.registry(Registries.ITEM), VaultWithdrawPayload::item,
            ByteBufCodecs.VAR_INT, VaultWithdrawPayload::count,
            VaultWithdrawPayload::new);

    @Override
    public Type<VaultWithdrawPayload> type() {
        return TYPE;
    }
}
