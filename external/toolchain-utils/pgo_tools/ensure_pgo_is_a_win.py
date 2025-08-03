#!/usr/bin/env python3
# Copyright 2023 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Reports how much performance win (in user time) PGO is for LLVM.

**This script is meant to be run from inside of the chroot.**

This is mostly intended to run regularly on Chrotomation, as it's just a super
thin wrapper around `benchmark_pgo_profiles.py`.
"""

import argparse
import logging
import sys
from typing import List

import benchmark_pgo_profiles
import pgo_tools


NO_PROFILE = benchmark_pgo_profiles.SpecialProfile.NONE
DEFAULT_PROFILE = benchmark_pgo_profiles.SpecialProfile.REMOTE


def calculate_pgo_speedup(
    no_profile: benchmark_pgo_profiles.RunData,
    default_profile: benchmark_pgo_profiles.RunData,
) -> float:
    """Returns the speedup attained by applying PGO.

    Returns:
        Percentage performance difference. If LLVM with PGO takes 100 seconds
        to run the benchmark, and LLVM without PGO takes 150, this will return
        1.5, since 150/100 == 1.5x speedup.
    """
    assert default_profile.user_time != 0, "pgo has a user time of 0?"
    return no_profile.user_time / default_profile.user_time


def main(argv: List[str]):
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.INFO,
    )

    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--minimum-speedup",
        type=float,
        help="""
        If the win of PGO is less than this, fail. Specified as an integer
        (--minimum-speedup=1.2 means speedup must be at least 1.2x).
        """,
    )
    opts = parser.parse_args(argv)
    minimum_speedup = opts.minimum_speedup

    pgo_tools.exit_if_not_in_chroot()

    run_results = benchmark_pgo_profiles.run_benchmark(
        # It's likely safe to assume that a fast LLVM without ThinLTO is fast
        # with ThinLTO.
        use_thinlto=False,
        profiles=[
            NO_PROFILE,
            DEFAULT_PROFILE,
        ],
    )
    assert (
        len(run_results) == 2
    ), f"Unexpected number of run results: {len(run_results)}"

    pgo_speedup = calculate_pgo_speedup(
        no_profile=run_results[0], default_profile=run_results[1]
    )
    logging.info("Speedup of PGO is %.2fx", pgo_speedup)
    if minimum_speedup is not None and minimum_speedup > pgo_speedup:
        sys.exit(
            f"Minimum speedup of {minimum_speedup} is greater than "
            f"observed speedup of {pgo_speedup}. Exiting with error."
        )


if __name__ == "__main__":
    main(sys.argv[1:])
