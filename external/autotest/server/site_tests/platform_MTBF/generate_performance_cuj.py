# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
USAGE: python generate_performance_cuj.py

Generates all the control files required to run the Tast-based performance
critical user journey (CUJ) test cases.

Tests are executed in a predefined order. Each test can be repeated number of
times according to test generation argument.
"""
import os

VERSION = 1

HOUR_IN_SECS = 60 * 60
DEFAULT_TEST_DURATION = 1 * HOUR_IN_SECS

SUITE = 'performance_cuj'
TEMPLATE_FILE = 'template.control.performance_cuj'
TEST_PREFIX = 'ui.'

# Following tests will be included in the generated test suite.
TESTS = [{
        'test': 'Tab Switch Basic Noproxy',
        'tast_name': 'TabSwitchCUJ2.basic_noproxy',
        'repeats': 3
}, {
        'test': 'Tab Switch Plus Noproxy',
        'tast_name': 'TabSwitchCUJ2.plus_noproxy',
        'repeats': 3
}, {
        'test': 'Tab Switch Premium Noproxy',
        'tast_name': 'TabSwitchCUJ2.premium_noproxy',
        'repeats': 3
}, {
        'test': 'Google Meet Basic 2',
        'tast_name': 'GoogleMeetCUJ.basic_two',
        'repeats': 3
}, {
        'test': 'Google Meet Basic Small',
        'tast_name': 'GoogleMeetCUJ.basic_small',
        'repeats': 3
}, {
        'test': 'Google Meet Basic Large',
        'tast_name': 'GoogleMeetCUJ.basic_large',
        'repeats': 3
}, {
        'test': 'Google Meet Basic Class',
        'tast_name': 'GoogleMeetCUJ.basic_class',
        'repeats': 3
}, {
        'test': 'Google Meet Plus Large',
        'tast_name': 'GoogleMeetCUJ.plus_large',
        'repeats': 3
}, {
        'test': 'Google Meet Plus Class',
        'tast_name': 'GoogleMeetCUJ.plus_class',
        'repeats': 3
}, {
        'test': 'Google Meet Premium Large',
        'tast_name': 'GoogleMeetCUJ.premium_large',
        'repeats': 3
}, {
        'test': 'Quick Check Basic Unlock',
        'tast_name': 'QuickCheckCUJ2.basic_unlock',
        'repeats': 3
}, {
        'test': 'Quick Check Basic Wakeup',
        'tast_name': 'QuickCheckCUJ2.basic_wakeup',
        'repeats': 3
}]


def _write_control_file(name, contents):
    f = open(name, 'w')
    f.write(contents)
    f.close()


def _read_template_file(filename):
    f = open(filename)
    d = f.read()
    f.close()
    return d


template = _read_template_file(
        os.path.join(os.path.dirname(os.path.realpath(__file__)),
                     TEMPLATE_FILE))

# Starting priority, will decrease for each test.
priority = 500

for test in TESTS:
    for i in range(int(test['repeats'])):
        test_name = (test['tast_name'] + '_{index:02n}').format(index=i + 1)
        control_file = template.format(
                name=test_name,
                priority=priority,
                duration=DEFAULT_TEST_DURATION,
                test_exprs=TEST_PREFIX + test['tast_name'],
                length='long',
                version=VERSION,
                attributes='suite:' + SUITE,
        )
        control_file_name = 'control.' + '_'.join([SUITE, test_name])
        _write_control_file(control_file_name, control_file)
        priority = priority - 1
