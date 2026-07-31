# Heartstead — References

Sources cited in the design conversation, plus the ones needed to actually build this.

---

## Verified facts (checked 2026-07-31)

| Fact | Value | Source |
|---|---|---|
| Current Java release | **26.2** ("Chaos Cubed", 16 Jun 2026) | [Pack format – Minecraft Wiki](https://minecraft.wiki/w/Pack_format) |
| 26.2 **data** pack format | **107.1** | [Pack format – Minecraft Wiki](https://minecraft.wiki/w/Pack_format) |
| 26.2 **resource** pack format | **88.0** | [Pack format – Minecraft Wiki](https://minecraft.wiki/w/Pack_format) |
| 26.1 data / resource | 101.1 / 84.0 | [Pack format – Minecraft Wiki](https://minecraft.wiki/w/Pack_format) |
| 26.3 snapshots | data 108.0 → 113.0, resource 89 → 94.0, still moving | [Java Edition 26.3 Snapshot 5](https://minecraft.wiki/w/Java_Edition_26.3_Snapshot_5) |
| `min_format`/`max_format` | Replaced `pack_format` as of 25w31a | [Pack format – Minecraft Wiki](https://minecraft.wiki/w/Pack_format) |

**Implication:** pin narrowly and expect to re-verify on the 26.3 release — formats moved six times
inside a single snapshot cycle.

> **The two pack formats are numbered independently.** For 26.2 the datapack is `107` and the resource
> pack is `88`. Copying one `pack.mcmeta` into the other folder silently produces an incompatible pack.
> Since 69.0 (1.21.9) both use minor versions, incremented instead of the major for non-breaking changes.

---

## Dialogs (`/dialog`)

The whole UI layer depends on this. Added in 1.21.6.

- [Dialogs — Datapack Wiki](https://datapack.wiki/wiki/files/dialogs) — the reference for dialog file
  shape, input types, and how inputs become macro values.
- [Dialog command generator — MinecraftMaps](https://www.minecraftmaps.com/tools/dialog-command-generator)
  — useful for scaffolding a dialog JSON quickly.
- [Minecraft snapshot 25w20a](https://www.minecraft.net/en-us/article/minecraft-snapshot-25w20a) —
  Mojang's own framing: dialogs are for **simple messages and input**, explicitly *not* for describing
  in-game UI. This is the source of the hard wall in design wiki §0.1.
- [GUI Maker — Modrinth](https://modrinth.com/datapack/gui-maker) — prior art worth reading before
  building the Vault UI. Also the source of the "dialogs only reload on world/server restart" note.

---

## Still needed (not yet gathered)

These matter and are not yet pinned down. Gather before the phase that needs them.

| Topic | Needed for | Why it's risky |
|---|---|---|
| 26.2 entity predicate format | Everything with a predicate | Changed to a component-style map; **rejects unknown sub-predicates**. Every 1.21.x example on the internet is wrong |
| `enchantment_level` predicate shape | §3.1 Abundance, §3.2 Kiln Touch | Shape changed in recent versions |
| `apply_bonus: ore_drops` semantics | §3.2 Kiln Touch × Fortune stacking | The whole point of Kiln Touch is that it *keeps* Fortune value |
| Villager `Offers` / `Tags` persistence in 26.2 | §7.5, §3.4 | 26.2 fixed an empty-`Offers` persistence bug. Behaviour is version-specific |
| Custom enchantment definition format | §3 all | Datapack enchantments are relatively new and still churning |
| `minecraft:item_model` component | Resource pack | Replaced the old CustomModelData predicate workflow |
| Macro command limits / escaping | §2 Vault, §7.1 Artisan | Macro injection via `/data` is where item loss will come from |

---

## Prior art worth studying

Read these before designing the equivalent system — not to copy, but to know what players already
expect and what has already gone wrong.

- **Storage:** Applied Energistics / Refined Storage (the terminal metaphor the Vault is imitating
  with a list instead of a grid).
- **Farm replacement:** Mob Grinding Utils, Industrial Foregoing, Woot — specifically how they handle
  "no mobs actually spawn" balance.
- **Lives:** Hardcore Hearts / Origins-style life systems, and the Lifesteal SMP heart-item economy.
- **Datapack-side:** any pack shipping a `/dialog` UI at scale, for iteration-speed workarounds.

---

## Source conversation

The design originated in a Claude conversation dated 30 Jul 2026, shared at
`claude.ai/share/d51527d6-59e7-4433-9300-d00d908e92d0`. That conversation produced a design wiki file
which is **not retrievable from the share link** — [DESIGN.md](DESIGN.md) is a reconstruction from
the conversation body. Gaps are tracked in [OPEN-QUESTIONS.md](OPEN-QUESTIONS.md).
