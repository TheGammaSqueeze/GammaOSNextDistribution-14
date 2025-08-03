#!/bin/bash
#
# Generate ArmNN SL driver prebuilts

eval set -- "$OPTS"
if [[ -z "$ANDROID_BUILD_TOP" ]]; then
  echo ANDROID_BUILD_TOP not set, bailing out
  echo you must run lunch before running this script
  exit 1
fi

set -e
cd $ANDROID_BUILD_TOP

source build/envsetup.sh
ARCHS="arm,arm64"
ARMNN_SL_DRIVER="libarmnn_support_library"

for arch in ${ARCHS//,/ }
do
  lunch "aosp_${arch}-userdebug"

  LIB=lib
  if [[ $arch =~ "64" ]]; then
    LIB=lib64
  fi

  TMPFILE=$(mktemp)
  build/soong/soong_ui.bash --make-mode ${ARMNN_SL_DRIVER} 2>&1 | tee ${TMPFILE}
  TARGETDIR=external/armnn/shim/sl/build/android_${arch}/${ARMNN_SL_DRIVER}_prebuilt.so
  mkdir -p ${TARGETDIR%/*}
  cp $OUT/system/${LIB}/${ARMNN_SL_DRIVER}.so ${TARGETDIR}

done

