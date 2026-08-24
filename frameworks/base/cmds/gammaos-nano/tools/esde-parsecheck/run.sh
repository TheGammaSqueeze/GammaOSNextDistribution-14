#!/bin/bash
# Host-side ES-DE theme parse-matrix validator.
#
# Compiles the device's GL-free parser (NanoEsdeTheme.cpp) plus tinyxml2 into a
# tiny host binary and loads every theme in a directory across its FULL declared
# matrix (variant x colorScheme x aspectRatio, plus every fontSize), reporting any
# load that comes back invalid. Catches parse regressions across the whole
# permutation space with no device and no Soong build.
#
# Usage:  ./run.sh [themes_dir]        (default: /work/esde_ref_themes)
set -e
here="$(cd "$(dirname "$0")" && pwd)"
nano="$(cd "$here/../.." && pwd)"                 # frameworks/base/cmds/gammaos-nano
tx="$(cd "$nano/../../../../external/tinyxml2" && pwd)"
out="$(mktemp -d)/parsecheck"
g++ -std=c++17 -O2 -I"$here" -I"$nano" -I"$tx" \
    "$here/main.cpp" "$nano/NanoEsdeTheme.cpp" "$tx/tinyxml2.cpp" -o "$out"
"$out" "${1:-/work/esde_ref_themes}"
