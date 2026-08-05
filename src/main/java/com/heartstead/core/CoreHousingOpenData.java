package com.heartstead.core;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * DESIGN.md §4.2 — what the client needs to build a housing screen.
 *
 * <p>The slot count is sent rather than read from config on the client because config is
 * server-authoritative (CONVENTIONS.md §5) — a client with a stale or different {@code housing_slots}
 * would otherwise build a menu of the wrong size and desync every slot index after it.
 *
 * <p>{@code claimedTargets} is DESIGN.md §4.3 made visible to the client: the targets of this family
 * that some <em>other</em> housing already runs. Without it the client cannot evaluate the one rule
 * that decides whether a core may go in the slot, and would have to place the core speculatively and
 * be corrected — which is precisely the "placed and then undone" shape §4.3 rules out. The server
 * re-checks against live state on every click regardless; this only stops the client predicting a
 * placement the server is going to refuse.
 */
public record CoreHousingOpenData(CoreFamily family, int bufferSlots, List<Identifier> claimedTargets) {

    public static final StreamCodec<RegistryFriendlyByteBuf, CoreHousingOpenData> STREAM_CODEC =
            StreamCodec.composite(
                    CoreFamily.STREAM_CODEC, CoreHousingOpenData::family,
                    ByteBufCodecs.VAR_INT, CoreHousingOpenData::bufferSlots,
                    Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()), CoreHousingOpenData::claimedTargets,
                    CoreHousingOpenData::new);
}
