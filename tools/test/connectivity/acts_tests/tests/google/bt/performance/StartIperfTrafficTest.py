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
import time
import acts.controllers.iperf_client as ipc
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import BtInterferenceBaseTest
from acts_contrib.test_utils.power.PowerBaseTest import ObjNew
from multiprocessing import Process
from acts_contrib.test_utils.bt.BtInterferenceBaseTest import setup_ap_connection
from acts_contrib.test_utils.wifi import wifi_power_test_utils as wputils
from acts.signals import TestPass


class StartIperfTrafficTest(BtInterferenceBaseTest):
    """
    """

    def __init__(self, configs):
        super().__init__(configs)
        req_params = ["IperfDuration"]
        self.unpack_userparams(req_params)

    def setup_class(self):
        self.dut = self.android_devices[0]
        self.wifi_int_pairs = []
        for i in range(len(self.attenuators) - 1):
            tmp_dict = {
                'dut': self.android_devices[i],
                'ap': self.access_points[i],
                'network': self.wifi_networks[i],
                'channel': self.wifi_networks[i]['channel'],
                'iperf_server': self.iperf_servers[i],
                'ether_int': self.packet_senders[i],
                'iperf_client': ipc.IPerfClientOverAdb(self.android_devices[i])
            }
            tmp_obj = ObjNew(**tmp_dict)
            self.wifi_int_pairs.append(tmp_obj)
        ##Setup connection between WiFi APs and Phones and get DHCP address
        # for the interface
        for obj in self.wifi_int_pairs:
            brconfigs = setup_ap_connection(obj.dut, obj.network, obj.ap)
            iperf_server_address = wputils.wait_for_dhcp(
                obj.ether_int.interface)
            setattr(obj, 'server_address', iperf_server_address)
            setattr(obj, 'brconfigs', brconfigs)

    def setup_test(self):
        self.log.info("Setup test initiated")

    def teardown_class(self):
        for obj in self.wifi_int_pairs:
            obj.ap.bridge.teardown(obj.brconfigs)
            self.log.info('Stop IPERF server at port {}'.format(
                obj.iperf_server.port))
            obj.iperf_server.stop()
            self.log.info('Stop IPERF process on {}'.format(obj.dut.serial))
            #obj.dut.adb.shell('pkill -9 iperf3')
            #only for glinux machine
            #            wputils.bring_down_interface(obj.ether_int.interface)
            obj.ap.close()

    def teardown_test(self):
        self.log.info("Setup test initiated")

    def test_start_iperf_traffic(self):
        self.channel_change_interval = self.dynamic_wifi_interference[
            'channel_change_interval_second']
        self.wifi_int_levels = list(
            self.dynamic_wifi_interference['interference_level'].keys())
        for wifi_level in self.wifi_int_levels:
            interference_atten_level = self.dynamic_wifi_interference[
                'interference_level'][wifi_level]
            end_time = time.time() + self.IperfDuration
            while time.time() < end_time:
                procs_iperf = []
                # Start IPERF on all three interference pairs
                for obj in self.wifi_int_pairs:
                    obj.iperf_server.start()
                    iperf_args = '-i 1 -t {} -p {} -J -R'.format(
                        self.IperfDuration, obj.iperf_server.port)
                    tag = 'chan_{}'.format(obj.channel)
                    proc_iperf = Process(target=obj.iperf_client.start,
                                         args=(obj.server_address, iperf_args,
                                               tag))
                    proc_iperf.start()
                    procs_iperf.append(proc_iperf)
                for proc in procs_iperf:
                    self.log.info('Started IPERF on all three channels')
                    proc.join()
        return True
