#!/usr/bin/env bash

# Copyright (C) 2015 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# A helper script that launches Trade Federation for atest
if [[ -z "${ATEST_HELPER}" ]]; then
    ATEST_HELPER="$(dirname $0)/atest_script_help.sh"
fi
source $ATEST_HELPER

# TODO b/63295046 (sbasi) - Remove this when LOCAL_JAVA_LIBRARIES includes
# installation.
# Include any host-side dependency jars.
if [[ ! -z "$ANDROID_HOST_OUT" ]]; then
    # TF will load the first test-suite-info.properties in those jar files it loaded.
    # In current cases that only those *ts suite jars have built in that file.
    # If user tested testcases which using those jar files with suite-info properties
    # but change the lunch target to different arch and continue testing other tests without the
    # need of those *ts jars then TF will not testing with below error message:
    # "None of the abi supported by this tests suite build".
    # Create atest-tradefed.jar with test-suite-info.properties and make sure it's priroty is higher
    # then other *ts-tradefed.jar.
    deps="atest-tradefed.jar
          compatibility-host-util.jar
          hamcrest-library.jar
          hosttestlib.jar
          cts-tradefed.jar
          sts-tradefed.jar
          vts-tradefed.jar
          csuite-harness.jar
          tradefed-isolation.jar
          host-libprotobuf-java-full.jar
          cts-dalvik-host-test-runner.jar
          compatibility-tradefed.jar"
    for dep in $deps; do
        if [ -f "$ANDROID_HOST_OUT/framework/$dep" ]; then
          TF_PATH+=":$ANDROID_HOST_OUT/framework/$dep"
        fi
    done
fi

# Accumulate prebuilt jars as a part of classpath when the configurations are
# packaged into a prebuilt jar (b/192046472)
TF_CORE_DIR=$ANDROID_BUILD_TOP/tools/tradefederation/core
if [ ! -d $TF_CORE_DIR ]; then
    TF_DIR=$ANDROID_BUILD_TOP/tools/tradefederation/prebuilts/filegroups
    GTF_DIR=$ANDROID_BUILD_TOP/vendor/google_tradefederation/prebuilts/filegroups
    PREBUILT_JARS=$(find $TF_DIR $GTF_DIR -type f -name '*.jar' 2>/dev/null)
    if [ -n "$PREBUILT_JARS" ]; then
        for jar in $PREBUILT_JARS; do
            TF_PATH+=":$jar"
        done
    fi
fi

if [ "$(uname)" == "Darwin" ]; then
    local_tmp_dir="$ANDROID_HOST_OUT/tmp"
    [[ -f "$local_tmp_dir" ]] || mkdir -p "$local_tmp_dir"
    java_tmp_dir_opt="-Djava.io.tmpdir=$local_tmp_dir"
fi

# Override the TF classpath with the minimal set of jars that correspond to the
# tests being run. This only happens when the Atest `--minimal-build` flag is
# enabled.
# TODO(b/283352284): Remove unnecessary entries once --minimal-build is the
# default.
if [ -n "${ATEST_HOST_JARS}" ]; then
    echo "Replaced TF_PATH from ${TF_PATH} to ${ATEST_HOST_JARS}"
    TF_PATH=${ATEST_HOST_JARS}
fi

# Customize TF related settings for ATest local run to align with test runs on
# CI.
extra_settings="
  --test-arg com.android.tradefed.testtype.python.PythonBinaryHostTest:python-options:-vv"


# Note: must leave $RDBG_FLAG and $TRADEFED_OPTS unquoted so that they go away when unset
LOCAL_MODE=1 START_FEATURE_SERVER=1 ${TF_JAVA} $RDBG_FLAG \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:-OmitStackTraceInFastThrow \
    $TRADEFED_OPTS \
    -cp "${TF_PATH}" \
    -DTF_JAR_DIR=${TF_JAR_DIR} ${java_tmp_dir_opt} \
    com.android.tradefed.command.CommandRunner "$@" $extra_settings
