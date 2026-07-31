#!/usr/bin/env python3
"""Generate the Artisan's Table recipe index.

NOT IMPLEMENTED — this is a spec stub. See docs/PROMPTS.md "Phase 5".

WHY THIS EXISTS
---------------
mcfunctions cannot read the vanilla recipe registry at runtime. The Artisan's Table
(docs/DESIGN.md §7.1) shows a searchable list of craftable recipes with Craft x1/x8/x64
buttons, which means the pack has to *ship* a precomputed index of every recipe it will
ever offer. That index is ~280 curated recipes. Hand-maintaining it is a trap: it goes
stale the first time a recipe changes, and nobody notices until a player can't craft
a ladder.

Build this BEFORE the Artisan's Table functions that consume it.

CONTRACT
--------
Input:
  - vanilla recipe data, extracted from the client/server jar for the target version
    (see docs/REFERENCES.md for the pinned version — currently 26.2)
  - datapack/data/heartstead/recipe/**.json  (our own recipes)
  - a curation list deciding which vanilla recipes are worth surfacing
    (~280 of them; the full registry is far too long to scroll in a dialog)

Output:
  - datapack/data/heartstead/generated/artisan_index.json

Requirements:
  - DETERMINISTIC. Same inputs must produce byte-identical output, so diffs are
    reviewable and a regeneration with no upstream change is a no-op.
  - Every generated file carries the "heartstead:_meta" marker described in
    docs/CONVENTIONS.md §7 — JSON has no comments, and someone WILL try to hand-edit this.
  - Fail loudly on an unknown ingredient type rather than silently dropping a recipe.
    A missing recipe is invisible in-game; a crash is not.

OPEN
----
The index schema itself is not specified — see docs/OPEN-QUESTIONS.md, "§7.1 Artisan's
Table — full spec". Decide the schema before writing this, not during.
"""

import sys


def main() -> int:
    raise SystemExit(
        "generate_recipe_index.py is a stub. Read the module docstring, then see "
        "docs/PROMPTS.md Phase 5."
    )


if __name__ == "__main__":
    sys.exit(main())
