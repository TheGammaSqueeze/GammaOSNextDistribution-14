#!/bin/bash
#
# Copyright 2023 The Android Open Source Project.
#
# Retrieves the current mockito-kotlin source code into the current directory

# Force stop on first error.
set -e

if [ $# -ne 1 ]; then
    echo "$0 <version>" >&2
    exit 1;
fi

if [ -z "$ANDROID_BUILD_TOP" ]; then
    echo "Missing environment variables. Did you run build/envsetup.sh and lunch?" >&2
    exit 1
fi

VERSION=${1}

SOURCE="git://github.com/mockito/mockito-kotlin.git"
INCLUDE="
    LICENSE
    mockito-kotlin/src/main
    "

working_dir="$(mktemp -d)"
trap "echo \"Removing temporary directory\"; rm -rf $working_dir" EXIT

echo "Fetching mockito-kotlin source into $working_dir"
git clone $SOURCE $working_dir/source
(cd $working_dir/source; git checkout $VERSION)

for include in ${INCLUDE}; do
  echo "Updating $include"
  rm -rf $include
  mkdir -p $(dirname $include)
  cp -R $working_dir/source/$include $include
done;

echo "Done"

# Update the version.
perl -pi -e "s|^Version: .*$|Version: ${VERSION}|" "README.version"

# Remove any documentation about local modifications.
mv README.version README.tmp
grep -B 100 "Local Modifications" README.tmp > README.version
echo "        None" >> README.version
rm README.tmp

echo "Done"
