#!/bin/bash
# Updater script for bazel prebuilt binaries in AOSP.
#
# This script handles both linux and darwin binaries in a single invocation. See
# README.md for more details.
#
# Usage: update.sh <commit>

set -euox pipefail

function err() {
    >&2 echo "$@"
    exit 1
}

# Check that the necessary tools are installed.
function check_prereqs() {
    if ! [[ "${PWD}" =~ .*/prebuilts/bazel/common$ ]]; then
        err "Error: Run this script from within the prebuilts/bazel/common directory."
    fi

    for cmd in jq curl; do
        if ! command -v "${cmd}" &> /dev/null; then
            err "Error: This script requires ${cmd}. Install it and ensure it is on your PATH."
        fi
    done
}

check_prereqs
commit="$1"; shift
bazel_src_dir="$1"; shift

ci_url="https://storage.googleapis.com/bazel-builds/metadata/${commit}.json"
platforms_json="$(curl -s "${ci_url}" | jq '{ platforms: .platforms }')"
if [[ $? != 0 ]]; then
    err "Failed to download or parse ${ci_url}. Exiting."
fi

darwin_nojdk_url="$(echo "${platforms_json}" | jq --raw-output '.[].macos.nojdk_url')"
darwin_nojdk_sha256="$(echo "${platforms_json}" | jq --raw-output '.[].macos.nojdk_sha256')"

linux_nojdk_url="$(echo "${platforms_json}" | jq --raw-output '.[].linux.nojdk_url')"
linux_nojdk_sha256="$(echo "${platforms_json}" | jq --raw-output '.[].linux.nojdk_sha256')"

function check_sha256() {
  local sha256=$1; shift
  local downloaded_file=$1; shift
    echo "Verifying checksum for ${downloaded_file} to be ${sha256}.."
    if ! echo "${sha256} ${downloaded_file}" | sha256sum --check --status ; then
      echo "ERROR: checksum of ${downloaded_file} did not match!"
      echo "${sha256} ${downloaded_file}" | sha256sum --check --status
      echo "${sha256} ${downloaded_file}" | sha256sum --check
      exit 1
    fi
}

function download_and_verify() {
    local os=$1; shift
    local url=$1; shift
    local sha256=$1; shift

    echo "Cleaning previous ${os} bazel binary.."
    rm -f bazel_nojdk-*-${os}-x86_64

    echo "Downloading ${os} bazel binary for ${commit}.."
    downloaded_file="bazel_nojdk-${commit}-${os}-x86_64"
    curl "${url}" --output "${downloaded_file}"

    check_sha256 "${sha256}" "${downloaded_file}"

    echo "Setting up bazel symlink for ${os}.."
    rm -f bazel && ln -s "${downloaded_file}" bazel
    chmod +x "${downloaded_file}"
}

common_bazel_dir=$(pwd)
# Update Linux binary.
cd ../linux-x86_64
linux_bazel_dir=$(pwd)
download_and_verify "linux" "${linux_nojdk_url}" "${linux_nojdk_sha256}"
linux_downloaded_file=${downloaded_file}
./${downloaded_file} license > LICENSE
cp LICENSE ../common/
# Update macOS binary.
cp LICENSE "../darwin-x86_64/"
cd "../darwin-x86_64"
download_and_verify "darwin" "${darwin_nojdk_url}" "${darwin_nojdk_sha256}"
${common_bazel_dir}/update_tools.sh "$linux_bazel_dir/$linux_downloaded_file" "$common_bazel_dir" "$linux_bazel_dir" "$bazel_src_dir"


echo "Done. This script may have affected all of prebuilts/bazel/common, prebuilts/bazel/linux-x86_64 and prebuilts/bazel/darwin-x86_64. Be sure to upload changes for all affected git repositories."
