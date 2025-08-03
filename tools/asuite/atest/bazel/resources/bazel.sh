#!/bin/bash
# Script to run Bazel in AOSP.
#
# This script sets up startup and environment variables to run Bazel with the
# AOSP JDK.
#
# Usage: bazel.sh [<startup options>] <command> [<args>]

set -eo pipefail

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &> /dev/null && pwd)

JDK_PATH="${SCRIPT_DIR}"/prebuilts/jdk/jdk17/linux-x86
BAZEL_BINARY="${SCRIPT_DIR}"/prebuilts/bazel/linux-x86_64/bazel

PROCESS_PATH="${JDK_PATH}"/bin:"${PATH}"

JAVA_HOME="${JDK_PATH}" \
PATH="${PROCESS_PATH}" \
  "${BAZEL_BINARY}" \
  --server_javabase="${JDK_PATH}" \
  "$@"
