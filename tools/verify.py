from pathlib import Path
from struct import unpack_from
from zipfile import ZipFile

jar = Path("dist/CrimeDistrict_Alpha022.jar")
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

    atlases = {"tiles": (128, 80), "walker": (96, 144), "sedan": (176, 44), "traffic": (192, 144)}
    atlases.update((f"pedestrian{i}", (96, 144)) for i in range(1,7))
    for name in [*atlases, *(f"prop{i}" for i in range(16))]:
        image = package.read(name + ".png")
        assert image.startswith(b"\x89PNG\r\n\x1a\n")
        size = unpack_from(">II", image, 16)
        if name in atlases:
            assert size == atlases[name]
            if name != "tiles":
                assert image[25] == 3 and b"tRNS" in image
        else:
            assert all(0 < edge <= 256 for edge in size)

    world = package.read("downtown.bin")
    columns, rows = unpack_from(">BB", world)
    assert columns * 16 > 240 and rows * 16 > 320
    end_tiles = 2 + columns * rows
    assert max(world[2:end_tiles]) < 40
    count, = unpack_from(">H", world, end_tiles)
    assert len(world) == end_tiles + 2 + 9 * count
    previous_depth = 0
    types = set()
    for i in range(count):
        kind, x, y, left, top, width, height = unpack_from(">BhhbbBB", world, end_tiles + 2 + i * 9)
        depth = y + top + height
        assert previous_depth <= depth <= rows * 16
        assert 0 <= x + left <= x + left + width <= columns * 16
        types.add(kind)
        previous_depth = depth
    assert types == set(range(16))
print(f"Verified {jar} ({jar.stat().st_size} bytes), preverification, resources, and JAD")
