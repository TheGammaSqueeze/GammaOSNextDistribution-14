#!/bin/bash -e
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# Obtain the most recent proto descriptors from chromiumos/infra/proto protos.
# This is needed to work with these protos from *.star code.

set -eu

CROS_CONFIG_REPO="https://chromium.googlesource.com/chromiumos/config"

CIPD_PROTOC_VERSION='v3.6.1'
CIPD_PROTOC_GEN_GO_VERSION='v1.3.2'

readonly script_dir="$(dirname "$(realpath -e "${BASH_SOURCE[0]}")")"
readonly target="${script_dir}/proto/descpb.bin"

readonly work_dir=$(mktemp --tmpdir -d genprotodescXXXXXX)
trap "rm -rf ${work_dir}" EXIT
echo "Using temporary directory ${work_dir}"

if [[ -n ${CHROMIUMOS_CONFIG_DIR+x} ]]; then
  echo "CHROMIUMOS_CONFIG_DIR is set: " \
    "Copying sources from ${CHROMIUMOS_CONFIG_DIR}/"
  cp -r "${CHROMIUMOS_CONFIG_DIR}/" "${work_dir}/config"
else
  echo "Creating a shallow clone of ${CROS_CONFIG_REPO}"
  git clone -q --depth=1 --shallow-submodules "${CROS_CONFIG_REPO}" \
    "${work_dir}/config"
fi
readonly cros_config_subdir="config/proto"

echo "Grabbing protoc from CIPD"
cipd_root=.cipd_bin
cipd ensure \
  -log-level warning \
  -root "${cipd_root}" \
  -ensure-file - \
  <<ENSURE_FILE
infra/tools/protoc/\${platform} protobuf_version:${CIPD_PROTOC_VERSION}
chromiumos/infra/tools/protoc-gen-go version:${CIPD_PROTOC_GEN_GO_VERSION}
ENSURE_FILE

# Need full path here for cipd as there is a cd to a different directory.
export PATH="${script_dir}/${cipd_root}:${PATH}"

echo "Generating protobuf descriptors"
readonly all_protos=$(
  cd "${work_dir}" &&
  find "${cros_config_subdir}" -name "*.proto" | sort
)
(
  cd "${work_dir}" &&
  export LC_ALL=C  # for stable sorting order
  protoc -I"${cros_config_subdir}" \
    --descriptor_set_out=descpb.bin ${all_protos}
)

echo "Copying generated descriptors"
cp "${work_dir}/descpb.bin" "${target}"
