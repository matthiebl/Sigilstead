# Sigilstead — Claude Code prompts

Ready-to-paste prompts for the work that is **still open**. Rules that apply to all of them:

- **Run Claude Code from the repo root.**
- **Reference wiki sections, don't re-describe features.** "Implement §7.1 per the wiki" keeps drift
  down; re-explaining invites a second, subtly different design.
- **One task per session.** Never ask for the whole mod.
- **Read the real API before writing against it.** 26.x ships unobfuscated, so the jar has true
  names — `javap -cp ~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar <class>`, or
  `./gradlew genSources` for full bodies. Every tutorial predating 26.1 is wrong about names
  (`ResourceLocation` is now `Identifier`) and usually about shapes too. See REFERENCES.md.
- **Claude Code can verify its own work** (`build`, `test`, `runGametest`). Expect it to. What it
  still can't judge is feel — balance and UI pleasantness need `runClient` and you.

Phase numbers match [DESIGN.md](DESIGN.md) §9.

## Status

Phases 0 through 4 have shipped. Rather than keep their prompts, this file keeps what they left
behind; the prompts themselves are in the git history.

| Phase | Content | Status |
|---|---|---|
| 0 | §1 spine, §12.1 Sigil sources, §12.2 recipes | ✅ |
| 0.5 | §7.2 villager trade persistence | ✅ |
| 1 | §6 lives and death | ✅ |
| 2 | §2 the Vault — storage layer and screen | ✅ |
| 2.5 | Economy retrofit to spec v0.4 (rename, drop tables, Vault rework) | ✅ |
| 3 | §3.1–3.2 enchantments, §3.3 Codex | ✅ |
| 3 | §7.1 Artisan's Table and Kit | ⬜ **open** |
| 4 | §4 cores, §5 the eleven classic replacements | ✅ |
| — | §2.2 Bundle override | ⬜ **open, unclaimed by any phase** |
| 5 | §7.3 the discovery path — advancement tree + recipe unlocks | ✅ |

Two bugs found before the first real playtest are tracked below, under **Known bugs**. Neither is
fixed; both are reproducible from `runClient`.

Phases 0–2 shipped against spec v0.3; v0.4 unified the currency and reworked the Vault, and Phase
2.5 closed that gap in three separate commits (rename, then drop tables, then behaviour). The v0.3
spine — `heart_shard` / `vital_heart` — no longer exists anywhere and has no migration path; this is
a pre-release mod, so old ids were deleted outright rather than aliased.

**Up next: the Artisan's Table.** It is the last unbuilt block in the spec, and the Vault's sync path
was deliberately built with it in mind as a second consumer (REFERENCES.md; see the comments on
`VaultSyncPayload` and `VaultClientCache`, and the standing `TODO Phase 3` in `SigilsteadClient`).

---

## Open — §7.1 Artisan's Table and Kit

```
Implement docs/DESIGN.md §7.1 — Artisan's Table (T1 block) and Artisan's Kit (T2
portable). Both recipes are in §12.2 and neither exists yet.

Read REFERENCES.md "the recipe book is reusable" first. Do NOT build a recipe browser:
extend the vanilla crafting menu and return RecipeBookType.CRAFTING, which inherits
search, category tabs, craftable-only and click-to-fill off the live RecipeManager.

The only new work is the two Vault hooks in that REFERENCES section. Route every
withdrawal through the existing Phase 2 transfer code in com.sigilstead.vault — a second
item-moving path is how the Vault gets a dupe bug, and it would invalidate the §2.6 suite.
The client-side Vault snapshot already exists and was designed for exactly this second
consumer; reuse VaultSyncPayload rather than adding a payload.

Pulling ingredients out of the Vault IS a withdrawal, so it obeys §2.0 reach: the Table is
always in local reach, the Kit used in the world needs the tier covering where the player
stands. Falls out of the existing rule — do not add a second concept for it.

Screen classes go in src/client per CONVENTIONS.md §3. Follow the patterns the Vault and
Codex screens already established rather than inventing a third.

GameTest the reach rule at minimum: craft from the Kit in an unbought dimension and assert
the withdrawal is refused. Then ./gradlew build && ./gradlew runGametest and report real
output.
```

## Open — §2.2 the Bundle override

Unclaimed by any phase, which is why it is still here. `bundle_slots` is deliberately absent from
`HsConfig` today (see the comment there) — this task adds it.

```
Implement the §2.2 Bundle override: replace vanilla Bundle behaviour with a UI-based
inventory of `bundle_slots` slots. Read the balance flag in §2.2 before starting — this is
the one place the design overwrites a vanilla item, and the slot count is the largest
unvalidated T1 buff in the pack.

Constraints already in the codebase, do not regress them:
- The Satchel crafts FROM a vanilla Bundle (§12.2), so the override must not break that
  recipe or the Satchel's own behaviour.
- §2.5 nesting already works in the other direction — Satchels and Pouches refuse to go
  inside container items, and the Vault rejects any stack with non-empty container or
  bundle_contents. See Vault.java. The override must keep both true.

bundle_slots is a config field per §12.7 and CONVENTIONS.md §5, currently absent from
HsConfig on purpose. Add it there.

./gradlew build && ./gradlew runGametest and report real output.
```

