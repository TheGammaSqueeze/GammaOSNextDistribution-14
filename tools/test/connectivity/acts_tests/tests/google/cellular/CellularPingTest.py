#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import acts_contrib.test_utils.cellular.cellular_base_test as cbt
import scapy.all as scapy


class CellularPingTest(cbt.CellularBaseTest):
    def test_lte_band_13_pul_low_bw_10_tm_3_mimo_2x2(self):
        host_ip = scapy.get_if_addr(self.packet_senders[0].interface)
        self.log.info('Will ping ' + host_ip)
        ping_result = self.dut.adb.shell('ping -w 5 ' + host_ip,
                                         ignore_status=True)
        self.log.info(ping_result)
        return '0% packet loss' in ping_result
