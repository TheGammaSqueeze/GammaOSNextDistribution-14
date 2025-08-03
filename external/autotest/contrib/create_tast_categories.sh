#!/bin/bash

set -e

usage() {
    echo "
Usage: $0 [OPTION]

Generates control files for Tast categories.

For each Tast category, e.g. 'example', a file
'server/site_tests/tast/control.category-<category>' and
'test_suites/control.bvt-tast-cq-<category>' is generated, and attributes to add
to attribute_allowlist.txt are printed.

The control files follow templates that use test expressions based on test name,
e.g. name:example.*.

-f      Overwrite existing control files.
-h      Print this help message.
"
    exit 1
}

overwrite=false

while getopts "fh" o; do
    case "${o}" in
        f)
            overwrite=true ;;
        *)
            usage ;;
    esac
done

readonly script_dir="$(dirname "$(realpath -e "${BASH_SOURCE[0]}")")"
readonly repo_root=$(realpath "${script_dir}"/..)
readonly src_root="$(realpath "${script_dir}"/../../../..)"

categories=()
types=( "remote" "local" )
for type in "${types[@]}"; do
    mapfile -t categories < <(find \
    "${src_root}/platform/tast-tests/src/chromiumos/tast/${type}/bundles/cros" \
    -maxdepth 1 -mindepth 1 -type d -printf "%f\n")
done

mapfile -t categories < <(printf '%s\n' "${categories[@]}" | sort -u)

attributes=()

for c in "${categories[@]}"; do
    test_suites_file="${repo_root}/test_suites/control.bvt-tast-cq-${c}"
    if [[ -e ${test_suites_file}  && ${overwrite} == "false" ]]; then
        echo "File ${test_suites_file} already exists. Use -f to overwrite."
        exit 1
    fi
    cat << EOF > "${test_suites_file}"
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

AUTHOR = "ChromeOS Team"
NAME = "bvt-tast-cq-${c}"
PURPOSE = 'Tests the critical Tast tests in the "${c}" category.'

TIME = "SHORT"
TEST_CATEGORY = "General"
TEST_CLASS = "suite"
TEST_TYPE = "Server"

DOC = """
This suite verifies ChromeOS's basic functionality for the ChromeOS Commit
Queue by running all Tast integration tests in the "${c}" category that must
always pass against a DUT. See http://go/tast for more information about Tast.

The only Autotest test executed by this suite is tast.category-${c}, which
is a server test that executes the tast executable. The tast executable runs
individual Tast tests. If any of these Tast tests fail, then the
test.category-${c} test (and this suite) fail.
"""

import common
from autotest_lib.server.cros.dynamic_suite import dynamic_suite

args_dict['name'] = NAME
args_dict['max_runtime_mins'] = 30
args_dict['timeout_mins'] = 60
args_dict['job'] = job

dynamic_suite.reimage_and_run(**args_dict)
EOF

    tast_file="${repo_root}/server/site_tests/tast/control.category-${c}"
    if [[ -e ${tast_file}  && ${overwrite} == "false" ]]; then
        echo "File ${tast_file} already exists. Use -f to overwrite."
        exit 1
    fi
    cat << EOF > "${tast_file}"
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import utils

AUTHOR = 'Chromium OS team'
NAME = 'tast.category-${c}'
TIME = 'MEDIUM'
TEST_TYPE = 'Server'
DEPENDENCIES = 'servo_state:WORKING'
ATTRIBUTES = 'suite:bvt-tast-cq-${c}'
MAX_RESULT_SIZE_KB = 256 * 1024

# tast.py uses binaries installed from autotest_server_package.tar.bz2.
REQUIRE_SSP = True

DOC = '''
Run the critical Tast tests in the "${c}" category.

Tast is an integration-testing framework analagous to the test-running portion
of Autotest. See https://chromium.googlesource.com/chromiumos/platform/tast/ for
more information.

This test runs Tast tests in the "${c}" category that are required to pass
against a remote DUT. It fails if any individual Tast tests fail.

See http://go/tast-failures for information about investigating failures.
'''

args_dict = utils.args_to_dict(args)
assert 'servo_state:WORKING' in DEPENDENCIES
servo_args = hosts.CrosHost.get_servo_arguments(args_dict)

def run(machine):
    job.run_test('tast',
                 host=hosts.create_host(machine, servo_args=servo_args),
                 test_exprs=['('
                             '"group:mainline" && '
                             '!informational && '
                             '"name:${c}.*"'
                             ')'],
                 ignore_test_failures=False, max_run_sec=1800,
                 command_args=args,
                 clear_tpm=True)

parallel_simple(run, machines)
EOF

    attributes+=( "suite:bvt-tast-cq-${c}" )

done

echo "Add the following attributes to attribute_allowlist.txt:"
printf "%s\n" "${attributes[@]}"