#!/bin/bash
OUTFILE="$1"
BUILD_NUMBER_FROM_FILE=${OUT_DIR}/build_number.txt
if test -f "$BUILD_NUMBER_FROM_FILE"; then
  cp ${BUILD_NUMBER_FROM_FILE} ${OUTFILE}
else
  DATETIME=$(TZ='UTC' date +'%Y.%m.%d')
  echo ${DATETIME}_local_build > ${OUTFILE}
fi
