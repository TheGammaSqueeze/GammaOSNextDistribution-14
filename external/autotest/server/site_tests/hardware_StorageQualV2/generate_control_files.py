# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
USAGE: python generate_control_files.py

Generates all the control files required to run the Tast-based storage qual full test.
In particular, following test blocks are generated:
- 00_setup_benchmarks: initial test benchmarks
- 01-40_stress: 40 universal test blocks
- 99_teardown_benchmarks: final test benchmarks

Generated tests are executed by one of the four test suites, according to the required
test length: storage_qual_v2_xs, storage_qual_v2_s, storage_qual_v2_m, storage_qual_v2_l
and storage_qual_v2_xl for extra small, small, medium, large and extra-large test length.
The difference is number of universal test blocks: 2, 10, 20, 30 or 40 respectively.
"""
import os

STORAGE_QUAL_VERSION = 2
HOUR_IN_SECS = 60 * 60
SUITE = 'storage_qual_v2'
TEST_PREFIX = 'storage.FullQualificationStress.'
TEMPLATE_FILE = 'template.control.storage_qual'
MAX_DUTS = 4

TESTS = [{
        'test': 'setup',
        'tast_name': 'setup_benchmarks',
        'iterations': 1,
        'duration': 1 * HOUR_IN_SECS,
        'priority': 200,
        'length': 'lengthy'
}, {
        'test': 'stress_{index:02n}',
        'tast_name': 'stress',
        'iterations': 40,
        'duration': 8 * HOUR_IN_SECS,
        'priority': 100,
        'length': 'long'
}, {
        'test': 'teardown',
        'tast_name': 'teardown_benchmarks',
        'iterations': 1,
        'duration': 1 * HOUR_IN_SECS,
        'priority': 50,
        'length': 'lengthy'
}]


def _get_suite_attributes(iteration):
    attrs = ['suite:%s_xl' % SUITE]
    if iteration < 30:
        attrs += ['suite:%s_l' % SUITE]
    if iteration < 20:
        attrs += ['suite:%s_m' % SUITE]
    if iteration < 10:
        attrs += ['suite:%s_s' % SUITE]
    if iteration < 2:
        attrs += ['suite:%s_xs' % SUITE]
    return attrs


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

for test in TESTS:
    for i in range(int(test['iterations'])):
        for d in range(1, MAX_DUTS + 1):
            test_name = test['test'].format(index=i + 1)
            control_file = template.format(
                    name='_'.join([SUITE, test_name]),
                    priority=int(test['priority'] - i),
                    duration=int(test['duration']),
                    test_exprs=TEST_PREFIX + test['tast_name'],
                    length=test['length'],
                    version=STORAGE_QUAL_VERSION,
                    attributes=", ".join(_get_suite_attributes(i)),
                    dependency="dut:%d" % d,
            )
            control_file_name = 'control.' + '_'.join(
                    [SUITE, test_name, str(d)])
            _write_control_file(control_file_name, control_file)
