package com.heartstead.registry;

import com.heartstead.network.VaultAnchorPayload;
import com.heartstead.network.VaultSyncPayload;
import com.heartstead.network.VaultWithdrawPayload;
import com.heartstead.vault.Vault;
import com.heartstead.vault.VaultMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * Custom payload registration for the Vault's client sync (DESIGN.md §2.4, REFERENCES.md — one sync
 * path, reused by Phase 3's Artisan). Type registration and the server-side receiver are both
 * common-safe; the client receiver lives in {@code src/client} (CONVENTIONS.md §3).
 */
public final class HsPayloads {

    private HsPayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(VaultSyncPayload.TYPE, VaultSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(VaultAnchorPayload.TYPE, VaultAnchorPayload.STREAM_CODEC);
        PayloadTypeRegistry.serverboundPlay().register(VaultWithdrawPayload.TYPE, VaultWithdrawPayload.STREAM_CODEC);

        ServerPlayNetworking.registerGlobalReceiver(VaultWithdrawPayload.TYPE, (payload, context) -> context.server().execute(() -> {
            ServerPlayer player = context.player();
            if (player.containerMenu instanceof VaultMenu vaultMenu) {
                vaultMenu.handleWithdraw(player, payload.item(), payload.count());
            }
        }));

        // A freshly-joined client needs the Anchor position even if it wasn't online to see it claimed —
        // otherwise it can't veto its own placement of a second one until the next server round trip.
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.player;
            ServerPlayNetworking.send(player, new VaultAnchorPayload(Vault.anchorPos(player.level())));
        });
    }
}
