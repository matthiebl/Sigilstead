# Heartstead — Open questions

**[OPEN]** = not yet decided. Resolve one by writing the spec into [DESIGN.md](DESIGN.md), then
delete the entry here.

A design pass on 2026-08-01 unified the currency (§1), reworked the Vault (§2) and moved every
number in the pack into [DESIGN.md](DESIGN.md) §12. What's left is genuinely open, not merely
unwritten.

---

## Blocking — must be resolved before the phase that needs them

### [OPEN] The Funnel's configuration surface — the wiki and the request disagree
Blocking the Linked Funnel bug fix (PROMPTS.md, Known bugs, Bug 1), and it is a real disagreement
rather than a gap.

[DESIGN.md](DESIGN.md) §2.1 says the Funnel is configured **in the world, with no screen**: empty
hand cycles input/output as a block state, right-click with an item sets the output filter. The
stated reason is CLAUDE.md's "capability is not a mandate" — two fields do not earn a screen handler,
and the mode being a block state means it is visible without a sync packet.

Asked for on 2026-08-15: a **custom interface** controlling input, output and filters, with the
Funnel modelled and behaving like a hopper.

Both are defensible and they are not compatible:

- **In-world (current spec).** No screen to draw — which matters while the UI pass (Bug 2) is open.
  But it makes every right-click destructive: there is currently no way to touch a Funnel without
  reconfiguring it, which is a large part of why it feels broken.
- **A screen.** Room for more than one filter, a visible mode toggle, and interaction that is not
  accidental. Costs a fourth screen in a mod whose existing three need work, and the mode either
  stops being a block state or has to be kept in sync with one.

A middle option exists and is not currently written down anywhere: keep the block state, keep
sneak-right-click as the in-world toggle, and put the *filter* behind a screen — the field that
actually needs more than one slot.

**Resolve by writing the choice into §2.1**, then fix the bug against it.

---

## Non-blocking — decide during the phase

### [OPEN] Every number in §12 is unvalidated
The whole of [DESIGN.md](DESIGN.md) §12 is reasoned, internally consistent, and has never been
played. **This cannot be closed at a desk.** Expect Phase 0 to end in retuning.

The unification made this both easier and harder. Easier because there is now one drop rate instead
of two. Harder because a wrong rate no longer under-rewards one system — it distorts storage, farms
and survivability at once, and they mask each other (§11). **Instrument acquisition and each of the
three spend paths separately in the dev world**, or you will not be able to tell which dial is wrong.

The single most suspect number is the Sigil rate itself (§12.1). It was set by reasoning that a Sigil
is worth ~4 old Heart Shards *and* that three sinks need roughly a third more value per hour than two
did. Both halves of that are guesses.

### [OPEN] Is one Sigil too coarse a unit?
[DESIGN.md](DESIGN.md) §1 deliberately has **no sub-unit** — you find Sigils, full stop. The three-way
branch supplies the granularity through recipe surcharges (§12.2), so a second tier would re-add the
stepping stone the unification removed.

The risk is drop *granularity*, not value: a 0.15% hostile-mob rate is a very lumpy way to pay out a
currency this central, and there is no way to pay a player a small amount. If playtesting says common
sources feel like they pay nothing at all, the escape hatch is a **Sigil Shard** sub-unit — several
combine into a Sigil, common sources pay shards, structures pay whole Sigils. Recorded rather than
built, because it costs an item and a crafting step and might not be needed.

### [OPEN] Unpacking containers on deposit
[DESIGN.md](DESIGN.md) §2.5 rejects any item holding contents, so a filled shulker box hauled home
does not deposit in one click — you place it, empty it by hand, then deposit. **Deferred deliberately
on 2026-08-01:** the friction is predicted, not felt.

The fix, if it bites: shift-clicking a filled shulker or bundle into the Vault **unpacks** it —
contents deposited, empty container returned. That keeps the anti-laundering property (nothing with
contents is ever *stored*) while removing the tedium. It is a new transfer path, so it would ship
with its own §2.6 conservation tests.

### [OPEN] A remote outpost that can withdraw — the Vault Relay
[DESIGN.md](DESIGN.md) §2.3 buys withdrawal reach a whole dimension at a time. The alternative shape,
borrowed from Refined Storage's transmitter/receiver pair, is a **Vault Relay**: a block you build at
a remote site that projects local reach around itself. It converts "buy a dimension" into "build an
outpost", which is more on-pitch for a mod about building.

Not specced because the dimensional ladder already covers the need and a Relay is a whole extra block
with its own placement and persistence rules. Revisit only if reach tiers feel like a shopping list
rather than an achievement.