---

## Known bugs

Both were found reading the code and the assets, not from a session — so **reproduce them in
`runClient` before believing the diagnosis in either prompt.**

### Bug 1 — the Linked Funnel does not work, and does not look like a Funnel

Reported: nothing about it functions in play. Two things are certainly wrong before anything else
is investigated:

- **It renders as a plain full cube.** `models/block/linked_funnel_input.json` and
  `..._output.json` are both `minecraft:block/cube_bottom_top`. There is no hopper-shaped model, no
  facing state, and nothing visually distinguishes input mode from output mode except the texture.
- **`useItemOn` swallows every right-click with an item in hand**
  ([LinkedFunnelBlock.java:89](../src/main/java/com/sigilstead/block/LinkedFunnelBlock.java#L89)),
  setting the filter from whatever you happen to be holding — including when you were trying to
  place a block against it. Combined with empty-hand cycling the mode, there is no way to interact
  with a Funnel without changing its configuration.

> **This request contradicts the wiki and the conflict is unresolved.** DESIGN.md §2.1 says the
> Funnel is configured in the world, with no screen, and gives a reason ("two fields do not earn a
> screen handler"). A custom interface with input/output control and filters is a different design.
> That decision is logged in OPEN-QUESTIONS.md — **settle it there first**, then implement, then
> update §2.1 in the same change.

```
The Linked Funnel (DESIGN.md §2.1) does not work in play. Before changing anything:
reproduce it in runClient and tell me what actually fails — placement, ticking,
transfer, or the interaction handler eating every right-click.

Then fix it in three parts, in this order, and don't merge them:

1. Behaviour. LinkedFunnelBlockEntity already routes everything through the §2.6-tested
   Vault transfer path; keep that. If the bug is in the block rather than the block
   entity, say so rather than rewriting the transfer code.
2. Model. A real hopper-shaped 3D model with a facing block state, input and output
   visually distinct. Vanilla's hopper model is the reference; do not blit a cube.
3. Interface. Read the OPEN-QUESTIONS.md entry "the Funnel's configuration surface"
   first — §2.1 currently forbids a screen, and that has to be resolved before a screen
   handler is written. If it resolves to a screen: input/output toggle and a filter slot
   that holds a type without consuming the item, following the Vault and Codex screen
   patterns rather than a third one, screen classes in src/client per CONVENTIONS.md §3.

GameTest the transfer in both directions, including the §2.0 rule that output mode is
refused outside the Vault's reach. ./gradlew build && ./gradlew runGametest, real output.
```

### Bug 2 — the interfaces do not feel like Minecraft

Reported: rounding is wrong and the screens read as not-vanilla. This covers every screen the mod
has — Vault (both tabs), Codex, core housing — so it is one pass, not four.

`HsGuiPainting` is where the shared drawing lives, and it is the right place for the fix; the
individual screens should end up with less painting code, not more.

```
Every Sigilstead screen (Vault §2.4, Codex §3.3, core housing §4.2) reads as
not-quite-Minecraft. Corner rounding is wrong and the panels don't sit in vanilla's own
visual language.

Start by reading vanilla's own widget sprites and nine-slice definitions out of the jar
rather than guessing at radii — 26.2 ships them as sprite JSON, and matching them is the
whole job. Report what vanilla actually uses before changing a pixel.

Then fix it centrally in src/client/.../screen/HsGuiPainting.java. Every screen should
come out of this using MORE of the shared painting and less of its own.

Do not redesign layouts or add features — §2.4's two-tab shape, the Codex's flows and the
housing's slots all stay exactly as they are. This is presentation only.

I have to judge this one in runClient, so end by telling me what to look at.
```

### Follow-up — a graphical guidebook

Deliberately not built alongside §7.3's advancement tree. The reasoning is in §7.3's last bullet:
the tooltip already renders a title, an icon and a description in vanilla's frame, which is most of
a guide page for none of the cost — and **building a new custom screen while Bug 2 is open would
inherit the same problem.** Do Bug 2 first, then decide whether the tree left a real gap.

The gap it would fill, if one is felt: the tree cannot draw a crafting grid. §7.3 unlocks the
recipes into the vanilla recipe book instead, which does draw them.

```
Play the §7.3 advancement tree from a fresh world and tell me which of these you
actually wanted and did not get: a drawn crafting grid, longer-form lore, or a page you
can re-read after the toast is gone. Then design the smallest thing that gives me that
one — and check whether the vanilla recipe book already does it before proposing a
screen.
```

---

## Utility prompts

### Playtest follow-up

The whole of §12 is unplayed. After a session in `runClient`:

```
I played <X> and <Y> felt wrong. Find the dial in DESIGN.md §12 that controls it, check
§10 for what else that dial moves, and tell me what a change would cost elsewhere before
changing anything.
```

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
textures, hardcoded values that should be config, and any file referencing a name other
than sigilstead. List them; don't fix them yet.

Every texture in assets/sigilstead/textures is currently a stand-in (commit c0251d5).
```
