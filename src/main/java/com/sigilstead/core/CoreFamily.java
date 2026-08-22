package com.sigilstead.core;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * DESIGN.md §4.1 — the four core families. A family is fixed at prime time (the crafting step) and
 * never changes; what a core learns afterwards is its <em>target</em>, not its family.
 *
 * <p>Each family names the imprint action that locks a target (§4.1's table) and the housing that
 * hosts it (§4.2). Deliberately <b>not</b> holding its own threshold or base rate: both are config
 * (CONVENTIONS.md §5), so they live in {@link com.sigilstead.config.AttunementConfig} and
 * {@link com.sigilstead.config.CoreConfig} rather than as literals on an enum constant.
 *
 * <p>{@link Imprint} is what a family's target <em>is</em>, and it is the one thing a §5 core cannot
 * override: a Soul Core always targets an entity type, a Lithic Core always targets a block.
 */
public enum CoreFamily implements StringRepresentable {

    /** Kill a hostile mob; the first mob type killed locks the target (§4.1). Housed in a Soul Cage. */
    SOUL("soul", Imprint.ENTITY),

    /** Harvest a mature crop; the first crop harvested locks the target. Housed in a Verdant Planter. */
    VERDANT("verdant", Imprint.BLOCK),

    /** Breed two animals; the first species bred locks the target. Housed in a Paddock. */
    PASTORAL("pastoral", Imprint.ENTITY),

    /** Mine a stone or earth block; the first block type mined locks the target. Housed in a Quarry Node. */
    LITHIC("lithic", Imprint.BLOCK);

    /**
     * What kind of registry object a family's target names — and therefore which loot table
     * {@link CoreYield} rolls when the core produces.
     */
    public enum Imprint {
        /** Target is an {@code EntityType} id; yield rolls that entity's death loot table. */
        ENTITY,
        /** Target is a {@code Block} id; yield rolls that block's break loot table. */
        BLOCK
    }

    public static final Codec<CoreFamily> CODEC = StringRepresentable.fromEnum(CoreFamily::values);

    public static final StreamCodec<io.netty.buffer.ByteBuf, CoreFamily> STREAM_CODEC =
            ByteBufCodecs.idMapper(id -> values()[id], CoreFamily::ordinal);

    private final String name;
    private final Imprint imprint;

    CoreFamily(String name, Imprint imprint) {
        this.name = name;
        this.imprint = imprint;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public Imprint imprint() {
        return imprint;
    }

    /** Path segment shared by this family's items, blocks and translation keys — {@code soul}, {@code verdant}, … */
    public String id() {
        return name;
    }
}
