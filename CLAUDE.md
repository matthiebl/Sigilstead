# Heartstead

A **Fabric mod** for Minecraft Java 26.2 that removes the need for technical farms and chest-sorting,
and rewards exploring, fighting and building instead.

> Was a data pack until 2026-07-31. If you find advice about `custom_data` markers, obscure base
> items, `/dialog` menus or `pack.mcmeta`, it is stale — see [docs/DESIGN.md](docs/DESIGN.md) §8.

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
| Java | **25** — Gradle itself must run on JDK 25, not just the toolchain |
| Mappings | **Mojang official.** Yarn has no 26.x mappings (newest is 1.21.11). Do not add a `yarn_mappings` property |
| Fabric Loader / API / Loom | 0.19.3 / 0.156.0+26.2 / 1.17.17 |

All versions live in `gradle.properties`. Verify against [docs/REFERENCES.md](docs/REFERENCES.md)
before changing any of them.

## Non-negotiables

1. **Namespace `heartstead`, package `com.heartstead`.** Every `ResourceLocation` goes through
   `Heartstead.id(String)`.
2. **Items and blocks are real registered objects.** No `custom_data` identity markers, no marker
   entities standing in for blocks, no base-item table. That era is over.
3. **Per-stack state uses data components; per-player state uses attachments.** Not scoreboards, not
   raw `CompoundTag`. Everything persisted goes through a versioned Codec.
4. **Respect the source-set split.** Anything that draws lives in `src/client`. A client-only class
   referenced from common code crashes dedicated servers, and it surfaces late.
5. **Content stays data-driven.** Recipes, loot tables, advancements, tags and enchantments are JSON
   in `src/main/resources/data/`. Being a mod is a licence to write Java where Java helps, not an
   instruction to write it everywhere. Prefer a data generator over hand-written repetitive JSON.
6. **Every tuning dial is a config field**, not a literal. (CONVENTIONS.md §5)
7. **Never require chunkloaders.** Offline accrual settles from a stored time delta on chunk load.
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
- The UI ceiling that shaped this design is gone (DESIGN.md §0.1) — but new capability is not a
  mandate. The pitch is casual convenience, not an ME terminal.
- Realms support is **undecided** (OPEN-QUESTIONS.md). Flag mod-only dependencies as you add them.
