#!/bin/bash

SCRIPT_DIR="$(dirname $0)"
DIST_DIR="$1"

STUDIO_JDK=${SCRIPT_DIR}"/../../../../prebuilts/jdk/jdk17/linux-x86"
OUT_INTERMEDIATES=${SCRIPT_DIR}"/../../../../out/soong/.intermediates"

${STUDIO_JDK}/bin/java -ea \
    -cp ${OUT_INTERMEDIATES}/external/junit/junit/linux_glibc_common/javac/junit.jar:${OUT_INTERMEDIATES}/external/hamcrest/hamcrest-core/hamcrest/linux_glibc_common/javac/hamcrest.jar:${OUT_INTERMEDIATES}/frameworks/layoutlib/create/layoutlib_create/linux_glibc_common/combined/layoutlib_create.jar:${OUT_INTERMEDIATES}/frameworks/layoutlib/create/tests/layoutlib-create-tests/linux_glibc_common/combined/layoutlib-create-tests.jar:${SCRIPT_DIR}/res \
    org.junit.runner.JUnitCore \
    com.android.tools.layoutlib.create.AllTests

