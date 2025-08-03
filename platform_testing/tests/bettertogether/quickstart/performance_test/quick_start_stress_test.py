#  Copyright (C) 2023 The Android Open Source Project
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

"""Stress tests for Neaby Connections used by the quick start flow."""

import datetime
import logging
import os
import sys
import time

# check the python version
if sys.version_info < (3,10):
  logging.error('The test only can run on python 3.10 and above')
  exit()

from mobly import asserts
from mobly import base_test
from mobly import test_runner
from mobly.controllers import android_device

# Allows local imports to be resolved via relative path, so the test can be run
# without building.
_performance_test_dir = os.path.dirname(os.path.dirname(__file__))
if _performance_test_dir not in sys.path:
  sys.path.append(_performance_test_dir)

from performance_test import nc_base_test
from performance_test import nc_constants
from performance_test import nearby_connection_wrapper
from performance_test import setup_utils

_TEST_SCRIPT_VERSION = '1.6'

_NEARBY_SNIPPET_2_PACKAGE_NAME = 'com.google.android.nearby.mobly.snippet.second'

_PERFORMANCE_TEST_REPEAT_COUNT = 100
_PERFORMANCE_TEST_MAX_CONSECUTIVE_ERROR = 5

_DELAY_BETWEEN_EACH_TEST_CYCLE = datetime.timedelta(seconds=5)
_TRANSFER_FILE_SIZE_1MB = 1024
_TRANSFER_FILE_SIZE_1GB = 1024 * 1024


