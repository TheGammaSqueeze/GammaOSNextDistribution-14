#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Tests for benchmark_pgo_profiles."""

import io
import json
import unittest

import benchmark_pgo_profiles


class Test(unittest.TestCase):
    """Tests for benchmark_pgo_profiles."""

    def test_run_data_parsing_succeeds(self):
        run_data = benchmark_pgo_profiles.RunData.from_json(
            "foo",
            io.StringIO(
                json.dumps(
                    {
                        "results": [
                            {
                                "user": 1.2,
                                "system": 1.3,
                            },
                        ],
                    }
                )
            ),
        )

        self.assertEqual(
            run_data,
            benchmark_pgo_profiles.RunData(
                tag="foo",
                user_time=1.2,
                system_time=1.3,
            ),
        )

    def test_special_profile_parsing_succeeds(self):
        for profile in benchmark_pgo_profiles.SpecialProfile:
            self.assertIs(
                profile, benchmark_pgo_profiles.parse_profile_path(str(profile))
            )


if __name__ == "__main__":
    unittest.main()