### [OPEN] Auto-restock
The cut Void Satchel had "auto-restock hotbar and armour" attached to it. That capability did not
move anywhere when the item was cut (§2.2) — it is simply not in the pack right now.

It is a genuinely nice T3 convenience and would fit as an upgrade to the Vault Pouch gated behind
End reach. Deliberately not specced: it is scope, it interacts with the withdrawal-is-ranged rule in
ways that need thought (does it fire while you're out of reach?), and the pack does not need it to
be good.

### [OPEN] How big is a housing's "small internal storage"?
[DESIGN.md](DESIGN.md) §4.2 never sizes it, and the number decides how long a housing with no Linked
Funnel keeps producing before it stalls. **Shipped as `core.housing_slots`, default 9** (one chest
row) — chosen so a housing left overnight fills and stops rather than banking forever, which is the
behaviour §4.2 asks for when it says internal storage limits should be meaningful. Unvalidated: nine
slots of a Quarry Node's 8-blocks-per-cycle output is roughly ten minutes, which may well feel short.

### [OPEN] Config surface
[CONVENTIONS.md](CONVENTIONS.md) §5 puts config in a codec-backed JSON file loaded on server start.
Undecided: whether operators get an in-game config screen, a command, or just the file. **The file
alone is the v1 answer** unless playtesting makes editing it painful — recorded here rather than
resolved because it costs nothing to defer.

### [OPEN] Bundle slot count
[DESIGN.md](DESIGN.md) §2.2 overrides the vanilla Bundle to a 9-slot UI inventory — ~9× vanilla
capacity at T1 for vanilla cost, and the only place the mod rewrites a vanilla item (pillar 6).
**Decided 2026-08-01: keep 9 and ship it as a config field** (`bundle_slots`). Flagged because it is
one of the two largest unvalidated buffs in the pack and the first thing to lower if the early game
feels weightless.

### [OPEN] Free universal deposit
[DESIGN.md](DESIGN.md) §2.0 grants deposit from anywhere, in any dimension, at T1. That deletes the
"walk home when your inventory is full" loop outright — the other largest unvalidated buff, and the
one most likely to be *too* convenient.

**Decided 2026-08-01: ship it on**, because it is the pitch and because the alternative (ranged
deposit) makes the T1 Satchel nearly useless. It ships behind `deposit_requires_reach` (default
`false`) so it can be switched rather than rewritten. Watch whether mining trips stop ending.

---

## Resolved

Kept briefly so that older notes and prompts make sense. Delete once nothing references them.

### ~~[RESOLVED 2026-08-01] Should Vault Sigils be craftable?~~
**Yes — the whole currency unified instead.** There is now one found item, the **Sigil**, crafting
into Core / Heart / Vault Sigil (§1). This fixed a real problem: under the old two-currency spine a
storage-focused player accumulated cores they didn't want and a farm-focused player found Sigils that
were dead in their hands.

The property the old uncraftable Sigil was protecting — *storage scales with how much you explored* —
is preserved by the **structural gate** instead: reach tiers cost dimensional Vault Sigils carrying a
proof item from the dimension they unlock (§2.3, §12.2). Sigils are fungible; a Ghast Tear is not.

### ~~[RESOLVED 2026-08-01] The portable Vault Anchor~~
An Anchor that dropped itself and opened a full Vault UI was a pocket Vault: place, withdraw, break,
walk on — which bypassed the entire access ladder at T1 for one amethyst shard. Fixed by **activation**
(§2.1): the first Anchor a world ever has activates free, every activation after costs a Vault Sigil,
and breaking one loses the activation. Carrying it costs a Sigil per use; moving house costs one once.

Rejected alternatives: an Anchor that doesn't drop (too weak — Anchors are cheap to recraft), and a
re-attunement timer (a delay players would read as a bug).

### ~~[RESOLVED 2026-08-01] Linked Barrel and Linked Chest~~
**Cut.** Their real function was portable remote deposit — carry a barrel, drop it in a mine, dump
everything — which undercut the Vault Pouch with a 2-pearl T1 block. §2.0 now grants remote deposit
outright, so the blocks had nothing left to do. Funnelling in and out of the Vault is one job and the
**Linked Funnel** does it, now T1 and no longer Echo-Shard-gated.

### ~~[RESOLVED 2026-08-01] The Void Satchel~~
**Cut.** Its entire job was "reach anywhere", and reach moved to the Anchor (§2.3) where it is shared
world progress. The access ladder is two rungs with one meaning: Satchel = deposit (the safe verb),
Pouch = withdrawal (the powerful one).

### ~~[RESOLVED 2026-08-01] Where do Sigil fragments come from?~~
Superseded. There are no fragments and no advancement pays one (§7.3); the sub-unit question is
reopened in a different form above as "is one Sigil too coarse a unit".

### [OPEN] Does the discovery path need a guidebook as well?
[DESIGN.md](DESIGN.md) §7.3 shipped as an advancement tree plus a recipe-unlock layer, and
deliberately **not** as a custom book screen. The tree covers title, icon, one-paragraph explanation
and ordering; the recipe book covers drawn crafting grids. What neither covers is longer-form lore,
and a toast you have already dismissed.

Deferred rather than rejected, for two reasons: the cheap half was worth shipping alone, and adding
a fourth custom screen while the existing three are being reworked (PROMPTS.md, Bug 2) would inherit
that problem. **Decide after playing the tree**, not before — the follow-up prompt in PROMPTS.md
asks the only question that settles it.

### ~~[RESOLVED 2026-08-15] Advancements — whether to have them at all~~
**Built**, as [DESIGN.md](DESIGN.md) §7.3's discovery path. The 2026-08-01 gate ("design them last,
after playtesting") was about a *reward* tree, which cannot be tuned before the economy is played.
This one pays nothing — no Sigils, no loot, no experience — so there was nothing to tune, and the
discovery gap it closes was real on day one of any world. The bounty objection below is untouched
and still binding.

### ~~[RESOLVED 2026-08-01] Bounty advancements~~
**Cut**, along with the reward crates and the capstone Sigils. Reasoning is preserved in
[DESIGN.md](DESIGN.md) §7.3, and the unified currency makes the objection stronger, not weaker: a
second source of the one item moves every dial in the pack at once.

### ~~[RESOLVED 2026-08-01] §5 classic farm cores, entries 3–11~~
All eleven entries have tier, housing, prime recipe, imprint condition and base rate, plus the
comparison against real technical farms — all now in [DESIGN.md](DESIGN.md) §12.5. They reuse the
four §4.2 housings; no new blocks.

### ~~[RESOLVED 2026-08-01] §7.1 Artisan's Table — full spec~~
Recipes in §12.2. The filtering problem dissolved: extending the vanilla crafting menu inherits the
whole recipe book (search, tabs, craftable-only, click-to-fill) off the live `RecipeManager`. Verified
class shapes are in [REFERENCES.md](REFERENCES.md). Note that pulling from the Vault is a withdrawal
and therefore obeys §2.0 reach — that falls out of the rule and needed no extra design.

### ~~[RESOLVED 2026-08-01] The Foundry~~
**Cut.** Smelting stays vanilla.

### ~~[RESOLVED 2026-08-01] Multiplayer scope~~
The Vault (§2) and the active-core registry (§4.3) are **world** state — one Anchor per world, shared
contents, pooled capacity *and pooled reach*. The Codex archive (§3.3) stays **per player**. What each
player owns individually is now which verbs they have (Satchel vs Pouch), not how far they reach.

### ~~[RESOLVED 2026-08-01] Per-player core cap~~
Replaced by **one active core per target, per world**, refused at the point of socketing (§4.3).

### ~~[RESOLVED 2026-08-01] Existing-world migration~~
**No catch-up path.** The mod works in an existing world, but the economy starts at zero — you
explore for Sigils like anyone else. Say so in the README.

### ~~[RESOLVED 2026-08-01] How far to take the UI~~
Settled per feature: the Vault Anchor is a two-tab screen, a slot grid with search and sort and
explicitly **not** a logistics network (§2.4); the Artisan reuses vanilla's recipe book rather than
inventing a browser (§7.1); core attunement is a tooltip and nothing else (§4.1).

### ~~[RESOLVED 2026-08-01] Realms support~~
**No Realms.** Realms takes data packs, not mods, and the block registration that forced the mod
decision isn't reversible.

### ~~[RESOLVED 2026-08-01] Pack name~~
**Heartstead**, committed. `heartstead` namespace, `com.heartstead` package. Availability against
existing Minecraft projects was never formally checked — worth a look before publishing, but not a
blocker.

### ~~[RESOLVED 2026-08-01] Excavation~~
**Cut.** Chain-break/vein-mine is not in the pack.

### ~~[RESOLVED 2026-07-31] Recipe ingredients can't check custom_data~~
Resolved by becoming a Fabric mod. Items are real registered ids, so an ingredient of
`heartstead:sigil` matches only a Sigil. **Do not reintroduce a base-item table.**
