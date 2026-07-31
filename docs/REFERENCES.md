# Heartstead — References

---

## Verified toolchain facts (checked 2026-07-31)

Everything here was queried directly from the source of truth, not from a tutorial or a search
snippet. Re-verify the same way when bumping versions — several popular mod-listing sites carry stale
numbers, and one of them reported the wrong Fabric Loader version during this check.

| Fact | Value | How to re-check |
|---|---|---|
| Current Java release | **26.2**, released 2026-06-16 | `meta.fabricmc.net/v2/versions/game` |
| Java required by 26.2 | **25** (`java-runtime-epsilon`) | `piston-meta.mojang.com` version manifest → `javaVersion` |
| Fabric Loader | **0.19.3** (stable) | `meta.fabricmc.net/v2/versions/loader` |
| Fabric API for 26.2 | **0.156.0+26.2** | `maven.fabricmc.net/.../fabric-api/maven-metadata.xml` |
| Fabric Loom | **1.17.17** (1.18.0 is alpha) | `maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml` |
| Gradle | **9.6.1** | `services.gradle.org/versions/current` |
| Yarn mappings for 26.x | **DO NOT EXIST** — newest yarn is 1.21.11 | `meta.fabricmc.net/v2/versions/yarn/26.2` returns `[]` |
| Bundled data pack format | 107.1 | [Pack format – Minecraft Wiki](https://minecraft.wiki/w/Pack_format) |

### Three traps this project already hit

1. **Yarn has no 26.x mappings.** Use `loom.officialMojangMappings()`. A `build.gradle` written from
   any standard Fabric tutorial will specify yarn and simply fail to resolve. Mojang's mappings carry
   their own licence — fine for a public mod, but read it before relicensing or vendoring.
2. **Loom requires Gradle itself to run on JDK 25.** A `java.toolchain` block is *not* enough; it
   covers compilation, not the Gradle JVM. The error is explicit:
   `Minecraft 26.2 requires Java 25 but Gradle is using 21`.
3. **26.2 has no data-pack item or block registration.** Confirmed 2026-07-31. It is still
   vanilla-item-plus-components, exactly as in 1.21.x. This is why the project is a mod
   ([DESIGN.md §8](DESIGN.md)).

---

## Still needed (not yet gathered)

Gather before the phase that needs them. **Read the decompiled source (`./gradlew genSources`) rather
than a tutorial** — this is the single most reliable way to avoid 1.21.x-era wrong answers.

| Topic | Needed for | Why it's risky |
|---|---|---|
| `Item.Properties` / `ResourceKey` construction | Phase 0, every item | Changed repeatedly across 1.21.x; items now need their key set at construction. Every older tutorial is wrong |
| `DataComponentType` registration + codecs | Per-stack state | The replacement for the data-pack `custom_data` approach |
| Fabric `AttachmentRegistry` | Per-player hearts, Vault | Serialisation and respawn-persistence semantics need checking |
| `ScreenHandler` / slot sync | Vault, Artisan, Foundry | The reason the project is a mod; also where desync bugs live |
| `SavedData` API | World-level core registry, waystones | Persistence shape |
| Custom enchantment definition format | §3 enchantments | Data-driven since 1.21, still churning |
| 26.2 entity predicate format | Loot tables, advancements | Component-style map that **rejects unknown sub-predicates** |
| Villager `Offers`/`Tags` persistence in 26.2 | §3.4, §7.5 | 26.2 fixed an empty-`Offers` persistence bug; behaviour is version-specific |
| Fabric GameTest entrypoint API | All testing | The verification story depends on it |

---

## Fabric documentation

- [Fabric Documentation](https://docs.fabricmc.net/) — the official developer guide
- [Custom Data Components](https://docs.fabricmc.net/develop/items/custom-data-components) — the
  replacement for data-pack `custom_data`
- [Fabric API](https://modrinth.com/mod/fabric-api) · [fabricapi.org](https://fabricapi.org/)
- [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html) — release-note style, useful
  for seeing what breaks between versions

## Minecraft data reference

- [Pack format – Minecraft Wiki](https://minecraft.wiki/w/Pack_format)
- [misode.github.io](https://misode.github.io/) — data pack generators and validators, version-aware
  through 26.2. Still useful: the mod's recipes, loot tables and advancements are the same JSON
- [Data pack – Minecraft Wiki](https://minecraft.wiki/w/Data_pack)

---

## Prior art worth studying

Read before designing the equivalent system — not to copy, but to know what players expect and what
has already gone wrong.

- **Storage:** Applied Energistics / Refined Storage — the terminal metaphor the Vault imitates.
  Now that a real slot grid is possible, their UI affordances are directly relevant.
- **Farm replacement:** Mob Grinding Utils, Industrial Foregoing, Woot — especially how they balance
  "no mobs actually spawn".
- **Lives:** Hardcore Hearts, Origins-style life systems, the Lifesteal SMP heart economy.

---

## Superseded

The following were load-bearing while this was a data pack and are kept only so old notes make sense:
`/dialog` documentation, the dialog command generator, GUI Maker on Modrinth, and the resource pack
format table (88.0 for 26.2). None apply to a mod.

## Source conversation

The design originated in a Claude conversation dated 30 Jul 2026, shared at
`claude.ai/share/d51527d6-59e7-4433-9300-d00d908e92d0`. It produced a design wiki file that is **not
retrievable from the share link** — [DESIGN.md](DESIGN.md) is a reconstruction from the conversation
body. Remaining gaps are tracked in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).

That conversation recommended datapack-first with an optional client mod. That recommendation was
**reversed** on 2026-07-31 after the block and item-identity limits proved fatal in practice; the
reasoning is in [DESIGN.md §8](DESIGN.md).
