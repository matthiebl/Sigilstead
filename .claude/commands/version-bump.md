---
description: Retarget the pack at a new Minecraft version
---

Retarget Heartstead at: $ARGUMENTS

1. Look up the **data pack** and **resource pack** format numbers for that version on
   <https://minecraft.wiki/w/Pack_format>. They are numbered **independently** — do not reuse one for
   the other.
2. Update `docs/REFERENCES.md` (the verified-facts table), `datapack/pack.mcmeta` and
   `resourcepack/pack.mcmeta`, and the version row in `docs/DESIGN.md` §9.
3. Check the version's changelog for breaking changes to: entity predicates, the `enchantment_level`
   predicate shape, loot table condition ordering, villager `Offers`/`Tags` persistence, dialog
   schema, and the `custom_data` / `item_model` components. Report what changed.
4. Produce a **re-test checklist** ordered by risk, leading with the two known-fragile mechanics:
   villager trade persistence (DESIGN.md §7.5) and anything that moves items through `/data`
   (DESIGN.md §2.5).

Do not migrate any code yet — report first.
