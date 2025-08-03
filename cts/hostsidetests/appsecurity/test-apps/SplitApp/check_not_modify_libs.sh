#!/bin/bash
LOCAL_DIR="$( dirname "${BASH_SOURCE}" )"

APP_DIR_IN_CTS="^hostsidetests\\/appsecurity\\/test-apps\\/SplitApp"
BUILD_LIBS_SCRIPT="${APP_DIR_IN_CTS}\\/build_libs\\.sh\$"
APP_LIBS_ANDROID_MK="${APP_DIR_IN_CTS}\\/libs/Android\\.mk\$"
NATIVE_MK_PATTERN="${APP_DIR_IN_CTS}\\/libs\\/.*\\/Android\\.mk\$"
MANIFEST_PATTERN="${APP_DIR_IN_CTS}\\/libs\\/.*\\/AndroidManifest\\.xml\$"
JNI_PATTERN="${APP_DIR_IN_CTS}\\/jni\\/.*\$"
LIB_SO_PATTERN="${APP_DIR_IN_CTS}\\/libs\\/.*\\/libsplitappjni.*\\.so\$"

MODIFY_JNI=0
MODIFY_ANDROID_MK=0
MODIFY_BUILD_LIBS_SCRIPT=0
LIB_SO_LIST=""
MK_LIST=""
MANIFEST_LIST=""
for f in $*
do
    echo "${f}" | grep -q "${BUILD_LIBS_SCRIPT}" && MODIFY_BUILD_LIBS_SCRIPT=1
    echo "${f}" | grep -q "${APP_LIBS_ANDROID_MK}" && MODIFY_ANDROID_MK=1

    echo "${f}" | grep -q "${NATIVE_MK_PATTERN}" && MK_LIST="${MK_LIST}\n ${f}"

    echo "${f}" | grep -q "${MANIFEST_PATTERN}" && MANIFEST_LIST="${MANIFEST_LIST}\n ${f}"

    echo "${f}" | grep -q "${JNI_PATTERN}" && MODIFY_JNI=1
    echo "${f}" | grep -q "${LIB_SO_PATTERN}" && LIB_SO_LIST="${LIB_SO_LIST}\n ${f}"
done

NUMBER_OF_ERRORS=0
if [[ ${MODIFY_ANDROID_MK} -ne 0 && ${MODIFY_BUILD_LIBS_SCRIPT} -eq 0 ]]
then
    ((NUMBER_OF_ERRORS++))
    echo -e "Please modify ${BUILD_LIBS_SCRIPT//\\/} instead of\n" \
        "\033[0;31;47m${APP_LIBS_ANDROID_MK//\\/}\033[0m?"
fi
if [[ -n "${MK_LIST}" && ${MODIFY_BUILD_LIBS_SCRIPT} -eq 0 ]]
then
    ((NUMBER_OF_ERRORS++))
    echo -e "Please modify ${BUILD_LIBS_SCRIPT//\\/} instead of" \
        "\033[0;31;47m${MK_LIST}\033[0m?"
fi
if [[ -n "${MANIFEST_LIST}" && ${MODIFY_BUILD_LIBS_SCRIPT} -eq 0 ]]
then
    ((NUMBER_OF_ERRORS++))
    echo -e "Please modify ${BUILD_LIBS_SCRIPT//\\/} instead of" \
        "\033[0;31;47m${MANIFEST_LIST}\033[0m?"
fi
if [[ -n "${LIB_SO_LIST}" && ${MODIFY_BUILD_LIBS_SCRIPT} -eq 0 && ${MODIFY_JNI} -eq 0 ]]
then
    ((NUMBER_OF_ERRORS++))
    echo -e "Please modify ${JNI_PATTERN//\\/} files instead of" \
        "\033[0;31;47m${LIB_SO_LIST}\033[0m?"
fi
if [[ ${NUMBER_OF_ERRORS} -gt 0 ]]
then
    echo "Please make sure to modify the file by running build_libs.sh.${NUMBER_OF_ERRORS}"
fi

exit ${NUMBER_OF_ERRORS}

