
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'login/ChromeProfileSanitary',
            suites = ['regression'],
            main_package = 'autotest_lib.client.site_tests.login_ChromeProfileSanitary.login_ChromeProfileSanitary',
        ),
        test_common.define_test(
            'login/CryptohomeDataLeak',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.login_CryptohomeDataLeak.login_CryptohomeDataLeak',
        ),
        test_common.define_test(
            'login/CryptohomeIncognito',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.login_CryptohomeIncognito.login_CryptohomeIncognito',
        ),
        test_common.define_test(
            'login/CryptohomeOwnerQuery',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.login_CryptohomeOwnerQuery.login_CryptohomeOwnerQuery',
        ),
        test_common.define_test(
            'login/GaiaLogin',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.login_GaiaLogin.login_GaiaLogin',
        ),
        test_common.define_test(
            'login/LoginSuccess',
            suites = ['bvt-inline', 'dev_drone_image_test', 'push_to_prod', 'skylab_staging_test', 'smoke'],
            main_package = 'autotest_lib.client.site_tests.login_LoginSuccess.login_LoginSuccess',
        ),
        test_common.define_test(
            'login/MultipleSessions',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.login_MultipleSessions.login_MultipleSessions',
        ),
        test_common.define_test(
            'login/OobeLocalization',
            suites = ['experimental'],
            main_package = 'autotest_lib.client.site_tests.login_OobeLocalization.login_OobeLocalization',
        ),
        test_common.define_test(
            'login/UnicornLogin',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.login_UnicornLogin.login_UnicornLogin',
        ),
        test_common.define_test(
            'login/UserPolicyKeys',
            suites = ['bvt-inline', 'smoke'],
            main_package = 'autotest_lib.client.site_tests.login_UserPolicyKeys.login_UserPolicyKeys',
        )
    ]
