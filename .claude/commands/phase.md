---
description: Implement a Heartstead design-wiki section
---

Implement the section(s) of `docs/DESIGN.md` named in: $ARGUMENTS

Before writing anything:

1. Read the named section(s) of `docs/DESIGN.md` in full, plus `docs/CONVENTIONS.md`.
2. Check `docs/OPEN-QUESTIONS.md` for a `[GAP]` or `[OPEN]` covering this area. If one exists, **stop
   and resolve it with me first** — don't invent the missing spec and proceed.
3. State back, in three or four lines: what you're building, which files you'll create, and any place
   the wiki is ambiguous. Then build it.

While building:

- Namespace `heartstead:`, `hs.` for scores/tags. Custom items identified via `custom_data` only.
- Every tuning number is a config scoreboard, not a literal.
- If you hit an unfamiliar JSON shape (a predicate, a dialog, an enchantment definition), show me
  **one** example and get it confirmed before generating the rest of the batch. 26.2 rejects unknown
  predicate sub-predicates, so a wrong shape fails silently across forty files.
- Prefer a generator in `scripts/` over emitting many near-identical files by hand.

When done, tell me exactly what to test in the dev world — and whether it needs a `/reload` or a full
world restart. Do not claim it works; you can't run Minecraft.
