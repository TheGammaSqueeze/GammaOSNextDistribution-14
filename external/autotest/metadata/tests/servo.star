
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'servo/ConsoleStress',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'servo/LabControlVerification',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'servo/LabstationVerification',
            suites = ['labstation_verification'],
            main_package = '',
        ),
        test_common.define_test(
            'servo/LogGrab',
            suites = ['servo_lab'],
            main_package = '',
        ),
        test_common.define_test(
            'servo/USBInstall',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'servo/USBMuxVerification',
            suites = ['servo_lab'],
            main_package = '',
        ),
        test_common.define_test(
            'servo/Verification',
            suites = ['servo_verification'],
            main_package = '',
        )
    ]
