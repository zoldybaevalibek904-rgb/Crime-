from pathlib import Path
from zipfile import ZipFile

jar = Path("dist/CrimeDistrict_Alpha01.jar")
jad = dict(line.split(": ", 1) for line in jar.with_suffix(".jad").read_text().splitlines())
assert jad["MIDlet-Jar-URL"] == jar.name
assert int(jad["MIDlet-Jar-Size"]) == jar.stat().st_size
with ZipFile(jar) as package:
    assert package.testzip() is None
    manifest = package.read("META-INF/MANIFEST.MF").decode()
    for key in ("MIDlet-1", "MIDlet-Vendor", "MIDlet-Name", "MIDlet-Version",
                "MicroEdition-Configuration", "MicroEdition-Profile"):
        assert f"{key}: {jad[key]}" in manifest
    entry = package.read("crimedistrict/CrimeDistrict.class")
    assert entry[:8] == bytes.fromhex("cafebabe0003002d")
    for name, width, height in (("tiles",16,16), ("walker",120,120), ("sedan",176,44)):
        path = Path("res", name + ".png")
        if path.exists():
            image = package.read(path.name)
            assert image.startswith(b"\x89PNG\r\n\x1a\n")
            assert int.from_bytes(image[16:20], "big") % width == 0
            assert int.from_bytes(image[20:24], "big") % height == 0
print(f"Verified {jar} ({jar.stat().st_size} bytes) and its JAD")
