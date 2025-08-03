#!/usr/bin/env python3
#
# Copyright 2023, The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""A collection of integration test cases for atest."""

from typing import Any, Callable

import atest_integration_test

# Note: The following constants should ideally be imported from their
#       corresponding prod source code, but this makes local execution of the
#       integration test harder due to some special dependencies in the prod
#       code. Therefore we copy the definition here for now in favor of easier
#       local integration test execution. If value changes in the source code
#       breaking the integration test becomes a problem in the future, we can
#       reconsider importing these constants.
# Log prefix for dry-run run command. Defined in atest/atest_main.py
_DRY_RUN_COMMAND_LOG_PREFIX = 'Internal run command from dry-run: '


class CommandVerificationTests(atest_integration_test.AtestTestCase):
  """Checks atest tradefed commands."""

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_animator_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'AnimatorTest'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsAnimationTestCases'
        ' --atest-include-filter'
        ' CtsAnimationTestCases:android.animation.cts.AnimatorTest'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
        ' --enable-parameterized-modules --exclude-module-parameters'
        ' secondary_user --exclude-module-parameters instant_app'
        ' --exclude-module-parameters multi_abi'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_animation_test_cases_animator_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'CtsAnimationTestCases:AnimatorTest'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsAnimationTestCases'
        ' --atest-include-filter'
        ' CtsAnimationTestCases:android.animation.cts.AnimatorTest'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
        ' --enable-parameterized-modules --exclude-module-parameters multi_abi'
        ' --exclude-module-parameters instant_app --exclude-module-parameters'
        ' secondary_user'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_sample_device_cases_shared_prefs_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'CtsSampleDeviceTestCases:SampleDeviceTest#testSharedPreferences'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest'
        ' --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' CtsSampleDeviceTestCases --atest-include-filter'
        ' CtsSampleDeviceTestCases:android.sample.cts.SampleDeviceTest#testSharedPreferences'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_sample_device_cases_android_sample_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'CtsSampleDeviceTestCases:android.sample.cts'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest'
        ' --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' CtsSampleDeviceTestCases --atest-include-filter'
        ' CtsSampleDeviceTestCases:android.sample.cts --skip-loading-config-jar'
        ' --log-level-display VERBOSE --log-level VERBOSE'
        ' --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_sample_device_cases_device_report_log_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'CtsSampleDeviceTestCases:android.sample.cts.SampleDeviceReportLogTest'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest'
        ' --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' CtsSampleDeviceTestCases --atest-include-filter'
        ' CtsSampleDeviceTestCases:android.sample.cts.SampleDeviceReportLogTest'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_animation_cases_sample_device_cases_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'CtsAnimationTestCases CtsSampleDeviceTestCases'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest'
        ' --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter CtsAnimationTestCases'
        ' --include-filter CtsSampleDeviceTestCases --skip-loading-config-jar'
        ' --log-level-display VERBOSE --log-level VERBOSE'
        ' --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_hello_world_tests_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'HelloWorldTests'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter HelloWorldTests'
        ' --include-filter hallo-welt --skip-loading-config-jar'
        ' --log-level-display VERBOSE --log-level VERBOSE'
        ' --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_mixed_managed_profile_ownr_pw_sufficient_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'MixedManagedProfileOwnerTest#testPasswordSufficientInitially'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module'
        ' CtsDevicePolicyManagerTestCases --atest-include-filter'
        ' CtsDevicePolicyManagerTestCases:com.android.cts.devicepolicy.MixedManagedProfileOwnerTest#testPasswordSufficientInitially'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --enable-parameterized-modules'
        ' --exclude-module-parameters instant_app --exclude-module-parameters'
        ' secondary_user --exclude-module-parameters multi_abi'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_perinstance_camerahidl_config_injection_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'PerInstance/CameraHidlTest#'
        'configureInjectionStreamsAvailableOutputs/0_internal_0'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' VtsHalCameraProviderV2_4TargetTest --atest-include-filter'
        ' VtsHalCameraProviderV2_4TargetTest:PerInstance/CameraHidlTest.configureInjectionStreamsAvailableOutputs/0_internal_0'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_vts_hal_camera_provider_config_injection_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'VtsHalCameraProviderV2_4TargetTest:PerInstance/'
        'CameraHidlTest#configureInjectionStreamsAvailableOutputs/'
        '0_internal_0'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' VtsHalCameraProviderV2_4TargetTest --atest-include-filter'
        ' VtsHalCameraProviderV2_4TargetTest:PerInstance/CameraHidlTest.configureInjectionStreamsAvailableOutputs/0_internal_0'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_android_animation_cts_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'android.animation.cts'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsAnimationTestCases'
        ' --atest-include-filter CtsAnimationTestCases:android.animation.cts'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
        ' --enable-parameterized-modules --exclude-module-parameters multi_abi'
        ' --exclude-module-parameters instant_app --exclude-module-parameters'
        ' secondary_user'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_android_sample_cts_device_report_log_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'android.sample.cts.SampleDeviceReportLogTest'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest'
        ' --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' CtsSampleDeviceTestCases --atest-include-filter'
        ' CtsSampleDeviceTestCases:android.sample.cts.SampleDeviceReportLogTest'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_android_sample_cts_shared_prefs_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'android.sample.cts.SampleDeviceTest#testSharedPreferences'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest'
        ' --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' CtsSampleDeviceTestCases --atest-include-filter'
        ' CtsSampleDeviceTestCases:android.sample.cts.SampleDeviceTest#testSharedPreferences'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --test-arg'
        ' com.android.tradefed.testtype.AndroidJUnitTest:exclude-annotation:android.platform.test.annotations.AppModeInstant'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_hello_world_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'hello_world_test'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter hello_world_test'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_native_benchmark_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'native-benchmark'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter native-benchmark'
        ' --log-level-display VERBOSE --log-level VERBOSE'
        ' --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_platform_native_example_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'platform_testing/tests/example/native'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter hello_world_test'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_platform_android_example_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'platform_testing/tests/example/native/Android.bp'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter hello_world_test'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_tf_core_config_native_benchmark_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'tools/tradefederation/core/res/config/native-benchmark.xml'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter native-benchmark'
        ' --log-level-display VERBOSE --log-level VERBOSE'
        ' --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_quick_access_wallet_robo_test(self):
    """Verify that the test's command runs correctly."""
    test_cmd = 'QuickAccessWalletRoboTests'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' QuickAccessWalletRoboTests --skip-loading-config-jar'
        ' --log-level-display VERBOSE --log-level VERBOSE'
        ' --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        test_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_quick_access_wallet_robo_host_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'QuickAccessWalletRoboTests --host'
    expected_cmd = (
        'atest_tradefed.sh template/atest_deviceless_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter'
        ' QuickAccessWalletRoboTests --skip-loading-config-jar'
        ' --log-level-display VERBOSE --log-level VERBOSE'
        ' --no-early-device-release -n --prioritize-host-config'
        ' --skip-host-arch-check'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_wifi_aware_cases_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'CtsWifiAwareTestCases'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter CtsWifiAwareTestCases'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --replicate-parent-setup'
        ' --multi-device-count 2'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_pts_bot_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'pts-bot:PAN/GN/MISC/UUID/BV-01-C'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter pts-bot'
        ' --atest-include-filter pts-bot:PAN/GN/MISC/UUID/BV-01-C'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_tee_ui_utils_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'TeeUIUtilsTest'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter teeui_unit_tests'
        ' --atest-include-filter teeui_unit_tests:TeeUIUtilsTest.*'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_tee_ui_utils_intersect_convext_obj_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'TeeUIUtilsTest#intersectTest,ConvexObjectConstruction,'
        'ConvexObjectLineIntersection'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --include-filter teeui_unit_tests'
        ' --atest-include-filter'
        ' teeui_unit_tests:TeeUIUtilsTest.ConvexObjectConstruction:TeeUIUtilsTest.ConvexObjectLineIntersection:TeeUIUtilsTest.intersectTest'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_activity_mgr_register_ui_change_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'CtsSecurityTestCases:android.security.cts.'
        'ActivityManagerTest#testActivityManager_'
        'registerUidChangeObserver_allPermission'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsSecurityTestCases'
        ' --atest-include-filter'
        ' CtsSecurityTestCases:android.security.cts.ActivityManagerTest#testActivityManager_registerUidChangeObserver_allPermission'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --enable-parameterized-modules'
        ' --exclude-module-parameters instant_app --exclude-module-parameters'
        ' secondary_user --exclude-module-parameters multi_abi'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_cts_activity_mgr_register_ui_change_java_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'cts/tests/tests/security/src/android/security/cts/'
        'ActivityManagerTest.java#testActivityManager_'
        'registerUidChangeObserver_allPermission'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsSecurityTestCases'
        ' --atest-include-filter'
        ' CtsSecurityTestCases:android.security.cts.ActivityManagerTest#testActivityManager_registerUidChangeObserver_allPermission'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --enable-parameterized-modules'
        ' --exclude-module-parameters instant_app --exclude-module-parameters'
        ' secondary_user --exclude-module-parameters multi_abi'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_permission_memory_footprint_apps_size_kt_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'cts/tests/tests/security/src/android/security/cts/'
        'PermissionMemoryFootprintTest.kt#'
        'checkAppsCantIncreasePermissionSizeAfterCreating'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsSecurityTestCases'
        ' --atest-include-filter'
        ' CtsSecurityTestCases:android.security.cts.PermissionMemoryFootprintTest#checkAppsCantIncreasePermissionSizeAfterCreating'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --enable-parameterized-modules'
        ' --exclude-module-parameters instant_app --exclude-module-parameters'
        ' multi_abi --exclude-module-parameters secondary_user'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_permission_memory_footprint_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = 'android.security.cts.PermissionMemoryFootprintTest'
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsSecurityTestCases'
        ' --atest-include-filter'
        ' CtsSecurityTestCases:android.security.cts.PermissionMemoryFootprintTest'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --enable-parameterized-modules'
        ' --exclude-module-parameters multi_abi --exclude-module-parameters'
        ' instant_app --exclude-module-parameters secondary_user'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  @atest_integration_test.ParallelTestRunner.run_in_parallel
  def test_permission_memory_footprint_apps_size_test(self):
    """Verify that the test's command runs correctly."""
    atest_cmd = (
        'android.security.cts.PermissionMemoryFootprintTest#'
        'checkAppsCantIncreasePermissionSizeAfterCreating'
    )
    expected_cmd = (
        'atest_tradefed.sh template/atest_device_test_base --template:map'
        ' test=atest --template:map log_saver=template/log/atest_log_saver'
        ' --no-enable-granular-attempts --module CtsSecurityTestCases'
        ' --atest-include-filter'
        ' CtsSecurityTestCases:android.security.cts.PermissionMemoryFootprintTest#checkAppsCantIncreasePermissionSizeAfterCreating'
        ' --skip-loading-config-jar --log-level-display VERBOSE --log-level'
        ' VERBOSE --no-early-device-release --enable-parameterized-modules'
        ' --exclude-module-parameters secondary_user'
        ' --exclude-module-parameters multi_abi --exclude-module-parameters'
        ' instant_app'
    )
    self._verify_atest_internal_runner_command(
        atest_cmd,
        self._assert_equivalent_cmds,
        expected_cmd=expected_cmd,
    )

  def _sanitize_runner_command(self, cmd: str) -> str:
    """Sanitize an atest runner command by removing non-essential args."""
    remove_args_starting_with = [
        '--skip-all-system-status-check',
        '--atest-log-file-path',
        'LD_LIBRARY_PATH=',
        '--proto-output-file=',
        '--log-root-path',
    ]
    remove_args_with_values = ['-s', '--serial']
    build_command = 'build/soong/soong_ui.bash'
    original_args = cmd.split()
    result_args = []
    for arg in original_args:
      if arg == build_command:
        result_args.append(f'./{build_command}')
        continue
      if not any(
          (arg.startswith(prefix) for prefix in remove_args_starting_with)
      ):
        result_args.append(arg)
    for arg in remove_args_with_values:
      while arg in result_args:
        idx = result_args.index(arg)
        # Delete value index first.
        del result_args[idx + 1]
        del result_args[idx]

    return ' '.join(result_args)

  def _assert_equivalent_cmds(
      self,
      atest_cmd: str,
      actual_cmd: str,
      expected_cmd: str,
  ) -> None:
    """Assert that the expected command is equivalent to the actual command.

    Non-essential arguments such as log directory and serial will be ignored.

    Args:
        atest_cmd: The atest command string that is being tested.
        actual_cmd: The actual atest internal runner command string.
        expected_cmd: The expected atest internal runner command string.

    Returns:
    """
    actual_cmd = self._sanitize_runner_command(actual_cmd)
    expected_cmd = self._sanitize_runner_command(expected_cmd)

    self.assertEqual(
        set(actual_cmd.split()),
        set(expected_cmd.split()),
        'Unexpected atest internal runner command generated for the'
        ' atest command `%s`.\nActual:\n`%s`\nExpected:\n`%s`'
        % (atest_cmd, actual_cmd, expected_cmd),
    )

  def _verify_atest_internal_runner_command(
      self,
      atest_cmd: str,
      assertion_func: Callable[str, None],
      **assertion_func_params: dict[str, Any],
  ) -> None:
    """Verifies atest's runner command using the provided assertion function.

    Args:
        atest_cmd: The atest command to execute. Note: Do not add the atest
          binary to the beginning of the command.
        assertion_func: A function that takes a test command string and an atest
          internal command string and runs assertions on it.
        **assertion_func_params: Parameters for the assertion function.
    """
    script = self.create_atest_script()

    def build_step(
        step_in: atest_integration_test.StepInput,
    ) -> atest_integration_test.StepOutput:
      result = self.run_atest_command(atest_cmd + ' --dry-run -cit', step_in)
      result.check_returncode()
      runner_cmd = result.get_atest_log_values_from_prefix(
          _DRY_RUN_COMMAND_LOG_PREFIX
      )[0]

      step_out = self.create_step_output()
      step_out.set_snapshot_include_paths([])
      step_out.add_snapshot_obj('runner_cmd', runner_cmd)
      return step_out

    def test_step(step_in: atest_integration_test.StepInput) -> None:
      runner_cmd = step_in.get_obj('runner_cmd')
      assertion_func(atest_cmd, runner_cmd, **assertion_func_params)

    script.add_build_step(build_step)
    script.add_test_step(test_step)
    script.run()


if __name__ == '__main__':
  atest_integration_test.main()
