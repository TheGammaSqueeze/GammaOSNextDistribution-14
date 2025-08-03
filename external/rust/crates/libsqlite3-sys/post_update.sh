#!/bin/bash

# $1 Path to the new version.
# $2 Path to the old version.

set -x
set -e

cp -a -n -r $2/android $1/
