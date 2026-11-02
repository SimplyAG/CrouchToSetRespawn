"""Draws the mod icon: a red Minecraft bed under a night sky with a respawn pin over the pillow.

Pixel art on a 32x32 grid, scaled 8x to 256x256 with no smoothing. Run with: py -3.12 art/make_icon.py
"""
from pathlib import Path

from PIL import Image

GRID = 32
SCALE = 8
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/crouchtosetrespawn/icon.png"

img = Image.new("RGBA", (GRID, GRID))
px = img.load()


def rect(x0, y0, x1, y1, color):
    """Fills the inclusive box x0..x1, y0..y1."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[x, y] = color


# Night sky, darker at the top.
for y in range(GRID):
    t = y / (GRID - 1)
    px_row = (int(18 + 22 * t), int(20 + 30 * t), int(54 + 50 * t), 255)
    for x in range(GRID):
        px[x, y] = px_row

for x, y in [(3, 3), (9, 6), (14, 2), (27, 4), (22, 8), (29, 12)]:
    px[x, y] = (230, 232, 255, 255)

# Moon with a few craters.
rect(24, 2, 28, 6, (236, 236, 214, 255))
px[25, 3] = px[27, 5] = (200, 200, 180, 255)

# Grass and dirt ground.
rect(0, 25, 31, 26, (94, 157, 52, 255))
rect(0, 27, 31, 31, (134, 96, 67, 255))
for x in range(0, 32, 3):
    px[x, 26] = (76, 130, 40, 255)
    px[(x + 1) % 32, 29] = (110, 78, 54, 255)

# Bed, side view: oak legs, oak frame, red blanket, white pillow on the left (head) end.
OAK = (162, 130, 78, 255)
OAK_DARK = (120, 94, 56, 255)
RED = (176, 46, 38, 255)
RED_LIGHT = (208, 72, 60, 255)
RED_DARK = (130, 30, 26, 255)
PILLOW = (232, 232, 232, 255)
PILLOW_SHADE = (196, 196, 204, 255)

rect(4, 23, 5, 24, OAK_DARK)
rect(26, 23, 27, 24, OAK_DARK)
rect(4, 21, 27, 22, OAK)
rect(4, 22, 27, 22, OAK_DARK)
rect(4, 18, 27, 20, RED)
rect(11, 17, 27, 17, RED_LIGHT)
rect(4, 20, 27, 20, RED_DARK)
rect(4, 16, 10, 18, PILLOW)
rect(4, 18, 10, 18, PILLOW_SHADE)

# Respawn pin over the pillow: gold teardrop with a dark centre, pointing down at the bed.
GOLD = (252, 200, 46, 255)
GOLD_DARK = (206, 146, 22, 255)
rect(5, 5, 9, 9, GOLD)
rect(6, 4, 8, 4, GOLD)
rect(6, 10, 8, 10, GOLD)
rect(7, 11, 7, 13, GOLD_DARK)
rect(6, 6, 8, 8, (64, 40, 10, 255))
px[7, 7] = (255, 240, 170, 255)
px[9, 9] = px[8, 10] = GOLD_DARK

OUT.parent.mkdir(parents=True, exist_ok=True)
img.resize((GRID * SCALE, GRID * SCALE), Image.NEAREST).save(OUT)
print(f"wrote {OUT}")
