# heartstead:load — runs on world load and /reload
#   @s: the server
#   writes: hs.* scoreboard objectives, heartstead:world config
#
# Responsibilities (see docs/CONVENTIONS.md §5, §6):
#   1. Create every hs.* objective (idempotent — this runs on every /reload)
#   2. Seed config fake-players ONLY if unset, so operator overrides survive a reload
#   3. Run per-system init, one line per system
#   4. Announce the version so it is obvious in the log which build is loaded

# --- objectives ---
# TODO Phase 1: scoreboard objectives add hs.deaths deathCount
# TODO:         scoreboard objectives add hs.tmp dummy

# --- config (seed-if-unset; never clobber an operator override) ---
# TODO Phase 4: hs.cfg.core_rate   = 100   (percent; 100 = 1.0x — DESIGN.md §11)
# TODO Phase 1: hs.cfg.heart_floor = 5     (DESIGN.md §5)

# --- system init ---
# TODO: function heartstead:economy/init
# TODO: function heartstead:lives/init
# TODO: function heartstead:vault/init
# TODO: function heartstead:cores/init