class QuickStartStressTest(nc_base_test.NCBaseTestClass):
  """Nearby Connection E2E stress tests for quick start flow."""

  performance_test_iterations: int

  # @typing.override
  def __init__(self, configs):
    super().__init__(configs)
    self._nearby_snippet_2_apk_path: str = None
    self._test_result: nc_constants.SingleTestResult = (
        nc_constants.SingleTestResult())
    self._quick_start_test_metrics: nc_constants.QuickStartTestMetrics = (
        nc_constants.QuickStartTestMetrics())
    self._test_script_version = _TEST_SCRIPT_VERSION

  # @typing.override
  def setup_test(self):
    self.discoverer.nearby2.stopDiscovery()
    self.discoverer.nearby2.stopAllEndpoints()
    self.advertiser.nearby2.stopAdvertising()
    self.advertiser.nearby2.stopAllEndpoints()

    super().setup_test()

  # @typing.override
  def setup_class(self):
    self._nearby_snippet_2_apk_path = self.user_params.get('files', {}).get(
        'nearby_snippet_2', [''])[0]
    self.performance_test_iterations = getattr(
        self.test_quick_start_performance, base_test.ATTR_REPEAT_CNT)
    logging.info('performance test iterations: %s',
                 self.performance_test_iterations)

    super().setup_class()

  # @typing.override
  def _setup_android_device(self, ad: android_device.AndroidDevice) -> None:
    super()._setup_android_device(ad)
    ad.log.info('try to install nearby_snippet_2_apk')
    if self._nearby_snippet_2_apk_path:
      setup_utils.install_apk(ad, self._nearby_snippet_2_apk_path)
    else:
      ad.log.warning('nearby_snippet_2 apk is not specified, '
                     'make sure it is installed in the device')
    ad.load_snippet('nearby2', _NEARBY_SNIPPET_2_PACKAGE_NAME)
    setup_utils.grant_manage_external_storage_permission(
        ad, _NEARBY_SNIPPET_2_PACKAGE_NAME
    )

    setup_utils.enable_bluetooth_multiplex(ad)


  # @typing.override
  def _teardown_device(self, ad: android_device.AndroidDevice) -> None:
    super()._teardown_device(ad)
    ad.nearby2.transferFilesCleanup()
    ad.unload_snippet('nearby2')

  @base_test.repeat(
      count=_PERFORMANCE_TEST_REPEAT_COUNT,
      max_consecutive_error=_PERFORMANCE_TEST_MAX_CONSECUTIVE_ERROR)
  def test_quick_start_performance(self) -> None:
    """Stress test for quick start flow."""
    try:
      self._mimic_quick_start_test(
          self.discoverer,
          self.advertiser,
          wifi_ssid=self.test_parameters.wifi_ssid,
          wifi_password=self.test_parameters.wifi_password,
      )
    finally:
      self._write_current_test_report()
      self._collect_current_test_metrics()
      time.sleep(_DELAY_BETWEEN_EACH_TEST_CYCLE.total_seconds())

  def _mimic_quick_start_test(
      self,
      discoverer,
      advertiser,
      wifi_ssid: str = '',
      wifi_password: str = '',
  ) -> None:
    """Mimics quick start flow test with 2 nearby connections."""
    if self.test_parameters.toggle_airplane_mode_target_side:
      setup_utils.toggle_airplane_mode(self.advertiser)
    if self.test_parameters.reset_wifi_connection:
      self._reset_wifi_connection()
    # 1. discoverer connect to wifi wlan
    self._test_result = nc_constants.SingleTestResult()
    if wifi_ssid:
      discoverer_wifi_latency = setup_utils.connect_to_wifi_wlan_till_success(
          discoverer, wifi_ssid, wifi_password
      )
      discoverer.log.info(
          'connecting to wifi in '
          f'{round(discoverer_wifi_latency.total_seconds())} s'
      )
      self._test_result.discoverer_wifi_wlan_expected = True
      self._test_result.discoverer_wifi_wlan_latency = discoverer_wifi_latency

    # 2. set up 1st connection
    advertising_discovery_medium = nc_constants.NearbyMedium(
        self.test_parameters.advertising_discovery_medium
    )
    nearby_snippet_1 = nearby_connection_wrapper.NearbyConnectionWrapper(
        advertiser,
        discoverer,
        advertiser.nearby,
        discoverer.nearby,
        advertising_discovery_medium=advertising_discovery_medium,
        connection_medium=nc_constants.NearbyMedium.BT_ONLY,
        upgrade_medium=nc_constants.NearbyMedium.BT_ONLY,
    )
    first_connection_setup_timeouts = nc_constants.ConnectionSetupTimeouts(
        nc_constants.FIRST_DISCOVERY_TIMEOUT,
        nc_constants.FIRST_CONNECTION_INIT_TIMEOUT,
        nc_constants.FIRST_CONNECTION_RESULT_TIMEOUT)

    try:
      nearby_snippet_1.start_nearby_connection(
          timeouts=first_connection_setup_timeouts,
          medium_upgrade_type=nc_constants.MediumUpgradeType.NON_DISRUPTIVE)
    finally:
      self._test_result.connection_setup_quality_info = (
          nearby_snippet_1.connection_quality_info
      )

    # 3. transfer file through bluetooth
    file_1_mb = _TRANSFER_FILE_SIZE_1MB
    self._test_result.bt_transfer_throughput_kbps = (
        nearby_snippet_1.transfer_file(
            file_1_mb, nc_constants.FILE_1M_PAYLOAD_TRANSFER_TIMEOUT,
            nc_constants.PayloadType.FILE))

    # second Wifi connection and transfer
    # 4. advertiser connect to wifi wlan
    if wifi_ssid:
      advertiser_wlan_latency = setup_utils.connect_to_wifi_wlan_till_success(
          advertiser, wifi_ssid, wifi_password)
      advertiser.log.info('connecting to wifi in '
                          f'{round(advertiser_wlan_latency.total_seconds())} s')
      advertiser.log.info(
          advertiser.nearby.wifiGetConnectionInfo().get('mFrequency')
      )
      self._test_result.advertiser_wifi_wlan_expected = True
      self._test_result.advertiser_wifi_wlan_latency = advertiser_wlan_latency
      time.sleep(
          self.test_parameters.target_post_wifi_connection_idle_time_sec
      )

    # 5. set up 2nd connection
    nearby_snippet_2 = nearby_connection_wrapper.NearbyConnectionWrapper(
        advertiser,
        discoverer,
        advertiser.nearby2,
        discoverer.nearby2,
        advertising_discovery_medium=advertising_discovery_medium,
        connection_medium=nc_constants.NearbyMedium.BT_ONLY,
        upgrade_medium=nc_constants.NearbyMedium(
            self.test_parameters.upgrade_medium
        ),
    )
    second_connection_setup_timeouts = nc_constants.ConnectionSetupTimeouts(
        nc_constants.SECOND_DISCOVERY_TIMEOUT,
        nc_constants.SECOND_CONNECTION_INIT_TIMEOUT,
        nc_constants.SECOND_CONNECTION_RESULT_TIMEOUT)
    try:
      nearby_snippet_2.start_nearby_connection(
          timeouts=second_connection_setup_timeouts,
          medium_upgrade_type=nc_constants.MediumUpgradeType.DISRUPTIVE,
          keep_alive_timeout_ms=self.test_parameters.keep_alive_timeout_ms,
          keep_alive_interval_ms=self.test_parameters.keep_alive_interval_ms,
      )
    finally:
      self._test_result.second_connection_setup_quality_info = (
          nearby_snippet_2.connection_quality_info
      )
      self._test_result.second_connection_setup_quality_info.medium_upgrade_expected = (
          True
      )

    # 6. transfer file through wifi
    file_1_gb = _TRANSFER_FILE_SIZE_1GB
    self._test_result.wifi_transfer_throughput_kbps = (
        nearby_snippet_2.transfer_file(
            file_1_gb, nc_constants.FILE_1G_PAYLOAD_TRANSFER_TIMEOUT,
            self.test_parameters.payload_type))

    # 7. disconnect 1st connection
    nearby_snippet_1.disconnect_endpoint()
    # 8. disconnect 2nd connection
    nearby_snippet_2.disconnect_endpoint()

  def _write_current_test_report(self) -> None:
    """Writes test report for each iteration."""

    quality_info = {
        '1st connection': (
            self._test_result.connection_setup_quality_info.get_dict()),
        'bt_kBps': self._test_result.bt_transfer_throughput_kbps,
        '2nd connection': (
            self._test_result.second_connection_setup_quality_info.get_dict()),
        'wifi_kBps': self._test_result.wifi_transfer_throughput_kbps,
    }

    if self._test_result.discoverer_wifi_wlan_expected:
      quality_info['src_wifi_connection'] = str(
          round(self._test_result.discoverer_wifi_wlan_latency.total_seconds())
      )
    if self._test_result.advertiser_wifi_wlan_expected:
      quality_info['tgt_wifi_connection'] = str(
          round(self._test_result.advertiser_wifi_wlan_latency.total_seconds())
      )
    test_report = {'quality_info': quality_info}

    self.discoverer.log.info(test_report)
    self.record_data({
        'Test Class': self.TAG,
        'Test Name': self.current_test_info.name,
        'properties': test_report,
    })

  def _collect_current_test_metrics(self) -> None:
    """Collects test result metrics for each iteration."""
    self._quick_start_test_metrics.first_discovery_latencies.append(
        self._test_result.connection_setup_quality_info.discovery_latency
    )
    self._quick_start_test_metrics.first_connection_latencies.append(
        self._test_result.connection_setup_quality_info.connection_latency
    )
    self._quick_start_test_metrics.bt_transfer_throughputs_kbps.append(
        self._test_result.bt_transfer_throughput_kbps
    )

    self._quick_start_test_metrics.second_discovery_latencies.append(
        self._test_result.second_connection_setup_quality_info.discovery_latency
    )
    self._quick_start_test_metrics.second_connection_latencies.append(
        self._test_result.second_connection_setup_quality_info.connection_latency
    )
    self._quick_start_test_metrics.medium_upgrade_latencies.append(
        self._test_result.second_connection_setup_quality_info.medium_upgrade_latency
    )
    self._quick_start_test_metrics.upgraded_wifi_transfer_mediums.append(
        self._test_result.second_connection_setup_quality_info.upgrade_medium)
    self._quick_start_test_metrics.wifi_transfer_throughputs_kbps.append(
        self._test_result.wifi_transfer_throughput_kbps
    )
    self._quick_start_test_metrics.discoverer_wifi_wlan_latencies.append(
        self._test_result.discoverer_wifi_wlan_latency)
    self._quick_start_test_metrics.advertiser_wifi_wlan_latencies.append(
        self._test_result.advertiser_wifi_wlan_latency)

  def _summary_upgraded_wifi_transfer_mediums(self) -> dict[str, int]:
    medium_counts = {}
    for (upgraded_medium
         ) in self._quick_start_test_metrics.upgraded_wifi_transfer_mediums:
      if upgraded_medium:
        medium_counts[upgraded_medium.name] = medium_counts.get(
            upgraded_medium.name, 0) + 1
    return medium_counts

  # @typing.override
  def _summary_test_results(self) -> None:
    """Summarizes test results of all iterations."""
    first_bt_transfer_stats = self._stats_throughput_result(
        'BT',
        self._quick_start_test_metrics.bt_transfer_throughputs_kbps,
        nc_constants.BT_TRANSFER_SUCCESS_RATE_TARGET_PERCENTAGE,
        self.test_parameters.bt_transfer_throughput_median_benchmark_kbps)

    second_wifi_transfer_stats = self._stats_throughput_result(
        'Wi-Fi',
        self._quick_start_test_metrics.wifi_transfer_throughputs_kbps,
        nc_constants.WIFI_TRANSFER_SUCCESS_RATE_TARGET_PERCENTAGE,
        self.test_parameters.wifi_transfer_throughput_median_benchmark_kbps)

    first_discovery_stats = self._stats_latency_result(
        self._quick_start_test_metrics.first_discovery_latencies)
    first_connection_stats = self._stats_latency_result(
        self._quick_start_test_metrics.first_connection_latencies)
    second_discovery_stats = self._stats_latency_result(
        self._quick_start_test_metrics.second_discovery_latencies)
    second_connection_stats = self._stats_latency_result(
        self._quick_start_test_metrics.second_connection_latencies)
    medium_upgrade_stats = self._stats_latency_result(
        self._quick_start_test_metrics.medium_upgrade_latencies)

    passed = True
    result_message = 'Passed'
    fail_message = ''
    if first_bt_transfer_stats.fail_targets:
      fail_message += self._generate_target_fail_message(
          first_bt_transfer_stats.fail_targets)
    if second_wifi_transfer_stats.fail_targets:
      fail_message += self._generate_target_fail_message(
          second_wifi_transfer_stats.fail_targets)
    if fail_message:
      passed = False
      result_message = 'Test Failed due to:\n' + fail_message

    detailed_stats = {
        '0 test iterations': self.performance_test_iterations,
        '1 Completed BT/Wi-Fi transfer': (
            f'{first_bt_transfer_stats.success_count}'
            f' / {second_wifi_transfer_stats.success_count}'),
        '2 BT transfer failures': {
            '1 discovery': first_discovery_stats.failure_count,
            '2 connection': first_connection_stats.failure_count,
            '3 transfer': self.performance_test_iterations - (
                first_bt_transfer_stats.success_count),
        },
        '3 Wi-Fi transfer failures': {
            '1 discovery': second_discovery_stats.failure_count,
            '2 connection': second_connection_stats.failure_count,
            '3 upgrade': medium_upgrade_stats.failure_count,
            '4 transfer': self.performance_test_iterations - (
                second_wifi_transfer_stats.success_count),
        },
        '4 Medium upgrade count': (
            self._summary_upgraded_wifi_transfer_mediums()),
        '5 50% and 95% of BT transfer speed (KBps)': (
            f'{first_bt_transfer_stats.percentile_50_kbps}'
            f' / {first_bt_transfer_stats.percentile_95_kbps}'),
        '6 50% and 95% of Wi-Fi transfer speed(KBps)': (
            f'{second_wifi_transfer_stats.percentile_50_kbps}'
            f' / {second_wifi_transfer_stats.percentile_95_kbps}'),
        '7 50% and 95% of discovery latency(sec)': (
            f'{first_discovery_stats.percentile_50}'
            f' / {first_discovery_stats.percentile_95} (1st), '
            f'{second_discovery_stats.percentile_50}'
            f' / {second_discovery_stats.percentile_95} (2nd)'),
        '8 50% and 95% of connection latency(sec)': (
            f'{first_connection_stats.percentile_50}'
            f' / {first_connection_stats.percentile_95} (1st), '
            f'{second_connection_stats.percentile_50}'
            f' / {second_connection_stats.percentile_95} (2nd)'),
        '9 50% and 95% of medium upgrade latency(sec)': (
            f'{medium_upgrade_stats.percentile_50}'
            f' / {medium_upgrade_stats.percentile_95}'),
    }

    self.record_data({
        'Test Class': self.TAG,
        'properties': {
            'test_script_version': _TEST_SCRIPT_VERSION,
            '00_test_report_alias_name': (
                self.test_parameters.test_report_alias_name),
            '01_test_result': result_message,
            '02_source_device_serial': self.discoverer.serial,
            '03_target_device_serial': self.advertiser.serial,
            '04_source_GMS_version': setup_utils.dump_gms_version(
                self.discoverer),
            '05_target_GMS_version': setup_utils.dump_gms_version(
                self.advertiser),
            '06_detailed_stats': detailed_stats
            }
        })

    asserts.assert_true(passed, result_message)


if __name__ == '__main__':
  test_runner.main()
