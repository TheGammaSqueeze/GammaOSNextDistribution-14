#!/bin/bash
# Builds the azahar-nano core (Azahar 3DS emulator with the GammaOS changes) from
# this source tree and installs the libraries the image ships into
# gammaos/emulators/azahar-nano-lib/:
#   libazahar-nano-core.so, libc++_shared.so and the libadrenotools hook libraries
#   (libmain_hook.so, libhook_impl.so, libfile_redirect_hook.so, libgsl_alloc_hook.so).
#
# Called by buildtv.sh before the image build; can also be run on its own.
#
# Needs (downloaded once into out/azahar-nano/ when missing):
#   - Android NDK r27c   (or set ANDROID_NDK to an existing r27c)
#   - CMake >= 3.25      (the host cmake is used when new enough)
# Ninja comes from prebuilts/build-tools.
#
# Environment:
#   AZAHAR_NANO_SKIP_BUILD=1  keep the libraries already in azahar-nano-lib
#   AZAHAR_NANO_JOBS=N        parallel jobs (default: nproc)

set -euo pipefail

if [ "${AZAHAR_NANO_SKIP_BUILD:-0}" = "1" ]; then
    echo "azahar-nano: AZAHAR_NANO_SKIP_BUILD=1, keeping the prebuilt libraries"
    exit 0
fi

TOP="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
SRC="$TOP/gammaos/emulators/azahar-nano"
WORK="$TOP/out/azahar-nano"
BUILD="$WORK/build"
DEST="$TOP/gammaos/emulators/azahar-nano-lib"
NINJA="$TOP/prebuilts/build-tools/linux-x86/bin/ninja"
JOBS="${AZAHAR_NANO_JOBS:-$(nproc)}"

NDK_VERSION="r27c"
NDK_ZIP_URL="https://dl.google.com/android/repository/android-ndk-${NDK_VERSION}-linux.zip"
CMAKE_VERSION="3.31.6"
CMAKE_TAR_URL="https://github.com/Kitware/CMake/releases/download/v${CMAKE_VERSION}/cmake-${CMAKE_VERSION}-linux-x86_64.tar.gz"

mkdir -p "$WORK"

fetch() {   # url, destination file
    if command -v curl >/dev/null 2>&1; then
        curl -fL --retry 3 -o "$2" "$1"
    else
        wget -O "$2" "$1"
    fi
}

