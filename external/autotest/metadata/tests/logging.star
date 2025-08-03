
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'logging/AsanCrash',
            suites = ['smoke'],
            main_package = 'autotest_lib.client.site_tests.logging_AsanCrash.logging_AsanCrash',
        ),
        test_common.define_test(
            'logging/CrashSender',
            suites = ['bvt-inline', 'partners', 'smoke'],
            main_package = 'autotest_lib.client.site_tests.logging_CrashSender.logging_CrashSender',
        ),
        test_common.define_test(
            'logging/CrashServices',
            suites = ['bvt-perbuild', 'jailed_build', 'partners'],
            main_package = 'autotest_lib.client.site_tests.logging_CrashServices.logging_CrashServices',
        ),
        test_common.define_test(
            'logging/FeedbackReport',
            suites = ['bvt-perbuild', 'usb_detect'],
            main_package = 'autotest_lib.client.site_tests.logging_FeedbackReport.logging_FeedbackReport',
        ),
        test_common.define_test(
            'logging/UserCrash',
            suites = ['bvt-cq', 'smoke'],
            main_package = 'autotest_lib.client.site_tests.logging_UserCrash.logging_UserCrash',
        )
    ]
