"""Register and clean native Crime District sprites from the supplied art sheet.

Usage: python3 tools/actor_art.py reference.png
Only native RGBA/indexed sprite sheets are shipped. Requires Pillow at authoring time.
"""
import argparse
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "res"
WALK_CELL = (24, 36)
# Sample windows are registered to the torso and common ground line, not to each
# frame's changing bounding box. Coordinates are for the 1536×1024 art sheet.
HERO_ROWS = (
    (286, 483, (102, 242, 384, 527)),  # back / UP
    (34, 227, (102, 243, 385, 529)),   # front / DOWN
    (536, 724, (92, 234, 376, 524)),   # left
    (780, 970, (94, 236, 381, 523)),   # right
)
# Input rows are sedan, taxi, SUV, van. Columns are front, back, left, right.
VEHICLE_BOUNDS = (
    ((653, 30, 769, 250), (792, 34, 911, 240), (933, 89, 1197, 193), (1241, 87, 1511, 193)),
    ((654, 274, 768, 489), (793, 278, 911, 481), (933, 321, 1202, 440), (1241, 320, 1510, 437)),
    ((653, 511, 771, 733), (789, 520, 923, 718), (947, 557, 1204, 687), (1232, 557, 1497, 687)),
    ((654, 755, 772, 988), (791, 758, 926, 965), (947, 804, 1204, 935), (1240, 802, 1505, 934)),
)
HUMAN = (
    "17151b 211c22 2d2529 3b3032 4b3b39 65504a "
    "22232a 2d2d36 3b3840 4a444b 61565a "
    "222d37 2c3d4d 394d62 4e637a 64778b "
    "594039 785348 9c634b b87957 cf9068 e2a97e f1bc90 "
    "746354 8e7a65 aa9277 c4ad91 ddcaab"
)
CARS = (
    "14141b 202129 2b2c37 393844 49444d 61565c "
    "202933 2a3442 374452 465668 596a7d 718395 93a0ae aeb4b9 "
    "594c49 70645e 897a71 9f9087 b6a99c ccbdb0 dfcbbb "
    "643e27 885128 a96b2c c18737 dba344 edbc64 f6d792 f8e3b1 "
    "783135 a6443c cf5a43 eb8161"
)
INK = "#201c23"


def colors(values):
    return [tuple(bytes.fromhex(value)) for value in values.split()]


def foreground(pixel):
    r, g, b = pixel
    # Matte checkerboard is above 225, original grey cast shadow above 145.
    # Warm highlights remain distinct from the neutral matte at equal luminance.
    return min(pixel) < 145 or (min(pixel) < 203 and max(pixel)-min(pixel) > 18)


def match(pixel, palette):
    return min(palette, key=lambda color: (pixel[0]-color[0])**2
                                         + 2*(pixel[1]-color[1])**2
                                         + (pixel[2]-color[2])**2)


