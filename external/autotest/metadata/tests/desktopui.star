
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'desktopui/CheckRlzPingSent',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.desktopui_CheckRlzPingSent.desktopui_CheckRlzPingSent',
        ),
        test_common.define_test(
            'desktopui/ChromeSanity',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.desktopui_ChromeCheck.desktopui_ChromeCheck',
        ),
        test_common.define_test(
            'desktopui/ConnectivityDiagnostics',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.desktopui_ConnectivityDiagnostics.desktopui_ConnectivityDiagnostics',
        ),
        test_common.define_test(
            'desktopui/FontCache',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.desktopui_FontCache.desktopui_FontCache',
        ),
        test_common.define_test(
            'desktopui/GmailLatency',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.desktopui_GmailLatency.desktopui_GmailLatency',
        ),
        test_common.define_test(
            'desktopui/HangDetector',
            suites = ['regression'],
            main_package = 'autotest_lib.client.site_tests.desktopui_HangDetector.desktopui_HangDetector',
        ),
        test_common.define_test(
            'desktopui/MediaAudioFeedback',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.desktopui_MediaAudioFeedback.desktopui_MediaAudioFeedback',
        ),
        test_common.define_test(
            'desktopui/ScreenLocker',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.desktopui_ScreenLocker.desktopui_ScreenLocker',
        ),
        test_common.define_test(
            'desktopui/SetFieldsWithChromeDriver',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.desktopui_SetFieldsWithChromeDriver.desktopui_SetFieldsWithChromeDriver',
        ),
        test_common.define_test(
            'desktopui/SimpleLogin',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.desktopui_SimpleLogin.desktopui_SimpleLogin',
        ),
        test_common.define_test(
            'desktopui/SpeechSynthesisSemiAuto',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.desktopui_SpeechSynthesisSemiAuto.desktopui_SpeechSynthesisSemiAuto',
        ),
        test_common.define_test(
            'desktopui/UrlFetchWithChromeDriver',
            suites = [],
            main_package = '',
        )
    ]
