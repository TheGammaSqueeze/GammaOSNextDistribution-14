#!/bin/bash

# Copyright (C) 2021 The Android Open Source Project
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
#

set -xeuo pipefail

apexer_tool_path="${RUNFILES_DIR}/__main__/system/apex/apexer/apexer"
conv_apex_manifest_tool_path="${RUNFILES_DIR}/__main__/system/apex/apexer/conv_apex_manifest"
deapexer_tool_path="${RUNFILES_DIR}/__main__/system/apex/tools/deapexer"
avb_tool_path="${RUNFILES_DIR}/__main__/external/avb"
e2fsdroid_path="${RUNFILES_DIR}/__main__/external/e2fsprogs/contrib/android/bin/e2fsdroid"
mke2fs_path="${RUNFILES_DIR}/__main__/external/e2fsprogs/misc/bin/mke2fs"
resize2fs_path="${RUNFILES_DIR}/__main__/external/e2fsprogs/resize/bin/resize2fs"
sefcontext_compile_path="${RUNFILES_DIR}/__main__/external/selinux/libselinux/bin/sefcontext_compile"
debugfs_path="${RUNFILES_DIR}/__main__/external/e2fsprogs/debugfs/bin/debugfs"
soong_zip_path="${RUNFILES_DIR}/__main__/prebuilts/build-tools/linux-x86/bin"
aapt2_path="${RUNFILES_DIR}/__main__/frameworks/base/tools/aapt2/bin/aapt2"
android_jar="${RUNFILES_DIR}/__main__/prebuilts/sdk/current/public/android.jar"
fsckerofs_path="$(readlink -f ${RUNFILES_DIR}/__main__/external/erofs-utils/bin/fsck.erofs/fsck.erofs)"

input_dir=$(mktemp -d)
output_dir=$(mktemp -d)

function cleanup {
  rm -rf ${input_dir}
  rm -rf ${output_dir}
}

trap cleanup ERR
#############################################
# prepare the inputs
#############################################
# Create the input directory with
# 1. a file with random bits
# 2. a file installed sub dir with random bits
# 3. a one-level symlink
# 4. a two-level symlink with "execroot/__main__" in the path
# 5. a two-level sumlink without "execroot/__main__" in the path
# 6. a three-level symlink with "execroot/__main__" in the path
echo "test file1" > "${input_dir}/file1"
chmod 755 "${input_dir}/file1"
echo "test file2" > "${input_dir}/file2"
chmod 755 "${input_dir}/file2"
mkdir -p "${input_dir}/execroot/__main__"
ln -s "${input_dir}/file1" "${input_dir}/one_level_sym"
ln -s "${input_dir}/file2" "${input_dir}/execroot/__main__/middle_sym"
ln -s "${input_dir}/execroot/__main__/middle_sym" "${input_dir}/two_level_sym_in_execroot"
ln -s "${input_dir}/one_level_sym" "${input_dir}/two_level_sym_not_in_execroot"
ln -s "${input_dir}/two_level_sym_in_execroot" "${input_dir}/three_level_sym_in_execroot"

# Create the APEX manifest file
manifest_dir=$(mktemp -d)
manifest_file="${manifest_dir}/apex_manifest.pb"
echo '{"name": "com.android.example.apex", "version": 1}' > "${manifest_dir}/apex_manifest.json"
"${conv_apex_manifest_tool_path}" proto "${manifest_dir}/apex_manifest.json" -o ${manifest_file}

# Create the file_contexts file
file_contexts_file=$(mktemp)
echo '
(/.*)?           u:object_r:root_file:s0
/execroot(/.*)?       u:object_r:execroot_file:s0
' > ${file_contexts_file}

output_file="${output_dir}/test.apex"

staging_dir=$(mktemp -d /tmp/temporary-dir.XXXXXXXX)
trap 'rm -rf -- "${staging_dir}"' EXIT

# Create the wrapper manifest file
staging_dir_builder_options_file=$(mktemp)
echo "{
  \"file_mapping\": {
    \"dir1/file1\": \"${input_dir}/file1\",
    \"dir2/dir3/file2\": \"${input_dir}/file2\",
    \"dir4/one_level_sym\": \"${input_dir}/one_level_sym\",
    \"dir5/two_level_sym_in_execroot\": \"${input_dir}/two_level_sym_in_execroot\",
    \"dir6/two_level_sym_not_in_execroot\": \"${input_dir}/two_level_sym_not_in_execroot\",
    \"dir7/three_level_sym_in_execroot\": \"${input_dir}/three_level_sym_in_execroot\"
  },
  \"staging_dir_path\": \"${staging_dir}\"
}" > ${staging_dir_builder_options_file}

