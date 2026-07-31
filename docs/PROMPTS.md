# Heartstead — Claude Code prompts

Ready-to-paste prompts, in build order. Rules that apply to all of them:

- **Run Claude Code from the repo root.**
- **Reference wiki sections, don't re-describe features.** "Implement §4.2 Soul Cage per the wiki"
  keeps drift down; re-explaining invites a second, subtly different design.
- **One phase per session.** Never ask for the whole mod.
- **Before writing against an unfamiliar API, run `./gradlew genSources` and read the real
  signature.** 26.x moves fast and item construction changed repeatedly across 1.21.x — every
  tutorial older than 1.21.5 is wrong, and the compile error won't point at the real problem.
- **Claude Code can verify its own work now** (`build`, `test`, `runGametest`). Expect it to. What it
  still can't judge is feel — balance and UI pleasantness need `runClient` and you.

---

## Phase 0 — Items and the heart economy

### 0.1 — Registration foundation

```
Read docs/DESIGN.md §1 and docs/CONVENTIONS.md §2.

Run ./gradlew genSources first, then show me the real 26.2 Item.Properties construction
signature before writing any registration code — I want to confirm the API shape rather
than find out via a confusing compile error.

Then implement HsComponents and HsItems with the three §1 items: heart_shard,
vital_heart, vault_sigil. Real registered items, creative tab entry, lang keys, item
models, and the two placeholder textures already in
src/main/resources/assets/heartstead/textures/item/.

Also add the Vital Heart crafting recipe as JSON in data/heartstead/recipe/.

Run ./gradlew build when done and report the actual result.
```

### 0.2 — Loot tables

```
Add Heart Shard to loot per docs/DESIGN.md §1: dungeon, mineshaft, temple and shipwreck
chests at 30% for 1-2; trial vaults; fishing treasure; 0.5% from any hostile mob; 10%
from Evoker and Elder Guardian.

26.2's entity predicate format is a component-style map that REJECTS unknown
sub-predicates, so 1.21.x examples are wrong. Show me one predicate and confirm it
before generating the rest.

Consider whether a Fabric loot-table-modification callback is a better fit than
overriding vanilla loot table JSON outright — overriding conflicts with every other mod
that touches the same table. Recommend one and say why.
```

### 0.3 — Drops and bounties

```
Implement docs/DESIGN.md §7.4 (loot and reward overhaul) and the bounty advancements
from §7.3 — explore, fight and build categories.

Propose the specific advancement triggers before writing them; I want to sanity-check
that "build" is actually detectable.
```

---

## Phase 0.5 — Risk spike

Still the two most dangerous mechanics. The difference from the data pack plan: each ships with a
GameTest instead of a manual checklist.

### 0.5a — Villager trade persistence

```
Build a proof of concept for docs/DESIGN.md §7.5 ONLY.

Inject a fixed trade onto a librarian and make it survive the villager regenerating
Offers on level-up and restock. Then write GameTests asserting it survives: level-up,
restock, chunk unload/reload, and world reload.

Run ./gradlew runGametest and report real output. If the tests can't cover world reload,
say so and give me a manual checklist for that case only.
```

### 0.5b — Excavation chain-break

```
docs/OPEN-QUESTIONS.md flags §3.3 Excavation as underspecified. Propose a concrete spec
first — block budget per level, durability model, block sets, recursion safety cap, and
interaction with Abundance and Kiln Touch on the same pickaxe.

Once I've agreed the spec, implement it with a GameTest covering the worst case, and
tell me the measured block count and tick cost.
```

---

## Phase 1 — Lives

```
Implement docs/DESIGN.md §5: Life Heart item and recipe, max_health attribute modifier
on consume, the 15-heart cap with escalating costs, -1 heart on death with a floor of 5,
and the cosmetic Frail indicator.

Player state uses attachments per CONVENTIONS.md §2.2, with a versioned codec. Every
number in §5 is a config field per §5 of CONVENTIONS.md, not a literal.

GameTest the floor: die repeatedly, assert health never goes below it.

Note DESIGN.md §1.1 — Blank Core must stay cheaper than Life Heart. Don't change either
recipe without flagging it.
```

---

## Phase 2 — Codex and librarian teaching

```
Implement docs/DESIGN.md §3.4 — the Codex block and block entity, Archive / Seal / Teach
flows, tier capacities, and the fixed librarian prices.

Reuse the Phase 0.5a persistence approach; read that code first rather than reinventing it.

The Codex is the first real block in the project, so establish the block + block entity +
screen handler patterns carefully here — cores and the Vault will copy them.
```

---

## Phase 3 — The Vault

The biggest lift.

```
Write the GameTest suite for docs/DESIGN.md §2.5 FIRST, before any Vault implementation:
deposit N / withdraw N exact conservation, full inventory on withdraw, capacity boundary,
mid-transfer shutdown, concurrent access. They should fail for the right reason
(no implementation) when you run them.

Then implement the storage layer per §2.1-2.3 with a versioned codec, and make the tests
pass. No UI in this task.
```

Then, separately:

```
Now the Vault screen per §2.4 — a real ScreenHandler with a scrollable slot grid, live
search, click-to-withdraw, shift-click-to-deposit and sort.

Screen classes go in src/client per CONVENTIONS.md §3; the screen handler itself is
common. Watch the client/server split — a leak here crashes dedicated servers.

Read §0.1 before you start: the UI ceiling is gone, but the pitch is casual convenience,
not an ME terminal. Propose the scope before building it.
```

---

## Phase 4 — Cores

```
Implement docs/DESIGN.md §4: Blank Core, the four attunement paths, the four housing
blocks, rate tiering, and every guardrail in §4.4 — especially the per-player active core
cap and offline accrual via a stored time delta settled on chunk load. No chunkloaders.

GameTest the accrual: advance the world clock, unload and reload the chunk, assert the
yield matches the formula and doesn't double-count. That's the bug this design invites.

Vault integration from §4.2 is the payoff — build it in this phase.

core_rate_multiplier ships at 1.0 but is a config field; I expect to lower it.
```

Then, separately:

```
docs/OPEN-QUESTIONS.md has §6 classic farm cores 3-11 with names only. Write the missing
spec — recipe, attunement condition, base rate, per-player cap — for each, matching the
Golem and Ominous entries. Propose it in DESIGN.md first; don't implement until I've
reviewed the rates.
```

---

## Phase 5 — Artisan's Table and Foundry

```
Implement docs/DESIGN.md §7.1. Note the pivot changed this feature substantially: read
the live RecipeManager instead of shipping a generated index, and a real 3x3 grid is
possible now.

The open problem is filtering — "every recipe in the game" isn't a usable list. Propose
the filtering and search UX before building it.
```

---

## Utility prompts

### Update the wiki after a decision

```
I changed <X> during implementation. Update docs/DESIGN.md to match, and remove the
matching entry from docs/OPEN-QUESTIONS.md if it resolved one. Don't touch anything that
didn't actually change.
```

### Version bump

```
26.3 released. Check meta.fabricmc.net and piston-meta for the new Loader / Fabric API /
Loom / Java versions, update gradle.properties and docs/REFERENCES.md, then run
./gradlew build and report what actually broke.

Don't trust mod-listing sites for version numbers — one of them was wrong about Fabric
Loader during the initial setup. Query the meta APIs directly.
```

### Before publishing

```
Audit the repo for anything still using a placeholder: TODO markers, placeholder
textures, hardcoded values that should be config, stale data-pack-era references
(custom_data markers, /dialog, pack.mcmeta, base-item tables), and any file referencing
a name other than heartstead. List them; don't fix them yet.
```
