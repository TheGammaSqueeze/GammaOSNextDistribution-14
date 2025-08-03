#!/bin/bash

# Thin wrapper around merge_target_files for vendor-frozen targets to
# allow flag changes to be made in a presubmit-guarded change.

set -e

while getopts ":t:d:v:b:m:r:" option ; do
  case "${option}" in
    t) TARGET=${OPTARG} ;;
    d) DIST_DIR=${OPTARG} ;;
    v) VENDOR_DIR=${OPTARG} ;;
    b) BUILD_ID=${OPTARG} ;;
    m) MERGE_CONFIG_DIR=${OPTARG} ;;
    r) HAS_RADIO_IMG=${OPTARG} ;;
    *) echo "Unexpected argument: -${OPTARG}" >&2 ;;
  esac
done

if [[ -z "${TARGET}" ]]; then
  echo "error: -t target argument not set"
  exit 1
fi
if [[ -z "${DIST_DIR}" ]]; then
  echo "error: -d dist dir argument not set"
  exit 1
fi
if [[ -z "${VENDOR_DIR}" ]]; then
  echo "error: -v vendor dir argument not set"
  exit 1
fi
if [[ -z "${BUILD_ID}" ]]; then
  echo "error: -b build id argument not set"
  exit 1
fi
if [[ -z "${HAS_RADIO_IMG}" ]]; then
  HAS_RADIO_IMG="true"
fi

# Move the system-only build artifacts to a separate folder
# so that the flashing tools use the merged files instead.
readonly SYSTEM_DIR=${DIST_DIR}/system_build
mkdir -p ${SYSTEM_DIR}
mv -f ${DIST_DIR}/android-info.txt ${SYSTEM_DIR}
mv -f ${DIST_DIR}/${TARGET}-*.zip ${SYSTEM_DIR}

source build/envsetup.sh
lunch ${TARGET}-userdebug

EXTRA_FLAGS=""
if [[ "${MERGE_CONFIG_DIR}" ]]; then
  EXTRA_FLAGS+=" --framework-item-list ${MERGE_CONFIG_DIR}/framework_item_list.txt \
  --framework-misc-info-keys ${MERGE_CONFIG_DIR}/framework_misc_info_keys.txt \
  --vendor-item-list ${MERGE_CONFIG_DIR}/vendor_item_list.txt"
fi
out/host/linux-x86/bin/merge_target_files \
  --framework-target-files ${SYSTEM_DIR}/${TARGET}-target_files*.zip \
  --vendor-target-files ${VENDOR_DIR}/*-target_files-*.zip \
  --allow-duplicate-apkapex-keys \
  --output-target-files ${DIST_DIR}/${TARGET}-target_files-${BUILD_ID}.zip \
  --output-img  ${DIST_DIR}/${TARGET}-img-${BUILD_ID}.zip \
  --output-ota  ${DIST_DIR}/${TARGET}-ota-${BUILD_ID}.zip \
  ${EXTRA_FLAGS}

# Copy bootloader.img, radio.img, and android-info.txt, needed for flashing.
cp ${VENDOR_DIR}/bootloader.img ${DIST_DIR}/bootloader.img
# Copy radio.img unless arg is "false" (eg. Android TV targets)
if [[ $HAS_RADIO_IMG = "true" ]]; then
  cp ${VENDOR_DIR}/radio.img ${DIST_DIR}/radio.img
fi

# Copy vendor otatools.zip, needed by sign_target_files_apks
if [[ -f "${VENDOR_DIR}/otatools.zip" ]]; then
  cp ${VENDOR_DIR}/otatools.zip ${DIST_DIR}/otatools_vendor.zip
fi

unzip -j -d ${DIST_DIR} \
  ${VENDOR_DIR}/*-target_files-*.zip \
  OTA/android-info.txt