canned_fs_config=$(mktemp)
echo "/ 0 2000 0755
/apex_manifest.json 1000 1000 0644
/apex_manifest.pb 1000 1000 0644
/dir1 0 2000 0755
/dir1/file1 1000 1000 0644
/dir2 0 2000 0755
/dir2/dir3 0 2000 0755
/dir2/dir3/file2 1000 1000 0644
/dir4 0 2000 0755
/dir4/one_level_sym 1000 1000 0644
/dir5 0 2000 0755
/dir5/two_level_sym_in_execroot 1000 1000 0644
/dir6 0 2000 0755
/dir6/two_level_sym_not_in_execroot 1000 1000 0644
/dir7 0 2000 0755
/dir7/three_level_sym_in_execroot 1000 1000 0644" > ${canned_fs_config}

apexer_tool_paths=${avb_tool_path}:${avb_tool_path}:${e2fsdroid_path}:${mke2fs_path}:${resize2fs_path}:${debugfs_path}:${soong_zip_path}:${aapt2_path}:${sefcontext_compile_path}

#############################################
# run staging_dir_builder
#############################################
"${RUNFILES_DIR}/__main__/build/bazel/rules/staging_dir_builder" \
  ${staging_dir_builder_options_file} \
  ${apexer_tool_path} \
  --manifest ${manifest_file} \
  --file_contexts ${file_contexts_file} \
  --key "${RUNFILES_DIR}/__main__/build/bazel/rules/test.pem" \
  --apexer_tool_path "${apexer_tool_paths}" \
  --android_jar_path ${android_jar} \
  --canned_fs_config ${canned_fs_config} \
  ${staging_dir} \
  ${output_file}

#############################################
# check the result
#############################################
"${deapexer_tool_path}" --debugfs_path="${debugfs_path}/debugfs" --fsckerofs_path="${fsckerofs_path}" extract ${output_file} ${output_dir}

# The expected mounted tree should be something like this:
# /tmp/tmp.9u7ViPlMr7
# ├── apex_manifest.pb
# ├── apex_payload.img
# ├── mnt
# │   ├── apex_manifest.pb
# │   ├── dir1
# │   │   └── file1
# │   ├── dir2
# │   │   └── dir3
# │   │       └── file2
# │   ├── dir4
# │   │   └── one_level_sym
#             (one level symlinks always resolve)
# │   ├── dir5
# │   │   └── two_level_sym_in_execroot
#             (two level symlink resolve if the path contains execroot/__main__)
# │   ├── dir6
# │   │   └── two_level_sym_not_in_execroot -> /tmp/tmp.evJh21oYGG/file1
#             (two level symlink resolve only one level otherwise)
# │   ├── dir7
# │   │   └── three_level_sym_in_execroot
#             (three level symlink resolve if the path contains execroot/__main__)
# └── test.apex

# b/215129834:
# https://android-review.googlesource.com/c/platform/system/apex/+/1944264 made
# it such that the hash of non-payload files in the APEX (like
# AndroidManifest.xml) will be included as part of the apex_manifest.pb via the
# apexContainerFilesHash string to ensure that changes to AndroidManifest.xml
# results in changes in content hash for the apex_payload.img. Since this is
# potentially fragile, we skip diffing the apex_manifest.pb, and just check that
# it exists.
test -f "${output_dir}/apex_manifest.pb" || echo "expected apex_manifest.pb to exist"

# check the contents with diff for the rest of the files
diff ${input_dir}/file1 ${output_dir}/dir1/file1
diff ${input_dir}/file2 ${output_dir}/dir2/dir3/file2
diff ${input_dir}/file1 ${output_dir}/dir4/one_level_sym
diff ${input_dir}/file2 ${output_dir}/dir5/two_level_sym_in_execroot
[ `readlink ${output_dir}/dir6/two_level_sym_not_in_execroot` = "${input_dir}/file1" ]
diff ${input_dir}/file2 ${output_dir}/dir7/three_level_sym_in_execroot

input_perms="$(stat -c '%A' ${input_dir}/file1)"
output_perms="$(stat -c '%A' ${staging_dir}/dir1/file1)"
if [ ${input_perms} != ${output_perms} ]; then
  echo "File permissions not matched!"
  exit 1
fi

input_perms="$(stat -c '%A' ${input_dir}/file2)"
output_perms="$(stat -c '%A' ${staging_dir}/dir5/two_level_sym_in_execroot)"
if [ ${input_perms} != ${output_perms} ]; then
  echo "File permissions not matched!"
  exit 1
fi


cleanup

echo "Passed for all test cases"
