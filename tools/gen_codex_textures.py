#!/usr/bin/env python3
"""
Generate the 16x16 Codex-family textures (RGBA PNG). Stdlib only — no Pillow.

    python3 tools/gen_codex_textures.py src/main/resources/assets/sigilstead/textures

An optional second argument writes 10x nearest-neighbour previews to a directory for review.

CONVENTIONS.md §6: this script is the artifact and the PNGs are build output, the same rule
tools/gen_sigil_textures.py follows. Retune a colour here and re-run it; do not hand-edit the
committed PNGs.

DESIGN.md §3.3: the Codex is a workbench for two items (Tome, Sealed Tome) plus the block itself.
The palette borrows the Vault family's amethyst-purple "essence" (docs/CONVENTIONS.md's existing
vault_anchor textures) so the two magic systems read as one world rather than two clashing kits.

  tome           plain indigo-bound book, silver clasp, empty — nothing archived yet
  sealed_tome    the same book with one rune sealed onto the cover in bright essence
  codex_top      an open book, pages fanned, an amethyst shard glowing in the gutter
  codex_side     a shelf of book spines with the same amethyst seam running through the middle
"""
import struct, zlib, os, sys

OUT = sys.argv[1]
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else None

W = H = 16

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

def mix(a, b, t):
    return (int(a[0] + (b[0] - a[0]) * t),
            int(a[1] + (b[1] - a[1]) * t),
            int(a[2] + (b[2] - a[2]) * t), 255)

CLEAR = (0, 0, 0, 0)
EDGE  = (20, 18, 30, 255)      # near-black outline, cool-toned to match indigo leather

# book cover — indigo leather
COVER_DARK = (40, 34, 68, 255)
COVER_MID  = (62, 52, 104, 255)
COVER_LITE = (86, 74, 138, 255)
CLASP      = (176, 168, 150, 255)   # dull silver
CLASP_LITE = (214, 208, 196, 255)

# pages, seen edge-on
PAGE_DARK = (196, 178, 132, 255)
PAGE_LITE = (230, 214, 172, 255)

# amethyst essence, shared with the Vault family's palette
ESSENCE_MID    = (132, 68, 200, 255)
ESSENCE_BRIGHT = (214, 168, 255, 255)

# shelf wood (codex_side)
WOOD_DARK = (86, 58, 40, 255)
WOOD_MID  = (112, 78, 54, 255)
WOOD_LITE = (140, 100, 70, 255)

def blank():
    return [[CLEAR] * W for _ in range(H)]

# --------------------------------------------------------------- book ---
# A closed book, front cover facing the viewer: leather cover with a 1px dark
# border, a raised spine-band down the left, a sliver of pages down the right,
# and a clasp at the centre. Shared by tome.png and sealed_tome.png.

def draw_book(sealed):
    px = blank()
    for y in range(1, 15):
        for x in range(1, 15):
            px[y][x] = EDGE
    for y in range(2, 14):
        for x in range(2, 13):
            px[y][x] = COVER_MID
    # spine band, upper-left highlight
    for y in range(2, 14):
        px[y][2] = COVER_LITE
        px[y][3] = COVER_LITE if y % 3 else COVER_DARK
    # lower-right shading
    for y in range(2, 14):
        for x in range(2, 13):
            if x + y > 22:
                px[y][x] = COVER_DARK
    # page edge on the right
    for y in range(3, 13):
        px[y][13] = PAGE_LITE if y % 2 else PAGE_DARK

    # clasp, centred
    for (x, y) in [(6, 7), (7, 7), (8, 7), (6, 8), (7, 8), (8, 8), (6, 9), (7, 9), (8, 9)]:
        px[y][x] = CLASP
    px[7][7] = CLASP_LITE

    if sealed:
        # a sealed rune in essence, replacing the plain clasp — the Empower step's mark
        rune = [
            (7, 5), (7, 6), (6, 7), (8, 7), (7, 8), (7, 9), (6, 6), (8, 6),
        ]
        for (x, y) in rune:
            px[y][x] = ESSENCE_MID
        px[7][7] = ESSENCE_BRIGHT
        # faint glow bleeding into the cover around the rune
        for (x, y) in rune:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 2 <= nx <= 12 and 2 <= ny <= 13 and (nx, ny) not in rune:
                    px[ny][nx] = mix(px[ny][nx], ESSENCE_MID, 0.35)

    return px

# -------------------------------------------------------- codex block ---

def draw_codex_top():
    """An open book from above: two page-fans either side of a glowing gutter."""
    px = blank()
    # Block faces must be opaque edge-to-edge — no transparent margin like an item icon gets,
    # or the texture fails to tile and shows a seam. Fill the true border (0 and 15), not just
    # the inset ring at 1 and 14.
    for y in range(16):
        for x in range(16):
            px[y][x] = EDGE
    for y in range(2, 14):
        for x in range(2, 14):
            px[y][x] = PAGE_MID = mix(PAGE_LITE, PAGE_DARK, 0.5)
    # page-fan strokes
    for y in range(2, 14):
        for x in range(2, 14):
            if x == 7 or x == 8:
                continue
            stripe = (x + y) % 3 == 0
            px[y][x] = PAGE_DARK if stripe else PAGE_LITE
    # the gutter: amethyst essence glowing between the pages
    for y in range(2, 14):
        px[y][7] = ESSENCE_MID
        px[y][8] = ESSENCE_MID
    for y in range(5, 11):
        px[y][7] = ESSENCE_BRIGHT
        px[y][8] = ESSENCE_BRIGHT
    return px

def draw_codex_side():
    """A shelf of book spines, in the same indigo/plum family, with the amethyst seam from the top texture running down through the middle."""
    px = blank()
    # Same edge-to-edge requirement as draw_codex_top — no transparent margin on a block face.
    for y in range(16):
        for x in range(16):
            px[y][x] = EDGE
    spine_colors = [COVER_DARK, COVER_MID, mix(COVER_MID, (90, 40, 90, 255), 0.4), COVER_LITE]
    x = 2
    i = 0
    while x < 14:
        width = 2 if i % 2 == 0 else 3
        color = spine_colors[i % len(spine_colors)]
        for xx in range(x, min(x + width, 14)):
            for y in range(2, 14):
                px[y][xx] = color
            px[2][xx] = mix(color, CLASP_LITE, 0.4)
        x += width
        i += 1
    # amethyst seam down the middle, echoing vault_anchor_side's central inlay
    for y in range(3, 13):
        px[y][7] = ESSENCE_MID
        px[y][8] = ESSENCE_MID
    for y in range(6, 10):
        px[y][7] = ESSENCE_BRIGHT
        px[y][8] = ESSENCE_BRIGHT
    return px

def main():
    os.makedirs(os.path.join(OUT, 'item'), exist_ok=True)
    os.makedirs(os.path.join(OUT, 'block'), exist_ok=True)

    items = {'tome': draw_book(False), 'sealed_tome': draw_book(True)}
    blocks = {'codex_top': draw_codex_top(), 'codex_side': draw_codex_side()}

    for name, px in items.items():
        path = os.path.join(OUT, 'item', name + '.png')
        write_png(path, px)
        if PREVIEW:
            write_png(os.path.join(PREVIEW, name + '_x10.png'), px, scale=10)
        print('wrote', path)

    for name, px in blocks.items():
        path = os.path.join(OUT, 'block', name + '.png')
        write_png(path, px)
        if PREVIEW:
            write_png(os.path.join(PREVIEW, name + '_x10.png'), px, scale=10)
        print('wrote', path)

if __name__ == '__main__':
    main()
