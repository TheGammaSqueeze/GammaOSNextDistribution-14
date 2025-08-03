#!/bin/bash
#
# Copyright (C) 2014 The Android Open Source Project
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
#

# Please change NDK_BUILD to point to the appropriate ndk-build in NDK. It's recommended to
# use the NDK with maximum backward compatibility, such as the NDK bundle in Android SDK.
NDK_BUILD="$HOME/Android/android-ndk-r16b/ndk-build"

function generateCopyRightComment() {
  local year="$1"
  local isAndroidManifest="$2"
  local lineComment='#'
  local copyrightStart=""
  local copyrightEnd=""
  local commentStart=""
  local commentEnd=""
  if [[ -n "$isAndroidManifest" ]]; then
    lineComment=""
    copyrightStart=$'<!--\n'
    copyrightEnd=$'\n-->'
    commentStart='<!--'
    commentEnd='-->'
  fi

  copyrightInMk=$(
    cat <<COPYRIGHT_COMMENT
${copyrightStart}${lineComment} Copyright (C) ${year} The Android Open Source Project
${lineComment}
${lineComment} Licensed under the Apache License, Version 2.0 (the "License");
${lineComment} you may not use this file except in compliance with the License.
${lineComment} You may obtain a copy of the License at
${lineComment}
${lineComment}      http://www.apache.org/licenses/LICENSE-2.0
${lineComment}
${lineComment} Unless required by applicable law or agreed to in writing, software
${lineComment} distributed under the License is distributed on an "AS IS" BASIS,
${lineComment} WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
${lineComment} See the License for the specific language governing permissions and
${lineComment} limitations under the License.${copyrightEnd}

${commentStart}${lineComment} Automatically generated file from build_libs.sh.${commentEnd}
${commentStart}${lineComment} DO NOT MODIFY THIS FILE.${commentEnd}

COPYRIGHT_COMMENT
  )
  echo "${copyrightInMk}"
}

function generateLibsAndroidMk {
  local targetFile=$1
  local copyrightInMk=$(generateCopyRightComment "2015")
  (
    cat <<LIBS_ANDROID_MK
${copyrightInMk}
include \$(call all-subdir-makefiles)
LIBS_ANDROID_MK
  ) >"${targetFile}"

}

function generateAndroidManifest {
  local targetFile="$1"
  local arch="$2"
  local splitNamePart="$3"
  (
    cat <<ANDROIDMANIFEST
<?xml version="1.0" encoding="utf-8"?>
<!-- Automatically generated file from build_libs.sh. -->
<!-- DO NOT MODIFY THIS FILE. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
        package="com.android.cts.splitapp"
        split="lib${splitNamePart}_${arch}">
    <application android:hasCode="false" />
</manifest>
ANDROIDMANIFEST
  ) >"${targetFile}"

}

function generateModuleForContentPartialMk {
  local arch="$1"
  local packagePartialName="$2"
  local rawDir="$3"
  local aaptRevisionFlags="$4"

  localPackage=$(
    cat <<MODULE_CONTENT_FOR_PARTIAL_MK

include \$(CLEAR_VARS)

LOCAL_PACKAGE_NAME := CtsSplitApp${packagePartialName}_${arch}
LOCAL_SDK_VERSION := current

LOCAL_JAVA_RESOURCE_DIRS := ${rawDir}

# tag this module as a cts test artifact
LOCAL_COMPATIBILITY_SUITE := cts general-tests

LOCAL_CERTIFICATE := cts/hostsidetests/appsecurity/certs/cts-testkey1
LOCAL_AAPT_FLAGS := --version-code 100 --replace-version${aaptRevisionFlags}

include \$(BUILD_CTS_SUPPORT_PACKAGE)
MODULE_CONTENT_FOR_PARTIAL_MK
  )
  echo "${localPackage}"
}

function generateAndroidMk() {
  local targetFile="$1"
  local arch="$2"
  local copyrightInMk=$(generateCopyRightComment "2014")
  local baseSplitMkModule=$(generateModuleForContentPartialMk "${arch}" "" "raw" "")
  local revisionSplitMkModule=$(generateModuleForContentPartialMk "${arch}" "_revision12" \
      "raw_revision" " --revision-code 12")

  (
    cat <<LIBS_ARCH_ANDROID_MK
#
${copyrightInMk}
LOCAL_PATH := \$(call my-dir)
${baseSplitMkModule}
${revisionSplitMkModule}
LIBS_ARCH_ANDROID_MK
  ) >"${targetFile}"
}

# Go build everything
rm -rf libs
cd jni/
$NDK_BUILD clean
$NDK_BUILD
cd ../

for arch in $(ls libs/); do
  (
    mkdir -p tmp/$arch/raw/lib/$arch/
    mv libs/$arch/* tmp/$arch/raw/lib/$arch/

    # The library file name in the new revision apk should have the same file name with base apk.
    mkdir -p tmp/$arch/raw_revision/lib/$arch/
    mv tmp/$arch/raw/lib/$arch/libsplitappjni_revision.so \
      tmp/$arch/raw_revision/lib/$arch/libsplitappjni.so

    archWithoutDash="${arch//[^a-zA-Z0-9_]/_}"
    generateAndroidManifest "tmp/$arch/AndroidManifest.xml" "${archWithoutDash}" ""

    generateAndroidMk "tmp/$arch/Android.mk" "$arch"
  )
done

generateLibsAndroidMk "tmp/Android.mk"

rm -rf libs
rm -rf obj

mv tmp libs
