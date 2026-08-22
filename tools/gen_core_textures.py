#!/usr/bin/env python3
"""
Generate the 16x16 DESIGN.md §4 core textures (RGBA PNG). Stdlib only — no Pillow.

    python3 tools/gen_core_textures.py src/main/resources/assets/sigilstead/textures

An optional second argument writes 10x nearest-neighbour previews to a directory for review.

CONVENTIONS.md §6: this script is the artifact and the PNGs are build output, the same rule
tools/gen_sigil_textures.py and tools/gen_codex_textures.py follow. Retune a colour here and re-run
it; do not hand-edit the committed PNGs.

DESIGN.md §4.1 says a core *is* a Core Sigil that learned something, so the art says the same: every
core item is the sigil family's stone disc, carried over from gen_sigil_textures.py, with a family
rune carved into it. The two states differ only in how alive the carving looks —

  primed_*_core   the rune carved but barely lit: primed, not yet attuned
  *_core          the same rune burning, with essence bleeding along the cracks (§4.1 step 2)

and the four housings (§4.2) are ordinary blocks in their family's material, deliberately plain
because §4.2 prices them as cheap — the Core is the cost.
"""
import struct, zlib, os, sys

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

W = H = 16
CX = CY = 7.5

ITEM_DIR = os.path.join(OUT, 'item')
BLOCK_DIR = os.path.join(OUT, 'block')


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


def mix(a, b, t):
    return (int(a[0] + (b[0] - a[0]) * t),
            int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t), 255)


CLEAR = (0, 0, 0, 0)

# ---- sigil-family stone, identical to gen_sigil_textures.py so the sets read as one kit ----
EDGE = (28, 25, 24, 255)
DARK = (74, 69, 64, 255)
MIDL = (141, 135, 126, 255)
BASE = (124, 118, 110, 255)
LITE = (158, 152, 142, 255)
SPEC = (176, 170, 160, 255)

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
    """Stable pseudo-random grain — no RNG, so the art is reproducible."""
    h = (x * 73856093) ^ (y * 19349663)
    return (h >> 5) & 7


def stone_pixel(x, y):
    if on_rim(x, y):
        return EDGE
    lit = (((x - 5.2) ** 2 + (y - 5.2) ** 2) ** 0.5)
    c = LITE if lit < 2.2 else MIDL if lit < 4.4 else BASE if lit < 6.6 else DARK
    s = speckle(x, y)
    if s == 0:
        c = SPEC if c is not DARK else BASE
    elif s == 7:
        c = BASE if c is not DARK else mix(DARK, EDGE, 0.3)
    return c


# ---- §4.1's four families: one rune and one essence colour each ----

RUNES = {
    # A skull, for "kill a hostile mob".
    'soul': (
        ".#####.",
        "#######",
        "#.###.#",
        "#.###.#",
        "#######",
        ".#####.",
        ".#.#.#.",
    ),
    # A sprout breaking soil, for "harvest a mature crop".
    'verdant': (
        "...#...",
        ".#.#.#.",
        ".#.#.#.",
        "..###..",
        "...#...",
        "...#...",
        ".#####.",
    ),
    # A cloven hoofprint, for "breed two animals".
    'pastoral': (
        ".##.##.",
        "###.###",
        "###.###",
        ".##.##.",
        ".......",
        "..###..",
        "..###..",
    ),
    # A pick striking, for "mine a stone or earth block".
    'lithic': (
        "#.....#",
        ".#...#.",
        "..###..",
        "...#...",
        "...#...",
        "...#...",
        "...#...",
    ),
}

# Family essence. All four sit near the Core Sigil's pale blue (62,168,224) in value so the set
# stays one family, and separate in hue so a glance tells them apart in a full inventory.
ESSENCE = {
    'soul': ((72, 200, 186), (196, 255, 246)),      # soul-fire teal
    'verdant': ((92, 186, 68), (206, 255, 176)),    # leaf green
    'pastoral': ((214, 152, 78), (255, 224, 170)),  # warm hay
    'lithic': ((150, 112, 214), (222, 196, 255)),   # amethyst, echoing the Quarry Node
}


def rune_mask(family):
    grid = RUNES[family]
    return {(4 + x, 4 + y) for y, row in enumerate(grid) for x, c in enumerate(row) if c == '#'}


