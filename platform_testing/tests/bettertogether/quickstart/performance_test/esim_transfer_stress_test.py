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

"""Esim transfer stress test, only use Bluetooth connection."""

import dataclasses
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

_DELAY_BETWEEN_EACH_TEST_CYCLE = datetime.timedelta(seconds=5)
_TRANSFER_FILE_SIZE_1MB = 1024

_PERFORMANCE_TEST_REPEAT_COUNT = 100
_PERFORMANCE_TEST_MAX_CONSECUTIVE_ERROR = 5


class EsimTransferStressTest(nc_base_test.NCBaseTestClass):
  """Esim transfer stress test."""

  @dataclasses.dataclass(frozen=False)
  class EsimTransferTestMetrics:
    """Metrics data for esim transfer test."""
    discovery_latencies: list[datetime.timedelta] = dataclasses.field(
        default_factory=list[datetime.timedelta])
    connection_latencies: list[datetime.timedelta] = dataclasses.field(
        default_factory=list[datetime.timedelta])
    bt_transfer_throughputs_kbps: list[float] = dataclasses.field(
        default_factory=list[float])

  # @typing.override
  def __init__(self, configs):
    super().__init__(configs)
    self._test_result = nc_constants.SingleTestResult()
    self._esim_transfer_test_metrics = self.EsimTransferTestMetrics()
    self._test_script_version = _TEST_SCRIPT_VERSION

  # @typing.override
  def setup_class(self):
    super().setup_class()
    self.performance_test_iterations = getattr(
        self.test_esim_transfer_performance, base_test.ATTR_REPEAT_CNT)
    logging.info('performance test iterations: %s',
                 self.performance_test_iterations)

  @base_test.repeat(
      count=_PERFORMANCE_TEST_REPEAT_COUNT,
      max_consecutive_error=_PERFORMANCE_TEST_MAX_CONSECUTIVE_ERROR)
  def test_esim_transfer_performance(self):
    """Esim transfer stress test, which only transfer data through BT."""
    try:
      self._mimic_esim_transfer()
    finally:
      self._write_current_test_report()
      self._collect_current_test_metrics()
      time.sleep(_DELAY_BETWEEN_EACH_TEST_CYCLE.total_seconds())

  def _mimic_esim_transfer(self):
    if self.test_parameters.toggle_airplane_mode_target_side:
      setup_utils.toggle_airplane_mode(self.advertiser)
    if self.test_parameters.reset_wifi_connection:
      self._reset_wifi_connection()
    self._test_result = nc_constants.SingleTestResult()
    # 1. connect to wifi
    wifi_ssid = self.test_parameters.wifi_ssid
    wifi_password = self.test_parameters.wifi_password
    if wifi_ssid:
      discoverer_wifi_latency = setup_utils.connect_to_wifi_wlan_till_success(
          self.discoverer, wifi_ssid, wifi_password
      )
      self.discoverer.log.info(
          'connecting to wifi in '
          f'{round(discoverer_wifi_latency.total_seconds())} s'
      )
      advertiser_wlan_latency = setup_utils.connect_to_wifi_wlan_till_success(
          self.advertiser, wifi_ssid, wifi_password)
      self.advertiser.log.info(
          'connecting to wifi in '
          f'{round(advertiser_wlan_latency.total_seconds())} s')
      self.advertiser.log.info(
          self.advertiser.nearby.wifiGetConnectionInfo().get('mFrequency')
      )

    # 2. set up BT connection
    advertising_discovery_medium = nc_constants.NearbyMedium(
        self.test_parameters.advertising_discovery_medium
    )
    nearby_snippet_1 = nearby_connection_wrapper.NearbyConnectionWrapper(
        self.advertiser,
        self.discoverer,
        self.advertiser.nearby,
        self.discoverer.nearby,
        advertising_discovery_medium=advertising_discovery_medium,
        connection_medium=nc_constants.NearbyMedium.BT_ONLY,
        upgrade_medium=nc_constants.NearbyMedium.BT_ONLY,
    )
    connection_setup_timeouts = nc_constants.ConnectionSetupTimeouts(
        nc_constants.FIRST_DISCOVERY_TIMEOUT,
        nc_constants.FIRST_CONNECTION_INIT_TIMEOUT,
        nc_constants.FIRST_CONNECTION_RESULT_TIMEOUT)

    try:
      nearby_snippet_1.start_nearby_connection(
          timeouts=connection_setup_timeouts,
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
    # 4. disconnect
    nearby_snippet_1.disconnect_endpoint()

  def _write_current_test_report(self):
    """Writes test report for each iteration."""

    quality_info = {
        'bt connection': (
            self._test_result.connection_setup_quality_info.get_dict()),
        'bt_kBps': self._test_result.bt_transfer_throughput_kbps,
    }
    test_report = {'quality_info': quality_info}

    self.discoverer.log.info(test_report)
    self.record_data({
        'Test Class': self.TAG,
        'Test Name': self.current_test_info.name,
        'properties': test_report,
    })

  def _collect_current_test_metrics(self):
    """Collects test result metrics for each iteration."""
    self._esim_transfer_test_metrics.discovery_latencies.append(
        self._test_result.connection_setup_quality_info.discovery_latency
    )
    self._esim_transfer_test_metrics.connection_latencies.append(
        self._test_result.connection_setup_quality_info.connection_latency
    )
    self._esim_transfer_test_metrics.bt_transfer_throughputs_kbps.append(
        self._test_result.bt_transfer_throughput_kbps
    )

  # @typing.override
  def _summary_test_results(self):
    """Summarizes test results of all iterations."""
    bt_transfer_stats = self._stats_throughput_result(
        'BT',
        self._esim_transfer_test_metrics.bt_transfer_throughputs_kbps,
        nc_constants.BT_TRANSFER_SUCCESS_RATE_TARGET_PERCENTAGE,
        self.test_parameters.bt_transfer_throughput_median_benchmark_kbps)

    discovery_stats = self._stats_latency_result(
        self._esim_transfer_test_metrics.discovery_latencies)
    connection_stats = self._stats_latency_result(
        self._esim_transfer_test_metrics.connection_latencies)

    passed = True
    result_message = 'Passed'
    fail_message = ''
    if bt_transfer_stats.fail_targets:
      fail_message += self._generate_target_fail_message(
          bt_transfer_stats.fail_targets)
    if fail_message:
      passed = False
      result_message = 'Test Failed due to:\n' + fail_message

    detailed_stats = {
        '0 test iterations': self.performance_test_iterations,
        '1 Completed BT transfer': f'{bt_transfer_stats.success_count}',
        '2 BT transfer failures': {
            '1 discovery': discovery_stats.failure_count,
            '2 connection': connection_stats.failure_count,
            '3 transfer': self.performance_test_iterations - (
                bt_transfer_stats.success_count),
        },
        '3 50% and 95% of BT transfer speed (KBps)': (
            f'{bt_transfer_stats.percentile_50_kbps}'
            f' / {bt_transfer_stats.percentile_95_kbps}'),
        '4 50% and 95% of discovery latency(sec)': (
            f'{discovery_stats.percentile_50}'
            f' / {discovery_stats.percentile_95}'),

        '5 50% and 95% of connection latency(sec)': (
            f'{connection_stats.percentile_50}'
            f' / {connection_stats.percentile_95}'),
    }

    self.record_data({
        'Test Class': self.TAG,
        'properties': {
            'test_script_version': self._test_script_version,
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
