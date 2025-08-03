#!/bin/bash
#
# Copyright (C) 2007 The Android Open Source Project
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

# This script is used by external_updater to replace a package. Don't
# invoke directly.

set -e

tmp_dir=$1
external_dir=$2
tmp_file=$3

# root of Android source tree
root_dir=`pwd`

echo "Entering $tmp_dir..."
cd $tmp_dir

function CopyIfPresent() {
  if [ -e $external_dir/$1 ]; then
    cp -a -n $external_dir/$1 .
  fi
}

echo "Copying preserved files..."
CopyIfPresent "Android.bp"
CopyIfPresent "Android.mk"
CopyIfPresent "CleanSpec.mk"
CopyIfPresent "LICENSE"
CopyIfPresent "NOTICE"
cp -a -f -n $external_dir/MODULE_LICENSE_* .
CopyIfPresent "METADATA"
CopyIfPresent "TEST_MAPPING"
CopyIfPresent ".git"
CopyIfPresent ".gitignore"
if compgen -G "$external_dir/cargo2android*"; then
    cp -a -f -n $external_dir/cargo2android* .
fi
if compgen -G "$external_dir/cargo_embargo*"; then
    cp -a -f -n $external_dir/cargo_embargo* .
fi
CopyIfPresent "patches"
CopyIfPresent "post_update.sh"
CopyIfPresent "OWNERS"
CopyIfPresent "README.android"
CopyIfPresent "rules.mk"
if compgen -G "$external_dir/cargo2rulesmk*"; then
    cp -a -f -n $external_dir/cargo2rulesmk* .
fi

file_counter=0
total_files=$(ls $tmp_dir/patches | grep -Ei '(diff|patch)$' | wc -l)
for p in $tmp_dir/patches/*.{diff,patch}
do
  file_counter=$((file_counter+1))
  [ -e "$p" ] || continue
  # Do not patch the Android.bp file, as we assume it will
  # patch itself.
  if [ -f $tmp_dir/Cargo.toml ]
  then
      [ "$(basename $p)" != "Android.bp.diff" ] || continue
      [ "$(basename $p)" != "Android.bp.patch" ] || continue
      [ "$(basename $p)" != "rules.mk.diff" ] || continue
      [ "$(basename $p)" != "rules.mk.patch" ] || continue
  fi
  echo "Applying patch [$file_counter/$total_files] $p..."
  patch -p1 -d $tmp_dir --no-backup-if-mismatch < $p;
done

if [ -f $tmp_dir/Cargo.toml -a -f $tmp_dir/Android.bp ]
then
    # regenerate Android.bp after local patches, as they may
    # have deleted files that it uses.
  /bin/bash `dirname $0`/regen_bp.sh $root_dir $external_dir
fi

if [ -f $tmp_dir/post_update.sh ]
then
  echo "Running post update script"
  $tmp_dir/post_update.sh $tmp_dir $external_dir
fi

echo "Swapping old and new..."
second_tmp_dir=`mktemp -d`
mv $external_dir $second_tmp_dir
mv $tmp_dir $external_dir
mv $second_tmp_dir/* $tmp_dir
rm -rf $second_tmp_dir
if [ -n "$tmp_file" ]; then
    # Write to the temporary file to show we have swapped.
    echo "Swapping" > $tmp_file
fi

cd $external_dir
git add .

exit 0
