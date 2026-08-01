package com.heartstead.network;

import com.heartstead.Heartstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — which of DESIGN.md §2.4's two tabs the client is showing.
 *
 * <p>Tab choice is a presentation concern, so it would normally stay entirely on the client. It
 * doesn't, for one concrete reason: the §2.3 upgrade slot is a real slot sitting where the storage
 * grid draws, and {@code Slot#isActive()} is evaluated server-side. A slot left active under the
 * grid would swallow clicks aimed at a stored stack. The server clamps this against the menu's own
 * {@link com.heartstead.vault.VaultAccess} anyway, so a client claiming to be on tab 1 with a Pouch
 * gets nothing.
 */
public record VaultTabPayload(boolean anchorTab) implements CustomPacketPayload {

    public static final Type<VaultTabPayload> TYPE = new Type<>(Heartstead.id("vault_tab"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultTabPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, VaultTabPayload::anchorTab, VaultTabPayload::new);

    @Override
    public Type<VaultTabPayload> type() {
        return TYPE;
    }
}
