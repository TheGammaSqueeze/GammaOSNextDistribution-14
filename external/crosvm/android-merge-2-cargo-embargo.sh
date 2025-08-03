#!/bin/bash

# Run cargo_embargo with the appropriate arguments.

set -e -u

function usage() { echo "$0 [-r]" && exit 1; }

REUSE=""
while getopts 'r' FLAG; do
  case ${FLAG} in
    r)
      REUSE="--reuse-cargo-out"
      ;;
    ?)
      echo "unknown flag."
      usage
      ;;
  esac
done

if ! [ -x "$(command -v bpfmt)" ]; then
  echo 'Error: bpfmt not found.' >&2
  exit 1
fi

# Use the specific rust version that crosvm upstream expects.
#
# TODO: Consider reading the toolchain from external/crosvm/rust-toolchain
#
# TODO: Consider using android's prebuilt rust binaries. Currently doesn't work
# because they try to incorrectly use system clang and llvm.
RUST_TOOLCHAIN="1.68.2"
rustup which --toolchain $RUST_TOOLCHAIN cargo || \
  rustup toolchain install $RUST_TOOLCHAIN
CARGO_BIN="$(dirname $(rustup which --toolchain $RUST_TOOLCHAIN cargo))"

cd $ANDROID_BUILD_TOP/external/crosvm

if [ ! "$REUSE" ]; then
  rm -f cargo.out cargo.metadata
  rm -rf target.tmp || /bin/true
fi

set -x
cargo_embargo $REUSE --cargo-bin "$CARGO_BIN" generate cargo_embargo.json
set +x

if [ ! "$REUSE" ]; then
  rm -f cargo.out cargo.metadata
  rm -rf target.tmp || /bin/true
fi

# Revert changes to Cargo.lock caused by cargo_embargo.
#
# Android diffs in Cargo.toml files can cause diffs in the Cargo.lock when
# cargo_embargo runs. This didn't happen with cargo2android.py because it
# ignored the lock file.
git restore Cargo.lock

# Fix workstation specific path in "metrics" crate's generated files.
# TODO(b/232150148): Find a better solution for protobuf generated files.
sed --in-place 's/path = ".*\/out/path = "./' metrics/out/generated.rs
