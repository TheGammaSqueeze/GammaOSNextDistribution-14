#!/bin/bash

set -o errexit

if [[ "${ANDROID_BUILD_TOP}" == "" ]]; then
  echo "Run source build/envsetup.sh && lunch to be able to format Android.bp"
  exit 2
fi

DIR=$(dirname $0)

"${DIR}"/generate_bp.py "$DIR" >"${DIR}/../Android.bp"

bpfmt -w "${DIR}/../Android.bp"

