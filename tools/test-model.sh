#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
game=dist/CrimeDistrict_Alpha02.jar
mkdir -p .build/modelqa
java -jar .build/deps/ecj-3.26.0.jar -1.8 -proc:none -cp "$game" \
    -d .build/modelqa tests/ModelQA.java
java -cp ".build/modelqa:$game" crimedistrict.ModelQA