def register(source, bounds, size, palette):
    """Resolve each target pixel from a registered aperture on the source.

    Use a foreground coverage threshold followed by a single opaque palette
    color. No filtered resizes, translucent matte fringes or interpolated hues.
    """
    x0, y0, x1, y1 = bounds
    w, h = size
    frame = Image.new("RGBA", size)
    for y in range(h):
        for x in range(w):
            points = [source.getpixel((int(x0+(x+(dx+.5)/5)*(x1-x0)/w),
                                       int(y0+(y+(dy+.5)/5)*(y1-y0)/h)))
                      for dy in range(5) for dx in range(5)]
            solid = [pixel for pixel in points if foreground(pixel)]
            if len(solid) >= 11:
                # Independent medians reject isolated highlight/shadow fragments.
                pixel = tuple(sorted(p[channel] for p in solid)[len(solid)//2] for channel in range(3))
                frame.putpixel((x, y), (*match(pixel, palette), 255))
    return frame


def shadow(size, bounds):
    im = Image.new("RGBA", size)
    ImageDraw.Draw(im).ellipse(bounds, fill=(30, 24, 34, 94))
    return im


def save_indexed(image, path):
    pixels = image.get_flattened_data()
    palette = [(0, 0, 0, 0), *sorted(set(pixels) - {(0, 0, 0, 0)})]
    if len(palette) > 256:
        raise ValueError("Sprite atlas exceeds the indexed PNG palette")
    index = {color: i for i, color in enumerate(palette)}
    native = Image.frombytes("P", image.size, bytes(index[pixel] for pixel in pixels))
    native.putpalette([channel for color in palette for channel in color[:3]])
    native.save(path, transparency=bytes(color[3] for color in palette), optimize=True)


def hero_frames(source):
    palette = colors(HUMAN)
    result = []
    for top, bottom, centers in HERO_ROWS:
        row = []
        for x in centers:
            # 124 source pixels span 20 native columns. On the reference,
            # cadence and fine features are about 5–6 source pixels apart.
            row.append(register(source, (x-62, top-4, x+62, bottom), (20, 33), palette))
        result.append(row)
    return result


def finish_vehicle(frame, model, direction):
    draw = ImageDraw.Draw(frame)
    if model in (0,2):
        # Reflections on the native door trim and roof edge; retain window contrast.
        if direction < 2:
            x = frame.width//2 - (5 if model == 0 else 6)
            draw.line([(x,17),(x,22),(x+1,23)],fill="#657486" if model == 0 else "#615861")
        else:
            y = 19
            x = 24 if direction == 2 else 13
            draw.line([(x,y),(x+6,y)],fill="#66788b" if model == 0 else "#746570")
    if model == 3 and direction < 2:
        # Hand-place the fluted panel and remove small matte holes from the
        # light paint. Glass, seams, lamps and trim remain from the artwork.
        draw.polygon([(17,6),(30,6),(31,8),(31,20),(29,22),(17,22),(16,20),(16,8)],
                     fill="#b9aa9d")
        draw.line([(17,7),(29,7),(30,8)], fill="#ddcabb")
        for x in (19,22,25,28):
            draw.line([(x,9),(x,19)], fill="#968881")
            draw.line([(x+1,10),(x+1,18)], fill="#ccbbad")
        draw.line([(17,21),(29,21)], fill="#a39288")
        if direction == 1:
            draw.polygon([(19,35),(29,35),(29,38),(27,39),(20,39)], fill="#b9aa9d")
            draw.line([(19,36),(28,36)], fill="#ccbbad")
    elif model == 3:
        x = 25 if direction == 2 else 7
        draw.line([(x,6),(x+13,6)], fill="#ddcabb")
        draw.line([(x+1,8),(x+13,8)], fill="#9f9087")
    elif model == 1:
        # A roof plate occupies the upper edge in profile and the roof in
        # the two axial views. The lettering always faces the screen.
        if direction == 0:
            draw.polygon([(17,26),(30,26),(29,30),(18,30)], fill="#29343f")
            draw.line([(18,27),(29,27)], fill="#465668")
        x, y = ((18,16),(18,18),(17,6),(19,6))[direction]
        draw.rectangle((x,y,x+11,y+5), fill="#35282b")
        draw.rectangle((x+1,y+1,x+10,y+4), fill="#e6bc6b")
        glyphs = ((7,2,2),(2,7,5),(5,2,5),(1,1,1))
        for left, rows in zip((0,3,6,9), glyphs):
            for dy, row in enumerate(rows):
                for dx in range(3 if left != 9 else 1):
                    if row & (1 << (2-dx if left != 9 else 0)):
                        draw.point((x+1+left+dx,y+2+dy), fill="#3f2c29")


def vehicles(source):
    sedan = Image.new("RGBA", (176, 44))
    traffic = Image.new("RGBA", (192, 144))
    palette = colors(CARS)
    for model, family in enumerate(VEHICLE_BOUNDS):
        cell = 44 if model == 0 else 48
        for direction, heading in enumerate((1, 0, 2, 3)):
            bounds = family[heading]
            if direction < 2:
                height = (39, 39, 42, 45)[model]
                width = (23, 23, 26, 27)[model]
                ground = (cell-height)//2
            else:
                width = (40, 41, 44, 46)[model]
                # Give the side views enough height to read at 240×320.
                # Maintain wheel contact and the established road footprint.
                height = (27, 28, 30, 31)[model]
                ground = cell//2 + (10 if model >= 2 else 9) - height
            left = (cell-width)//2
            frame = shadow((cell, cell), (left, ground+height-4, left+width-1, min(cell-1, ground+height+1)))
            sprite = register(source, bounds, (width, height), palette)
            frame.alpha_composite(sprite, (left, ground))
            finish_vehicle(frame, model, direction)
            if model == 0:
                sedan.paste(frame, (direction*cell, 0))
            else:
                traffic.paste(frame, (direction*cell, (model-1)*cell))
    save_indexed(sedan, ROOT / "sedan.png")
    save_indexed(traffic, ROOT / "traffic.png")


def polish_hero(rows):
    # Sparse stitched folds follow the existing native garment clusters.
    folds = (
        ((5,13),(6,14),(14,12),(14,16),(5,18),(13,19)),
        ((5,13),(5,15),(14,13),(13,16),(5,18),(13,19)),
        ((12,12),(13,13),(14,15),(12,17),(13,19)),
        ((7,12),(6,13),(6,15),(7,17),(6,18)),
    )
    result = []
    for direction,row in enumerate(rows):
        polished = []
        for pose in row:
            frame = pose.copy()
            for x,y in folds[direction]:
                r,g,b,a = frame.getpixel((x,y))
                if a == 255 and 36 <= r <= 77 and abs(r-b) < 22:
                    frame.putpixel((x,y),(r+12,g+11,b+13,255))
            polished.append(frame)
        result.append(polished)
    return result


def pack_people(rows):
    atlas = Image.new("RGBA", (96, 144))
    for direction, row in enumerate(rows):
        for phase, sprite in enumerate(row):
            cell = shadow(WALK_CELL, (5, 29, 19, 34))
            cell.alpha_composite(sprite, (2, 0))
            atlas.paste(cell, (phase * WALK_CELL[0], direction * WALK_CELL[1]))
    return atlas


# Outerwear, trousers, and skin ramps from dark fold to top-facing highlight.
WARDROBE = (
    ("3c2c2c 593d35 795542 987258", "25252b 363039 4a4147 65545a", "9a5a49 c58663 e9ac7b"),
    ("46414a 736970 9c9393 bdb0a7", "232a38 333b4e 48516a 616981", "955e4d c68b67 e9b284"),
    ("452533 713340 93434c b66562", "272832 383847 515260 6b6879", "965446 c47d61 e5a477"),
    ("252734 343646 494c60 676877", "24252c 36343f 4b4652 66606b", "805044 ac6d54 d29770"),
    ("324341 4f6257 768675 a9ad8d", "392d32 5d423d 79564b 9b745e", "774337 a86348 ce8a60"),
    ("5e5357 8a7974 b6a49a d6c4af", "282632 383647 4e4b62 6d677d", "905549 bc8065 e5a47f"),
)
HAIR = (
    "292128 4a3638", "251e23 59403b", "2a2029 594049",
    "35262b 6b4b40", "28212b 543b37", "3f292d 875a49",
)


def shade(pixel, ramp):
    level = (pixel[0]*2 + pixel[1]*4 + pixel[2])//7
    if level < 40:
        return ramp[0]
    if level < 57:
        return ramp[1]
    if level < 74:
        return ramp[2]
    return ramp[-1]


def paint_clothing(sprite, model):
    out = sprite.copy()
    jacket, pants, skin = (colors(ramp) for ramp in WARDROBE[model-1])
    for y in range(10, 33):
        for x in range(sprite.width):
            r,g,b,a = sprite.getpixel((x,y))
            if a == 0 or max(r,g,b) < 34:
                continue
            if r > b + 32 and r > 130 and (y >= 18 or (y <= 12 and 7 <= x <= 13)):
                color = skin[1 if r < 210 else 2]
            elif y >= 21 and b > r + 9:
                color = shade((r,g,b), pants)
                if model == 3 and y >= 25:
                    color = skin[min(2, int(r > 55) + int(r > 76))]
            elif r > b + 18 and r >= 100:
                if model == 2 or model == 5:
                    color = jacket[2 if r < 172 else 3]
                elif model == 3:
                    color = jacket[2 if r < 185 else 3]
                else:
                    color = (170, 151, 136) if r < 170 else (217, 200, 175)
            elif y <= 21:
                color = shade((r,g,b), jacket)
            elif y >= 29 and model in (2, 3, 5, 6) and max(r,g,b) >= 40:
                color = colors("776966 a89889 c9b7a1")[min(2, int(r>46)+int(r>65))]
            else:
                continue
            out.putpixel((x,y), (*color,255))
    return out


def paint_head(out, model, direction):
    d = ImageDraw.Draw(out)
    d.rectangle((0,0,19,9), fill=(0,0,0,0))
    hair, gleam = colors(HAIR[model-1])
    skin_low, skin, lit = colors(WARDROBE[model-1][2])
    profile = direction >= 2

    def points(coords):
        return [(19-x if direction == 3 else x, y) for x,y in coords]

    def patch(coords, tint):
        d.polygon(points(coords), fill=tint)

    def mark(coords, tint):
        d.line(points(coords), fill=tint)

    if profile:
        patch([(6,4),(6,2),(9,1),(13,1),(15,3),(15,7),(13,10),(11,11),(8,10),(7,8),(5,7)],INK)
        patch([(7,5),(12,4),(13,7),(11,10),(9,10),(8,9),(7,8),(6,7)],skin)
        mark([(7,7),(8,8),(9,8)],lit)
        mark([(8,6),(8,7)],INK)
        mark([(9,9),(11,9)],skin_low)
        patch([(6,4),(7,2),(11,2),(13,3),(14,4),(14,7),(13,9),(12,8),(11,6),(9,5),(7,5)],hair)
        mark([(8,3),(10,2),(12,3)],gleam)
    else:
        d.polygon([(5,5),(6,2),(8,1),(12,1),(14,2),(15,5),(15,8),(13,11),(8,11),(6,9)],fill=INK)
        if direction == 1:
            d.polygon([(7,5),(13,5),(14,7),(13,9),(12,10),(9,10),(7,9),(6,7)],fill=skin)
            d.line([(7,8),(10,9),(12,9)],fill=lit)
            d.point((8,7),fill=INK)
            d.point((12,7),fill=INK)
            d.point((10,9),fill=skin_low)
            d.polygon([(6,4),(7,2),(11,2),(13,3),(14,5),(13,6),(11,5),(9,6),(7,5)],fill=hair)
            d.line([(7,3),(9,2),(11,2)],fill=gleam)
        else:
            d.polygon([(6,4),(8,2),(12,2),(14,4),(14,7),(12,9),(9,9),(7,8)],fill=hair)
            d.line([(8,3),(10,2),(13,4)],fill=gleam)
            d.line([(9,10),(12,10)],fill=skin_low)

    if model == 1:
        if profile:
            patch([(5,5),(6,2),(8,1),(13,1),(15,3),(15,5),(14,6),(5,6),(4,5)],INK)
            patch([(6,4),(7,2),(13,2),(14,4),(13,5),(6,5)],"#555360")
            mark([(8,2),(12,2)],"#85808a")
            mark([(4,5),(8,5),(10,4),(14,4)],"#726d78")
        else:
            d.polygon([(5,5),(6,2),(9,1),(13,1),(15,3),(16,5),(14,6),(6,6)],fill=INK)
            d.polygon([(6,4),(8,2),(12,2),(14,3),(14,5),(7,5)],fill="#575464")
            d.line([(8,2),(12,2)],fill="#86818a")
            d.line([(6,5),(14,5)],fill="#44414c")
            if direction == 0:
                d.line([(8,6),(12,6)],fill="#877779")
    elif model == 2:
        if profile:
            patch([(7,2),(10,1),(13,1),(15,3),(14,5),(11,4),(10,3),(7,4)],hair)
            mark([(8,2),(11,2),(13,3)],gleam)
            patch([(13,10),(15,10),(16,12),(15,14),(12,13),(11,11)],"#625a63")
            mark([(12,11),(15,12),(15,13)],"#b6a39c")
        else:
            d.polygon([(6,4),(6,2),(8,0),(11,1),(14,1),(15,3),(14,5),(12,4),(9,4),(7,5)],fill=hair)
            d.line([(8,1),(11,2),(13,2)],fill=gleam)
            if direction == 0:
                d.polygon([(6,10),(8,11),(13,11),(15,10),(15,13),(12,15),(9,15),(6,13)],fill="#5d555d")
                d.line([(7,12),(9,13),(12,13),(14,12)],fill="#b9aaa0")
    elif model == 3:
        if profile:
            patch([(7,3),(10,1),(13,2),(15,4),(15,9),(17,12),(17,15),(15,17),(13,15),(13,12),(12,10),(13,7),(11,5)],INK)
            patch([(9,3),(11,2),(13,3),(14,6),(14,9),(16,13),(15,15),(14,13),(14,10),(13,8),(13,5)],hair)
            mark([(11,3),(13,5),(13,8),(15,12),(15,14)],gleam)
        else:
            for x in (5,14):
                d.polygon([(x,5),(x+1,5),(x+2,13),(x+1,16),(x-1,15),(x-1,9)],fill=INK)
                d.line([(x,7),(x,11),(x+1,14)],fill=gleam)
            if direction == 0:
                d.polygon([(6,4),(8,2),(12,2),(14,4),(15,11),(13,14),(9,14),(6,11)],fill=hair)
                d.line([(8,4),(7,8),(8,11),(10,12)],fill=gleam)
    elif model == 4:
        if profile:
            patch([(6,4),(7,2),(11,1),(14,2),(15,4),(13,5),(11,4)],hair)
            mark([(7,3),(11,2),(13,2)],gleam)
        else:
            d.polygon([(6,4),(7,2),(12,1),(14,2),(15,4),(14,5),(11,4),(8,4),(7,5)],fill=hair)
            d.line([(8,3),(11,2),(13,2)],fill=gleam)
    elif model == 5:
        patch([(5,5),(6,2),(8,1),(12,0),(14,2),(15,5),(14,6),(6,6)],INK)
        patch([(6,4),(8,2),(12,1),(14,3),(14,5),(6,5)],"#555f59")
        mark([(8,2),(9,2),(10,1),(12,1)],"#8f947e")
        mark([(6,5),(14,5)],"#788775")
        mark([(8,4),(13,4)],"#3d4a46")
    elif model == 6:
        # The ponytail sits on her right. It appears behind the near ear
        # from the right and behind the far shoulder from the left.
        if profile:
            patch([(13,4),(15,4),(17,7),(16,9),(17,12),(15,14),(14,12),(14,9)],INK)
            mark([(14,5),(16,7),(15,10),(16,12)],gleam if direction == 3 else hair)
        else:
            x = 15 if direction == 0 else 5
            offset = 1 if direction == 0 else -1
            d.polygon([(x,5),(x+offset*2,6),(x+offset*2,9),(x+offset*3,12),
                       (x,13),(x-offset,10)],fill=INK)
            d.line([(x+offset,7),(x+offset,9),(x+offset*2,11)],fill=gleam)
            d.point((x,7),fill="#a98874")


def finish_clothing(out, model, direction, frame):
    d = ImageDraw.Draw(out)
    if model in (1, 4) and direction == 1:
        tone = "#b18c75" if model == 1 else "#757083"
        d.line([(7,12),(9,14),(8,16)],fill=tone)
        d.line([(13,12),(11,14),(12,16)],fill=tone)
        if model == 4:
            d.line([(10,13),(10,18)],fill="#894b53")
            d.point((10,12),fill="#ac7369")
    elif model == 2 and direction == 1:
        d.line([(8,12),(8,16)],fill="#c7b5a9")
        d.line([(12,12),(12,15)],fill="#bbaa9e")
        d.line([(8,19),(12,19)],fill="#67616a")
    elif model == 3:
        xs = (6,13) if direction < 2 else ((8,13) if direction == 2 else (6,12))
        d.polygon([(xs[0]+1,20),(xs[1]-1,20),(xs[1]+1,24),(xs[0],24)],fill="#30303c")
        d.line([(xs[0]+1,24),(xs[1],24)],fill="#565367")
    elif model == 5:
        x = 7 if direction != 2 else 9
        if direction != 0:
            d.line([(x,12),(x+2,15),(x+4,17)],fill="#b8b69b")
            d.line([(x,13),(x+2,16),(x+4,18)],fill="#3f4a47")
        else:
            d.line([(7,13),(12,13),(14,12)],fill="#b8b69b")
    elif model == 6:
        x = 14 if direction in (0,2) else 4
        y = 16 + (frame == 2)
        d.line([(x,y),(x,y+4)],fill="#533f43")
        d.polygon([(x-1,y+4),(x+2,y+4),(x+3,y+7),(x+2,y+9),(x-1,y+8)],fill=INK)
        d.line([(x,y+5),(x+2,y+5),(x+2,y+7),(x,y+7)],fill="#986d56")
        d.point((x+1,y+6),fill="#d0a37d")


def pedestrian_rows(hero, model):
    result = []
    for direction, row in enumerate(hero):
        poses = []
        for frame, sprite in enumerate(row):
            out = paint_clothing(sprite, model)
            paint_head(out, model, direction)
            finish_clothing(out, model, direction, frame)
            poses.append(out)
        result.append(poses)
    return result


if __name__ == "__main__":
    arguments = argparse.ArgumentParser(description=__doc__)
    arguments.add_argument("reference", type=Path)
    source = Image.open(arguments.parse_args().reference).convert("RGB")
    if source.size != (1536, 1024):
        arguments.error("Expected the registered 1536×1024 Crime District sheet")
    hero = hero_frames(source)
    save_indexed(pack_people(polish_hero(hero)), ROOT / "walker.png")
    for model in range(1, 7):
        save_indexed(pack_people(pedestrian_rows(hero, model)), ROOT / f"pedestrian{model}.png")
    vehicles(source)
