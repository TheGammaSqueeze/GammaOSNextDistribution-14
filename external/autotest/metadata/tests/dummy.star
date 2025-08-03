
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'dummy/Fail',
            suites = ['another_suite', 'dev_drone_image_test', 'dummy', 'push_to_prod', 'skylab_staging_test'],
            main_package = '',
        ),
        test_common.define_test(
            'dummy/FailServer',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'stub/Pass',
            suites = ['dev_drone_image_test', 'stub', 'stubclientretries', 'push_to_prod', 'skylab_staging_test', 'something_else'],
            main_package = 'autotest_lib.client.site_tests.stub_Pass.stub_Pass',
        ),
        test_common.define_test(
            'dummy/PassServer',
            suites = ['dummy_server'],
            main_package = '',
        ),
        test_common.define_test(
            'dummy/RepeatArgs',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'dummy/SynchronousOffload',
            suites = ['offloads'],
            main_package = 'autotest_lib.client.site_tests.dummy_SynchronousOffload.dummy_SynchronousOffload',
        ),
        test_common.define_test(
            'dummy/SynchronousOffloadServer',
            suites = ['offloads'],
            main_package = '',
        )
    ]
