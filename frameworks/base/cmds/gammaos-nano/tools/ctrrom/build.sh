#!/bin/sh
# Builds the ROM banner extraction host test (tools/ctrrom).
#   ./build.sh               g++, Mesa EGL/GLES2 + zlib (extract and render)
#   CXX=clang++ ./build.sh   clang with the device's -O3 -ffast-math flags plus -mfma, so the
#                            x86 backend may fuse multiplies and adds like arm64 does
#   TARGET=arm64 ./build.sh  the NDK clang with the device flags, a static Android arm64
#                            binary (extract only) to run under qemu-aarch64-static
# OUT selects the output directory (default ./out), NDK the NDK root.
set -e
cd "$(dirname "$0")"
OUT=${OUT:-out}
mkdir -p "$OUT"
DEVICE_FLAGS="-O3 -ffast-math -fno-math-errno -fno-trapping-math"
WARN="-Wall -Wextra -Werror -Wunused -Wunreachable-code -Wno-unused-parameter -Wno-missing-field-initializers"
if [ "$TARGET" = arm64 ]; then
  NDK=${NDK:-/work/android-ndk-r27c}
  CC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/clang++ --target=aarch64-linux-android34"
  $CC -std=c++17 -g $WARN $DEVICE_FLAGS -mtune=cortex-a55 -I. -I../.. -c ../../NanoCtrRom.cpp -o "$OUT/NanoCtrRom-arm64.o"
  $CC -std=c++17 -O1 -g -Wall -DCTRROM_NO_GL -I. -I../.. ctrrom.cpp "$OUT/NanoCtrRom-arm64.o" ../../NanoCtrPack.cpp \
    -static -static-libstdc++ -lz -o "$OUT/ctrrom-arm64"
  exit 0
fi
CXX=${CXX:-g++}
EXTRA="-O2"
case "$CXX" in *clang*) EXTRA="$DEVICE_FLAGS -mfma";; esac
[ "$OUT/stbimpl.o" -nt stbimpl.cpp ] || $CXX -O1 -c stbimpl.cpp -o "$OUT/stbimpl.o"
$CXX -std=c++17 -g $WARN $EXTRA -I. -I../.. -c ../../NanoCtrRom.cpp -o "$OUT/NanoCtrRom.o"
$CXX -std=c++17 -O1 -g -Wall -Wno-unused-function -DGL_GLEXT_PROTOTYPES -I. -I../.. \
  ctrrom.cpp "$OUT/NanoCtrRom.o" ../../NanoCtrPack.cpp ../../NanoCtr3d.cpp "$OUT/stbimpl.o" \
  -lEGL -lGLESv2 -lz -o "$OUT/ctrrom"
