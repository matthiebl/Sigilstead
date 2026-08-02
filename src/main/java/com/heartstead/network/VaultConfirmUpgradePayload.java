package com.heartstead.network;

import com.heartstead.Heartstead;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * C2S — DESIGN.md §2.4: confirms the upgrade an upgrade slot is currently armed with, the way an
 * enchanting table's confirm click is separate from placing the item. Dropping a Sigil into a slot
 * only arms it; nothing is spent, and nothing happens to the Vault, until this fires.
 *
 * <p>Carries the {@link com.heartstead.vault.VaultUpgradeKind} ordinal, not the sigil count or the
 * dimension — the server reads the slot's actual contents itself, the same anti-spoofing shape as
 * every other Vault intent in this package.
 */
public record VaultConfirmUpgradePayload(int kind) implements CustomPacketPayload {

    public static final Type<VaultConfirmUpgradePayload> TYPE = new Type<>(Heartstead.id("vault_confirm_upgrade"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultConfirmUpgradePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, VaultConfirmUpgradePayload::kind,
                    VaultConfirmUpgradePayload::new);

    @Override
    public Type<VaultConfirmUpgradePayload> type() {
        return TYPE;
    }
}
