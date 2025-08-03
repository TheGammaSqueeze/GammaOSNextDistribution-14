#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Copyright 2020 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Emulate test setup that fails (i.e. failed flash to device)"""


import sys


def Main():
    return 1  ## False, flashing failure


if __name__ == "__main__":
    retval = Main()
    sys.exit(retval)
