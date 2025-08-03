# Copyright 2013 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Define a type that wraps a Benchmark instance."""


import math
import statistics
from typing import Any

import numpy as np


# See crbug.com/673558 for how these are estimated.
_estimated_stddev = {
    "octane": 0.015,
    "kraken": 0.019,
    "speedometer": 0.007,
    "speedometer2": 0.006,
    "dromaeo.domcoreattr": 0.023,
    "dromaeo.domcoremodify": 0.011,
    "graphics_WebGLAquarium": 0.008,
    "page_cycler_v2.typical_25": 0.021,
    "loading.desktop": 0.021,  # Copied from page_cycler initially
}

# Numpy makes it hard to know the real type of some inputs
# and outputs, so this type alias is just for docs.
FloatLike = Any


def isf(x: FloatLike, mu=0.0, sigma=1.0, pitch=0.01) -> FloatLike:
    """Compute the inverse survival function for value x.

    In the abscence of using scipy.stats.norm's isf(), this function
    attempts to re-implement the inverse survival function by calculating
    the numerical inverse of the survival function, interpolating between
    table values. See bug b/284489250 for details.

    Survival function as defined by:
    https://en.wikipedia.org/wiki/Survival_function

    Examples:
        >>> -2.0e-16 < isf(0.5) <  2.0e-16
        True

    Args:
        x: float or numpy array-like to compute the ISF for.
        mu: Center of the underlying normal distribution.
        sigma: Spread of the underlying normal distribution.
        pitch: Absolute spacing between y-value interpolation points.

    Returns:
        float or numpy array-like representing the ISF of `x`.
    """
    norm = statistics.NormalDist(mu, sigma)
    # np.interp requires a monotonically increasing x table.
    # Because the survival table is monotonically decreasing, we have to
    # reverse the y_vals too.
    y_vals = np.flip(np.arange(-4.0, 4.0, pitch))
    survival_table = np.fromiter(
        (1.0 - norm.cdf(y) for y in y_vals), y_vals.dtype
    )
    return np.interp(x, survival_table, y_vals)


# Get #samples needed to guarantee a given confidence interval, assuming the
# samples follow normal distribution.
def _samples(b: str) -> int:
    # TODO: Make this an option
    # CI = (0.9, 0.02), i.e., 90% chance that |sample mean - true mean| < 2%.
    p = 0.9
    e = 0.02
    if b not in _estimated_stddev:
        return 1
    d = _estimated_stddev[b]
    # Get at least 2 samples so as to calculate standard deviation, which is
    # needed in T-test for p-value.
    n = int(math.ceil((isf((1 - p) / 2) * d / e) ** 2))
    return n if n > 1 else 2


class Benchmark(object):
    """Class representing a benchmark to be run.

    Contains details of the benchmark suite, arguments to pass to the suite,
    iterations to run the benchmark suite and so on. Note that the benchmark name
    can be different to the test suite name. For example, you may want to have
    two different benchmarks which run the same test_name with different
    arguments.
    """

    def __init__(
        self,
        name,
        test_name,
        test_args,
        iterations,
        rm_chroot_tmp,
        perf_args,
        suite="",
        show_all_results=False,
        retries=0,
        run_local=False,
        cwp_dso="",
        weight=0,
    ):
        self.name = name
        # For telemetry, this is the benchmark name.
        self.test_name = test_name
        # For telemetry, this is the data.
        self.test_args = test_args
        self.iterations = iterations if iterations > 0 else _samples(name)
        self.perf_args = perf_args
        self.rm_chroot_tmp = rm_chroot_tmp
        self.iteration_adjusted = False
        self.suite = suite
        self.show_all_results = show_all_results
        self.retries = retries
        if self.suite == "telemetry":
            self.show_all_results = True
        if run_local and self.suite != "telemetry_Crosperf":
            raise RuntimeError(
                "run_local is only supported by telemetry_Crosperf."
            )
        self.run_local = run_local
        self.cwp_dso = cwp_dso
        self.weight = weight
