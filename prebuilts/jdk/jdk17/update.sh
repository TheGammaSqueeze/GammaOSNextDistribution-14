#!/bin/bash -ev

# Updates prebuilts/jdk/jdk17 from build server. (ab/openjdk)
# Usage:
#    $ cd <REPO>
#    $ repo start jdk17_update prebuilts/jdk/jdk17
#    $ prebuilts/jdk/jdk17/update.sh <BUILD_NUMBER>
#    $ repo upload --cbr prebuilts/jdk/jdk17


BUILD_NUMBER="${1:?Specify build number}"
LINUX_ZIP=/tmp/$$.linux.zip
DARWIN_ZIP=/tmp/$$.darwin.zip
DARWIN_ARM64_ZIP=/tmp/$$.darwin_arm64.zip

FETCH_ARTIFACT=/google/data/ro/projects/android/fetch_artifact
cd "$(dirname $0)"

rm -rf staging
mkdir -p staging

$FETCH_ARTIFACT --bid ${BUILD_NUMBER} --disable_progressbar --target linux_openjdk17 jdk.zip ${LINUX_ZIP}
$FETCH_ARTIFACT --bid ${BUILD_NUMBER} --disable_progressbar --target darwin_mac_openjdk17 jdk.zip ${DARWIN_ZIP}
$FETCH_ARTIFACT --bid ${BUILD_NUMBER} --disable_progressbar --target darwin_aarch64_jetbrainsruntime17 jdk.zip ${DARWIN_ARM64_ZIP}
$FETCH_ARTIFACT --bid ${BUILD_NUMBER} --disable_progressbar --target linux_openjdk17 manifest_${BUILD_NUMBER}.xml staging/manifest.xml

rm -rf staging/linux-x86
mkdir -p staging/linux-x86
(cd staging/linux-x86; unzip -q ${LINUX_ZIP})
touch staging/linux-x86/MODULE_LICENSE_GPL

rm -rf staging/darwin-x86
mkdir staging/darwin-x86
(cd staging/darwin-x86; unzip -q ${DARWIN_ZIP})
touch staging/darwin-x86/MODULE_LICENSE_GPL

rm -rf staging/darwin-arm64
mkdir staging/darwin-arm64
(cd staging/darwin-arm64; unzip -q ${DARWIN_ARM64_ZIP})
(cp -r staging/darwin-arm64/Contents/Home/* staging/darwin-arm64; rm -rf staging/darwin-arm64/jdk-17.0.2.jdk)
touch staging/darwin-x86/MODULE_LICENSE_GPL

rm -f ${LINUX_ZIP} ${DARWIN_ZIP} ${DARWIN_ARM64_ZIP}

# Copy the RBE toolchain inputs files from the previous version to the staging
# directory.
cp linux-x86/bin/*_remote_toolchain_inputs staging/linux-x86/bin

version=$(staging/linux-x86/bin/java -version 2>&1 | grep "OpenJDK Runtime Environment" | sed -e 's/.*(\(.*\))/\1/')

rm -rf linux-x86 darwin-x86 darwin-arm64 manifest.xml
mv staging/* .
rmdir staging
git add linux-x86 darwin-x86 darwin-arm64 manifest.xml
git commit -m "Update to JDK $version" --edit
