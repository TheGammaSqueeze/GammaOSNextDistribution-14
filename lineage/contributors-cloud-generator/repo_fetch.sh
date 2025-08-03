#!/bin/bash

GIT_CMD=/usr/bin/git
GIT_USER=a
GIT_PWD=a

OLD_PWD=`pwd`
REPO_DIR=$OLD_PWD/db/repos/$1
STATS_DIR=$OLD_PWD/db/stats/$1

MIRROR_DIR=$MIRROR_ROOT/$1.git

if [ -d "$MIRROR_DIR" ]; then
    echo "MIRROR: $MIRROR_DIR"
    cd "$MIRROR_DIR"
elif [ ! -d "$REPO_DIR" ]; then
    echo "REPO: $REPO_DIR"
    mkdir -p $REPO_DIR
    cd $REPO_DIR
    $GIT_CMD init
    $GIT_CMD remote add origin https://$GIT_USER:$GIT_PWD@github.com/$1.git
    $GIT_CMD fetch
else
    echo "REPO: $REPO_DIR"
    cd $REPO_DIR
    $GIT_CMD fetch
fi

# Get repository stats
mkdir -p $STATS_DIR
$GIT_CMD shortlog -e -s -n --all > $STATS_DIR/all_stats.dat
$GIT_CMD shortlog -e -s -n --all --grep='Automatic translation import' > $STATS_DIR/translations_stats.dat

cd $OLD_PWD