def draw_core(family, lit):
    """The sigil disc with a family rune. `lit` is the finished core; unlit is the Primed Core."""
    px = [[CLEAR] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if on_disc(x, y):
                px[y][x] = stone_pixel(x, y)

    rune = rune_mask(family)
    mid, bright = ESSENCE[family]
    mid = mid + (255,)
    bright = bright + (255,)

    if not lit:
        # Primed: the carving is cut but cold. Just enough colour to say which family it is.
        for (x, y) in rune:
            px[y][x] = mix(DARK, mid, 0.35)
        return px

    # Attuned: essence bleeding along four diagonal hairline cracks, a halo, then the rune itself.
    for y in range(H):
        for x in range(W):
            if not on_disc(x, y) or (x, y) in rune or on_rim(x, y):
                continue
            dx, dy = x - CX, y - CY
            if abs(abs(dx) - abs(dy)) < 0.6 and abs(dx) > 2.4:
                px[y][x] = mix(px[y][x], mid, 0.80)

    for y in range(H):
        for x in range(W):
            if not on_disc(x, y) or (x, y) in rune:
                continue
            if any((x + dx, y + dy) in rune for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[y][x] = mix(px[y][x], mid, 0.30)

    for (x, y) in rune:
        edge_of_rune = any((x + dx, y + dy) not in rune
                           for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        px[y][x] = mid if edge_of_rune else bright
    if all(any((x + dx, y + dy) not in rune for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
           for (x, y) in rune):
        for (x, y) in rune:
            if (x - CX) + (y - CY) < -1.5:
                px[y][x] = bright

    return px


# ---------------------------------------------------------------- §4.2 housings ---
# Plain material blocks. §4.2: "Housings are deliberately cheap — the Core is the cost."

IRON_DARK = (74, 74, 80, 255)
IRON_MID = (124, 124, 132, 255)
IRON_LITE = (166, 166, 176, 255)
VOID = (24, 22, 28, 255)

SOIL_DARK = (58, 42, 30, 255)
SOIL_MID = (86, 62, 44, 255)
SOIL_LITE = (110, 82, 58, 255)
LEAF = (92, 152, 58, 255)

WOOD_DARK = (94, 72, 44, 255)
WOOD_MID = (132, 102, 62, 255)
HAY_DARK = (150, 122, 40, 255)
HAY_MID = (196, 166, 62, 255)
HAY_LITE = (222, 198, 96, 255)

SLATE_DARK = (38, 38, 42, 255)
SLATE_MID = (62, 62, 68, 255)
SLATE_LITE = (86, 86, 94, 255)
AMETHYST = (150, 112, 214, 255)
AMETHYST_LITE = (206, 178, 255, 255)


def grain(x, y, a, b, c):
    s = speckle(x, y)
    return a if s == 0 else c if s == 7 else b


def soul_cage_side():
    px = [[VOID] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            # A lattice of bars, thicker at the frame.
            if x in (0, 15) or y in (0, 15):
                px[y][x] = IRON_LITE
            elif x % 5 == 2 or y % 5 == 2:
                px[y][x] = IRON_MID if (x + y) % 3 else IRON_DARK
    return px


def soul_cage_top():
    px = soul_cage_side()
    for y in range(6, 10):
        for x in range(6, 10):
            px[y][x] = mix(VOID, ESSENCE['soul'][0] + (255,), 0.5)
    return px


def verdant_planter_side():
    px = [[SOIL_MID] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            if y < 4:
                px[y][x] = grain(x, y, WOOD_MID, WOOD_DARK, WOOD_MID)
            else:
                px[y][x] = grain(x, y, SOIL_LITE, SOIL_MID, SOIL_DARK)
    for x in range(W):
        px[3][x] = WOOD_DARK
    return px


def verdant_planter_top():
    px = [[SOIL_MID] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            px[y][x] = grain(x, y, SOIL_LITE, SOIL_MID, SOIL_DARK)
    # Two furrows and a sprout in the middle.
    for x in range(2, 14):
        px[5][x] = SOIL_DARK
        px[10][x] = SOIL_DARK
    for y in range(6, 10):
        px[y][7] = LEAF
    px[6][6] = LEAF
    px[6][8] = LEAF
    return px


def paddock_side():
    px = [[HAY_MID] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            px[y][x] = grain(x, y, HAY_LITE, HAY_MID, HAY_DARK)
    # Two fence rails and two posts across the bale.
    for x in range(W):
        px[4][x] = WOOD_MID
        px[5][x] = WOOD_DARK
        px[11][x] = WOOD_MID
        px[12][x] = WOOD_DARK
    for y in range(H):
        for x in (2, 3, 12, 13):
            px[y][x] = WOOD_MID if x in (2, 12) else WOOD_DARK
    return px


def paddock_top():
    px = [[HAY_MID] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            px[y][x] = grain(x, y, HAY_LITE, HAY_MID, HAY_DARK)
    for x in range(W):
        px[0][x] = WOOD_DARK
        px[15][x] = WOOD_DARK
    for y in range(H):
        px[y][0] = WOOD_DARK
        px[y][15] = WOOD_DARK
    return px


def deepslate_bricks():
    px = [[SLATE_MID] * W for _ in range(H)]
    for y in range(H):
        for x in range(W):
            px[y][x] = grain(x, y, SLATE_LITE, SLATE_MID, SLATE_DARK)
    for x in range(W):
        px[0][x] = SLATE_DARK
        px[8][x] = SLATE_DARK
    for y in range(1, 8):
        px[y][0] = SLATE_DARK
        px[y][8] = SLATE_DARK
    for y in range(9, H):
        px[y][4] = SLATE_DARK
        px[y][12] = SLATE_DARK
    return px


def quarry_node_side():
    return deepslate_bricks()


def quarry_node_top():
    px = deepslate_bricks()
    # An amethyst cluster set into the face — the recipe's Amethyst Shard, made visible.
    for y in range(5, 11):
        for x in range(5, 11):
            if abs(x - 7.5) + abs(y - 7.5) <= 2.5:
                px[y][x] = AMETHYST
    px[7][7] = AMETHYST_LITE
    px[7][8] = AMETHYST_LITE
    px[8][7] = AMETHYST_LITE
    return px


BLOCKS = {
    'soul_cage_side': soul_cage_side,
    'soul_cage_top': soul_cage_top,
    'verdant_planter_side': verdant_planter_side,
    'verdant_planter_top': verdant_planter_top,
    'paddock_side': paddock_side,
    'paddock_top': paddock_top,
    'quarry_node_side': quarry_node_side,
    'quarry_node_top': quarry_node_top,
}


def emit(directory, name, px):
    os.makedirs(directory, exist_ok=True)
    write_png(os.path.join(directory, name + '.png'), px)
    if PREVIEW:
        os.makedirs(PREVIEW, exist_ok=True)
        write_png(os.path.join(PREVIEW, name + '.png'), px, scale=10)
    print(name)


for family in RUNES:
    emit(ITEM_DIR, 'primed_%s_core' % family, draw_core(family, lit=False))
    emit(ITEM_DIR, '%s_core' % family, draw_core(family, lit=True))

for name, fn in BLOCKS.items():
    emit(BLOCK_DIR, name, fn())
