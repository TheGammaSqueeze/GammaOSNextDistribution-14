#!/bin/bash

# Quit if any command produces an error.
set -e

BUILD_ONLY="false"
while getopts "b" opt; do
  case ${opt} in
    b)
      BUILD_ONLY="true"
      ;;
  esac
done

# Build and run the CHRE unit test binary.
JOB_COUNT=$((`grep -c ^processor /proc/cpuinfo`))

# Export the variant Makefile.
export CHRE_VARIANT_MK_INCLUDES="$CHRE_VARIANT_MK_INCLUDES \
  variant/googletest/variant.mk"

make clean
make google_x86_googletest_debug -j$JOB_COUNT

if [ "$BUILD_ONLY" = "false" ]; then
./out/google_x86_googletest_debug/libchre ${@:1}
else
    if [ ! -f ./out/google_x86_googletest_debug/libchre ]; then
        echo  "./out/google_x86_googletest_debug/libchre does not exist."
        exit 1
    fi
fi