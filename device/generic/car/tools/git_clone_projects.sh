#!/bin/bash

# Copyright (C) 2020 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

echo "An example to clone minimal car/tools project to operate AVDs."

if [[ -z $GIT_REPO_URL ]]; then
	GIT_REPO_URL="https://android.googlesource.com"
fi
echo "GIT_REPO_URL=$GIT_REPO_URL"

if [[ -z $BRANCH ]]; then
    echo 'You may set BRANCH="target-branch"'
fi
echo "BRANCH=$BRANCH"

if [[ -z $WORK_DIR ]]; then
    export WORK_DIR="$PWD"
fi
echo "WORK_DIR=$WORK_DIR"

mkdir -p $WORK_DIR
cd $WORK_DIR

echo "git clone https://android.googlesource.com/device/generic/car"
PROJECTS=0
SECONDS=0
PROJECT_PATH="device/generic/car"
if [[ -z $BRANCH ]]; then
    git clone "$GIT_REPO_URL/$PROJECT_PATH"
else
	git clone -b $BRANCH "$GIT_REPO_URL/$PROJECT_PATH"
fi
let "PROJECTS++"
cd "$WORK_DIR/car"
f=`git rev-parse --git-dir`/hooks/commit-msg ; mkdir -p $(dirname $f) ; curl -Lo $f https://gerrit-review.googlesource.com/tools/hooks/commit-msg ; chmod +x $f
cd "$WORK_DIR"
echo

ls -l "$WORK_DIR"

echo "

Cloning $PROJECTS projects takes: $SECONDS sec.

Do your magic and then get the change pushed for review, e.g.:
git add -u
git commit
git push origin HEAD:refs/for/BRANCH
"
