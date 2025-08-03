#!/bin/bash -ex
#
# Copyright 2023 Google Inc. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Update between branches, to a new major version.

cd `dirname ${BASH_SOURCE[0]}`/..

if [ -z ${1+x} ]; then
    echo "Usage: update-major-version.sh <upstream tag>"
    exit 1
fi
NEW_VERSION=$1
NEW_VERSION_SHORT=${NEW_VERSION#upstream-}
CURRENT_VERSION=upstream-$(grep 'version:' METADATA | grep -E -o 'v[0-9][^"]+')
CURRENT_HEAD=$(git rev-parse HEAD)
echo "Updating from $CURRENT_VERSION to $NEW_VERSION"

ADDED_FILES=$(git diff --name-only --diff-filter=A $CURRENT_VERSION HEAD | sort)
CHANGED_FILES=$(git diff --name-only --diff-filter=a $CURRENT_VERSION HEAD | sort)

PATCHFILE=$(mktemp --suffix=.patch)
trap "rm -f $PATCHFILE" EXIT

git diff --patch $CURRENT_VERSION HEAD -- $CHANGED_FILES > $PATCHFILE

git merge --no-commit -s ours $NEW_VERSION
git restore --staged --worktree --source=$NEW_VERSION -- .
git restore --staged --worktree --source=$CURRENT_HEAD -- $ADDED_FILES
patch -p1 < $PATCHFILE
git add $CHANGED_FILES
sed -i -e "s/\(version: \"\)v[0-9][^\"]*/\1$NEW_VERSION_SHORT/" METADATA
git add METADATA

NEW_ADDED_FILES=$(git diff --name-only --diff-filter=A --staged $NEW_VERSION | sort)
if [ "$ADDED_FILES" != "$NEW_ADDED_FILES" ] ; then
    echo "Added files don't match"
    exit 1
fi

NEW_CHANGED_FILES=$(git diff --name-only --diff-filter=a --staged $NEW_VERSION | sort)
if [ "$CHANGED_FILES" != "$NEW_CHANGED_FILES" ] ; then
    echo "Changed files don't match"
    exit 1
fi
