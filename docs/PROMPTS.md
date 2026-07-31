# Heartstead — Claude Code prompts

Ready-to-paste prompts, in build order. Rules that apply to all of them:

- **Run Claude Code from the repo root**, never a subfolder — it needs to see `datapack/`,
  `resourcepack/` and `scripts/` together to keep item IDs and tags consistent across all three.
- **Reference wiki sections, don't re-describe features.** "Implement §4.2 Soul Cage per the wiki"
  keeps drift down; re-explaining it invites a second, subtly different design.
- **One phase per session.** Never ask for the whole pack.
- **Nothing is done until you `/reload` it in the dev world and watch it work.** Claude Code cannot
  launch Minecraft to check its own output, and loot table / predicate JSON is easy to get subtly
  wrong. Dialogs need a full world restart, not `/reload`.

---

## Phase 0 — Heart economy

Pure JSON, zero runtime risk, changes the feel of the game immediately. Ship this alone and it's
already a good pack.

### 0.1 — Items and recipes

```
Read docs/DESIGN.md §1 and docs/CONVENTIONS.md.

Implement the Heart Shard and Vital Heart items, and the Vital Heart crafting recipe
(§1). Follow the custom_data conventions in CONVENTIONS.md §2 exactly — I want to be
able to grep for hs item ids later.

Also add the resource pack side: item model entries, textures as 16x16 placeholders,
and lang keys so both items are readable without the resource pack.

Do not touch loot tables yet — that's the next task.
```

### 0.2 — Loot tables

```
Add Heart Shard to loot per docs/DESIGN.md §1: dungeon, mineshaft, temple and
shipwreck chests at 30% for 1-2; trial vaults; fishing treasure; 0.5% from any hostile
mob; 10% from Evoker and Elder Guardian.

Before writing any predicate, check the 26.2 entity predicate format — it moved to a
component-style map and now REJECTS unknown sub-predicates, so 1.21.x examples are
wrong. Show me one predicate before generating the rest.

List every vanilla loot table you're overriding, and flag any that a common datapack
would also want to override.
```

### 0.3 — Broadened drops and bounties

```
Implement docs/DESIGN.md §7.4 (loot and reward overhaul) and the bounty advancements
from §7.3.

For bounties I want the three categories from the pillars — explore, fight, build —
each paying shards/emeralds. Propose the specific advancement triggers before writing
them; I want to sanity-check that "build" is actually detectable.
```

---

## Phase 0.5 — Risk spike (do this before anything depends on it)

The two most dangerous mechanics in the pack. Better to hit format quirks in week one on a throwaway
feature than after the Vault and Cores are wired through them.

### 0.5a — Villager trade persistence

```
Build a throwaway proof of concept for docs/DESIGN.md §7.5 ONLY. Nothing else.

Inject a fixed trade onto a librarian, tag the villager, and re-apply the offer when
the villager regenerates Offers on level-up or restock. I want to test that it
survives: level-up, restock, relog, chunk unload/reload, and world restart.

Put it in a scratch namespace, not heartstead:. Give me a test checklist I can run
in-game and report back on.
```

### 0.5b — Excavation chain-break

```
docs/OPEN-QUESTIONS.md flags §3.3 Excavation as underspecified. Before implementing:
propose a concrete spec — block budget per level, durability model, block sets,
recursion safety cap, and how it interacts with Abundance and Kiln Touch on the same
pickaxe.

Then build a throwaway proof of concept in a scratch namespace and tell me the
worst-case block count and tick cost.
```

---

## Phase 1 — Lives

```
Implement docs/DESIGN.md §5 in full: Life Heart item and recipe, max_health attribute
modifier on consume, the 15-heart cap with escalating costs, -1 heart on death with a
floor of 5, and the cosmetic Frail indicator at the floor.

Every number in §5 must be a config scoreboard per CONVENTIONS.md §6, not a literal.

Note §1.1: Blank Core must stay cheaper than Life Heart. Don't change either recipe
without flagging it.
```

---

## Phase 2 — Codex and librarian teaching

```
Implement docs/DESIGN.md §3.4 — the Codex block, Archive / Seal / Teach flows, tier
capacities, and the fixed librarian prices.

This depends on the Phase 0.5a persistence result — read that code first and reuse the
approach rather than reinventing it.

Build the three dialogs last, and remember dialogs don't hot-reload: batch all dialog
changes so I only have to restart the world once per round.
```

---

## Phase 3 — The Vault

The biggest engineering lift. Do it after the economy is tuned.

```
Implement docs/DESIGN.md §2. Start with the storage layer only — the /data schema per
CONVENTIONS.md §4, deposit, withdraw, and the capacity limits from §2.3. No UI yet.

Write it defensively: §2.5 says this system will eat someone's inventory at least once.
Every path that removes an item must be guarded and must fail closed. Tell me where the
unrecoverable failure modes are.

Then give me a command-line test harness — functions I can run to deposit/withdraw N of
X and assert the totals — before we build any dialog.
```

Follow-up once the storage layer is trusted:

```
Now the Vault dialog UI per §2.4. Remember §0.1: no draggable slots, no grid. Searchable
list, withdraw 1/16/64 buttons per row, pinned favourites, recents page, page counter.
```

---

## Phase 4 — Cores

```
Implement docs/DESIGN.md §4: Blank Core, the four attunement paths, the four housings,
rate tiering, and every guardrail in §4.4 — especially the per-player active core cap
and offline accrual via world-time delta. No chunkloaders, ever.

Vault integration from §4.2 (output into an adjacent Linked block) is the payoff — build
it in this phase, not later.

core_rate_multiplier ships at 1.0 but must be a config value; I expect to lower it.
```

Then, separately:

```
docs/OPEN-QUESTIONS.md has §6 classic farm cores 3-11 with names only. Write the missing
spec — recipe, attunement condition, base rate, per-player cap — for each, matching the
shape of the Golem and Ominous entries. Propose it in DESIGN.md first; don't implement
until I've reviewed the rates.
```

---

## Phase 5 — Artisan's Table and Foundry

**Build the generator first.** The recipe index is a dependency of the crafting system, and having it
as a real generated artifact rather than hand-maintained JSON saves you every time you add a recipe.

```
Write scripts/generate_recipe_index.py per its docstring. It reads the vanilla recipe
data plus our own recipes and emits the curated ~280-recipe index the Artisan's Table
needs, because functions can't read the recipe registry at runtime.

Output must be deterministic — same input, byte-identical output — so diffs are readable.
Do not write the Artisan's Table functions yet.
```

---

## Utility prompts

### Update the wiki after a decision

```
I changed <X> during implementation. Update docs/DESIGN.md to match, and remove the
matching entry from docs/OPEN-QUESTIONS.md if it resolved one. Don't touch anything
that didn't actually change.
```

### Version bump

```
26.3 released. Update docs/REFERENCES.md with the new pack format, bump min_format/
max_format in both pack.mcmeta files, and give me a re-test checklist focused on the
version-sensitive areas — villager Offers manipulation, entity predicates, and the
enchantment_level predicate shape.
```

### Before publishing

```
Audit the repo for anything still using a placeholder: TODO markers, placeholder
textures, un-namespaced ids, config literals that should be scoreboards, and any file
still referencing a name other than heartstead. List them; don't fix them yet.
```
