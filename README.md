# Heartstead

> A Fabric mod that deletes technical Minecraft as a *prerequisite* — without deleting Minecraft.
> Explore, fight and build; don't grind, AFK, or build rectangles.

**Status:** design complete, implementation not started. **Minecraft 26.2 · Fabric · Java 25.**

## What it does

| System | Replaces |
|---|---|
| **The Vault** — player-bound virtual storage, deposit into linked blocks, withdraw from anywhere | Chest sorting halls |
| **Cores** — attune a core by *playing*, socket it into housing, get passive yield | Mob farms, iron farms, raid farms, quarries |
| **The Codex** — archive an enchanted book, teach a librarian to sell that enchantment forever | Trade-hall reroll grinding |
| **Lives** — keep inventory, lose a heart instead; earn hearts back by exploring | Losing your stuff to a creeper at y=-58 |
| **Abundance / Kiln Touch / Excavation** — bulk-drop, autosmelt-with-Fortune, chain-break | Manual bulk gathering |
| **Artisan's Table & Foundry** — craft and smelt from inventory *and* Vault | Furnace arrays, walking to a bench |

Everything hangs off one found item — the **Heart Shard** — so exploring is never wasted, and extra
lives compete with farm cores for the same currency.

## Read this first

**[docs/DESIGN.md](docs/DESIGN.md)** is the spec. Everything else supports it:

- [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — registration, source sets, persistence, testing
- [docs/PROMPTS.md](docs/PROMPTS.md) — build order as ready-to-paste Claude Code prompts
- [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — what still needs deciding
- [docs/REFERENCES.md](docs/REFERENCES.md) — verified version facts and source links

## Setup

Minecraft 26.2 requires **Java 25**, and Loom needs Gradle itself running on it — a Gradle toolchain
alone is not enough.

```bash
brew install openjdk@25
```

Then point Gradle at it, either per-shell:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25
```

or permanently, by adding this line to `gradle.properties` (it's gitignored-friendly to instead use
`~/.gradle/gradle.properties` if your path differs):

```
org.gradle.java.home=/opt/homebrew/opt/openjdk@25
```

## Build and run

```bash
./gradlew build          # compile + unit tests
./gradlew runClient      # launch a dev client
./gradlew runServer      # launch a dev server
./gradlew runGametest    # headless in-world tests
./gradlew genSources     # decompile Minecraft to read real API signatures
```

The jar lands in `build/libs/`. Install Fabric Loader 0.19.3+ and Fabric API alongside it.

## Repo layout

```
heartstead/
├── build.gradle  settings.gradle  gradle.properties   # versions live here
├── src/
│   ├── main/java/com/heartstead/
│   │   ├── Heartstead.java          # common entrypoint
│   │   ├── registry/                # HsItems, HsBlocks, HsComponents, ...
│   │   ├── vault/ core/ codex/ lives/ economy/ enchantment/
│   │   ├── config/  util/
│   │   └── gametest/                # in-world automated tests
│   ├── main/resources/
│   │   ├── fabric.mod.json
│   │   ├── assets/heartstead/       # textures, models, lang
│   │   └── data/heartstead/         # recipes, loot tables, advancements, tags
│   ├── client/java/com/heartstead/client/    # screens, renderers, keybinds
│   └── test/java/                   # plain JUnit, no world
└── docs/
```

**Content is still data-driven.** Recipes, loot tables and advancements are JSON in
`src/main/resources/data/` exactly as they'd be in a data pack — being a mod didn't change that.

## Why a mod and not a data pack

Short version: a data pack cannot register a block, and this design has twelve of them. Three of the
five flagship systems are inventory UIs that data packs can't draw. And the mod can test itself.

Full reasoning, including what it costs: [DESIGN.md §8](docs/DESIGN.md).

## Naming

`Heartstead` is taken from the repo directory and has **not** been availability-checked against
existing Minecraft projects. Alternatives that were checked clean: *Wanderhoard*, *Cairnkeep*,
*Tallyheart*, *Farstead*. See [OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — the mod id is baked into
every registry call, so settle this before publishing.
