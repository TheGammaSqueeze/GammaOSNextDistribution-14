#!/bin/bash

readonly OUT_DIR="$1"
readonly DIST_DIR="$2"
readonly BUILD_NUMBER="$3"

readonly SCRIPT_DIR="$(dirname "$0")"
readonly SCRIPT_TO_TOP="../../../../../.."

readonly FAILURE_DIR=layoutlib-test-failures
readonly FAILURE_ZIP=layoutlib-test-failures.zip

STUDIO_JDK=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/prebuilts/jdk/jdk11/linux-x86"
MISC_COMMON=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/prebuilts/misc/common"
OUT_INTERMEDIATES=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/soong/.intermediates"
NATIVE_LIBRARIES=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/host/linux-x86/lib64/"
JAVA_LIBRARIES=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/soong/.intermediates/platform_testing/libraries/screenshot/deviceless/layoutlib/tests/res/testApp/MyApplication/layoutlib-myapplication/android_common/javac"
SDK=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/host/linux-x86/sdk/sdk*/android-sdk*"
SDK_REPO=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/host/linux-x86/sdk-repo"
FONT_DIR=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/host/common/obj/PACKAGING/fonts_intermediates"
KEYBOARD_DIR=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/host/common/obj/PACKAGING/keyboards_intermediates"
ICU_DATA_PATH=${SCRIPT_DIR}"/${SCRIPT_TO_TOP}/out/host/linux-x86/com.android.i18n/etc/icu/icudt71l.dat"
TMP_DIR=$(mktemp -d)
PLATFORM=${TMP_DIR}/"android"

# Copy resources to a temp directory
cp -r ${SDK}/platforms/android* ${PLATFORM}

# Unzip build-tools to access aapt2
mkdir ${TMP_DIR}/build-tools
unzip -q ${SDK_REPO}/sdk-repo-linux-build-tools.zip -d ${TMP_DIR}/build-tools

# Compile 9-patch files
mkdir ${TMP_DIR}/compiled
mkdir ${TMP_DIR}/manifest
echo \
'<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.google.android.layoutlib" />' \
> ${TMP_DIR}/manifest/AndroidManifest.xml
find ${SDK}/platforms/android*/data/res -name "*.9.png" -print0 | xargs -0 ${TMP_DIR}/build-tools/android-*/aapt2 compile -o ${TMP_DIR}/compiled/
find ${TMP_DIR}/compiled -name "*.flat" -print0 | xargs -0 -s 1000000 ${TMP_DIR}/build-tools/android-*/aapt2 link -o ${TMP_DIR}/compiled.apk --manifest ${TMP_DIR}/manifest/AndroidManifest.xml -R
unzip -q ${TMP_DIR}/compiled.apk -d ${TMP_DIR}
for f in ${TMP_DIR}/res/*; do mv "$f" "${f/-v4/}";done
cp -RL ${TMP_DIR}/res ${PLATFORM}/data

# Run layoutlib tests
${STUDIO_JDK}/bin/java -ea \
    -Dnative.lib.path=${NATIVE_LIBRARIES} \
    -Dfont.dir=${FONT_DIR} \
    -Dicu.data.path=${ICU_DATA_PATH} \
    -Dkeyboard.dir=${KEYBOARD_DIR} \
    -Dplatform.dir=${PLATFORM} \
    -Dtest_res.dir=${SCRIPT_DIR}/res \
    -Dtest_failure.dir=${OUT_DIR}/${FAILURE_DIR} \
    -cp ${MISC_COMMON}/tools-common/tools-common-prebuilt.jar:${MISC_COMMON}/ninepatch/ninepatch-prebuilt.jar:${MISC_COMMON}/sdk-common/sdk-common.jar:${MISC_COMMON}/kxml2/kxml2-2.3.0.jar:${MISC_COMMON}/layoutlib_api/layoutlib_api-prebuilt.jar:${OUT_INTERMEDIATES}/prebuilts/tools/common/m2/trove-prebuilt/linux_glibc_common/combined/trove-prebuilt.jar:${OUT_INTERMEDIATES}/external/junit/junit/linux_glibc_common/javac/junit.jar:${OUT_INTERMEDIATES}/external/guava/guava-jre/linux_glibc_common/javac/guava-jre.jar:${OUT_INTERMEDIATES}/external/hamcrest/hamcrest-core/hamcrest/linux_glibc_common/javac/hamcrest.jar:${OUT_INTERMEDIATES}/external/mockito/mockito/linux_glibc_common/combined/mockito.jar:${OUT_INTERMEDIATES}/external/objenesis/objenesis/linux_glibc_common/javac/objenesis.jar:${OUT_INTERMEDIATES}/frameworks/layoutlib/bridge/layoutlib/linux_glibc_common/withres/layoutlib.jar:${OUT_INTERMEDIATES}/frameworks/layoutlib/temp_layoutlib/linux_glibc_common/gen/temp_layoutlib.jar:${OUT_INTERMEDIATES}/frameworks/layoutlib/bridge/tests/layoutlib-tests/linux_glibc_common/withres/layout-testlib.jar:${OUT_INTERMEDIATES}/platform_testing/libraries/screenshot/deviceless/layoutlib/tests/platform-layoutlib-sample-tests/linux_glibc_common/withres/platform-layoutlib-sample-tests.jar:${OUT_INTERMEDIATES}/platform_testing/libraries/screenshot/deviceless/layoutlib/tests/res/testApp/MyApplication/layoutlib-myapplication/android_common/javac/layoutlib-myapplication.jar \
    org.junit.runner.JUnitCore \
    com.android.layoutlib.bridge.android.Main

test_exit_code=$?

# Create zip of all failure screenshots
if [[ -d "${OUT_DIR}/${FAILURE_DIR}" ]]; then
    zip -q -j -r ${OUT_DIR}/${FAILURE_ZIP} ${OUT_DIR}/${FAILURE_DIR}
fi

# Move failure zip to dist directory if specified
if [[ -d "${DIST_DIR}" ]] && [[ -e "${OUT_DIR}/${FAILURE_ZIP}" ]]; then
    mv ${OUT_DIR}/${FAILURE_ZIP} ${DIST_DIR}
fi

# Clean
rm -rf ${TMP_DIR}
rm -rf ${OUT_DIR}/${FAILURE_DIR}

exit ${test_exit_code}

