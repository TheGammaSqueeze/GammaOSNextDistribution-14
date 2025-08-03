#!/bin/bash

# Script that cleans up branches created by ojluni_merge_to_main. They are
# usually name something like 'expected_upstream_e0bywxovb5'.

if [[ -z "${ANDROID_BUILD_TOP}" ]]
then
  echo -e "ANDROID_BUILD_TOP not found. You need to run lunch first."
  exit 1
fi

pushd "${ANDROID_BUILD_TOP}/libcore"

for br in $(git branch | grep -E "^\s*expected_upstream_[a-z0-9]+$")
do
  echo git branch -D ${br}
  git branch -D ${br}
done

popd
