#!/bin/bash
set -e

top=$(cd $(dirname $0)/../../../.. && pwd)

out=$top/out/python3
python_src=$top/external/python/cpython3

# On Linux, enter the Docker container and reinvoke this script.
if [ "$(uname)" == "Linux" -a "$SKIP_DOCKER" == "" ]; then
    docker build -t ndk-python3 $python_src/kokoro
    export SKIP_DOCKER=1
    docker run -v$top:$top -eKOKORO_BUILD_ID -eSKIP_DOCKER \
      --entrypoint $python_src/kokoro/kokoro_build.sh \
      ndk-python3
    exit $?
fi

extra_ldflags=
extra_notices=

if [ "$(uname)" == "Darwin" ]; then
    # The Kokoro big-sur builder has some extra x86-64 libraries installed in
    # /usr/local (using homebrew), which override the MacOS SDK. At least 3
    # modules are affected (_dbm, _gdbm, and _lzma).
    brew_all_pkgs=$(brew list)
    printf "Brew packages installed:\n%s\n\n" "$brew_all_pkgs"
    brew_pkgs=
    for name in gdbm xz; do
        if echo "$brew_all_pkgs" | grep -q "^${name}\(@\|$\)"; then
            brew_pkgs="$brew_pkgs $name"
        fi
    done
    if [ -n "$brew_pkgs" ]; then
        # A local developer probably won't have $KOKORO_ARTIFACTS_DIR set.
        if [ -n "$KOKORO_ARTIFACTS_DIR" ]; then
            # Pass --ignore-dependencies because some Homebrew packages still
            # need the packages we want to remove, notably python@3.9, which
            # we're using later to run kokoro/build.py.
            cmd="brew uninstall --ignore-dependencies $brew_pkgs"
            echo "Will run in 5 seconds (press Ctrl-C to abort): $cmd"
            sleep 5
            $cmd
        else
            echo "!!! WARNING: Your machine has Homebrew packages installed that could"
            echo "!!! affect how some extension modules are built:"
            echo "!!!"
            echo "!!!    $brew_pkgs"
            echo "!!!"
        fi
    fi

    # http://g3doc/devtools/kokoro/g3doc/userdocs/macos/selecting_xcode
    if [ -d /Applications/Xcode_12.5.1.app ]; then
        xcode=/Applications/Xcode_12.5.1.app
        cmd="sudo xcode-select -s $xcode/Contents/Developer"
        echo "Running: $cmd"
        $cmd
        export SDKROOT=$xcode/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk
    fi
    echo "Selected Xcode: $(xcode-select -p)"
elif [ "$(uname)" == "Linux" ]; then
    # Build libffi.a for use with the _ctypes module.
    (cd $top/external/libffi && ./autogen.sh)
    rm -fr $top/out/libffi
    mkdir -p $top/out/libffi/build
    pushd $top/out/libffi/build
    $top/external/libffi/configure \
        --enable-static --disable-shared --with-pic --disable-docs \
        --prefix=$top/out/libffi/install
    make -j$(nproc) install
    popd

    # cpython's configure script will use pkg-config to set LIBFFI_INCLUDEDIR,
    # which setup.py reads. It doesn't use pkg-config to add the library search
    # dir. With no --prefix, libffi.a would install to /usr/local/lib64, which
    # doesn't work because, even though setup.py links using -lffi, setup.py
    # first searches for libffi.{a,so} and needs to find it. setup.py searches
    # in /usr/local/lib and /usr/lib64, but not /usr/local/lib64.
    #
    # Use -Wl,--exclude-libs to hide libffi.a symbols in _ctypes.*.so.
    export PKG_CONFIG_PATH=$top/out/libffi/install/lib/pkgconfig
    extra_ldflags="$extra_ldflags -L$top/out/libffi/install/lib64 -Wl,--exclude-libs=libffi.a"
    extra_notices="$extra_notices $top/external/libffi/LICENSE"
fi

rm -fr $out

python3 --version
python3 $python_src/kokoro/build.py $python_src $out $out/artifact \
    "${KOKORO_BUILD_ID:-dev}" "$extra_ldflags" "$extra_notices"

# Verify that some extensions can be loaded.
$out/install/bin/python3 -c 'import binascii, bz2, ctypes, curses, curses.panel, hashlib, zlib'

$top/toolchain/ndk-kokoro/gen_manifest.py --root $top \
    -o "$out/artifact/manifest-${KOKORO_BUILD_ID:-dev}.xml"
