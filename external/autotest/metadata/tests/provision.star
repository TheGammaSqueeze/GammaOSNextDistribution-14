
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'provision/AutoUpdate',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'provision/CheetsUpdate',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'provision/Cr50TOT',
            suites = ['faft_cr50_tot'],
            main_package = '',
        ),
        test_common.define_test(
            'provision/Cr50Update',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'provision/FactoryImage',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'provision/FirmwareUpdate',
            suites = [],
            main_package = '',
        )
    ]
