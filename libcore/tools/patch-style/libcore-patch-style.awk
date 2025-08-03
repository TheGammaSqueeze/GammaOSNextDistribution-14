# Copyright 2020 The Android Open Source Project
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

# Simple libcore patch-style checking based on http://go/libcore-patch-style.
#
# See sample-input.java for examples of matches and failures.
#
# Usage:
#  awk -f libcore-patch-style.awk <file0> [... fileN]
#
# This script ignores any files whose name does not end in the suffix ".java".
#
# To scan all source code in the libcore tree:
#  $ cd libcore
#  $ find . -type f | xargs awk -f tools/patch-style/libcore-patch-style.awk
#
# To find sources with the most issues:
#  $ cd libcore
#  $ find . -type f | xargs awk -f tools/patch-style/libcore-patch-style.awk \
#                   | grep -F ./ | sed -e 's/:.*//' | uniq -c | sort -n -r | head

BEGIN {
  g_errors = 0                    # Number of errors accumulated.
  g_expected_end = ""             # Expected END line.
  g_max_length = 100              # Maximum line length for markers (0 == no checking).
  g_stop_oneline_interleaving = 0 # Error one-line comments between BEGIN and END markers.
}

BEGINFILE {
  # Skip files whose names do not have a .java suffix.
  if (FILENAME !~ /\.java$/) {
    nextfile
  }

  # Reset the line number for reporting errors back to zero.
  NR = 0

  # Clear expected end marker as processing a new file.
  g_expected_end = ""
}

function error(message) {
  print(FILENAME ":" NR ":", message "\n")
  g_errors += 1;
}

function expectationError(reason, expected, actual) {
  error(reason "\n  Expected: \"" expected "\"\n  Actual:   \"" actual "\"")
}

function inputError(reason, actual) {
  error(reason "\n  Input:    \"" actual "\"")
}

function checkLineLength(line) {
  if (g_max_length > 0 && length(line) > g_max_length) {
    inputError("Line too long", line)
  }
}

function leftTrim(message) {
  return gensub(/^ */, "", 1, message)
}

function failIfEndExpected() {
  if (g_expected_end != "") {
    expectationError("Missing END marker.", g_expected_end, $0)
    g_expected_end = ""
  }
}

function expectEndFor(begin_line) {
  g_expected_end = begin_line
  sub("BEGIN", "END", g_expected_end)
}

function actualEndFor(actual_line) {
  if (actual_line != g_expected_end) {
    expectationError("Bad END marker.", g_expected_end, actual_line)
  }
  g_expected_end = ""
}

function processBeginMarker(line) {
  failIfEndExpected()
  expectEndFor(line)
}

function processEndMarker(line) {
  actualEndFor(line)
}

# BEGIN marker ending in a period.
/^ *\/\/ BEGIN Android-(added|changed|note|removed):.*\./ {
  checkLineLength($0)
  processBeginMarker($0)
  next
}

# BEGIN marker ending in a period.
/^ *\/\/ END Android-(added|changed|note|removed):.*\./ {
  checkLineLength($0)
  processEndMarker($0)
  next
}

# BEGIN marker ending in a bug reference.
/^ *\/\/ BEGIN Android-(added|changed|note|removed):.*[.](http:\/\/)?b\/[1-9][0-9]*/ {
  checkLineLength($0)
  processBeginMarker($0)
  next
}

# BEGIN marker ending in anything else, oops!
/^ *\/\/ BEGIN Android-(added|changed|note|removed)[^:].*/ {
  inputError("BEGIN marker is missing colon or description.", $0)
  next
}

# END marker, should be paired with last BEGIN marker.
/^ *\/\/ END Android-(added|changed|note|removed):.*/ {
  checkLineLength($0)
  processEndMarker($0)
  next
}

# One line change marker ending in a period.
/^ *\/\/ Android-(added|changed|note|removed):.*[.]/ {
  checkLineLength($0)
  if (g_stop_oneline_interleaving) {
    failIfEndExpected()
  }
  next
}

# One line change marker ending in a bug reference.
/^ *\/\/ Android-(added|changed|note|removed):[.](http:\/\/)?b\/[1-9][0-9]*/ {
  checkLineLength($0)
  if (g_stop_oneline_interleaving) {
    failIfEndExpected()
  }
  next
}

# One line change marker missing comment after colon.
/^ *\/\/ BEGIN Android-(added|changed|note|removed).*/ {
  inputError("Bad change marker: missing colon or description.", $0)
  next
}

# Something that looks like a potential change marker.
/^ *(\/\*|\/\/|\*) *(Android|ANDROID)-/ {
  if (g_stop_oneline_interleaving) {
    failIfEndExpected()
  }
  inputError("Bad change marker.", $0)
  next
}

END {
  failIfEndExpected()
  printf("Found " g_errors " libcore patch style issues.\n")
  exit g_errors == 0
}
