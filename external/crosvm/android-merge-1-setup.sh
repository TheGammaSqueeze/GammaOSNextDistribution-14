#!/bin/bash

set -ex

function usage() { echo "$0 [-s][-b]" && exit 1; }

sync=""
branch=""
while getopts 'sb' FLAG; do
  case ${FLAG} in
    s)
      sync="sync"
      ;;
    b)
      branch="branch"
      ;;
    ?)
      echo "unknown flag."
      usage
      ;;
  esac
done

shift $((OPTIND-1))
if [ $# != 0 ]; then
    echo "unknown positional argument."
    usage
fi

if [ "$sync" = "sync" ]
then
  read -p "This script will sync your crosvm project. Do you wish to proceed? [y/N]" -n 1 -r
  if [[ ! $REPLY =~ ^[Yy]$ ]]
  then
    exit 1;
  fi
fi

if [ -z $ANDROID_BUILD_TOP ]; then echo "forgot to source build/envsetup.sh?" && exit 1; fi
cd $ANDROID_BUILD_TOP/external/crosvm

if [[ ! -z $(git branch --list merge) && ! "$branch" = "branch" ]];
  then
    echo "branch merge already exists. Forgot to clean up?" && exit 1;
fi
rustup update
if [ "$sync" = "sync" ]
then
  repo sync -c -j96
  git fetch aosp upstream-main
fi

source $ANDROID_BUILD_TOP/build/envsetup.sh
m blueprint_tools cargo_embargo crosvm

if [ ! "$branch" = "branch" ];
  then
    repo start merge;
fi

git merge --log aosp/upstream-main
$ANDROID_BUILD_TOP/external/crosvm/tools/install-deps
