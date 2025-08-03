
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'display/ClientChameleonConnection',
            suites = ['chameleon_dp', 'chameleon_dp_hdmi', 'chameleon_hdmi', 'chameleon_vga'],
            main_package = 'autotest_lib.client.site_tests.display_ClientChameleonConnection.display_ClientChameleonConnection',
        ),
        test_common.define_test(
            'display/DisplayContainEdid',
            suites = ['platform_internal_display'],
            main_package = 'autotest_lib.client.site_tests.display_DisplayContainEdid.display_DisplayContainEdid',
        ),
        test_common.define_test(
            'display/EdidStress.daily',
            suites = ['chameleon_hdmi_perbuild'],
            main_package = '',
        ),
        test_common.define_test(
            'display/InternalDisplayRotation',
            suites = ['platform_internal_display'],
            main_package = 'autotest_lib.client.site_tests.display_InternalDisplayRotation.display_InternalDisplayRotation',
        ),
        test_common.define_test(
            'display/ServerChameleonConnection',
            suites = ['chameleon_hdmi_unstable'],
            main_package = '',
        ),
        test_common.define_test(
            'display/SwitchMode',
            suites = ['chameleon_dp', 'chameleon_dp_hdmi', 'chameleon_hdmi', 'chameleon_hdmi_perbuild'],
            main_package = '',
        )
    ]
