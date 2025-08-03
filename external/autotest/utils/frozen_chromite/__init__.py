# -*- coding: utf-8 -*-
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import os
import sys

# Add the third_party/ dir to our search path so that we can find the
# modules in there automatically.  This isn't normal, so don't replicate
# this pattern elsewhere.
_chromite_dir = os.path.normpath(os.path.dirname(os.path.realpath(__file__)))
_third_party_dir = os.path.join(_chromite_dir, 'third_party')
sys.path.insert(0, _third_party_dir)
sys.path.insert(
    # Allow Python 2 or 3 specific modules under a separate subpath.
    1, os.path.join(_third_party_dir, 'python%s' % sys.version_info.major))
