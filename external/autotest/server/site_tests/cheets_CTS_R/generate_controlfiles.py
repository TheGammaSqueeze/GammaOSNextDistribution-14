#!/usr/bin/env python3
# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# This is a trampoline script to invoke the actual generator script.

import os
import sys

target_script_name = 'generate_controlfiles_CTS_R.py'
target_script_path = os.path.abspath(
        os.path.join(os.path.dirname(__file__), '..', '..', 'cros', 'tradefed',
                     target_script_name))

# Note: Remove this once JDK9+ is the base JDK.
try:
    os.environ['JAVA_HOME'] = '/usr/lib64/jvm/jdk-9.0.4'
    os.environ['PATH'] = os.environ['JAVA_HOME']\
        + '/bin:' + os.environ['PATH']
except OSError:
    logging.error('Can\'t change current PATH directory')

os.execv(target_script_path, sys.argv)
