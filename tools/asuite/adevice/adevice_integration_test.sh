#!/usr/bin/env bash

# Copyright (C) 2023 The Android Open Source Project
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

set -e
shopt -s extglob

# TODO(rbraunstein): This probably drops the line number we fail at.
# Find a better way.
fail_with_message() {
  echo "$1"
  exit 1
}

# NOTE: if we want to discern stdout from stderr see: https://stackoverflow.com/questions/962255/how-to-store-standard-error-
# TODO(rbraunstein): Do matrix testing for failures
# similar to: frameworks/native/libs/binder/tests/parcel_fuzzer/test_fuzzer/run_fuzz_service_test.sh

assert_fails_with_output() {
  local command="$1"
  local expected_output="$2"
  if OUTPUT="$($command 2>&1)"
  then
    fail_with_message "COMMAND should have failed"
  else
    echo "$OUTPUT" | grep -q -F "$expected_output" ||
    (echo "actual output doesn't match expectation:\nactual [$OUTPUT]" && exit 1)
  fi
}

assert_ok_with_output() {
  local command="$1"
  local expected_output="$2"
  if OUTPUT="$($command 2>&1)"
  then
    echo "$OUTPUT" | grep -q -F "$expected_output" ||
    (echo "actual output doesn't match expectation:\nactual [$OUTPUT]" && exit 1)
  else
    fail_with_message "COMMAND should have passed, not exit with exit code: $?"
  fi
}

# test bad option
assert_fails_with_output \
  "./adevice --should-fail" \
  "unexpected argument '--should-fail'"


# test help
assert_ok_with_output \
  "./adevice --help" \
  "Usage: adevice [OPTIONS] <COMMAND>" \

# test bare command is help
# no subcommand is like --help, but exits non-zero
assert_fails_with_output \
  "./adevice" \
  "Usage: adevice [OPTIONS] <COMMAND>" \


# test help with PRODUCT_OUTPUT
(export ANDROID_PRODUCT_OUT=something
 assert_ok_with_output \
  "./adevice --help" \
  "Usage: adevice [OPTIONS] <COMMAND>")

# Test --help without PRODUCT_OUTPUT set
# TODO(rbraunstein): matrix test across env variables, don't replicate ugly test code.
(export ANDROID_PRODUCT_OUT=
assert_ok_with_output \
  "./adevice --help" \
  "Usage: adevice [OPTIONS] <COMMAND>")


# TODO(rbraunstein): Add more tests, like passing a needed subcommand.
# TODO(rbraunstein): Find a framework so each test case reports pass.
