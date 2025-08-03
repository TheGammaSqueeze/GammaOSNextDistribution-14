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

readme() {
    echo '
Clone a git project for the unbundled development workflows in instead of
    the whole Android repo
e.g.
GIT_REPO_URL="https://android.googlesource.com" \
    PROJECT="platform/tools/aadevtools" \
    WORK_DIR="$PWD" \
    ./aadevtools/dev/clone_proj.sh
'
}

if [[ -z $GIT_REPO_URL ]]; then
    readme
    GIT_REPO_URL="https://android.googlesource.com"
fi
echo "GIT_REPO_URL=$GIT_REPO_URL"

if [[ -z $PROJECT ]]; then
    PROJECT="platform/tools/aadevtools"
fi
echo "PROJECT=$PROJECT"

# e.g. taking "aadevtools" from "platform/tools/aadevtools"
PROJECT_DIR=${PROJECT##*/}

TODAY=$(date +"%y%m%d")
if [[ -z $WORK_DIR ]]; then
    export WORK_DIR="$PWD/$TODAY/$BRANCH"
fi
echo "WORK_DIR=$WORK_DIR"

mkdir -p $WORK_DIR
cd $WORK_DIR

SECONDS=0
echo "Cloning $PROJECT"

if [[ -z $BRANCH ]]; then
    git clone "$GIT_REPO_URL/$PROJECT"
else
    echo echo "BRANCH=$BRANCH"
    git clone -b $BRANCH "$GIT_REPO_URL/$PROJECT"
fi

echo "Setup gerrit hooks"
cd "$WORK_DIR/$PROJECT_DIR"
f=`git rev-parse --git-dir`/hooks/commit-msg ; mkdir -p $(dirname $f) ; curl -Lo $f https://gerrit-review.googlesource.com/tools/hooks/commit-msg ; chmod +x $f

cd $WORK_DIR
ls -l

echo "
Cloned $PROJECT projects took $SECONDS sec.

Do your magic and then get the change pushed for review, e.g.:
git add .
git commit
git push origin HEAD:refs/for/'BRANCH'
"
