
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'telemetry/AFDOGenerate',
            suites = ['AFDO_record'],
            main_package = '',
        ),
        test_common.define_test(
            'telemetry/AFDOGenerateClient',
            suites = ['AFDO_page_replay'],
            main_package = 'autotest_lib.client.site_tests.telemetry_AFDOGenerateClient.telemetry_AFDOGenerateClient',
        ),
        test_common.define_test(
            'telemetry/Crosperf',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'telemetry/Sanity',
            suites = ['bvt-perbuild', 'smoke'],
            main_package = 'autotest_lib.client.site_tests.telemetry_Check.telemetry_Check',
        ),
        test_common.define_test(
            'telemetry/ScrollingActionTests',
            suites = [],
            main_package = '',
        )
    ]
