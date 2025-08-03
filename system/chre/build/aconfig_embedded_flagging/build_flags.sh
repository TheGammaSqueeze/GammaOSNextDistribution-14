#!/bin/bash

# Argument format:
#  $1     = Path to aconfig binary
#  $2     = Path to the aconfig_embedded_flagging directory
# [$3:$@] = Any number of aconfig flag value *.textproto files

# Set the default ACONFIG variables if not provided by environment
DEFAULT_ACONFIG_BIN=$ANDROID_BUILD_TOP/prebuilts/build-tools/linux-x86/bin/aconfig
DEFAULT_ACONFIG_EMB_DIR=.
OUT_DIR="out"

# Get fully qualified path of any input variables before moving to build dir
IN_ACONFIG_BIN=$(realpath "${1:-$DEFAULT_ACONFIG_BIN}")
IN_ACONFIG_EMB_DIR=$(realpath "${2:-$DEFAULT_ACONFIG_EMB_DIR}")

# Process remaining arguments for flag values
shift
shift
IN_FLAG_VALUES=""
for i in "$@"; do
  IN_FLAG_VALUES+="--values "
  IN_FLAG_VALUES+=$(realpath "$i ")
done

pushd $IN_ACONFIG_EMB_DIR > /dev/null
mkdir -p $OUT_DIR

# Create the aconfig cache based off of the flag declarations and values
$IN_ACONFIG_BIN create-cache \
            --package android.embedded.chre.flags \
            --declarations chre_embedded_flags.aconfig \
            $IN_FLAG_VALUES \
            --values local-chre_embedded_flag_values.textproto \
            --cache $OUT_DIR/chre_embedded_cache

# Create the flagging library (.h, .cc) based off of the cache
$IN_ACONFIG_BIN create-cpp-lib \
            --cache $OUT_DIR/chre_embedded_cache \
            --out $OUT_DIR/chre_embedded_flag_lib

# Create a text record for the flag values with this build
$IN_ACONFIG_BIN dump \
            --cache $OUT_DIR/chre_embedded_cache \
            --format text \
            --out $OUT_DIR/chre_embedded_flag_dump

popd > /dev/null