#!/bin/bash

set -eu

if [ $# -lt 1 ]; then
  echo "usage $0 <version-name>"
  exit 1;
fi
readonly VERSION_NAME=$1
shift 1

$(dirname $0)/validate-dagger-version.sh "$VERSION_NAME"

pushd $(mktemp -d)
wget https://github.com/google/dagger/archive/dagger-$VERSION_NAME.zip -P .
OUTPUT=$(shasum -a 256 dagger-$VERSION_NAME.zip)
echo "SHA sum: $OUTPUT"
popd
