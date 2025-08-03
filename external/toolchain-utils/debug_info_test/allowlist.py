# -*- coding: utf-8 -*-
# Copyright 2018 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Allowlist functions."""


import glob
import os
import re


# Matching a string of length m in an NFA of size n is O(mn^2), but the
# performance also depends largely on the implementation. It appears to be fast
# enough according to the tests.
#
# The performance bottleneck of this script is readelf. Unless this becomes
# slower than readelf, don't waste time here.
def is_allowlisted(list_name, pattern):
    """Check whether the given pattern is specified in the allowlist.

    Args:
      list_name: name of the allowlist.
      pattern: the target string.

    Returns:
      True if matched otherwise False.
    """
    return pattern and allowlists[list_name].match(pattern)


def prepare_allowlist(patterns):
    """Join and compile the re patterns.

    Args:
      patterns: regex patterns.

    Returns:
      A compiled re object.
    """
    return re.compile("|".join(patterns))


def load_allowlists(dirname):
    """Load allowlists under dirname.

    An allowlist ends with .allowlist.

    Args:
      dirname: path to the dir.

    Returns:
      A dictionary of 'filename' -> allowlist matcher.
    """
    wlist = {}
    for fn in glob.glob(os.path.join(dirname, "*.allowlist")):
        key = os.path.splitext(os.path.basename(fn))[0]
        with open(fn, "r", encoding="utf-8") as f:
            patterns = f.read().splitlines()
            patterns = [l for l in patterns if l != ""]
            patterns = [l for l in patterns if l[0] != "#"]
        wlist[key] = prepare_allowlist(patterns)
    return wlist


allowlists = load_allowlists(os.path.dirname(__file__))
