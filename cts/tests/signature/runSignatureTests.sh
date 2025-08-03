#! /bin/bash
#
# Copyright 2017 The Android Open Source Project.
#
# Builds and runs signature APK tests.

set -e

if [ -z "$ANDROID_BUILD_TOP" ]; then
    echo "Missing environment variables. Did you run build/envsetup.sh and lunch?" >&2
    exit 1
fi

if [ $# -eq 0 ]; then
    PACKAGES="
CtsCurrentApiSignatureTestCases
CtsSystemApiSignatureTestCases
CtsAndroidTestMockCurrentApiSignatureTestCases
CtsAndroidTestRunnerCurrentApiSignatureTestCases
CtsAndroidTestBase29ApiSignatureTestCases
CtsAndroidTestBaseUsesLibraryApiSignatureTestCases
CtsAndroidTestBaseCurrentApiSignatureTestCases

CtsApacheHttpLegacy27ApiSignatureTestCases
CtsApacheHttpLegacyCurrentApiSignatureTestCases
CtsApacheHttpLegacyUsesLibraryApiSignatureTestCases

CtsSystemApiAnnotationTestCases

CtsHiddenApiBlocklistCurrentApiTestCases
CtsHiddenApiBlocklistApi27TestCases
CtsHiddenApiBlocklistApi28TestCases
CtsHiddenApiBlocklistDebugClassTestCases

CtsHiddenApiKillswitchWildcardTestCases
CtsHiddenApiKillswitchSdkListTestCases
CtsHiddenApiKillswitchDebugClassTestCases

CtsSharedLibsApiSignatureTestCases
"
else
    PACKAGES=${1+"$@"}
fi

atest ${PACKAGES}
