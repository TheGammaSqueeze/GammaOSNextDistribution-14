#!/bin/bash

# $1 Path to the new version.
# $2 Path to the old version.

set -x
set -e

cp -a -n -r $2/android $1/

# upstream OWNERS files are not AOSP Gerrit OWNERS files.
rm -f `find grpc -name OWNERS`
