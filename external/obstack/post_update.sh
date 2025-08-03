#!/bin/bash -x

# $1 Path to the new version.
# $2 Path to the old version.

# We only want a few files from the archive, so delete any files that weren't
# in the old version.  Start with deleting whole directories first.
find $1 -maxdepth 1 -type d -printf "%P\n" | while read f; do
  if [ ! -d "$2/$f" ]; then
      rm -rf $1/$f
  fi
done

find $1 -printf "%P\n" | while read f; do
  if [ ! -e "$2/$f" ]; then
      rm -rf $1/$f
  fi
done

# Copy over the android directory
cp -r $2/android $1/android
