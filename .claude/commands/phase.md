---
description: Implement a Sigilstead design-wiki section
---

Implement the section(s) of `docs/DESIGN.md` named in: $ARGUMENTS

Before writing anything:

1. Read the named section(s) of `docs/DESIGN.md` in full, plus `docs/CONVENTIONS.md`.
2. Check `docs/OPEN-QUESTIONS.md` for a `[GAP]` or `[OPEN]` covering this area. If one exists, **stop
   and resolve it with me first** — don't invent the missing spec and proceed.
3. If it touches an API you haven't confirmed for 26.2, run `./gradlew genSources` and read the real
   signature. Item construction and component registration both changed across 1.21.x; pre-1.21.5
   tutorials are wrong and the compile error won't say so.
4. State back in three or four lines: what you're building, which files, and any place the wiki is
   ambiguous. Then build it.

While building:

- Namespace `sigilstead`, package `com.sigilstead`. Ids via `Sigilstead.id()`.
- Real registered items and blocks — no `custom_data` identity markers, no marker entities standing
  in for blocks, no base-item table.
- Per-stack state → data components. Per-player → attachments. All persisted state → versioned codec.
- Respect the source-set split: anything that draws goes in `src/client`.
- Keep content data-driven — recipes, loot tables, advancements and tags stay JSON.
- Every tuning number is a config field, not a literal.
- Anything that moves items gets its GameTest written **first**.

When done, run `./gradlew build` and — if you added GameTests — `./gradlew runGametest`. **Report the
real output.** If something failed, say so; don't describe intent as if it were a result. Then tell me
what still needs a human in `runClient`, since feel and balance aren't testable.
