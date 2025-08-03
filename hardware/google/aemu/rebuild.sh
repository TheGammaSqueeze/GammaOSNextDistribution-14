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
INSTALL_DIR=$SCRIPT_DIR/install
CC=/usr/bin/clang
CXX=/usr/bin/clang++

run rm -rf $BUILD_DIR $INSTALL_DIR
run mkdir $BUILD_DIR
(
  run cd $BUILD_DIR &&
  cmake -DCMAKE_INSTALL_PREFIX=$INSTALL_DIR -DAEMU_COMMON_GEN_PKGCONFIG=ON -DAEMU_COMMON_BUILD_CONFIG=gfxstream -DENABLE_VKCEREAL_TESTS=OFF . ../ &&
  make -j &&
  make install
) || error "Build failed!"

echo "Successfully built and installed to $INSTALL_DIR."
