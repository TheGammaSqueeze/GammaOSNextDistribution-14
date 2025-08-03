#!/bin/bash

readonly OUT_DIR="$1"
readonly DIST_DIR="$2"
readonly BUILD_NUMBER="$3"

readonly SCRIPT_DIR="$(dirname "$0")"

readonly ARM=arm64
readonly X86=x86_64

NATIVE_LIBRARIES=${SCRIPT_DIR}"/../../out/host/darwin-x86/lib64"

# Find lipo command used to create and manipulate universal binaries
LIPO=$(/usr/bin/xcrun --find lipo)

mkdir ${OUT_DIR}/${ARM}
mkdir ${OUT_DIR}/${X86}

# Split all universal binaries built for layoutlib into an ARM64 version and a X86_64 version
for f in ${NATIVE_LIBRARIES}/*
do
  ${LIPO} $f -output ${OUT_DIR}/${ARM}/$(basename $f) -thin ${ARM}
  ${LIPO} $f -output ${OUT_DIR}/${X86}/$(basename $f) -thin ${X86}
done

# Put the single architecture binaries inside the DIST folder to be accessible on ab/
if [[ -d "${DIST_DIR}" ]]; then
    mkdir -p ${DIST_DIR}/layoutlib_native/darwin
    cp -r ${OUT_DIR}/${ARM} ${DIST_DIR}/layoutlib_native/darwin
    cp -r ${OUT_DIR}/${X86} ${DIST_DIR}/layoutlib_native/darwin
fi

# Clean
rm -rf ${OUT_DIR}/${ARM}
rm -rf ${OUT_DIR}/${X86}

exit 0
