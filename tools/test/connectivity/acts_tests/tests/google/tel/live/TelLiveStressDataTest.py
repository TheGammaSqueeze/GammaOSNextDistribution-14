#!/usr/bin/env python3
#
#   Copyright 2019 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
"""
    Test Script for Telephony Stress data Test
"""
import collections
import time

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_test_utils import iperf_test_by_adb
from acts_contrib.test_utils.tel.tel_test_utils import iperf_udp_test_by_adb
from acts.logger import epoch_to_log_line_timestamp
from acts.utils import get_current_epoch_time


class TelLiveStressDataTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.ad = self.android_devices[0]
        self.iperf_server_address = self.user_params.get("iperf_server",
                                                         '0.0.0.0')
        self.iperf_tcp_port = int(
            self.user_params.get("iperf_tcp_port", 0))
        self.iperf_udp_port = int(
            self.user_params.get("iperf_udp_port", 0))
        self.iperf_duration = int(
            self.user_params.get("iperf_duration", 60))
        self.iperf_iteration = int(
            self.user_params.get("iperf_iteration", 10))
        self.sleep_time_between_iperf_iterations = int(
            self.user_params.get("sleep_time_between_iperf_iterations", 2))

    def stress_test_upload(self, test_tcp=True):
        """Start the upload iperf stress test.

        Args:
            test_tcp: True for using TCP, using UDP otherwise.

        Returns:
            True if success, False if fail.
        """
        fail_count = collections.defaultdict(int)
        for i in range(1, self.iperf_iteration + 1):
            msg = "Stress Throughput Test %s Iteration: <%s> / <%s>" % (
                self.test_name, i, self.iperf_iteration)
            begin_time = get_current_epoch_time()
            self.log.info(msg)
            iteration_result = True
            if test_tcp:
                if not iperf_test_by_adb(self.log,
                                         self.ad,
                                         self.iperf_server_address,
                                         self.iperf_tcp_port,
                                         False,
                                         self.iperf_duration):
                    fail_count["upload"] += 1
                    iteration_result = False
                    self.log.error("%s upload failure.", msg)
            else:
                if not iperf_udp_test_by_adb(self.log,
                                             self.ad,
                                             self.iperf_server_address,
                                             self.iperf_udp_port,
                                             False,
                                             self.iperf_duration):
                    fail_count["upload"] += 1
                    iteration_result = False
                    self.log.error("%s upload failure.", msg)

            self.log.info("%s %s", msg, iteration_result)
            if not iteration_result:
                self._take_bug_report("%s_UploadNo_%s" % (self.test_name, i),
                                      begin_time)

            if self.sleep_time_between_iperf_iterations:
                self.ad.droid.goToSleepNow()
                time.sleep(self.sleep_time_between_iperf_iterations)

        test_result = True
        for failure, count in fail_count.items():
            if count:
                self.log.error("%s: %s %s failures in %s iterations",
                               self.test_name, count, failure,
                               self.iperf_iteration)
                test_result = False
        return test_result

    def stress_test_download(self, test_tcp=True):
        """Start the download iperf stress test.

        Args:
            test_tcp: True for using TCP, using UDP otherwise.

        Returns:
            True if success, False if fail.
        """
        fail_count = collections.defaultdict(int)
        for i in range(1, self.iperf_iteration + 1):
            msg = "Stress Throughput Test %s Iteration: <%s> / <%s>" % (
                self.test_name, i, self.iperf_iteration)
            begin_time = get_current_epoch_time()
            self.log.info(msg)
            iteration_result = True
            if test_tcp:
                if not iperf_test_by_adb(self.log,
                                         self.ad,
                                         self.iperf_server_address,
                                         self.iperf_tcp_port,
                                         True,
                                         self.iperf_duration):
                    fail_count["download"] += 1
                    iteration_result = False
                    self.log.error("%s download failure.", msg)
            else:
                if not iperf_udp_test_by_adb(self.log,
                                             self.ad,
                                             self.iperf_server_address,
                                             self.iperf_udp_port,
                                             True,
                                             self.iperf_duration):
                    fail_count["download"] += 1
                    iteration_result = False
                    self.log.error("%s download failure.", msg)

            self.log.info("%s %s", msg, iteration_result)
            if not iteration_result:
                self._take_bug_report("%s_DownloadNo_%s" % (self.test_name, i),
                                      begin_time)

            if self.sleep_time_between_iperf_iterations:
                self.ad.droid.goToSleepNow()
                time.sleep(self.sleep_time_between_iperf_iterations)

        test_result = True
        for failure, count in fail_count.items():
            if count:
                self.log.error("%s: %s %s failures in %s iterations",
                               self.test_name, count, failure,
                               self.iperf_iteration)
                test_result = False
        return test_result

    @test_tracker_info(uuid="190fdeb1-541e-455f-9f37-762a8e55c07f")
    @TelephonyBaseTest.tel_test_wrap
    def test_tcp_upload_stress(self):
        return iperf_test_by_adb(self.log,
                                 self.ad,
                                 self.iperf_server_address,
                                 self.iperf_tcp_port,
                                 False,
                                 self.iperf_duration)

    @test_tracker_info(uuid="af9805f8-6ed5-4e05-823e-d88dcef45637")
    @TelephonyBaseTest.tel_test_wrap
    def test_tcp_download_stress(self):
        return iperf_test_by_adb(self.log,
                                 self.ad,
                                 self.iperf_server_address,
                                 self.iperf_tcp_port,
                                 True,
                                 self.iperf_duration)

    @test_tracker_info(uuid="55bf5e09-dc7b-40bc-843f-31fed076ffe4")
    @TelephonyBaseTest.tel_test_wrap
    def test_udp_upload_stress(self):
        return iperf_udp_test_by_adb(self.log,
                                     self.ad,
                                     self.iperf_server_address,
                                     self.iperf_udp_port,
                                     False,
                                     self.iperf_duration)

    @test_tracker_info(uuid="02ae88b2-d597-45df-ab5a-d701d1125a0f")
    @TelephonyBaseTest.tel_test_wrap
    def test_udp_download_stress(self):
        return iperf_udp_test_by_adb(self.log,
                                     self.ad,
                                     self.iperf_server_address,
                                     self.iperf_udp_port,
                                     True,
                                     self.iperf_duration)

    @test_tracker_info(uuid="79aaa7ec-5046-4ffe-b27a-ca93e404e9e0")
    @TelephonyBaseTest.tel_test_wrap
    def test_tcp_upload_data_stress(self):
        return self.stress_test_upload()

    @test_tracker_info(uuid="6a1e5032-9498-4d23-8ae9-db36f1a238c1")
    @TelephonyBaseTest.tel_test_wrap
    def test_tcp_download_data_stress(self):
        return self.stress_test_download()

    @test_tracker_info(uuid="22400c16-dbbb-41c9-afd0-86b525a0bcee")
    @TelephonyBaseTest.tel_test_wrap
    def test_udp_upload_data_stress(self):
        return self.stress_test_upload(test_tcp=False)

    @test_tracker_info(uuid="9f3b2818-5265-422e-9e6f-9ee08dfcc696")
    @TelephonyBaseTest.tel_test_wrap
    def test_udp_download_data_stress(self):
        return self.stress_test_download(test_tcp=False)