#!/bin/bash
set -e

cd "$(dirname $0)/../../"
SCRIPT_DIR="$(pwd)"
echo "Script running from $(pwd)"

# resolve DIST_DIR
if [ -z "$DIST_DIR" ]; then
  DIST_DIR="$SCRIPT_DIR/out/dist"
fi
mkdir -p "$DIST_DIR"

export OUT_DIR=out
export DIST_DIR="$DIST_DIR"

JAVA_HOME="$(pwd)/prebuilts/studio/jdk/jdk17/linux" tools/gradlew -p tools/ publishLocal --stacktrace

# Depend on the generated version.properties file, as the version depends on
# the release flag
versionProperties="$OUT_DIR/build/base/builder-model/build/resources/main/com/android/builder/model/version.properties"
# Mac grep doesn't support -P, so use perl version of `grep -oP "(?<=buildVersion = ).*"`
export LINT_VERSION=`perl -nle'print $& while m{(?<=baseVersion=).*}g' $versionProperties`
export LINT_REPO="$(pwd)/out/repo"

JAVA_HOME="$(pwd)/prebuilts/jdk/jdk17/linux-x86/" tools/gradlew -p tools/metalava \
  --no-daemon \
  --stacktrace \
   --dependency-verification=off
