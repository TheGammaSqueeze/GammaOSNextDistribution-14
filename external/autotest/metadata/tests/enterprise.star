
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'enterprise/CFM_AtrusUpdaterStress',
            suites = ['hotrod-remora'],
            main_package = 'autotest_lib.client.site_tests.enterprise_CFM_AtrusUpdaterStress.enterprise_CFM_AtrusUpdaterStress',
        ),
        test_common.define_test(
            'enterprise/CFM_AutoZoomSanity',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_AutotestSmokeTest',
            suites = ['bluestreak-pre-cq', 'hotrod'],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_BizlinkUpdater',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_CEC',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_HuddlyMonitor',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_HuddlyUpdater',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_LogitechMeetupUpdater',
            suites = ['hotrod'],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_LogitechPtzUpdater',
            suites = ['hotrod'],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_MeetAppSanity',
            suites = ['hotrod-remora'],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_MimoUpdater',
            suites = ['hotrod-remora'],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/CFM_SiSFwUpdater',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/ClearTPM',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/FakeEnrollment',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.enterprise_FakeEnrollment.enterprise_FakeEnrollment',
        ),
        test_common.define_test(
            'enterprise/KioskEnrollment',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.enterprise_KioskEnrollment.enterprise_KioskEnrollment',
        ),
        test_common.define_test(
            'enterprise/KioskEnrollmentServer',
            suites = ['enterprise'],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/KioskPerf',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.enterprise_KioskPerf.enterprise_KioskPerf',
        ),
        test_common.define_test(
            'enterprise/OnlineDemoMode',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/OnlineDemoModeEnrollment',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.enterprise_OnlineDemoModeEnrollment.enterprise_OnlineDemoModeEnrollment',
        ),
        test_common.define_test(
            'enterprise/PowerManagement',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.enterprise_PowerManagement.enterprise_PowerManagement',
        ),
        test_common.define_test(
            'enterprise/RemoraRequisition',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.enterprise_RemoraRequisition.enterprise_RemoraRequisition',
        ),
        test_common.define_test(
            'enterprise/RemoraRequisitionDisplayUsage',
            suites = ['hotrod-remora'],
            main_package = 'autotest_lib.client.site_tests.enterprise_RemoraRequisitionDisplayUsage.enterprise_RemoraRequisitionDisplayUsage',
        ),
        test_common.define_test(
            'enterprise/RemoraRequisitionDisplayUsageServer',
            suites = ['hotrod-remora'],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/RemoraRequisitionServer',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'enterprise/SmbProviderDaemon',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.enterprise_SmbProviderDaemon.enterprise_SmbProviderDaemon',
        )
    ]
