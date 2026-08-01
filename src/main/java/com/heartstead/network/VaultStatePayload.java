package com.heartstead.network;

import com.heartstead.Heartstead;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * S2C — everything DESIGN.md §2.4's tab 1 needs to draw itself: the §2.1 activation state, the §2.3
 * capacity and reach the world has bought, and whether the "use a Vault Sigil from the Vault"
 * fallback currently applies.
 *
 * <p>Deliberately a snapshot of <em>facts</em>, not of permissions the client then enforces. It
 * carries {@code canWithdrawHere} so the screen can grey a click out rather than letting the player
 * click into a refusal, but the server re-evaluates the same rule on every intent — this is a
 * rendering aid, and nothing here decides anything (CONVENTIONS.md §5).
 *
 * <p>Sent alongside {@link VaultSyncPayload} off the same revision counter, so the tab-1 numbers and
 * the grid can never be from two different instants.
 */
public record VaultStatePayload(
        boolean activated,
        boolean everActivated,
        int sigilsSpent,
        int distinctTypeCap,
        int stackDepthCap,
        int distinctTypesUsed,
        List<ResourceKey<Level>> reachTiers,
        boolean canActivateFromVaultStock,
        boolean canWithdrawHere,
        int reactivationCost) implements CustomPacketPayload {

    public static final Type<VaultStatePayload> TYPE = new Type<>(Heartstead.id("vault_state"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ResourceKey<Level>>> REACH_CODEC =
            ByteBufCodecs.<RegistryFriendlyByteBuf, ResourceKey<Level>, List<ResourceKey<Level>>>collection(
                    ArrayList::new, ResourceKey.streamCodec(net.minecraft.core.registries.Registries.DIMENSION));

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultStatePayload> STREAM_CODEC = StreamCodec.of(
            VaultStatePayload::write, VaultStatePayload::read);

    private static void write(RegistryFriendlyByteBuf buf, VaultStatePayload payload) {
        buf.writeBoolean(payload.activated);
        buf.writeBoolean(payload.everActivated);
        buf.writeVarInt(payload.sigilsSpent);
        buf.writeVarInt(payload.distinctTypeCap);
        buf.writeVarInt(payload.stackDepthCap);
        buf.writeVarInt(payload.distinctTypesUsed);
        REACH_CODEC.encode(buf, payload.reachTiers);
        buf.writeBoolean(payload.canActivateFromVaultStock);
        buf.writeBoolean(payload.canWithdrawHere);
        buf.writeVarInt(payload.reactivationCost);
    }

    private static VaultStatePayload read(RegistryFriendlyByteBuf buf) {
        return new VaultStatePayload(
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                REACH_CODEC.decode(buf),
                buf.readBoolean(),
                buf.readBoolean(),
                buf.readVarInt());
    }

    @Override
    public Type<VaultStatePayload> type() {
        return TYPE;
    }

    /** The state a client holds before the first packet arrives: a dormant Vault it can't do anything with. */
    public static VaultStatePayload empty() {
        return new VaultStatePayload(false, false, 0, 0, 0, 0, List.of(), false, false, 1);
    }
}
