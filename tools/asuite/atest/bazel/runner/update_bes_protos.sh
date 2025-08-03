#!/bin/bash
# Updater script for Bazel BES protos for BazelTest
#
# Usage: update_bes_protos.sh <commit>
#
# TODO(b/254334040): Move protos to prebuilts/bazel/common and update alongside
# bazel.

set -euo pipefail

COMMIT="$1"; shift

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)
DEST_DIR="${SCRIPT_DIR}/src/main/protobuf"

echo "Updating proto files..."
wget -P "${DEST_DIR}" https://raw.githubusercontent.com/bazelbuild/bazel/"${COMMIT}"/src/main/java/com/google/devtools/build/lib/buildeventstream/proto/build_event_stream.proto
wget -P "${DEST_DIR}" https://raw.githubusercontent.com/bazelbuild/bazel/"${COMMIT}"/src/main/protobuf/command_line.proto
wget -P "${DEST_DIR}" https://raw.githubusercontent.com/bazelbuild/bazel/"${COMMIT}"/src/main/protobuf/failure_details.proto
wget -P "${DEST_DIR}" https://raw.githubusercontent.com/bazelbuild/bazel/"${COMMIT}"/src/main/protobuf/invocation_policy.proto
wget -P "${DEST_DIR}" https://raw.githubusercontent.com/bazelbuild/bazel/"${COMMIT}"/src/main/protobuf/option_filters.proto
echo "Done!"
