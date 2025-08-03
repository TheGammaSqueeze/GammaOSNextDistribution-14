
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'touch/GestureNav',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_GestureNav.touch_GestureNav',
        ),
        test_common.define_test(
            'touch/MouseScroll',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_MouseScroll.touch_MouseScroll',
        ),
        test_common.define_test(
            'touch/ScrollDirection',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_ScrollDirection.touch_ScrollDirection',
        ),
        test_common.define_test(
            'touch/StylusTaps',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_StylusTaps.touch_StylusTaps',
        ),
        test_common.define_test(
            'touch/TabSwitch',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_TabSwitch.touch_TabSwitch',
        ),
        test_common.define_test(
            'touch/TapSettings',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_TapSettings.touch_TapSettings',
        ),
        test_common.define_test(
            'touch/TouchscreenScroll',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_TouchscreenScroll.touch_TouchscreenScroll',
        ),
        test_common.define_test(
            'touch/TouchscreenTaps',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_TouchscreenTaps.touch_TouchscreenTaps',
        ),
        test_common.define_test(
            'touch/TouchscreenZoom',
            suites = ['touch'],
            main_package = 'autotest_lib.client.site_tests.touch_TouchscreenZoom.touch_TouchscreenZoom',
        )
    ]
