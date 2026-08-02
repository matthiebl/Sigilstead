package com.heartstead.network;

import com.heartstead.Heartstead;
import com.heartstead.codex.CodexArchive;
import com.heartstead.codex.CodexArchiveTier;
import com.heartstead.config.HsConfigManager;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * S2C — DESIGN.md §3.3: everything the Codex screen needs to draw the archived-enchantments list and
 * the capacity summary. Sent once on menu open and again after every action that can change it
 * (Archive, buy-capacity, Seal) — the archive only ever changes because of this player's own menu
 * actions, so there is no revision counter to poll the way {@code VaultMenu} polls the Vault.
 */
public record CodexSyncPayload(List<Entry> archived, int capacityTier, int capacityCap) implements CustomPacketPayload {

    public static final Type<CodexSyncPayload> TYPE = new Type<>(Heartstead.id("codex_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Entry> ENTRY_CODEC = StreamCodec.composite(
            ResourceKey.streamCodec(Registries.ENCHANTMENT), Entry::enchantment,
            ByteBufCodecs.VAR_INT, Entry::level,
            Entry::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, CodexSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ENTRY_CODEC), CodexSyncPayload::archived,
            ByteBufCodecs.VAR_INT, CodexSyncPayload::capacityTier,
            ByteBufCodecs.VAR_INT, CodexSyncPayload::capacityCap,
            CodexSyncPayload::new);

    @Override
    public Type<CodexSyncPayload> type() {
        return TYPE;
    }

    public static CodexSyncPayload of(CodexArchive archive) {
        List<Entry> entries = archive.archived().entrySet().stream()
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .toList();
        CodexArchiveTier tier = CodexArchiveTier.forTier(archive.capacityTier(), HsConfigManager.get().codex());
        return new CodexSyncPayload(entries, archive.capacityTier(), tier.capacity());
    }

    public record Entry(ResourceKey<Enchantment> enchantment, int level) {
    }
}
