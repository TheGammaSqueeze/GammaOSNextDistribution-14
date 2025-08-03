
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# !!! GENERATED FILE. DO NOT EDIT !!!

load('//metadata/test_common.star', 'test_common')

def define_tests():
    return [
        test_common.define_test(
            'power/AudioDetector',
            suites = ['bvt-perbuild'],
            main_package = 'autotest_lib.client.site_tests.power_AudioDetector.power_AudioDetector',
        ),
        test_common.define_test(
            'power/BacklightControl',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_BacklightControl.power_BacklightControl',
        ),
        test_common.define_test(
            'power/BatteryCharge',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_BatteryCharge.power_BatteryCharge',
        ),
        test_common.define_test(
            'power/BatteryDrain',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_BatteryDrain.power_BatteryDrain',
        ),
        test_common.define_test(
            'power/BrightnessResetAfterReboot',
            suites = ['bvt-perbuild', 'partners'],
            main_package = '',
        ),
        test_common.define_test(
            'power/CPUFreq',
            suites = ['power_build'],
            main_package = 'autotest_lib.client.site_tests.power_CPUFreq.power_CPUFreq',
        ),
        test_common.define_test(
            'power/CPUIdle',
            suites = ['power_build'],
            main_package = 'autotest_lib.client.site_tests.power_CPUIdle.power_CPUIdle',
        ),
        test_common.define_test(
            'power/ChargeControlWrapper',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/CheckAC',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_CheckAC.power_CheckAC',
        ),
        test_common.define_test(
            'power/Consumption',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_Consumption.power_Consumption',
        ),
        test_common.define_test(
            'power/DeferForFlashrom',
            suites = ['power_build'],
            main_package = '',
        ),
        test_common.define_test(
            'power/Display',
            suites = ['power_daily', 'power_monitoring', 'power_check'],
            main_package = 'autotest_lib.client.site_tests.power_Display.power_Display',
        ),
        test_common.define_test(
            'power/Draw',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/Dummy',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_Dummy.power_Dummy',
        ),
        test_common.define_test(
            'power/HotCPUSuspend',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.power_HotCPUSuspend.power_HotCPUSuspend',
        ),
        test_common.define_test(
            'power/Idle',
            suites = ['bvt-perbuild', 'power_idle', 'power_monitoring', 'power_check'],
            main_package = 'autotest_lib.client.site_tests.power_Idle.power_Idle',
        ),
        test_common.define_test(
            'power/IdleServer',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/IdleSuspend',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.power_IdleSuspend.power_IdleSuspend',
        ),
        test_common.define_test(
            'power/KernelSuspend',
            suites = ['jailed_build'],
            main_package = 'autotest_lib.client.site_tests.power_KernelSuspend.power_KernelSuspend',
        ),
        test_common.define_test(
            'power/LoadTest',
            suites = ['power_loadtest'],
            main_package = 'autotest_lib.client.site_tests.power_LoadTest.power_LoadTest',
        ),
        test_common.define_test(
            'power/LowMemorySuspend',
            suites = ['crosbolt_perf_weekly'],
            main_package = 'autotest_lib.client.site_tests.power_LowMemorySuspend.power_LowMemorySuspend',
        ),
        test_common.define_test(
            'power/MemorySuspend',
            suites = ['jailed_build'],
            main_package = 'autotest_lib.client.site_tests.power_MemorySuspend.power_MemorySuspend',
        ),
        test_common.define_test(
            'power/Monitoring',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/NoConsoleSuspend',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.power_NoConsoleSuspend.power_NoConsoleSuspend',
        ),
        test_common.define_test(
            'power/PowerlogWrapper',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/ProbeDriver.probe_ac',
            suites = ['power_build'],
            main_package = 'autotest_lib.client.site_tests.power_ProbeDriver.power_ProbeDriver',
        ),
        test_common.define_test(
            'power/RPMTest',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/Resume',
            suites = ['crosbolt_perf_perbuild'],
            main_package = 'autotest_lib.client.site_tests.power_Resume.power_Resume',
        ),
        test_common.define_test(
            'power/ServodWrapper',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/Speedometer2',
            suites = ['power_daily'],
            main_package = 'autotest_lib.client.site_tests.power_Speedometer2.power_Speedometer2',
        ),
        test_common.define_test(
            'power/Standby',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_Standby.power_Standby',
        ),
        test_common.define_test(
            'power/StatsCPUFreq',
            suites = ['power_build'],
            main_package = 'autotest_lib.client.site_tests.power_StatsCPUFreq.power_StatsCPUFreq',
        ),
        test_common.define_test(
            'power/StatsUSB',
            suites = ['power_build'],
            main_package = 'autotest_lib.client.site_tests.power_StatsUSB.power_StatsUSB',
        ),
        test_common.define_test(
            'power/SuspendToIdle',
            suites = ['power_daily'],
            main_package = 'autotest_lib.client.site_tests.power_SuspendToIdle.power_SuspendToIdle',
        ),
        test_common.define_test(
            'power/Thermal',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_Thermal.power_Thermal',
        ),
        test_common.define_test(
            'power/ThermalLoad',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_ThermalLoad.power_ThermalLoad',
        ),
        test_common.define_test(
            'power/USBHotplugInSuspend',
            suites = [],
            main_package = '',
        ),
        test_common.define_test(
            'power/UiResume',
            suites = ['crosbolt_perf_perbuild'],
            main_package = 'autotest_lib.client.site_tests.power_UiResume.power_UiResume',
        ),
        test_common.define_test(
            'power/VideoCall',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_VideoCall.power_VideoCall',
        ),
        test_common.define_test(
            'power/VideoDRMPlayback',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_VideoDRMPlayback.power_VideoDRMPlayback',
        ),
        test_common.define_test(
            'power/VideoDetector',
            suites = ['kernel_daily_regression'],
            main_package = 'autotest_lib.client.site_tests.power_VideoDetector.power_VideoDetector',
        ),
        test_common.define_test(
            'power/VideoEncode',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_VideoEncode.power_VideoEncode',
        ),
        test_common.define_test(
            'power/VideoPlayback',
            suites = ['power_daily', 'power_monitoring', 'power_check'],
            main_package = 'autotest_lib.client.site_tests.power_VideoPlayback.power_VideoPlayback',
        ),
        test_common.define_test(
            'power/VideoSuspend',
            suites = ['kernel_daily_regression', 'video'],
            main_package = 'autotest_lib.client.site_tests.power_VideoSuspend.power_VideoSuspend',
        ),
        test_common.define_test(
            'power/WaitForCoolDown',
            suites = [],
            main_package = 'autotest_lib.client.site_tests.power_WaitForCoolDown.power_WaitForCoolDown',
        ),
        test_common.define_test(
            'power/WakeSources',
            suites = ['power_build'],
            main_package = '',
        ),
        test_common.define_test(
            'power/WakeupRTC',
            suites = ['kernel_per-build_regression'],
            main_package = 'autotest_lib.client.site_tests.power_WakeupRTC.power_WakeupRTC',
        ),
        test_common.define_test(
            'power/WebGL',
            suites = ['power_daily', 'power_monitoring', 'power_check'],
            main_package = 'autotest_lib.client.site_tests.power_WebGL.power_WebGL',
        ),
        test_common.define_test(
            'power/WifiIdle',
            suites = ['power_daily'],
            main_package = 'autotest_lib.client.site_tests.power_WifiIdle.power_WifiIdle',
        )
    ]
