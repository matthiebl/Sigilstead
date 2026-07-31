# Heartstead

> A Minecraft Java datapack that deletes technical Minecraft as a *prerequisite* — without deleting
> Minecraft. Explore, fight and build; don't grind, AFK, or build rectangles.

**Status:** design complete, implementation not started. Target **Java 26.2** (data pack format 107.1).

## What it does

| System | Replaces |
|---|---|
| **The Vault** — player-bound virtual storage, deposit by dropping into linked barrels, withdraw from anywhere | Chest sorting halls |
| **Cores** — attune a core by *playing*, socket it into housing, get passive yield | Mob farms, iron farms, raid farms, quarries |
| **The Codex** — archive an enchanted book, teach a librarian to sell that enchantment forever | Trade-hall reroll grinding |
| **Lives** — keep inventory, lose a heart instead; earn hearts back by exploring | Losing your stuff to a creeper at y=-58 |
| **Abundance / Kiln Touch / Excavation** — bulk-drop, autosmelt-with-Fortune, chain-break | Manual bulk gathering |
| **Artisan's Table & Foundry** — craft and smelt from inventory *and* Vault | Furnace arrays, walking to a bench |

Everything hangs off one found item — the **Heart Shard** — so exploring is never wasted, and extra
lives compete with farm cores for the same currency.

## Read this first

**[docs/DESIGN.md](docs/DESIGN.md)** is the spec. Everything else supports it:

- [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — naming, `custom_data` shape, storage layout
- [docs/PROMPTS.md](docs/PROMPTS.md) — build order, as ready-to-paste Claude Code prompts
- [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — what still needs deciding
- [docs/REFERENCES.md](docs/REFERENCES.md) — version facts and source links
- [CLAUDE.md](CLAUDE.md) — conventions Claude Code must follow

## Repo layout

```
heartstead/
├── datapack/                     # ships as heartstead-datapack.zip
│   ├── pack.mcmeta
│   └── data/
│       ├── heartstead/
│       │   ├── function/         # load, tick, and one dir per system
│       │   ├── loot_table/  advancement/  recipe/
│       │   ├── enchantment/ dialog/       predicate/
│       │   ├── item_modifier/    tags/
│       └── minecraft/
│           ├── tags/function/    # load.json, tick.json
│           └── loot_table/       # vanilla overrides
├── resourcepack/                 # ships as heartstead-resourcepack.zip
│   ├── pack.mcmeta
│   └── assets/heartstead/{models,textures,lang}
├── scripts/
│   ├── generate_recipe_index.py  # Artisan's Table recipe index — build before the Table
│   └── build.sh                  # produces both zips into dist/
├── dev/
│   └── link.sh                   # symlinks both packs into a local test world
└── docs/
```

Two independent zips, one repo, one source of truth. The resource pack is **not** nested inside the
datapack — that keeps "datapack only" distribution easy later.

## Dev loop

```bash
./dev/link.sh heartstead-dev
```

Creates a symlink from your test world's `datapacks/` and your `resourcepacks/` back to this repo, so
you edit here and `/reload` in-game — no copying.

Then, in the test world (cheats on):

```
/gamerule sendCommandFeedback true
```

**`/reload` picks up** functions, loot tables, recipes, advancements, predicates.
**Dialogs do not hot-reload** — they need a world/server restart.

## Build

```bash
./scripts/build.sh
```

Writes `dist/heartstead-datapack.zip` and `dist/heartstead-resourcepack.zip`.

## Distribution

Datapack-first, with an **optional** client mod later for UI polish only (slot-based screens, a
keybind, live search). The datapack covers ~90% of the design on its own and never depends on the mod
— a friend can join a vanilla world and install nothing. See [DESIGN.md §8](docs/DESIGN.md).

## Naming

`Heartstead` is taken from the repo directory and has **not** been availability-checked against
existing Minecraft projects. Alternatives that were checked clean: *Wanderhoard*, *Cairnkeep*,
*Tallyheart*, *Farstead*. See [OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — the namespace is baked
into every file, so settle this before publishing.
