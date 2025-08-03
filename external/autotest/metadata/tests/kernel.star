
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'kernel/AsyncDriverProbe',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.kernel_AsyncDriverProbe.kernel_AsyncDriverProbe',
        ),
        test_common.define_test(
            'kernel/CrosECSysfsAccel',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.kernel_CrosECSysfsAccel.kernel_CrosECSysfsAccel',
        ),
        test_common.define_test(
            'kernel/Delay',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.kernel_Delay.kernel_Delay',
        ),
        test_common.define_test(
            'kernel/ExternalUsbPeripheralsDetectionTest',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'kernel/FirmwareRequest',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.kernel_FirmwareRequest.kernel_FirmwareRequest',
        ),
        test_common.define_test(
            'kernel/IdlePerf',
            suites = ['crosbolt_perf_weekly'],
            main_package = '',
        ),
        test_common.define_test(
            'kernel/Ktime',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.kernel_Ktime.kernel_Ktime',
        ),
        test_common.define_test(
            'kernel/Lmbench',
            suites = ['kernel_per-build_benchmarks'],
            main_package = 'autotest_lib.client.site_tests.kernel_Lmbench.kernel_Lmbench',
        ),
        test_common.define_test(
            'kernel/MemoryRamoop',
            suites = ['experimental'],
            main_package = '',
        ),
        test_common.define_test(
            'kernel/SchedBandwith',
            suites = ['hwqual'],
            main_package = 'autotest_lib.client.site_tests.kernel_SchedBandwith.kernel_SchedBandwith',
        ),
        test_common.define_test(
            'kernel/TPMStress',
            suites = ['kernel_daily_regression', 'stress'],
            main_package = 'autotest_lib.client.site_tests.kernel_TPMStress.kernel_TPMStress',
        )
    ]
