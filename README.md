# Sigilstead

> A Fabric mod that deletes technical Minecraft as a *prerequisite* — without deleting Minecraft.
> Explore, fight and build; don't grind, AFK, or build rectangles.

**Status:** design complete, implementation not started. **Minecraft 26.2 · Fabric · Java 25.**

## What it does

| System | Replaces |
|---|---|
| **The Vault** — shared world storage; deposit into it from anywhere, withdraw as far as you've explored | Chest sorting halls |
| **Cores** — attune a core by *playing*, socket it into housing, get passive yield | Mob farms, iron farms, raid farms, quarries |
| **The Codex** — archive an enchanted book, teach a librarian to sell that enchantment forever | Trade-hall reroll grinding |
| **Lives** — keep inventory, lose a heart instead; earn hearts back by exploring | Losing your stuff to a creeper at y=-58 |
| **Abundance / Kiln Touch** — bulk-drop multiplication, autosmelt that keeps Fortune | Manual bulk gathering |
| **Artisan's Table** — craft from inventory *and* Vault, vanilla recipe book included | Walking to a bench, hunting for materials |

Everything hangs off one found item — the **Sigil** — which crafts into exactly one of three things:
a farm core, an extra heart, or storage. Exploring is never wasted, no drop is ever dead in your
hands, and every Sigil you pick up is a choice between the three.

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

## Adding a version

```
git tag -a v0.x.x -m "Sigilstead 0.x.x"
git push origin v0.x.x
```

## Repo layout

```
sigilstead/
├── build.gradle  settings.gradle  gradle.properties   # versions live here
├── src/
│   ├── main/java/com/sigilstead/
│   │   ├── Sigilstead.java          # common entrypoint
│   │   ├── registry/                # HsItems, HsBlocks, HsComponents, ...
│   │   ├── vault/ core/ codex/ lives/ economy/ enchantment/
│   │   ├── config/  util/
│   │   └── gametest/                # in-world automated tests
│   ├── main/resources/
│   │   ├── fabric.mod.json
│   │   ├── assets/sigilstead/       # textures, models, lang
│   │   └── data/sigilstead/         # recipes, loot tables, advancements, tags
│   ├── client/java/com/sigilstead/client/    # screens, renderers, keybinds
│   └── test/java/                   # plain JUnit, no world
└── docs/
```
