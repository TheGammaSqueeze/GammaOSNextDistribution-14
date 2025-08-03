
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'security/CpuVulnerabilities',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.security_CpuVulnerabilities.security_CpuVulnerabilities',
        ),
        test_common.define_test(
            'security/Libcontainer',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.security_Libcontainer.security_Libcontainer',
        ),
        test_common.define_test(
            'security/OpenSSLRegressions',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.security_OpenSSLRegressions.security_OpenSSLRegressions',
        ),
        test_common.define_test(
            'security/ProcessManagementPolicy',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.security_ProcessManagementPolicy.security_ProcessManagementPolicy',
        ),
        test_common.define_test(
            'security/RendererSandbox',
            suites = ['security'],
            main_package = 'autotest_lib.client.site_tests.security_RendererSandbox.security_RendererSandbox',
        ),
        test_common.define_test(
            'security/RootfsOwners',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.security_RootfsOwners.security_RootfsOwners',
        ),
        test_common.define_test(
            'security/SandboxLinuxUnittests',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.security_SandboxLinuxUnittests.security_SandboxLinuxUnittests',
        ),
        test_common.define_test(
            'security/SeccompSyscallFilters',
            suites = ['security'],
            main_package = 'autotest_lib.client.site_tests.security_SeccompSyscallFilters.security_SeccompSyscallFilters',
        ),
        test_common.define_test(
            'security/SessionManagerDbusEndpoints',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.security_SessionManagerDbusEndpoints.security_SessionManagerDbusEndpoints',
        ),
        test_common.define_test(
            'security/SysVIPC',
            suites = ['security'],
            main_package = 'autotest_lib.client.site_tests.security_SysVIPC.security_SysVIPC',
        ),
        test_common.define_test(
            'security/kASLR',
            suites = ['security_weekly'],
            main_package = '',
        ),
        test_common.define_test(
            'security/x86Registers',
            suites = ['security'],
            main_package = 'autotest_lib.client.site_tests.security_x86Registers.security_x86Registers',
        )
    ]
