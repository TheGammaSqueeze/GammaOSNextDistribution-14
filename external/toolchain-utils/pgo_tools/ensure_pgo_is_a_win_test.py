#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tests for ensure_pgo_is_a_win."""

from typing import Tuple
import unittest

import benchmark_pgo_profiles
import ensure_pgo_is_a_win


def synthesize_run_data(
    no_profile_user_time: float, profile_user_time: float
) -> Tuple[benchmark_pgo_profiles.RunData, benchmark_pgo_profiles.RunData]:
    return (
        benchmark_pgo_profiles.RunData(
            tag=str(ensure_pgo_is_a_win.NO_PROFILE),
            user_time=no_profile_user_time,
            system_time=1,
        ),
        benchmark_pgo_profiles.RunData(
            tag=str(ensure_pgo_is_a_win.DEFAULT_PROFILE),
            user_time=profile_user_time,
            system_time=1,
        ),
    )


class Test(unittest.TestCase):
    """Tests for ensure_pgo_is_a_win."""

    def test_speedup_calculation_works(self):
        no_profile, profile = synthesize_run_data(
            no_profile_user_time=1, profile_user_time=1
        )
        self.assertEqual(
            ensure_pgo_is_a_win.calculate_pgo_speedup(no_profile, profile), 1
        )

        no_profile, profile = synthesize_run_data(
            no_profile_user_time=3, profile_user_time=2
        )
        self.assertEqual(
            ensure_pgo_is_a_win.calculate_pgo_speedup(no_profile, profile), 1.5
        )


if __name__ == "__main__":
    unittest.main()
