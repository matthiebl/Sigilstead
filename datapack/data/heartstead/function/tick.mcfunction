# heartstead:tick — runs every tick
#   @s: the server
#
# DISPATCH ONLY. No logic lives here. See docs/CONVENTIONS.md §3.
#
# Anything doing real work runs from a `schedule`d function or a gated interval, never
# unconditionally every tick. Cores in particular settle on chunk load from a stored
# world-time delta (DESIGN.md §4.4) — they must NOT be ticked, and the pack must never
# require a chunkloader.

# --- every tick (keep this list empty if at all possible) ---

# --- gated intervals ---
# TODO Phase 3: vault deposit scan  — every 10t
# TODO Phase 4: core settle sweep   — every 20t, budgeted
