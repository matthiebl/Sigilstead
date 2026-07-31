# Heartstead

A **Fabric mod** for Minecraft Java 26.2 that removes the need for technical farms and chest-sorting,
and rewards exploring, fighting and building instead.

## Ground truth

**[docs/DESIGN.md](docs/DESIGN.md) is the spec.** Read the relevant section before implementing
anything. If a request and the wiki disagree, say so rather than silently picking one. If the wiki is
wrong, fix the wiki in the same change.

- [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — registration, source-set split, persistence, config, testing
- [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — known gaps; check before assuming something is unspecified by accident
- [docs/PROMPTS.md](docs/PROMPTS.md) — the phase-by-phase build order
- [docs/REFERENCES.md](docs/REFERENCES.md) — verified version facts and what's still unverified

## Toolchain

| | |
|---|---|
| Minecraft | 26.2 |
| Java | **25** — Gradle itself must run on it. Handled by `gradle/gradle-daemon-jvm.properties`; no manual install needed |
| Mappings | **None.** 26.x is unobfuscated — no yarn, no mojmap, no remapping. Do not add a mappings dependency |
| Fabric Loader / API / Loom | 0.19.3 / 0.156.0+26.2 / 1.17.17 |

All versions live in `gradle.properties`. Verify against [docs/REFERENCES.md](docs/REFERENCES.md)
before changing any of them.

## Non-negotiables

1. **Namespace `heartstead`, package `com.heartstead`.** Every `ResourceLocation` goes through
   `Heartstead.id(String)`.
2. **Items and blocks are real registered objects.** No `custom_data` identity markers, no marker
   entities standing in for blocks, no base-item table. That era is over.
3. **Per-stack state uses data components; per-player state uses attachments; world state uses
   `SavedData`.** Not scoreboards, not raw `CompoundTag`. Everything persisted goes through a
   versioned Codec. Which of the three a system uses is a design decision — CONVENTIONS.md §4 has
   the table, and the Vault is world state while the Codex archive is not.
4. **Respect the source-set split.** Anything that draws lives in `src/client`. A client-only class
   referenced from common code crashes dedicated servers, and it surfaces late.
5. **Content stays data-driven.** Recipes, loot tables, advancements, tags and enchantments are JSON
   in `src/main/resources/data/`. Being a mod is a licence to write Java where Java helps, not an
   instruction to write it everywhere. Prefer a data generator over hand-written repetitive JSON.
6. **Every tuning dial is a config field**, not a literal. (CONVENTIONS.md §5)
7. **Never require chunkloaders.** Core offline accrual settles the whole elapsed delta from one
   stored timestamp on chunk load, capped (DESIGN.md §4.2). Never accrue from two clocks.
8. **Check `genSources` before writing against an unfamiliar API.** 26.x moves fast and item
   construction changed repeatedly across 1.21.x — pre-1.21.5 tutorials are wrong.

## Verification — you can now check your own work

```bash
./gradlew build          # compile + unit tests
./gradlew test           # pure logic only
./gradlew runGametest    # headless in-world tests
./gradlew genSources     # decompile MC to read real signatures
```

**Run them.** "It compiles and the tests pass" is now a claim you're able to make truthfully — so
make it, or say plainly that you didn't run them.

What you still cannot judge is *feel* — balance, pacing, whether a UI is pleasant. That needs
`./gradlew runClient` and a human. Don't claim a feature is good, only that it works.

**For anything that moves items, write the GameTest before the implementation.** The Vault is the
main offender (DESIGN.md §2.5).

## Working style

- One phase at a time, per [docs/PROMPTS.md](docs/PROMPTS.md). Don't run ahead.
- **Capability is not a mandate.** Being a mod means a real UI is possible everywhere; that is not a
  reason to build one. The pitch is casual convenience, not an ME terminal. Where vanilla already
  does the job — the recipe book behind the Artisan's Table (§7.1) — reuse it rather than reinvent.
- **No Realms.** Realms takes data packs, not mods; settled, not a live trade-off (OPEN-QUESTIONS.md).
