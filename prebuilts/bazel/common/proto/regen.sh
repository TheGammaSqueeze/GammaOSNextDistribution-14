#!/bin/bash

set -e

function die() { echo "ERROR: $1" >&2; exit 1; }

readonly error_msg="Maybe you need to run 'lunch aosp_arm-eng && m aprotoc blueprint_tools'?"

if ! which aprotoc &>/dev/null; then
  die "could not find aprotoc. ${error_msg}"
fi

if ! aprotoc --go_out=paths=source_relative:. analysis_v2/analysis_v2.proto build/build.proto; then
  die "build failed. ${error_msg}"
fi
