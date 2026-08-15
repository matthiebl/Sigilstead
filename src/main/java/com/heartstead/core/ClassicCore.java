package com.heartstead.core;

import com.heartstead.Heartstead;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * DESIGN.md §5 / §12.5 — the eleven classic farm replacements. Each names the housing family it
 * shares with §4, the target its prime recipe locks (real for the five whose generic path is
 * meant to reach the same place — "the target is implied by the core" — synthetic for the six
 * milestone-gated ones, so ordinary play can never reach the same override for free), and the
 * bespoke loot table §4.2 requires ("the core supplies the rate and the loot table").
 *
 * <p>Six of the eleven — Ominous, Barter, Guardian, Wither Skull, Ender, Shulker — use a
 * {@code heartstead:} target rather than the real mob they are thematically about. That is
 * deliberate: overrides in {@link com.heartstead.config.ClassicCoreConfig} and
 * {@link CoreTargets} are keyed on the target id alone, so if Wither Skull targeted
 * {@code minecraft:wither_skeleton} directly, an ordinary Primed Soul Core could reach the exact
 * same 1.6-skulls/hr table via 16 free-range kills — no fortress, no skull, no gate at all. A
 * synthetic id can only ever be reached through the milestone hook that locks it.
 *
 * <p>Golem, Tidal, Slime, Apiary and Geode use their real mob/block id on purpose: §5's own text
 * says a Counted core's "target is implied by the core", i.e. the prime recipe is a themed
 * shortcut to a target the generic family mechanism could reach anyway (kill 16 drowned with any
 * Primed Soul Core, mine 32 amethyst clusters with any Primed Lithic Core). Golem's target
 * ({@code minecraft:iron_golem}) is additionally safe because iron golems are not hostile and so
 * are never on {@code soul_imprintable}.
 */
public enum ClassicCore {

    GOLEM("golem", CoreFamily.SOUL, "minecraft:iron_golem"),
    OMINOUS("ominous", CoreFamily.SOUL, "heartstead:ominous"),
    BARTER("barter", CoreFamily.SOUL, "heartstead:barter"),
    GUARDIAN("guardian", CoreFamily.SOUL, "heartstead:guardian"),
    TIDAL("tidal", CoreFamily.SOUL, "minecraft:drowned"),
    WITHER_SKULL("wither_skull", CoreFamily.SOUL, "heartstead:wither_skull"),
    ENDER("ender", CoreFamily.SOUL, "heartstead:ender"),
    SHULKER("shulker", CoreFamily.SOUL, "heartstead:shulker"),
    SLIME("slime", CoreFamily.SOUL, "minecraft:slime"),
    APIARY("apiary", CoreFamily.PASTORAL, "minecraft:bee"),
    GEODE("geode", CoreFamily.LITHIC, "minecraft:amethyst_cluster");

    private final String id;
    private final CoreFamily family;
    private final Identifier target;
    private final ResourceKey<LootTable> lootTable;

    ClassicCore(String id, CoreFamily family, String target) {
        this.id = id;
        this.family = family;
        this.target = Identifier.parse(target);
        this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, Heartstead.id("classic_cores/" + id));
    }

    public String id() {
        return id;
    }

    public CoreFamily family() {
        return family;
    }

    public Identifier target() {
        return target;
    }

    public ResourceKey<LootTable> lootTable() {
        return lootTable;
    }

    /** True for the six milestone-shaped entries, whose target only {@code heartstead} code ever locks. */
    public boolean isSynthetic() {
        return target.getNamespace().equals(Heartstead.MOD_ID);
    }

    /** §5's "the finished core, named for what it learned" line, for the synthetic six. */
    public Component displayName() {
        return Component.translatable("heartstead.classic_core." + id);
    }

    /** What this core actually produces, for the §4.1 tooltip preview — see {@link CoreTargets#yieldDescription}. */
    public Component yieldDescription() {
        return Component.translatable("heartstead.classic_core." + id + ".yield");
    }

    private static final Map<Identifier, ClassicCore> BY_TARGET = buildIndex();

    private static Map<Identifier, ClassicCore> buildIndex() {
        Map<Identifier, ClassicCore> map = new HashMap<>();
        for (ClassicCore core : values()) {
            map.put(core.target, core);
        }
        return Map.copyOf(map);
    }

    /** The classic core (if any) that overrides {@code target}'s rate and loot table. */
    public static Optional<ClassicCore> forTarget(Identifier target) {
        return Optional.ofNullable(BY_TARGET.get(target));
    }
}
