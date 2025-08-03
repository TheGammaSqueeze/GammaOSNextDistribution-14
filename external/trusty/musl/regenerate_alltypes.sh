#!/bin/bash

set -e
set -u

#TRUSTY: update the alltypes.h files that have been checked in.

MUSL_DIR=$(dirname "${BASH_SOURCE[0]}")

for ARCH in arm aarch64 x86_64
do
  sed -f ${MUSL_DIR}/tools/mkalltypes.sed ${MUSL_DIR}/arch/${ARCH}/bits/alltypes.h.in ${MUSL_DIR}/include/alltypes.h.in > ${MUSL_DIR}/arch/${ARCH}/bits/alltypes.h
done
