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

import random
import time
from acts.signals import TestFailure
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import BtInterferenceBaseTest
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import get_iperf_results
from acts_contrib.test_utils.power.PowerBTBaseTest import ramp_attenuation
from multiprocessing import Process, Queue

DEFAULT_THDN_THRESHOLD = 0.9
MAX_ATTENUATION = 95
TIME_OVERHEAD = 2


class BtInterferenceDynamicTest(BtInterferenceBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        self.iperf_duration = self.audio_params['duration'] + TIME_OVERHEAD
        self.wait_for_interference = self.dynamic_wifi_interference[
            'waittime_to_inject_interference']
        self.channel_change_interval = self.dynamic_wifi_interference[
            'channel_change_interval_second']
        self.interference_channels = self.dynamic_wifi_interference[
            'two_hoppable_channels'] + self.dynamic_wifi_interference[
                'one_hoppable_channel']

        self.bt_signal_levels = list(
            self.dynamic_wifi_interference['bt_signal_level'].keys())
        self.wifi_int_levels = list(
            self.dynamic_wifi_interference['interference_level'].keys())
        self.bt_atten_levels = list(
            self.dynamic_wifi_interference['bt_signal_level'].values())
        self.wifi_atten_levels = list(
            self.dynamic_wifi_interference['interference_level'].values())
        for bt_level in self.bt_signal_levels:
            bt_atten_level = self.dynamic_wifi_interference['bt_signal_level'][
                bt_level]
            for wifi_level in self.wifi_int_levels:
                interference_atten_level = self.dynamic_wifi_interference[
                    'interference_level'][wifi_level]
                self.generate_test_case_randomchange(
                    bt_atten_level, interference_atten_level,
                    self.channel_change_interval)
                for channels in self.interference_channels:
                    self.generate_test_case(bt_atten_level,
                                            interference_atten_level, channels)

    def generate_test_case(self, bt_atten_level, interference_atten_level,
                           dynamic_channels):
        """Function to generate test cases with different parameters.
        Args:
            bt_atten_level: bt path attenuation level
            interference_atten_level: wifi interference path attenuation level
            channels: wifi interference channel or channel combination
        """
        def test_case_fn():
            self.bt_afh_with_dynamic_interference(bt_atten_level,
                                                  interference_atten_level,
                                                  dynamic_channels)

        bt_signal_level = self.bt_signal_levels[self.bt_atten_levels.index(
            bt_atten_level)]
        wifi_int_level = self.wifi_int_levels[self.wifi_atten_levels.index(
            interference_atten_level)]
        interference_chans_before = dynamic_channels[0]
        interference_chans_after = dynamic_channels[1]
        chans_before_str = 'channel_'
        chans_after_str = 'channel_'
        if 0 in interference_chans_before:
            chans_before_str = 'no_interference'
        else:
            for i in interference_chans_before:
                chans_before_str = chans_before_str + str(i) + '_'
        for i in interference_chans_after:
            chans_after_str = chans_after_str + str(i) + '_'
        test_case_name = ('test_bt_afh_from_{}to_{}bt_signal_level_{}_'
                          'interference_level_{}'.format(
                              chans_before_str, chans_after_str,
                              bt_signal_level, wifi_int_level))
        setattr(self, test_case_name, test_case_fn)

    def generate_test_case_randomchange(self, bt_atten_level,
                                        interference_atten_level, interval):
        def test_case_fn():
            self.bt_afh_with_fast_changing_interference(
                bt_atten_level, interference_atten_level, interval)

        bt_signal_level = self.bt_signal_levels[self.bt_atten_levels.index(
            bt_atten_level)]
        wifi_int_level = self.wifi_atten_levels[self.wifi_atten_levels.index(
            interference_atten_level)]
        test_case_name = ('test_bt_afh_with_random_channel_interference_bt'
                          '_signal_level_{}_interference_level_{}'.format(
                              bt_signal_level, wifi_int_level))
        setattr(self, test_case_name, test_case_fn)

    def interference_rssi_mapping_from_attenuation(self, interference_level):
        """Function to get wifi rssi-to-interference level mapping
        Args:
            interference_level: interference level in terms of attenuation
        """
        self.log.info('Get WiFi RSSI at the desired attenuation level')
        for obj in self.wifi_int_pairs:
            obj.attenuator.set_atten(interference_level)
        self.get_interference_rssi()

    def get_rssi_at_channel(self, channel):
        """Function to get wifi rssi-to-interference level at each channel
        Args:
            channel: the channel to query the rssi
        Returns:
            rssi: wifi rssi at the queried channel
        """
        for item in self.interference_rssi:
            if item['chan'] == channel:
                rssi = item['rssi']
        return rssi

    def inject_dynamic_wifi_interference(self, interference_level,
                                         interference_channels, time_wait):
        """Function to inject dynamic wifi interference to bt link.
        Args:
            interference_level: signal strength of interference, represented
                by attenuation level
            interference_channels: interference channel for before and after,
                e.g. [chans_before, chans_after]
            time_wait: time wait to inject new interference
        """
        all_pair = range(len(self.wifi_int_pairs))
        #List of channels before and after changing the interference
        interference_chans_before = interference_channels[0]
        interference_chans_after = interference_channels[1]
        #Set existing wifi interference attenuation level
        if 0 not in interference_chans_before:
            interference_pair_indices_before = self.locate_interference_pair_by_channel(
                interference_chans_before)
            inactive_interference_pair_indices_before = [
                item for item in all_pair
                if item not in interference_pair_indices_before
            ]
            self.log.info(
                'Set pre-existing interference before A2DP streaming')
            for i in interference_pair_indices_before:
                self.log.info(
                    'Set {} dB on attenuator {}, wifi rssi {} dBm at chan {}'.
                    format(
                        interference_level, i + 1,
                        self.get_rssi_at_channel(
                            self.wifi_int_pairs[i].channel),
                        self.wifi_int_pairs[i].channel))
                self.wifi_int_pairs[i].attenuator.set_atten(interference_level)
            for i in inactive_interference_pair_indices_before:
                self.log.info('Set attenuation {} dB on attenuator {}'.format(
                    MAX_ATTENUATION, i + 1))
                self.wifi_int_pairs[i].attenuator.set_atten(MAX_ATTENUATION)
        ##Debug_purpose
        for i in self.attenuators:
            self.log.info(i.get_atten())

        #Set after change wifi interference attenuation level
        interference_pair_indices_after = self.locate_interference_pair_by_channel(
            interference_chans_after)
        inactive_interference_pair_indices_after = [
            item for item in all_pair
            if item not in interference_pair_indices_after
        ]
        #Wait for time_wait second to inject new interference
        time.sleep(time_wait)
        self.log.info('Inject new interference during A2DP streaming')
        for i in interference_pair_indices_after:
            self.log.info(
                'Set {} dB on attenuator {}, with wifi rssi {} dBm at chan {}'.
                format(
                    interference_level, i + 1,
                    self.get_rssi_at_channel(self.wifi_int_pairs[i].channel),
                    self.wifi_int_pairs[i].channel))
            self.wifi_int_pairs[i].attenuator.set_atten(interference_level)
        for i in inactive_interference_pair_indices_after:
            self.log.info('Set attenuation {} dB on attenuator {}'.format(
                MAX_ATTENUATION, i + 1))
            self.wifi_int_pairs[i].attenuator.set_atten(MAX_ATTENUATION)
        ##Debug_purpose
        for i in self.attenuators:
            self.log.info('Attenuator {} set to {} dB'.format(
                self.attenuators.index(i) + 1, i.get_atten()))
        self.log.info('Dymanic inteference injected')

    def inject_fast_changing_wifi_interference(self, interference_level,
                                               interval):
        """Function to inject changing wifi interference one channel a time.
        Args:
            interference_level: signal strength of interference, represented
                by attenuation level
            interval: interval between channel changes
        """
        all_pair = range(len(self.wifi_int_pairs))
        #Set initial WiFi interference at channel 1
        self.log.info('Start with interference at channel 1')
        self.wifi_int_pairs[0].attenuator.set_atten(interference_level)
        self.wifi_int_pairs[1].attenuator.set_atten(MAX_ATTENUATION)
        self.wifi_int_pairs[2].attenuator.set_atten(MAX_ATTENUATION)
        current_int_pair = [0]
        inactive_int_pairs = [
            item for item in all_pair if item not in current_int_pair
        ]
        time.sleep(interval)
        #Inject randomlized channel wifi interference
        self.log.info(
            'Inject random changing channel (1,6,11) wifi interference'
            'every {} second'.format(interval))
        while True:
            current_int_pair = [
                random.randint(inactive_int_pairs[0], inactive_int_pairs[1])
            ]
            inactive_int_pairs = [
                item for item in all_pair if item not in current_int_pair
            ]
            self.wifi_int_pairs[current_int_pair[0]].attenuator.set_atten(
                interference_level)
            self.log.info(
                'Current interference {} at channel {} with rssi {} dBm'.
                format(
                    interference_level,
                    self.wifi_int_pairs[current_int_pair[0]].channel,
                    self.get_rssi_at_channel(
                        self.wifi_int_pairs[current_int_pair[0]].channel)))
            for i in inactive_int_pairs:
                self.wifi_int_pairs[i].attenuator.set_atten(MAX_ATTENUATION)
            ##Debug_purpose
            for i in self.attenuators:
                self.log.info('Attenuator {} set to {} dB'.format(
                    self.attenuators.index(i) + 1, i.get_atten()))
            time.sleep(interval)

    def bt_afh_with_dynamic_interference(self, bt_atten_level,
                                         interference_atten_level,
                                         dynamic_channels):
        """Run a2dp audio quality with dynamic interference added.
        Args:
            bt_atten_level: signal level of bt in terms of attenuation
            interference_atten_level: interference level in terms of attenuation
            dynamic_channels: interference channels before and after
        """
        ramp_attenuation(self.attenuator, bt_atten_level)
        self.interference_rssi_mapping_from_attenuation(
            interference_atten_level)
        [
            rssi_master, pwl_master, rssi_c0_master, rssi_c1_master,
            txpw_c0_master, txpw_c1_master, bftx_master, divtx_master
        ], [rssi_slave] = self._get_bt_link_metrics()
        rssi_primary = rssi_master.get(self.dut.serial, -127)
        tag_bt = 'bt_signal_level_{}'.format(
            bt_atten_level)
        procs_iperf = []
        for obj in self.wifi_int_pairs:
            obj.iperf_server.start()
            iperf_args = '-i 1 -t {} -p {} -J -R'.format(
                self.iperf_duration, obj.iperf_server.port)
            tag = 'chan_{}'.format(obj.channel)
            proc_iperf = Process(target=obj.iperf_client.start,
                                 args=(obj.server_address, iperf_args, tag))
            proc_iperf.start()
            procs_iperf.append(proc_iperf)
        self.log.info('Start IPERF on all three channels')
        queue = Queue()
        proc_bt_audio = Process(target=self.play_and_record_audio,
                                args=(self.audio_params['duration'], queue))
        proc_interference = Process(
            target=self.inject_dynamic_wifi_interference,
            args=(interference_atten_level, dynamic_channels,
                  self.wait_for_interference))
        proc_bt_audio.start()
        proc_interference.start()
        proc_bt_audio.join()
        proc_interference.join()
        for proc in procs_iperf:
            proc.join()
        for obj in self.wifi_int_pairs:
            iperf_throughput = get_iperf_results(obj.iperf_server)
            self.log.info(
                'Throughput for channel {} interference is {} Mbps'.format(
                    obj.channel, iperf_throughput))
            obj.iperf_server.stop()
        audio_captured = queue.get()
        thdns = self.run_thdn_analysis(audio_captured, tag_bt)
        self.log.info('THDN results are {}'.format(thdns))
        for thdn in thdns:
            if thdn >= self.audio_params['thdn_threshold']:
                raise TestFailure('AFH failed')

    def bt_afh_with_fast_changing_interference(self, bt_atten_level,
                                               interference_atten_level,
                                               interval):
        """Run a2dp audio quality with random channel fast changing interference
        Args:
            bt_signale_level: signal level of bt in terms of attenuation
            interference_level: interference level in terms of attenuation
            interval: interval between channel changes
        """
        ramp_attenuation(self.attenuator, bt_atten_level)
        self.interference_rssi_mapping_from_attenuation(
            interference_atten_level)
        [
            rssi_master, pwl_master, rssi_c0_master, rssi_c1_master,
            txpw_c0_master, txpw_c1_master, bftx_master, divtx_master
        ], [rssi_slave] = self._get_bt_link_metrics()
        rssi_primary = rssi_master.get(self.dut.serial, -127)
        tag_bt = 'bt_signal_level_{}'.format(
            bt_atten_level)
        procs_iperf = []
        #Start IPERF on all three interference pairs
        for obj in self.wifi_int_pairs:
            obj.iperf_server.start()
            iperf_args = '-i 1 -t {} -p {} -J -R'.format(
                self.iperf_duration, obj.iperf_server.port)
            tag = 'chan_{}'.format(obj.channel)
            proc_iperf = Process(target=obj.iperf_client.start,
                                 args=(obj.server_address, iperf_args, tag))
            proc_iperf.start()
            procs_iperf.append(proc_iperf)
        self.log.info('Start IPERF on all three channels')
        queue = Queue()
        proc_bt_audio = Process(target=self.play_and_record_audio,
                                args=(self.audio_params['duration'], queue))
        proc_interference = Process(
            target=self.inject_fast_changing_wifi_interference,
            args=(interference_atten_level, interval))
        proc_bt_audio.start()
        proc_interference.start()
        proc_bt_audio.join()
        while proc_bt_audio.is_alive():
            continue
        proc_interference.terminate()
        proc_interference.join()
        for proc in procs_iperf:
            proc.join()
        for obj in self.wifi_int_pairs:
            iperf_throughput = get_iperf_results(obj.iperf_server)
            self.log.info(
                'Throughput for channel {} interference is {} Mbps'.format(
                    obj.channel, iperf_throughput))
            obj.iperf_server.stop()
        audio_captured = queue.get()
        thdns = self.run_thdn_analysis(audio_captured, tag_bt)
        self.log.info('THDN results are {}'.format(thdns))
        for thdn in thdns:
            if thdn >= self.audio_params['thdn_threshold']:
                raise TestFailure('AFH failed')