---
description: Reconcile the design wiki with what the code actually does
---

Reconcile `docs/DESIGN.md` with the implementation.

Scope: $ARGUMENTS (if empty, everything implemented so far).

1. For each implemented system, compare the code against its wiki section. Report **only** real
   divergences — a recipe that differs, a rate that differs, a guardrail that isn't enforced, a
   config value that got hardcoded.
2. For each divergence, say which side is right. Usually the code (it was tested); sometimes the
   wiki (the code drifted). Don't assume.
3. Apply the fixes to whichever side is wrong.
4. Remove any `docs/OPEN-QUESTIONS.md` entry that implementation has actually resolved, and add one
   for anything that turned out to be underspecified.

Don't reformat, don't reword, don't "improve" prose. Only change what diverged.