# --- NDK -----------------------------------------------------------------------
NDK="${ANDROID_NDK:-$WORK/android-ndk-${NDK_VERSION}}"
if [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
    echo "azahar-nano: downloading Android NDK ${NDK_VERSION} (once)"
    fetch "$NDK_ZIP_URL" "$WORK/ndk.zip"
    unzip -q -o "$WORK/ndk.zip" -d "$WORK"
    rm -f "$WORK/ndk.zip"
    NDK="$WORK/android-ndk-${NDK_VERSION}"
fi

# --- CMake ---------------------------------------------------------------------
CMAKE=""
if command -v cmake >/dev/null 2>&1; then
    ver="$(cmake --version | head -1 | awk '{print $3}')"
    major="${ver%%.*}"; rest="${ver#*.}"; minor="${rest%%.*}"
    if [ "$major" -gt 3 ] || { [ "$major" -eq 3 ] && [ "$minor" -ge 25 ]; }; then
        CMAKE="$(command -v cmake)"
    fi
fi
if [ -z "$CMAKE" ]; then
    CMAKE="$WORK/cmake-${CMAKE_VERSION}-linux-x86_64/bin/cmake"
    if [ ! -x "$CMAKE" ]; then
        echo "azahar-nano: downloading CMake ${CMAKE_VERSION} (once)"
        fetch "$CMAKE_TAR_URL" "$WORK/cmake.tar.gz"
        tar -xzf "$WORK/cmake.tar.gz" -C "$WORK"
        rm -f "$WORK/cmake.tar.gz"
    fi
fi

# --- Profile-guided optimization ----------------------------------------------------
# AZAHAR_NANO_PGO=generate builds an instrumented core into out/azahar-nano/build-pgogen
# (installed to out/azahar-nano/pgogen-lib, never into the shipped prebuilts) that writes
# its profile to /data/local/tmp/azpgo on the device (sys.gammaos.azahar_nano.pgo_dump=1).
# Otherwise, when gammaos/pgo/azahar-nano.profdata exists the core is optimized with it.
PGO_MODE="${AZAHAR_NANO_PGO:-use}"
PGO_PROFILE="$SRC/gammaos/pgo/azahar-nano.profdata"
EXTRA_FLAGS=""
if [ "$PGO_MODE" = "generate" ]; then
    BUILD="$WORK/build-pgogen"
    DEST="$WORK/pgogen-lib"
    EXTRA_FLAGS="-fprofile-generate=/data/local/tmp/azpgo -DGAMMAOS_PGO_GENERATE=1"
elif [ "$PGO_MODE" = "use" ] && [ -f "$PGO_PROFILE" ]; then
    EXTRA_FLAGS="-fprofile-use=$PGO_PROFILE -Wno-profile-instr-out-of-date -Wno-profile-instr-unprofiled -Wno-backend-plugin"
fi

# --- Configure (when new or the flags changed) and build ----------------------------
FLAGS_STAMP="$BUILD/.gammaos_extra_flags"
if [ ! -f "$BUILD/build.ninja" ] || [ "$(cat "$FLAGS_STAMP" 2>/dev/null)" != "$EXTRA_FLAGS" ]; then
    echo "azahar-nano: configuring (extra flags: ${EXTRA_FLAGS:-none})"
    "$CMAKE" -S "$SRC" -B "$BUILD" -G Ninja \
        -DCMAKE_C_FLAGS="$EXTRA_FLAGS" \
        -DCMAKE_CXX_FLAGS="$EXTRA_FLAGS" \
        -DCMAKE_SHARED_LINKER_FLAGS="$EXTRA_FLAGS" \
        -DCMAKE_MAKE_PROGRAM="$NINJA" \
        -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=arm64-v8a \
        -DANDROID_PLATFORM=android-31 \
        -DANDROID_ARM_NEON=true \
        -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON \
        -DCMAKE_BUILD_TYPE=Release \
        -DENABLE_QT=OFF \
        -DENABLE_SDL2=OFF \
        -DENABLE_WEB_SERVICE=OFF \
        -DENABLE_SCRIPTING=OFF \
        -DENABLE_TESTS=OFF \
        -DENABLE_LTO=ON \
        -DCITRA_ENABLE_BUNDLED_FFMPEG=OFF \
        -DENABLE_FFMPEG_VIDEO_DUMPER=OFF \
        -DENABLE_COMPATIBILITY_LIST_DOWNLOAD=OFF \
        -DCITRA_WARNINGS_AS_ERRORS=OFF
    mkdir -p "$BUILD"
    printf '%s' "$EXTRA_FLAGS" > "$FLAGS_STAMP"
fi

echo "azahar-nano: building the core (${JOBS} jobs)"
"$NINJA" -C "$BUILD" -j "$JOBS" azahar-nano-core main_hook hook_impl file_redirect_hook gsl_alloc_hook

# --- Install (stripped) into the image's prebuilt folder --------------------------
STRIP="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip"
mkdir -p "$DEST"
install_lib() {   # source, name
    "$STRIP" -o "$DEST/$2" "$1"
}
install_lib "$BUILD/src/android/app/src/main/jni/libazahar-nano-core.so" libazahar-nano-core.so
for hook in main_hook hook_impl file_redirect_hook gsl_alloc_hook; do
    install_lib "$BUILD/externals/libadrenotools/src/hook/lib${hook}.so" "lib${hook}.so"
done
cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so" \
    "$DEST/libc++_shared.so"
"$STRIP" "$DEST/libc++_shared.so"

echo "azahar-nano: libraries installed into ${DEST#$TOP/}"
