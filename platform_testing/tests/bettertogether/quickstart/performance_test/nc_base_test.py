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

"""Mobly base test class for Neaby Connections."""

import dataclasses
import datetime
import logging
import time

from mobly import asserts
from mobly import base_test
from mobly import records
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import errors

from performance_test import nc_constants
from performance_test import setup_utils

NEARBY_SNIPPET_PACKAGE_NAME = 'com.google.android.nearby.mobly.snippet'


class NCBaseTestClass(base_test.BaseTestClass):
  """Nearby Connection E2E tests."""

  def __init__(self, configs):
    super().__init__(configs)
    self.ads: list[android_device.AndroidDevice] = []
    self.advertiser: android_device.AndroidDevice = None
    self.discoverer: android_device.AndroidDevice = None
    self.test_parameters: nc_constants.TestParameters = None
    self._test_script_version = None
    self._nearby_snippet_apk_path: str = None
    self.performance_test_iterations: int = 1
    self.num_bug_reports: int = 0

  def setup_class(self) -> None:
    self.ads = self.register_controller(android_device, min_number=2)
    self.test_parameters = self._get_test_parameter()
    self._nearby_snippet_apk_path = self.user_params.get('files', {}).get(
        'nearby_snippet', [''])[0]

    # set run identifier property
    self._set_run_identifier()

    utils.concurrent_exec(
        self._setup_android_device,
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

    try:
      self.discoverer = android_device.get_device(
          self.ads, role='source_device'
      )
      self.advertiser = android_device.get_device(
          self.ads, role='target_device'
      )
    except errors.Error:
      logging.warning(
          'The source,target devices are not specified in testbed;'
          'The result may not be expected.'
      )
      self.advertiser, self.discoverer = self.ads

  def _set_run_identifier(self) -> None:
    """Set a run_identifier property describing the test run context."""
    run_identifier = {}
    run_identifier['test_version'] = self._test_script_version
    run_identifier['alias'] = self.test_parameters.test_report_alias_name
    run_identifier['devices'] = [
        f'{ad.model}({ad.build_info["build_id"]})' for ad in self.ads
    ]
    run_identifier_str = ', '.join(
        [f'{key}:{value}' for key, value in run_identifier.items()]
    )
    run_identifier_str = f'{{{run_identifier_str}}}'
    self.record_data(
        {'properties': {'run_identifier': run_identifier_str}}
    )

  def _disconnect_from_wifi(self, ad: android_device.AndroidDevice) -> None:
    if not ad.is_adb_root:
      ad.log.info("Can't clear wifi network in non-rooted device")
      return
    ad.nearby.wifiClearConfiguredNetworks()
    time.sleep(nc_constants.WIFI_DISCONNECTION_DELAY.total_seconds())

  def _setup_android_device(self, ad: android_device.AndroidDevice) -> None:
    if not ad.is_adb_root:
      if self.test_parameters.allow_unrooted_device:
        ad.log.info('Unrooted device is detected. Test coverage is limited')
      else:
        asserts.skip('The test only can run on rooted device.')

    setup_utils.disable_gms_auto_updates(ad)

    ad.debug_tag = ad.serial + '(' + ad.adb.getprop('ro.product.model') + ')'
    ad.log.info('try to install nearby_snippet_apk')
    if self._nearby_snippet_apk_path:
      setup_utils.install_apk(ad, self._nearby_snippet_apk_path)
    else:
      ad.log.warning(
          'nearby_snippet apk is not specified, '
          'make sure it is installed in the device'
      )
    ad.load_snippet('nearby', NEARBY_SNIPPET_PACKAGE_NAME)

    ad.log.info('grant manage external storage permission')
    setup_utils.grant_manage_external_storage_permission(
        ad, NEARBY_SNIPPET_PACKAGE_NAME
    )

    if not ad.nearby.wifiIsEnabled():
      ad.nearby.wifiEnable()
    self._disconnect_from_wifi(ad)
    setup_utils.enable_logs(ad)

    setup_utils.disable_redaction(ad)

    if (
        self.test_parameters.upgrade_medium
        == nc_constants.NearbyMedium.WIFIAWARE_ONLY.value
    ):
      setup_utils.enable_wifi_aware(ad)

    if self.test_parameters.wifi_country_code:
      setup_utils.set_country_code(
          ad, self.test_parameters.wifi_country_code
      )

  def setup_test(self):
    self._reset_nearby_connection()

  def _reset_wifi_connection(self) -> None:
    """Resets wifi connections on both devices."""
    self.discoverer.nearby.wifiClearConfiguredNetworks()
    self.advertiser.nearby.wifiClearConfiguredNetworks()
    time.sleep(nc_constants.WIFI_DISCONNECTION_DELAY.total_seconds())

  def _reset_nearby_connection(self) -> None:
    """Resets nearby connection."""
    self.discoverer.nearby.stopDiscovery()
    self.discoverer.nearby.stopAllEndpoints()
    self.advertiser.nearby.stopAdvertising()
    self.advertiser.nearby.stopAllEndpoints()
    time.sleep(nc_constants.NEARBY_RESET_WAIT_TIME.total_seconds())

  def _teardown_device(self, ad: android_device.AndroidDevice) -> None:
    ad.nearby.transferFilesCleanup()
    setup_utils.enable_gms_auto_updates(ad)
    if self.test_parameters.disconnect_wifi_after_test:
      self._disconnect_from_wifi(ad)
    ad.unload_snippet('nearby')

  def teardown_test(self) -> None:
    utils.concurrent_exec(
        lambda d: d.services.create_output_excerpts_all(self.current_test_info),
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )

  def teardown_class(self) -> None:
    utils.concurrent_exec(
        self._teardown_device,
        param_list=[[ad] for ad in self.ads],
        raise_on_exception=True,
    )
    # handle summary results
    self._summary_test_results()

  def _summary_test_results(self) -> None:
    pass

  def _get_test_parameter(self) -> nc_constants.TestParameters:
    test_parameters_names = {
        field.name for field in dataclasses.fields(nc_constants.TestParameters)
    }
    test_parameters = nc_constants.TestParameters(
        **{
            key: val
            for key, val in self.user_params.items()
            if key in test_parameters_names
        }
    )

    return test_parameters

  def on_fail(self, record: records.TestResultRecord) -> None:
    self.num_bug_reports = self.num_bug_reports + 1
    if (self.num_bug_reports <= nc_constants.MAX_NUM_BUG_REPORT):
      logging.info('take bug report for failure')
      android_device.take_bug_reports(
          self.ads,
          destination=self.current_test_info.output_path,
    )

  def _stats_throughput_result(
      self,
      medium_name: str,
      throughput_indicators: list[float],
      success_rate_target: float,
      median_benchmark_kbps: float,
  ) -> nc_constants.ThroughputResultStats:
    """Statistics the throughput test result of all iterations."""
    n = self.performance_test_iterations
    filtered = [
        x
        for x in throughput_indicators
        if x != nc_constants.UNSET_THROUGHPUT_KBPS
    ]
    if not filtered:
      # all test cases are failed
      return nc_constants.ThroughputResultStats(
          success_rate=0.0,
          average_kbps=0.0,
          percentile_50_kbps=0.0,
          percentile_95_kbps=0.0,
          success_count=0,
          fail_targets=[
              nc_constants.FailTargetSummary(
                  f'{medium_name} transfer success rate',
                  0.0,
                  success_rate_target,
                  '%',
              )
          ],
      )
    # use the descenting order of the throughput
    filtered.sort(reverse=True)
    success_count = len(filtered)
    success_rate = round(
        success_count * 100.0 / n, nc_constants.SUCCESS_RATE_PRECISION_DIGITS
    )
    average_kbps = round(sum(filtered) / len(filtered))
    percentile_50_kbps = filtered[
        int(len(filtered) * nc_constants.PERCENTILE_50_FACTOR)
    ]
    percentile_95_kbps = filtered[
        int(len(filtered) * nc_constants.PERCENTILE_95_FACTOR)
    ]
    fail_targets: list[nc_constants.FailTargetSummary] = []
    if success_rate < success_rate_target:
      fail_targets.append(
          nc_constants.FailTargetSummary(
              f'{medium_name} transfer success rate',
              success_rate,
              success_rate_target,
              '%',
          )
      )
    if percentile_50_kbps < median_benchmark_kbps:
      fail_targets.append(
          nc_constants.FailTargetSummary(
              f'{medium_name} median transfer speed (KBps)',
              percentile_50_kbps,
              median_benchmark_kbps,
          )
      )
    return nc_constants.ThroughputResultStats(
        success_rate,
        average_kbps,
        percentile_50_kbps,
        percentile_95_kbps,
        success_count,
        fail_targets,
    )

  def _stats_latency_result(
      self, latency_indicators: list[datetime.timedelta]
  ) -> nc_constants.LatencyResultStats:
    n = self.performance_test_iterations
    filtered = [
        latency.total_seconds()
        for latency in latency_indicators
        if latency != nc_constants.UNSET_LATENCY
    ]
    if not filtered:
      # All test cases are failed.
      return nc_constants.LatencyResultStats(
          average_latency=0.0,
          percentile_50=0.0,
          percentile_95=0.0,
          failure_count=n,
      )

    filtered.sort()
    average = (
        round(
            sum(filtered) / len(filtered), nc_constants.LATENCY_PRECISION_DIGITS
        )
        / n
    )
    percentile_50 = round(
        filtered[int(len(filtered) * nc_constants.PERCENTILE_50_FACTOR)],
        nc_constants.LATENCY_PRECISION_DIGITS,
    )
    percentile_95 = round(
        filtered[int(len(filtered) * nc_constants.PERCENTILE_95_FACTOR)],
        nc_constants.LATENCY_PRECISION_DIGITS,
    )

    return nc_constants.LatencyResultStats(
        average, percentile_50, percentile_95, n - len(filtered)
    )

  def _generate_target_fail_message(
      self, fail_targets: list[nc_constants.FailTargetSummary]
  ) -> str:
    return ''.join(
        f'{fail_target.title}: {fail_target.actual}{fail_target.unit}'
        f' < {fail_target.goal}{fail_target.unit}\n'
        for fail_target in fail_targets
    )
