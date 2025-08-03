#!/bin/bash

set -eux

if [ $# -lt 1 ]; then
  echo "usage $0 <version-name>"
  exit 1;
fi
readonly VERSION_NAME=$1
shift 1

$(dirname $0)/validate-dagger-version.sh "$VERSION_NAME"

# Set the version string that is used as a tag in all of our libraries. If
# another repo depends on a versioned tag of Dagger, their java_library.tags
# should match the versioned release.
sed -i s/'${project.version}'/"${VERSION_NAME}"/g build_defs.bzl

# Note: We avoid commiting until after deploying in case deploying fails and
# we need to run the script again.
git commit -m "${VERSION_NAME} release" build_defs.bzl
git tag -a -m "Dagger ${VERSION_NAME}" dagger-"${VERSION_NAME}"
git push origin tag dagger-"${VERSION_NAME}"
