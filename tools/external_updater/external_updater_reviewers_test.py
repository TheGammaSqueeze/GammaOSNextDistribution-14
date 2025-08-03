# Copyright (C) 2020 The Android Open Source Project
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
"""Unit tests for external updater reviewers."""

from typing import List, Mapping, Set
import unittest

import reviewers


class ExternalUpdaterReviewersTest(unittest.TestCase):
    """Unit tests for external updater reviewers."""

    def setUp(self):
        super().setUp()
        # save constants in reviewers
        self.saved_proj_reviewers = reviewers.PROJ_REVIEWERS
        self.saved_rust_reviewers = reviewers.RUST_REVIEWERS
        self.saved_rust_reviewer_list = reviewers.RUST_REVIEWER_LIST
        self.saved_rust_crate_owners = reviewers.RUST_CRATE_OWNERS

    def tearDown(self):
        super().tearDown()
        # restore constants in reviewers
        reviewers.PROJ_REVIEWERS = self.saved_proj_reviewers
        reviewers.RUST_REVIEWERS = self.saved_rust_reviewers
        reviewers.RUST_REVIEWER_LIST = self.saved_rust_reviewer_list
        reviewers.RUST_CRATE_OWNERS = self.saved_rust_crate_owners

    def _collect_reviewers(self, num_runs, proj_path):
        counters = {}
        for _ in range(num_runs):
            name = reviewers.find_reviewers(proj_path)
            if name in counters:
                counters[name] += 1
            else:
                counters[name] = 1
        return counters

    def test_reviewers_types(self):
        """Check the types of PROJ_REVIEWERS and RUST_REVIEWERS."""
        # Check type of PROJ_REVIEWERS
        self.assertIsInstance(reviewers.PROJ_REVIEWERS, Mapping)
        for key, value in reviewers.PROJ_REVIEWERS.items():
            self.assertIsInstance(key, str)
            # pylint: disable=isinstance-second-argument-not-valid-type
            # https://github.com/PyCQA/pylint/issues/3507
            if isinstance(value, (List, Set)):
                for x in value:
                    self.assertIsInstance(x, str)
            else:
                self.assertIsInstance(value, str)
        # Check element types of the reviewers list and map.
        self.assertIsInstance(reviewers.RUST_REVIEWERS, Mapping)
        for (name, quota) in reviewers.RUST_REVIEWERS.items():
            self.assertIsInstance(name, str)
            self.assertIsInstance(quota, int)

    def test_reviewers_constants(self):
        """Check the constants associated to the reviewers."""
        # There should be enough people in the reviewers pool.
        self.assertGreaterEqual(len(reviewers.RUST_REVIEWERS), 3)
        # Assume no project reviewers and recreate RUST_REVIEWER_LIST
        reviewers.PROJ_REVIEWERS = {}
        reviewers.RUST_REVIEWER_LIST = reviewers.create_rust_reviewer_list()
        sum_projects = sum(reviewers.RUST_REVIEWERS.values())
        self.assertEqual(sum_projects, len(reviewers.RUST_REVIEWER_LIST))

    def test_reviewers_randomness(self):
        """Check random selection of reviewers."""
        # This might fail when the random.choice function is extremely unfair.
        # With N * 20 tries, each reviewer should be picked at least twice.
        # Assume no project reviewers and recreate RUST_REVIEWER_LIST
        reviewers.PROJ_REVIEWERS = {}
        reviewers.RUST_REVIEWER_LIST = reviewers.create_rust_reviewer_list()
        num_tries = len(reviewers.RUST_REVIEWERS) * 20
        counters = self._collect_reviewers(num_tries, "rust/crates/libc")
        self.assertEqual(len(counters), len(reviewers.RUST_REVIEWERS))
        for n in counters.values():
            self.assertGreaterEqual(n, 5)
        self.assertEqual(sum(counters.values()), num_tries)

    def test_project_reviewers(self):
        """For specific projects, select only the specified reviewers."""
        reviewers.PROJ_REVIEWERS = {
            "rust/crates/p1": "x@g.com",
            "rust/crates/p_any": ["x@g.com", "y@g.com"],
            "rust/crates/p_all": {"z@g", "x@g.com", "y@g.com"},
        }
        counters = self._collect_reviewers(20, "external/rust/crates/p1")
        self.assertEqual(len(counters), 1)
        self.assertTrue(counters["r=x@g.com"], 20)
        counters = self._collect_reviewers(20, "external/rust/crates/p_any")
        self.assertEqual(len(counters), 2)
        self.assertGreater(counters["r=x@g.com"], 2)
        self.assertGreater(counters["r=y@g.com"], 2)
        self.assertTrue(counters["r=x@g.com"] + counters["r=y@g.com"], 20)
        counters = self._collect_reviewers(20, "external/rust/crates/p_all")
        # {x, y, z} reviewers should be sorted
        self.assertEqual(counters["r=x@g.com,r=y@g.com,r=z@g"], 20)

    def test_weighted_reviewers(self):
        """Test create_rust_reviewer_list."""
        reviewers.PROJ_REVIEWERS = {
            "any_p1": "x@g",  # 1 for x@g
            "any_p2": {"xyz", "x@g"},  # 1 for x@g, xyz is not a rust reviewer
            "any_p3": {"abc", "x@g"},  # 0.5 for "abc" and "x@g"
        }
        reviewers.RUST_REVIEWERS = {
            "x@g": 5,  # ceil(5 - 2.5) = 3
            "abc": 2,  # ceil(2 - 0.5) = 2
        }
        reviewer_list = reviewers.create_rust_reviewer_list()
        self.assertEqual(reviewer_list, ["x@g", "x@g", "x@g", "abc", "abc"])
        # Error case: if nobody has project quota, reset everyone to 1.
        reviewers.RUST_REVIEWERS = {
            "x@g": 1,  # ceil(1 - 2.5) = -1
            "abc": 0,  # ceil(0 - 0.5) = 0
        }
        reviewer_list = reviewers.create_rust_reviewer_list()
        self.assertEqual(reviewer_list, ["x@g", "abc"])  # everyone got 1


if __name__ == "__main__":
    unittest.main(verbosity=2)
