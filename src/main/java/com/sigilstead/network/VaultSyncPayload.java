package com.sigilstead.network;

import com.sigilstead.Sigilstead;
import com.sigilstead.vault.VaultKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * S2C — the Vault contents snapshot DESIGN.md §2.4 needs to render the screen and Phase 3's Artisan
 * needs for craftability (REFERENCES.md "the client needs a synced Vault snapshot" — one sync path,
 * two consumers). Sent whenever {@link com.sigilstead.vault.VaultMenu} notices the Vault's
 * revision counter has moved since the last tick it checked.
 *
 * <p>This is a full snapshot, not a diff — simplest correct thing at the sizes §2.3 allows (up to a
 * few hundred distinct types), and it means a late-joining or reconnecting client is never left with
 * a partial view.
 *
 * <p>Entries are keyed by {@link VaultKey}, so the wire carries each variant's whole component patch
 * and the client can draw a damage bar, an enchantment glint and a custom name on the right cell —
 * §2.4's "hovering shows the item's tooltip" is only true if the tooltip is the real stack's.
 */
public record VaultSyncPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<VaultSyncPayload> TYPE = new Type<>(Sigilstead.id("vault_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            VaultKey.STREAM_CODEC, Entry::key,
            ByteBufCodecs.VAR_INT, Entry::count,
            Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, VaultSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC),
            VaultSyncPayload::entries,
            VaultSyncPayload::new);

    @Override
    public Type<VaultSyncPayload> type() {
        return TYPE;
    }

    public static VaultSyncPayload of(Map<VaultKey, Integer> contents) {
        List<Entry> entries = contents.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
        return new VaultSyncPayload(entries);
    }

    /** One grid cell: a stored variant and how many of it the Vault holds. */
    public record Entry(VaultKey key, int count) {
    }
}
