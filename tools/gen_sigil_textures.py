#!/usr/bin/env python3
"""
Generate the 16x16 Sigil item textures (RGBA PNG). Stdlib only — no Pillow.

    python3 tools/gen_sigil_textures.py src/main/resources/assets/heartstead/textures/item

An optional second argument writes 10x nearest-neighbour previews to a directory for review.

CONVENTIONS.md §6 applies here the way it applies to loot tables: **this script is the artifact and
the PNGs are build output.** Retune a colour or redraw a rune here and re-run it; do not hand-edit
the committed PNGs, because the next run will overwrite them.

DESIGN.md §1: one found Sigil, three children. The art says the same thing — every sigil is the
same plain round stone disc, and the crafted ones are that disc with a rune carved into it and
magical essence bleeding out of the carving.

  sigil                 plain uncarved stone, no rune and no essence
  core_sigil            diamond rune, pale blue essence
  heart_sigil           heart rune, red essence
  vault_sigil           vault-door rune, purple essence
  overworld/nether/end  the same purple — ring, flame and saltire runes
"""
import struct, zlib, os, sys

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

W = H = 16
CX = CY = 7.5

# ---------------------------------------------------------------- png ---

def write_png(path, px, scale=1):
    raw = b''
    for y in range(H * scale):
        raw += b'\x00'
        for x in range(W * scale):
            raw += bytes(px[y // scale][x // scale])
    def chunk(t, d):
        c = t + d
        return struct.pack('>I', len(d)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    open(path, 'wb').write(
        b'\x89PNG\r\n\x1a\n'
        + chunk(b'IHDR', struct.pack('>IIBBBBB', W * scale, H * scale, 8, 6, 0, 0, 0))
        + chunk(b'IDAT', zlib.compress(raw, 9))
        + chunk(b'IEND', b''))

# ------------------------------------------------------------- palette ---

CLEAR = (0, 0, 0, 0)
EDGE  = (28, 25, 24, 255)   # outer rim, near-black
DARK  = (74, 69, 64, 255)   # carved / shaded stone
MIDL  = (141, 135, 126, 255)
BASE  = (124, 118, 110, 255)
LITE  = (158, 152, 142, 255)
SPEC  = (176, 170, 160, 255)  # speckle

def mix(a, b, t):
    return (int(a[0] + (b[0] - a[0]) * t),
            int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t), 255)

# ---------------------------------------------------------------- disc ---
# Hand-set spans so the circle reads cleanly at 16px instead of aliasing.
SPAN = {
    0: None, 1: (6, 9), 2: (4, 11), 3: (3, 12), 4: (2, 13), 5: (2, 13),
    6: (1, 14), 7: (1, 14), 8: (1, 14), 9: (1, 14), 10: (2, 13),
    11: (2, 13), 12: (3, 12), 13: (4, 11), 14: (6, 9), 15: None,
}

def on_disc(x, y):
    s = SPAN[y]
    return s is not None and s[0] <= x <= s[1]

def on_rim(x, y):
    if not on_disc(x, y):
        return False
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        nx, ny = x + dx, y + dy
        if not (0 <= nx < W and 0 <= ny < H) or not on_disc(nx, ny):
            return True
    return False

def speckle(x, y):
    """Stable pseudo-random stone grain — no RNG, so the art is reproducible."""
    h = (x * 73856093) ^ (y * 19349663)
    return (h >> 5) & 7

# --------------------------------------------------------------- runes ---
# 7x7, drawn into the disc at (4,4). '#' is the carved stroke. Kept small on purpose:
# the stone ring around the rune is what makes it read as carved rather than painted.

RUNES = {
    'core_sigil': (
        "...#...",
        "..###..",
        ".#####.",
        "#######",
        ".#####.",
        "..###..",
        "...#...",
    ),
    'heart_sigil': (
        ".......",
        ".##.##.",
        "#######",
        "#######",
        ".#####.",
        "..###..",
        "...#...",
    ),
    'vault_sigil': (
        "#######",
        "#######",
        "##...##",
        "##...##",
        "##...##",
        "#######",
        "#######",
    ),
    'overworld_vault_sigil': (
        "..###..",
        ".#####.",
        "##...##",
        "##...##",
        "##...##",
        ".#####.",
        "..###..",
    ),
    'nether_vault_sigil': (
        "...#...",
        "..###..",
        "..###..",
        ".#####.",
        "#######",
        "#######",
        ".#####.",
    ),
    'end_vault_sigil': (
        "##...##",
        "###.###",
        ".#####.",
        "..###..",
        ".#####.",
        "###.###",
        "##...##",
    ),
}

ESSENCE = {
    'core_sigil':            ((62, 168, 224), (188, 238, 255)),
    'heart_sigil':           ((196, 44, 60),  (255, 150, 162)),
    'vault_sigil':           ((132, 68, 200), (214, 168, 255)),
    'overworld_vault_sigil': ((132, 68, 200), (214, 168, 255)),
    'nether_vault_sigil':    ((132, 68, 200), (214, 168, 255)),
    'end_vault_sigil':       ((132, 68, 200), (214, 168, 255)),
}

def rune_mask(name):
    grid = RUNES[name]
    return {(4 + x, 4 + y) for y, row in enumerate(grid) for x, c in enumerate(row) if c == '#'}

# --------------------------------------------------------------- paint ---

def stone_pixel(x, y):
    """Bare stone, lit from the upper left, with grain."""
    if on_rim(x, y):
        return EDGE
    # Radial falloff from a light point up and left of centre. A linear gradient reads as a
    # diagonal stripe across a 14px disc; a radial one reads as a rounded stone.
    lit = (((x - 5.2) ** 2 + (y - 5.2) ** 2) ** 0.5)
    c = LITE if lit < 2.2 else MIDL if lit < 4.4 else BASE if lit < 6.6 else DARK
    s = speckle(x, y)
    if s == 0:
        c = SPEC if c is not DARK else BASE
    elif s == 7:
        c = BASE if c is not DARK else mix(DARK, EDGE, 0.3)
    return c

def draw(name):
    px = [[CLEAR] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if on_disc(x, y):
                px[y][x] = stone_pixel(x, y)

    if name == 'sigil':
        # Uncarved stone, and nothing else. The three children are visibly this disc with a rune
        # cut into it, so anything drawn here would have to survive being read as a rune.
        return px

    rune = rune_mask(name)
    mid, bright = ESSENCE[name]
    mid = mid + (255,)
    bright = bright + (255,)

    # 1. essence bleeding out of the carving, along four diagonal hairline cracks
    for y in range(H):
        for x in range(W):
            if not on_disc(x, y) or (x, y) in rune or on_rim(x, y):
                continue
            dx, dy = x - CX, y - CY
            if abs(abs(dx) - abs(dy)) < 0.6 and abs(dx) > 2.4:
                px[y][x] = mix(px[y][x], mid, 0.80)

    # 2. glow halo — the stone immediately around the carving picks up the light
    for y in range(H):
        for x in range(W):
            if not on_disc(x, y) or (x, y) in rune:
                continue
            near = any((x + dx, y + dy) in rune for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near:
                px[y][x] = mix(px[y][x], mid, 0.30)

    # 3. the rune itself: mid essence, brightest where the carving is deepest (its centre)
    for (x, y) in rune:
        edge_of_rune = any((x + dx, y + dy) not in rune
                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        px[y][x] = mid if edge_of_rune else bright
    # thin runes have no interior — give them a highlight on the upper-left stroke instead
    if all(any((x + dx, y + dy) not in rune for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
           for (x, y) in rune):
        for (x, y) in rune:
            if (x - CX) + (y - CY) < -1.5:
                px[y][x] = bright

    return px

for name in ['sigil'] + list(RUNES):
    px = draw(name)
    write_png(os.path.join(OUT, name + '.png'), px)
    if PREVIEW:
        write_png(os.path.join(PREVIEW, name + '_x10.png'), px, scale=10)
    print('wrote', name + '.png')
