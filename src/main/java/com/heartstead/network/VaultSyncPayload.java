package com.heartstead.network;

import com.heartstead.Heartstead;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.Item;

/**
 * S2C — the Vault contents snapshot DESIGN.md §2.4 needs to render the screen and Phase 3's Artisan
 * needs for craftability (REFERENCES.md "the client needs a synced Vault snapshot" — one sync path,
 * two consumers). Sent whenever {@link com.heartstead.vault.VaultMenu} notices the Vault's
 * revision counter has moved since the last tick it checked.
 *
 * <p>This is a full snapshot, not a diff — simplest correct thing at the sizes §2.3 allows (up to a
 * few hundred distinct types), and it means a late-joining or reconnecting client is never left with
 * a partial view.
 */
public record VaultSyncPayload(List<Entry> entries) implements CustomPacketPayload {

    public static final Type<VaultSyncPayload> TYPE = new Type<>(Heartstead.id("vault_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM_CODEC = ByteBufCodecs.registry(Registries.ITEM);

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ITEM_CODEC, Entry::item,
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

    public static VaultSyncPayload of(Map<Item, Integer> contents) {
        List<Entry> entries = contents.entrySet().stream().map(e -> new Entry(e.getKey(), e.getValue())).toList();
        return new VaultSyncPayload(entries);
    }

    public record Entry(Item item, int count) {
    }
}
