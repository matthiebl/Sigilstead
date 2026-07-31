# Heartstead

A Minecraft Java datapack (+ resource pack) that removes the need for technical farms and chest-sorting,
and rewards exploring, fighting and building instead.

## Ground truth

**[docs/DESIGN.md](docs/DESIGN.md) is the spec.** Read the relevant section before implementing
anything. If a request and the wiki disagree, say so rather than silently picking one. If the wiki is
wrong, fix the wiki in the same change.

- [docs/CONVENTIONS.md](docs/CONVENTIONS.md) — IDs, `custom_data` shape, function naming, storage
  layout, scoreboards. Follow it exactly; it's what keeps sessions consistent.
- [docs/OPEN-QUESTIONS.md](docs/OPEN-QUESTIONS.md) — known gaps. Check here before assuming something
  is unspecified by accident.
- [docs/PROMPTS.md](docs/PROMPTS.md) — the phase-by-phase build order.
- [docs/REFERENCES.md](docs/REFERENCES.md) — verified version facts and the things still unverified.

## Non-negotiables

1. **Namespace is `heartstead:`.** Scoreboard/tag prefix is `hs.`. Never introduce a second namespace
   except in a deliberate throwaway spike.
2. **Custom items are identified by `minecraft:custom_data` → `heartstead` → `id`**, never by display
   name or lore. See CONVENTIONS.md §2.
3. **Target Java 26.2**, `min_format: 107`, `max_format: [107, 1]`. 26.2 changed the entity predicate
   format to a component-style map and **rejects unknown sub-predicates** — every 1.21.x predicate
   example on the internet is wrong. Verify the shape before generating a batch of them.
4. **Dialogs are not inventories.** No draggable slots, no grid, no forced chest GUI, no inventory
   click detection. Design lists-with-buttons; don't propose a grid. (DESIGN.md §0.1)
5. **Never require chunkloaders.** Offline accrual uses a stored world-time delta settled on chunk load.
6. **Every tuning dial is a config scoreboard**, not a literal in a function. (CONVENTIONS.md §6)
7. **The datapack never depends on the optional client mod.** The mod is a renderer over datapack
   state and owns no state and no balance numbers. (DESIGN.md §8)

## Verification

Claude Code cannot launch Minecraft. **Nothing is done until Matt has `/reload`ed it in the dev world
and watched it work.** Say what needs testing, don't claim it works.

- Functions, loot tables, recipes, advancements → `/reload`.
- **Dialogs → full world/server restart.** Batch dialog changes so a round of testing needs one restart.
- The Vault (DESIGN.md §2.5) will eat someone's inventory during development. Every item-removing path
  fails closed or it doesn't ship.

## Layout

```
datapack/      data/heartstead/... plus data/minecraft/tags/function for load & tick
resourcepack/  assets/heartstead/... models, textures, lang
scripts/       generators and build; generated JSON is never hand-edited
docs/          spec — see above
dev/           symlink helper for the local test world
```

## Working style for this repo

- One phase at a time, per docs/PROMPTS.md. Don't run ahead into the next phase.
- Prefer generating structurally-repetitive JSON with a script in `scripts/` over emitting 40 files
  by hand — the generator is the artifact, the JSON is output.
- Show one example of an unfamiliar JSON shape (predicate, dialog, enchantment) and get it confirmed
  before generating the rest of the batch.
