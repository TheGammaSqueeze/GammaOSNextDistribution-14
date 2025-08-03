#!/bin/bash
set -u

readonly OUT_DIR="$1"
readonly DIST_DIR="$2"
readonly SCRIPT_DIR="$(dirname "$0")"
readonly BASE_DIR=`readlink -m ${SCRIPT_DIR}/../../../../`
echo "BASE_DIR: $BASE_DIR"
readonly FAILURE_DIR=layoutlib-test-failures
readonly FAILURE_ZIP=layoutlib-test-failures.zip

readonly CLEAN_TMP_FILES=1
readonly USE_SOONG=1

readonly APP_NAME="regression"
#readonly APP_NAME="test_HelloActivity"

STUDIO_JDK="${BASE_DIR}/prebuilts/jdk/jdk17/linux-x86"
MISC_COMMON="${BASE_DIR}/prebuilts/misc/common"
OUT_INTERMEDIATES="${BASE_DIR}/out/soong/.intermediates"
NATIVE_LIBRARIES="${BASE_DIR}/out/host/linux-x86/lib64/"
JAVA_LIBRARIES="${BASE_DIR}/out/host/common/obj/JAVA_LIBRARIES/"
HOST_LIBRARIES="${BASE_DIR}/out/host/linux-x86/"
SDK="${BASE_DIR}/out/host/linux-x86/sdk/sdk*/android-sdk*"
SDK_REPO="${BASE_DIR}/out/host/linux-x86/sdk-repo"
FONT_DIR="${BASE_DIR}/out/host/common/obj/PACKAGING/fonts_intermediates"
KEYBOARD_DIR="${BASE_DIR}/out/host/common/obj/PACKAGING/keyboards_intermediates"
ICU_DATA_PATH="${BASE_DIR}/out/host/linux-x86/com.android.i18n/etc/icu/icudt72l.dat"
TMP_DIR=${OUT_DIR}"/layoutlib_tmp"

PLATFORM=${TMP_DIR}/"android"

if [ ! -d $TMP_DIR ]; then
    # Copy resources to a temp directory
    mkdir -p ${TMP_DIR} ${PLATFORM} ${TMP_DIR}/build-tools ${TMP_DIR}/compiled ${TMP_DIR}/manifest

    cp -r ${SDK}/platforms/android*/** ${PLATFORM}

    # Unzip build-tools to access aapt2
    unzip -q ${SDK_REPO}/sdk-repo-linux-build-tools.zip -d ${TMP_DIR}/build-tools

    # Compile 9-patch files
    echo \
'<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.google.android.layoutlib" />' \
> ${TMP_DIR}/manifest/AndroidManifest.xml
    find ${SDK}/platforms/android*/data/res -name "*.9.png" -print0 | xargs -0 ${TMP_DIR}/build-tools/android-*/aapt2 compile -o ${TMP_DIR}/compiled/
    find ${TMP_DIR}/compiled -name "*.flat" -print0 | xargs -0 -s 1000000 ${TMP_DIR}/build-tools/android-*/aapt2 link -o ${TMP_DIR}/compiled.apk --manifest ${TMP_DIR}/manifest/AndroidManifest.xml -R
    unzip -q ${TMP_DIR}/compiled.apk -d ${TMP_DIR}
    for f in ${TMP_DIR}/res/*; do mv "$f" "${f/-v4/}";done
    cp -RL ${TMP_DIR}/res ${PLATFORM}/data
fi


TEST_JARS="${OUT_INTERMEDIATES}/frameworks/layoutlib/bridge/tests/layoutlib-tests/linux_glibc_common/withres/layoutlib-tests.jar"
GRADLE_RES="-Dtest_res.dir=${SCRIPT_DIR}/res"

# Run layoutlib tests
#DEBUGGER=' -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000 '
DEBUGGER=' '

set -x
${STUDIO_JDK}/bin/java -ea $DEBUGGER \
    -Dnative.lib.path=${NATIVE_LIBRARIES} \
    -Dfont.dir=${FONT_DIR} \
    -Dicu.data.path=${ICU_DATA_PATH} \
    -Dkeyboard.dir=${KEYBOARD_DIR} \
    -Dplatform.dir=${PLATFORM} \
    -Dtest_failure.dir=${OUT_DIR}/${FAILURE_DIR} \
    ${GRADLE_RES} \
    -cp ${TEST_JARS} \
    org.junit.runner.JUnitCore \
    com.android.layoutlib.bridge.intensive.Main
test_exit_code=$?
set +x


# Create zip of all failure screenshots
rm -f ${OUT_DIR}/${FAILURE_ZIP}
if [[ -d "${OUT_DIR}/${FAILURE_DIR}" ]]; then
    zip -q -j -r ${OUT_DIR}/${FAILURE_ZIP} ${OUT_DIR}/${FAILURE_DIR}
fi

# Move failure zip to dist directory if specified
if [[ -d "${DIST_DIR}" ]] && [[ -e "${OUT_DIR}/${FAILURE_ZIP}" ]]; then
    mv ${OUT_DIR}/${FAILURE_ZIP} ${DIST_DIR}
fi

# Clean
if [[ $CLEAN_TMP_FILES -eq 1 ]]; then
  rm -rf ${TMP_DIR}
  rm -rf ${OUT_DIR}/${FAILURE_DIR}
fi

exit ${test_exit_code}
