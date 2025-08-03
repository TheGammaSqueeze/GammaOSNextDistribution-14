#!/usr/bin/env bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

GOOGLE_JAVA_FORMAT=$SCRIPT_DIR/../../../../../prebuilts/tools/common/google-java-format/google-java-format
ANDROID_BP_FORMAT=$SCRIPT_DIR/../../../../../prebuilts/build-tools/linux-x86/bin/bpfmt

$GOOGLE_JAVA_FORMAT --aosp -i $(find $SCRIPT_DIR/../ -name "*.java")
$ANDROID_BP_FORMAT -w $(find $SCRIPT_DIR/../ -name "*.bp")
