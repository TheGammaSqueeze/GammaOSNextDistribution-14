# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

"""kt_asserts"""

load("//:visibility.bzl", "RULES_KOTLIN")
load("@bazel_skylib//lib:unittest.bzl", "asserts")

visibility(RULES_KOTLIN)

def _equals(a, b):
    return a == b

def _list_matching(left, right, matcher = None):
    """Find the overlap between two lists.

    Args:
        left: [list[A]]
        right: [list[B]]
        matcher: [function(A,B):bool] A matcher on the two list types

    Returns:
        [(list[A], list[(A, B)], list[B])] The left-only, matching-pair, and right-only lists
    """

    matcher = matcher or _equals

    left_only = []
    matches = []
    right_only = list(right)

    def _process_left_ele(left_ele):
        for index, right_ele in enumerate(right_only):
            if matcher(left_ele, right_ele):
                right_only.pop(index)
                matches.append((left_ele, right_ele))
                return

        left_only.append(left_ele)

    for left_ele in left:
        _process_left_ele(left_ele)

    return (left_only, matches, right_only)

def _assert_list_matches(env, expected, actual, matcher = None, items_name = "items"):
    """Assert two lists have an exact matching.

    Args:
        env: [unittest.env]
        expected: [list[A]]
        actual: [list[B]]
        matcher: [function(A,B):bool]
        items_name: [string] The plural noun describing the list items in an error message

    Returns:
        [None] Fails if assertion violated
    """

    extra_expected, _, extra_actual = _list_matching(expected, actual, matcher = matcher)
    asserts.true(
        env,
        len(extra_actual) == 0 and len(extra_expected) == 0,
        "Unmatched expected {name} {expected}\nUnmatched actual {name} {actual}".format(
            name = items_name,
            expected = extra_expected,
            actual = extra_actual,
        ),
    )

def _assert_required_mnemonic_counts(env, required_mnemonic_counts, actual_actions):
    """Assert that some set of menemonics is present/absent within a set of Actions.

    Args:
        env: [unittest.env]
        required_mnemonic_counts: [dict[string,string]] The menemonics to check -> expected count
        actual_actions: [list[Action]]

    Returns:
        [None] Fails if assertion violated
    """

    considered_actual_mnemonics = [
        x.mnemonic
        for x in actual_actions
        # Ignore any mnemonics not mentioned by the user
        if (x.mnemonic in required_mnemonic_counts)
    ]

    required_mnemonics = []
    for m, c in required_mnemonic_counts.items():
        for _ in range(0, int(c)):
            required_mnemonics.append(m)

    _assert_list_matches(
        env,
        required_mnemonics,
        considered_actual_mnemonics,
        items_name = "mnemonics",
    )

kt_asserts = struct(
    list_matches = _assert_list_matches,
    required_mnemonic_counts = _assert_required_mnemonic_counts,
)
