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
| **Abundance / Kiln Touch** — bulk-drop multiplication, autosmelt that keeps Fortune | Manual bulk gathering |
| **Artisan's Table** — craft from inventory *and* Vault, vanilla recipe book included | Walking to a bench, hunting for materials |

Everything hangs off one found item — the **Heart Shard** — so exploring is never wasted, and extra
lives compete with farm cores for the same currency.

## Read this first

**[docs/DESIGN.md](docs/DESIGN.md)** is the spec. Everything else supports it:

- [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — registration, source sets, persistence, testing
- [docs/PROMPTS.md](docs/PROMPTS.md) — build order as ready-to-paste Claude Code prompts
- [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — what still needs deciding
- [docs/REFERENCES.md](docs/REFERENCES.md) — verified version facts and source links

## Setup

Nothing to install but a JDK-capable Gradle — `./gradlew` handles the rest.

Minecraft 26.2 requires **Java 25**, and Loom needs Gradle *itself* running on it (a `java.toolchain`
block is not enough). That's handled by `gradle/gradle-daemon-jvm.properties`, which pins
`toolchainVersion=25` and lets Gradle auto-download a matching JDK per platform. A fresh clone builds
without any manual JDK setup.

If you'd rather use a local JDK 25:

```bash
brew install openjdk@25
```

It installs keg-only, so it won't disturb an existing JDK 17/21.

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

Short version: a data pack cannot register a block, and this design has a dozen of them. Several
flagship systems are inventory UIs that data packs can't draw. And the mod can test itself.

The cost, accepted: **no Realms** (it takes data packs, not mods), both sides must install for
multiplayer, and releases trail new Minecraft versions.

## Adding it to an existing world

It works, but there is **no catch-up path**. A world with chests full of diamonds still has zero
Heart Shards, and the only way to get them is to go exploring — same as a fresh world. That's
deliberate: the economy is the point of the mod.
