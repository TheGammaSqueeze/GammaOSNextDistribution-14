#!/bin/bash
function run() {
  echo "Running: $@"
  $@
}

function error() {
  echo "Error: $@"
  exit 1
}

SCRIPT_DIR="$( cd -- "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
BUILD_DIR=$SCRIPT_DIR/build
INSTALL_DIR=$1
CC=/usr/bin/clang
CXX=/usr/bin/clang++

run rm -rf $BUILD_DIR $INSTALL_DIR
run mkdir $BUILD_DIR
(
  run meson --prefix $INSTALL_DIR $BUILD_DIR &&
  ninja -C $BUILD_DIR install
) || error "Build failed!"

echo "Successfully built and installed to $INSTALL_DIR."
