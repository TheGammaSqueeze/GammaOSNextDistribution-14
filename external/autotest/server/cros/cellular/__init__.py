# -*- coding: utf-8 -*-
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Consistent path includes that work across local-dev and portage package"""

import sys
import os

this_dir = os.path.dirname(__file__)
sys.path.insert(0, this_dir)
# Portage path
sys.path.insert(0, os.path.join(this_dir, '../../../config/python/'))
# Local path
sys.path.insert(0, os.path.join(this_dir, '../../../../../../config/python/'))
