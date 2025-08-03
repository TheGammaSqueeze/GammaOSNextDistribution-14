#!/bin/bash
# Script used to build Tinysys.

# make sure $ANDROID_BUILD_TOP is set
if [[ -z "$ANDROID_BUILD_TOP" ]]; then
    echo "Must setup Android build environment first" 1>&2
    echo "Run the following commands from the repo root:" 1>&2
    echo " source build/envsetup.sh" 1>&2
    echo " lunch vext_k6985v1_64-userdebug" 1>&2
    exit 1
fi

# make sure $RISCV_TOOLCHAIN_PATH & $RISCV_TINYSYS_PREFIX are set
if [[ -z "$RISCV_TOOLCHAIN_PATH" ]] || [[ -z "$RISCV_TINYSYS_PREFIX" ]]; then
    echo "Must provide RISCV_TOOLCHAIN_PATH & RISCV_TINYSYS_PREFIX" 1>&2
    echo "Example:" 1>&2
    echo " RISCV_TOOLCHAIN_PATH=\$ANDROID_BUILD_TOP/prebuilts/clang/md32rv/linux-x86 \\" 1>&2
    echo " RISCV_TINYSYS_PREFIX=\$ANDROID_BUILD_TOP/vendor/mediatek/proprietary/tinysys \\" 1>&2
    echo " build/tools/build_tinysys.sh" 1>&2
    exit 1
fi

pushd $ANDROID_BUILD_TOP/system/chre > /dev/null

target=${1:-aosp_riscv55e03_tinysys}

CHRE_VARIANT_MK_INCLUDES=variant/tinysys/variant.mk \
 IS_ARCHIVE_ONLY_BUILD=true \
 make $target

popd > /dev/null
