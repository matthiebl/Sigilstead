package com.sigilstead.network;

import com.sigilstead.Sigilstead;
import com.sigilstead.vault.VaultKey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — DESIGN.md §2.4 "click-to-withdraw" as an intent, not a slot click: the Vault grid isn't
 * backed by real {@link net.minecraft.world.inventory.Slot}s (§2.3 allows hundreds of distinct
 * types, filtered live by search), so there's nothing for vanilla's slot-click protocol to address.
 * The server ({@link com.sigilstead.vault.VaultMenu}) re-validates the request against live Vault
 * stock and the player's actual inventory space before moving anything — this payload only names
 * what the player asked for, never how much is safe to give them.
 *
 * <p>It names a {@link VaultKey} rather than an item, so clicking one of ten differently-enchanted
 * swords withdraws <em>that</em> sword. A key the Vault doesn't hold is simply not found and nothing
 * moves, which is why it is safe to let the client name one.
 */
public record VaultWithdrawPayload(VaultKey key, int count) implements CustomPacketPayload {

    public static final Type<VaultWithdrawPayload> TYPE = new Type<>(Sigilstead.id("vault_withdraw"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultWithdrawPayload> STREAM_CODEC = StreamCodec.composite(
            VaultKey.UNTRUSTED_STREAM_CODEC, VaultWithdrawPayload::key,
            ByteBufCodecs.VAR_INT, VaultWithdrawPayload::count,
            VaultWithdrawPayload::new);

    @Override
    public Type<VaultWithdrawPayload> type() {
        return TYPE;
    }
}
