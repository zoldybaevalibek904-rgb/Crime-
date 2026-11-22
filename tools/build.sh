#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .build/deps dist
base=https://repo.maven.apache.org/maven2
fetch() {
    local file=.build/deps/${1##*/}
    if [[ ! -s "$file" ]]; then
        curl --fail --silent --show-error --retry 3 -o "$file.part" "$base/$1"
        mv "$file.part" "$file"
    fi
}
fetch org/eclipse/jdt/ecj/3.26.0/ecj-3.26.0.jar
fetch org/microemu/cldcapi11/2.0.4/cldcapi11-2.0.4.jar
fetch org/microemu/midpapi20/2.0.4/midpapi20-2.0.4.jar
fetch net/sf/proguard/proguard-base/6.2.2/proguard-base-6.2.2.jar

api=.build/deps/cldcapi11-2.0.4.jar:.build/deps/midpapi20-2.0.4.jar
rm -rf .build/classes .build/verified
mkdir -p .build/classes
java -jar .build/deps/ecj-3.26.0.jar -source 1.4 -target 1.4 \
    -bootclasspath "$api" -d .build/classes src
java -jar .build/deps/proguard-base-6.2.2.jar \
    -injars .build/classes -outjars .build/verified -libraryjars "$api" \
    -target 1.1 -dontshrink -dontoptimize -dontobfuscate -microedition \
    -keep 'public class crimedistrict.CrimeDistrict { public <init>(); }'

cat > .build/manifest <<'MANIFEST'
Manifest-Version: 1.0
MIDlet-Name: Crime District
MIDlet-Version: 0.3.0
MIDlet-Vendor: Crime District
MIDlet-1: Crime District,,crimedistrict.CrimeDistrict
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
MANIFEST
name=CrimeDistrict_Alpha03
python3 - "$name" <<'PYZIP'
import sys
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED

with ZipFile("dist/" + sys.argv[1] + ".jar", "w") as jar:
    def add(name, data):
        jar.writestr(ZipInfo(name), data, compress_type=ZIP_DEFLATED)
    manifest = Path(".build/manifest").read_bytes().replace(b"\n", b"\r\n")
    add("META-INF/MANIFEST.MF", manifest + b"\r\n")
    for root in (Path(".build/verified"), Path("res")):
        for path in sorted(root.rglob("*")):
            if path.is_file() and not path.name.startswith("."):
                add(path.relative_to(root).as_posix(), path.read_bytes())
PYZIP
{
    tail -n +2 .build/manifest
    printf 'MIDlet-Jar-URL: %s.jar\nMIDlet-Jar-Size: %s\n' "$name" "$(wc -c < "dist/$name.jar")"
} > "dist/$name.jad"
python3 tools/verify.py
