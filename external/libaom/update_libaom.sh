#!/bin/bash -e
#
# Copyright (c) 2012 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This tool is used to update libaom source code to a revision of the upstream
# repository. Modified from Chromium src/third_party/libvpx/update_libvpx.sh

# Usage:
#
# $ ./update_libaom.sh [branch | revision | file or url containing a revision]
# When specifying a branch it may be necessary to prefix with origin/

# Tools required for running this tool:
#
# 1. Linux / Mac
# 2. git

export LC_ALL=C

die() {
  echo "$@"
  exit 1
}

cleanup() {
  git remote remove $REMOTE 2>/dev/null
}

# Location for the remote git repository.
GIT_REPO="https://aomedia.googlesource.com/aom"

# Update to TOT by default.
GIT_BRANCH="main"

BASE_DIR=`pwd`

if [ -n "$1" ]; then
  GIT_BRANCH="$1"
  if [ -f "$1"  ]; then
    GIT_BRANCH=$(<"$1")
  elif [[ $1 = http* ]]; then
    GIT_BRANCH=`curl $1`
  fi
fi

prev_hash="$(egrep "^Commit: [[:alnum:]]" README.android | awk '{ print $2 }')"
echo "prev_hash:$prev_hash"

REMOTE="update_upstream"

trap cleanup EXIT

# Add a remote for upstream git repository
git remote add $REMOTE $GIT_REPO

# Fetch remote's GIT_BRANCH
git fetch $REMOTE $GIT_BRANCH --tags

# Get commit id corresponding to branch/revision in upstream repository
REMOTE_BRANCHES="$(git remote show $REMOTE)"

if [[ "$REMOTE_BRANCHES" == *"$GIT_BRANCH"* ]]; then
  UPSTREAM_COMMIT=$(git rev-list -n 1 $REMOTE/$GIT_BRANCH)
else
  UPSTREAM_COMMIT=$(git rev-list -n 1 $GIT_BRANCH)
fi

[ -z "$UPSTREAM_COMMIT" ] \
  && die "Unable to get upstream commit corresponding to ${GIT_BRANCH}";

# Defer the commit until after updating METADATA & README.android.
git merge --no-commit $UPSTREAM_COMMIT

# Get the current commit hash.
hash=$(git log $UPSTREAM_COMMIT -1 --format="%H")

# Update date and commit info in METADATA & README.android.
sed -E -i'' \
  -e "s/^([[:space:]]+year:).*/\1 $(date +'%Y')/" \
  -e "s/^([[:space:]]+month:).*/\1 $(date +'%-m')/" \
  -e "s/^([[:space:]]+day:).*/\1 $(date +'%-d')/" \
  METADATA
sed -E -i'' \
  -e "s/^(Date:).*/\1 $(date +'%A %B %d %Y')/" \
  -e "s/^(Branch:).*/\1 $GIT_BRANCH/" \
  -e "s/^(Commit:).*/\1 $hash/" \
  README.android

git commit -a -v

echo "Update the version field in README.android, README.version and METADATA."

chmod 755 build/cmake/*.pl
