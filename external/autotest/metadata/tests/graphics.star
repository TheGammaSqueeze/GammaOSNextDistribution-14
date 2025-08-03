
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'graphics/GLAPICheck',
            suites = ['bvt-perbuild', 'graphics', 'graphics_per-day', 'graphics_system', 'hwqual'],
            main_package = 'autotest_lib.client.site_tests.graphics_GLAPICheck.graphics_GLAPICheck',
        ),
        test_common.define_test(
            'graphics/GLBench',
            suites = ['graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_GLBench.graphics_GLBench',
        ),
        test_common.define_test(
            'graphics/GLMark2',
            suites = ['graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_GLMark2.graphics_GLMark2',
        ),
        test_common.define_test(
            'graphics/Gbm',
            suites = ['bvt-perbuild', 'graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_Gbm.graphics_Gbm',
        ),
        test_common.define_test(
            'graphics/Idle',
            suites = ['bvt-perbuild', 'graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_Idle.graphics_Idle',
        ),
        test_common.define_test(
            'graphics/KernelConfig',
            suites = ['graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_KernelConfig.graphics_KernelConfig',
        ),
        test_common.define_test(
            'graphics/KernelMemory',
            suites = ['bvt-perbuild', 'graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_KernelMemory.graphics_KernelMemory',
        ),
        test_common.define_test(
            'graphics/LibDRM',
            suites = ['bvt-perbuild', 'graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_LibDRM.graphics_LibDRM',
        ),
        test_common.define_test(
            'graphics/PerfControl',
            suites = ['bvt-perbuild', 'graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_PerfControl.graphics_PerfControl',
        ),
        test_common.define_test(
            'graphics/PowerConsumption',
            suites = ['bvt-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'graphics/SanAngeles',
            suites = ['graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_SanAngeles.graphics_SanAngeles',
        ),
        test_common.define_test(
            'graphics/Sanity',
            suites = ['graphics', 'graphics_per-day', 'graphics_system'],
            main_package = 'autotest_lib.client.site_tests.graphics_Check.graphics_Check',
        ),
        test_common.define_test(
            'graphics/VTSwitch',
            suites = ['bvt-perbuild', 'graphics_per-day'],
            main_package = 'autotest_lib.client.site_tests.graphics_VTSwitch.graphics_VTSwitch',
        ),
        test_common.define_test(
            'graphics/WebGLAquarium',
            suites = ['bvt-perbuild', 'crosbolt_perf_perbuild', 'graphics', 'graphics_browser', 'graphics_per-day', 'partners'],
            main_package = 'autotest_lib.client.site_tests.graphics_WebGLAquarium.graphics_WebGLAquarium',
        ),
        test_common.define_test(
            'graphics/WebGLManyPlanetsDeep',
            suites = ['graphics', 'graphics_browser', 'graphics_per-day', 'partners'],
            main_package = 'autotest_lib.client.site_tests.graphics_WebGLManyPlanetsDeep.graphics_WebGLManyPlanetsDeep',
        )
    ]
