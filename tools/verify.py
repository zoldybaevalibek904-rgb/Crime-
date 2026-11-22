from pathlib import Path
from struct import unpack_from
from zipfile import ZipFile

jar = Path("dist/CrimeDistrict_Alpha04.jar")
jad = dict(line.split(": ", 1) for line in jar.with_suffix(".jad").read_text().splitlines())
assert jad["MIDlet-Jar-URL"] == jar.name
assert int(jad["MIDlet-Jar-Size"]) == jar.stat().st_size
with ZipFile(jar) as package:
    assert package.testzip() is None
    manifest = dict(line.split(": ", 1) for line in
                    package.read("META-INF/MANIFEST.MF").decode().splitlines() if line)
    for key in ("MIDlet-1", "MIDlet-Vendor", "MIDlet-Name", "MIDlet-Version",
                "MicroEdition-Configuration", "MicroEdition-Profile"):
        assert manifest[key] == jad[key]
    assert jad["MicroEdition-Configuration"] == "CLDC-1.1"
    assert jad["MicroEdition-Profile"] == "MIDP-2.0"
    entry = jad["MIDlet-1"].split(",")[2].replace(".", "/") + ".class"
    assert entry in package.namelist()
    for name in package.namelist():
        if name.endswith(".class"):
            assert name.startswith("crimedistrict/")
            assert package.read(name)[:8] == bytes.fromhex("cafebabe0003002d")
    assert b"\x00\x08StackMap" in package.read("crimedistrict/CrimeDistrict$Screen.class")

    atlases = {"tiles": (128, 112), "walker": (96, 144), "sedan": (176, 44), "traffic": (192, 144), "cruiser": (176, 44), "ambulance": (192,48), "actions": (180,176)}
    atlases.update((f"pedestrian{i}", (96, 144)) for i in range(1,27))
    for name in [*atlases, *(f"prop{i}" for i in range(29))]:
        image = package.read(name + ".png")
        assert image.startswith(b"\x89PNG\r\n\x1a\n")
        size = unpack_from(">II", image, 16)
        if name in atlases:
            assert size == atlases[name]
            if name != "tiles":
                assert image[25] == 3 and b"tRNS" in image
        else:
            assert all(0 < edge <= 256 for edge in size)

    world = package.read("city04.bin")
    columns, rows = unpack_from(">BB", world)
    assert (columns, rows) == (192, 176)
    end_tiles = 2 + columns * rows * 9 // 8
    zones = bytearray(columns*rows)
    for cy in range(rows//16):
        for cx in range(columns//16):
            base = 2+(cy*(columns//16)+cx)*288
            for i in range(256):
                bit = i*9
                p = base+(bit>>3)
                item = ((world[p] | world[p+1]<<8) >> (bit&7)) & 511
                assert item&63 < 56
                zones[(cy*16+i//16)*columns+cx*16+i%16] = item>>6
    assert set(zones) == set(range(8))
    count, = unpack_from(">H", world, end_tiles)
    assert len(world) == end_tiles + 2 + 9 * count
    previous_depth = 0
    for i in range(count):
        kind, x, y, left, top, width, height = unpack_from(">BhhbbBB", world, end_tiles + 2 + i * 9)
        depth = y + top + height
        assert kind < 29 and previous_depth <= depth <= rows * 16
        assert 0 <= x + left <= x + left + width <= columns * 16
        if width:
            for row in range((y+top)//16,(depth-1)//16+1):
                for col in range((x+left)//16,(x+left+width-1)//16+1):
                    assert zones[row*columns+col] not in (0,4,7), (kind,col,row)
        previous_depth = depth
print(f"Verified {jar} ({jar.stat().st_size} bytes), preverification, resources, and JAD")
