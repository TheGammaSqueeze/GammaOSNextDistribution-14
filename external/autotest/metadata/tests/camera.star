
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'camera/HAL3Perf',
            suites = ['crosbolt_perf_nightly'],
            main_package = 'autotest_lib.client.site_tests.camera_HAL3Perf.camera_HAL3Perf',
        ),
        test_common.define_test(
            'camera/V4L2',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.camera_V4L2.camera_V4L2',
        )
    ]
