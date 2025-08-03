#!/bin/bash
#
# Generate sample SL driver prebuilts

eval set -- "$OPTS"
if [[ -z "$ANDROID_BUILD_TOP" ]]; then
  echo ANDROID_BUILD_TOP not set, bailing out
  echo you must run lunch before running this script
  exit 1
fi

set -e
cd $ANDROID_BUILD_TOP

source build/envsetup.sh
ARCHS="x86,arm,arm64,x86_64,riscv64"
SAMPLE_SL_DRIVER="neuralnetworks_sample_sl_driver"

for arch in ${ARCHS//,/ }
do
  if [[ $arch == "arm64" || $arch == "x86_64" ]]; then
    lunch "aosp_cf_${arch}_phone_pgagnostic-trunk-userdebug"
  else
    lunch "aosp_${arch}-trunk-userdebug"
  fi

  LIB=lib
  if [[ $arch =~ "64" ]]; then
    LIB=lib64
  fi

  TMPFILE=$(mktemp)
  build/soong/soong_ui.bash --make-mode ${SAMPLE_SL_DRIVER} 2>&1 | tee ${TMPFILE}
  TARGETDIR=packages/modules/NeuralNetworks/driver/sample_shim/android_${arch}/neuralnetworks_sample_sl_driver_prebuilt.so
  mkdir -p ${TARGETDIR%/*}
  cp $OUT/system/${LIB}/neuralnetworks_sample_sl_driver.so ${TARGETDIR}
done

