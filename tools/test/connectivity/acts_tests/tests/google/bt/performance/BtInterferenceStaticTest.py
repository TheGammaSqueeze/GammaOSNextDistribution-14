#!/usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.
"""Stream music through connected device from phone across different
attenuations."""

from acts_contrib.test_utils.bt.BtInterferenceBaseTest import BtInterferenceBaseTest
from acts.metrics.loggers.blackbox import BlackboxMetricLogger
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import get_iperf_results
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import inject_static_wifi_interference
from multiprocessing import Process, Queue
from acts.signals import TestPass

DEFAULT_THDN_THRESHOLD = 0.9
MAX_ATTENUATION = 95
TIME_OVERHEAD = 2


class BtInterferenceStaticTest(BtInterferenceBaseTest):

    def __init__(self, configs):
        super().__init__(configs)
        self.bt_attenuation_range = range(self.attenuation_vector['start'],
                                          self.attenuation_vector['stop'] + 1,
                                          self.attenuation_vector['step'])
        self.iperf_duration = self.audio_params['duration'] + TIME_OVERHEAD
        test_metrics = [
            'wifi_chan1_rssi', 'wifi_chan6_rssi', 'wifi_chan11_rssi', 'bt_range'
        ]
        for metric in test_metrics:
            setattr(self, '{}_metric'.format(metric),
                    BlackboxMetricLogger.for_test_case(metric_name=metric))

    def setup_generated_tests(self):
        for level in list(
                self.static_wifi_interference['interference_level'].values()):
            for channels in self.static_wifi_interference['channels']:
                arg_set = [(level, channels)]
                self.generate_tests(
                    test_logic=self.bt_range_with_static_wifi_interference,
                    name_func=self.create_test_name,
                    arg_sets=arg_set)

    def create_test_name(self, level, channels):
        str_channel_test = ''
        for i in channels:
            str_channel_test = str_channel_test + str(i) + "_"
        test_case_name = ('test_bt_range_with_static_interference_level_{}_'
                          'channel_{}'.format(level, str_channel_test))
        return test_case_name

    def bt_range_with_static_wifi_interference(self, level, channels):
        """Test function to measure bt range under interference.

        Args:
            interference_level: wifi interference level
            channels: wifi interference channels
        """
        #setup wifi interference by setting the correct attenuator
        inject_static_wifi_interference(self.wifi_int_pairs, level, channels)
        # Read interference RSSI
        self.get_interference_rssi()
        self.wifi_chan1_rssi_metric.metric_value = self.interference_rssi[0][
            'rssi']
        self.wifi_chan6_rssi_metric.metric_value = self.interference_rssi[1][
            'rssi']
        self.wifi_chan11_rssi_metric.metric_value = self.interference_rssi[2][
            'rssi']
        for atten in self.bt_attenuation_range:
            # Set attenuation for BT link
            self.attenuator.set_atten(atten)
            [
                rssi_master, pwl_master, rssi_c0_master, rssi_c1_master,
                txpw_c0_master, txpw_c1_master, bftx_master, divtx_master
            ], [rssi_slave] = self._get_bt_link_metrics()
            rssi_primary = rssi_master.get(self.dut.serial, -127)
            pwl_primary = pwl_master.get(self.dut.serial, -127)
            rssi_secondary = rssi_slave.get(self.bt_device_controller.serial,
                                            -127)
            tag = 'attenuation_{}dB'.format(atten)
            self.log.info(
                'BT attenuation set to {} dB and start A2DP streaming'.format(
                    atten))
            procs_iperf = []
            for obj in self.wifi_int_pairs:
                obj.iperf_server.start()
                self.log.info('Started IPERF server at port {}'.format(
                    obj.iperf_server.port))
                iperf_args = '-i 1 -t {} -p {} -J -R'.format(
                    self.iperf_duration, obj.iperf_server.port)
                tag = 'chan_{}'.format(obj.channel)
                proc_iperf = Process(target=obj.iperf_client.start,
                                     args=(obj.server_address, iperf_args, tag))
                procs_iperf.append(proc_iperf)

            #play a2dp streaming and run thdn analysis
            queue = Queue()
            proc_bt = Process(target=self.play_and_record_audio,
                              args=(self.audio_params['duration'], queue))
            for proc in procs_iperf:
                proc.start()
            proc_bt.start()
            proc_bt.join()
            for proc in procs_iperf:
                proc.join()
            for obj in self.wifi_int_pairs:
                iperf_throughput = get_iperf_results(obj.iperf_server)
                self.log.info(
                    'Throughput for channel {} interference is {} Mbps'.format(
                        obj.channel, iperf_throughput))
                obj.iperf_server.stop()
                self.log.info('Stopped IPERF server at port {}'.format(
                    obj.iperf_server.port))
            audio_captured = queue.get()
            thdns = self.run_thdn_analysis(audio_captured, tag)
            self.log.info('THDN results are {} at {} dB attenuation'.format(
                thdns, atten))
            self.log.info('DUT rssi {} dBm, master tx power level {}, '
                          'RemoteDevice rssi {} dBm'.format(
                              rssi_primary, pwl_primary, rssi_secondary))
            for thdn in thdns:
                if thdn >= self.audio_params['thdn_threshold']:
                    self.log.info('Under the WiFi interference condition: '
                                  'channel 1 RSSI: {} dBm, '
                                  'channel 6 RSSI: {} dBm'
                                  'channel 11 RSSI: {} dBm'.format(
                                      self.interference_rssi[0]['rssi'],
                                      self.interference_rssi[1]['rssi'],
                                      self.interference_rssi[2]['rssi']))
                    raise TestPass(
                        'Max range for this test is {}, with BT master RSSI at'
                        ' {} dBm'.format(atten, rssi_primary))

