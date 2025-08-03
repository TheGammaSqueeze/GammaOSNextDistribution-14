
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'autoupdate/Backoff',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_Backoff.autoupdate_Backoff',
        ),
        test_common.define_test(
            'autoupdate/BadMetadata',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_BadMetadata.autoupdate_BadMetadata',
        ),
        test_common.define_test(
            'autoupdate/CannedOmahaUpdate',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_CannedOmahaUpdate.autoupdate_CannedOmahaUpdate',
        ),
        test_common.define_test(
            'autoupdate/CatchBadSignatures',
            suites = ['au-perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'autoupdate/CrashBrowserAfterUpdate',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_CrashBrowserAfterUpdate.autoupdate_CrashBrowserAfterUpdate',
        ),
        test_common.define_test(
            'autoupdate/EOL',
            suites = ['au-perbuild'],
            main_package = 'autotest_lib.client.site_tests.autoupdate_EOL.autoupdate_EOL',
        ),
        test_common.define_test(
            'autoupdate/EndToEndTest',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'autoupdate/InstallAndUpdateDLC',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_InstallAndUpdateDLC.autoupdate_InstallAndUpdateDLC',
        ),
        test_common.define_test(
            'autoupdate/LoginStartUpdateLogout',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_LoginStartUpdateLogout.autoupdate_LoginStartUpdateLogout',
        ),
        test_common.define_test(
            'autoupdate/Rollback',
            suites = ['bvt-installer'],
            main_package = '',
        ),
        test_common.define_test(
            'autoupdate/StartOOBEUpdate',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_StartOOBEUpdate.autoupdate_StartOOBEUpdate',
        ),
        test_common.define_test(
            'autoupdate/StatefulCompatibility',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'autoupdate/UpdateFromUI',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_UpdateFromUI.autoupdate_UpdateFromUI',
        ),
        test_common.define_test(
            'autoupdate/UrlSwitch',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_UrlSwitch.autoupdate_UrlSwitch',
        ),
        test_common.define_test(
            'autoupdate/UserData',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.autoupdate_UserData.autoupdate_UserData',
        )
    ]
