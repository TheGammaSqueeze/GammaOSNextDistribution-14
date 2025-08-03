#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert.py_hal import PyHal
from blueberry.tests.gd.cert.matchers import HciMatchers
from blueberry.tests.gd.cert import gd_base_test
from blueberry.utils import bluetooth
from mobly import test_runner
import hci_packets as hci

_GRPC_TIMEOUT = 10


class SimpleHalTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HAL', cert_module='HAL')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)
        self.dut_hal = PyHal(self.dut)
        self.cert_hal = PyHal(self.cert)

        self.dut_hal.reset()
        self.cert_hal.reset()

    def teardown_test(self):
        self.dut_hal.close()
        self.cert_hal.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def test_stream_events(self):
        self.dut_hal.send_hci_command(
            hci.LeAddDeviceToFilterAcceptList(address_type=hci.FilterAcceptListAddressType.RANDOM,
                                              address=bluetooth.Address('0C:05:04:03:02:01')))
        assertThat(self.dut_hal.get_hci_event_stream()).emits(
            HciMatchers.Exactly(
                hci.LeAddDeviceToFilterAcceptListComplete(num_hci_command_packets=1, status=hci.ErrorCode.SUCCESS)))

    def test_loopback_hci_command(self):
        self.dut_hal.send_hci_command(hci.WriteLoopbackMode(loopback_mode=hci.LoopbackMode.ENABLE_LOCAL))

        command = hci.LeAddDeviceToFilterAcceptList(address_type=hci.FilterAcceptListAddressType.RANDOM,
                                                    address=bluetooth.Address('0C:05:04:03:02:01'))
        self.dut_hal.send_hci_command(command)

        assertThat(self.dut_hal.get_hci_event_stream()).emits(HciMatchers.LoopbackOf(command.serialize()))

    def test_inquiry_from_dut(self):
        self.cert_hal.send_hci_command(hci.WriteScanEnable(scan_enable=hci.ScanEnable.INQUIRY_AND_PAGE_SCAN))

        self.dut_hal.send_hci_command(hci.Inquiry(lap=hci.Lap(lap=0x33), inquiry_length=0x30, num_responses=0xff))

        assertThat(self.dut_hal.get_hci_event_stream()).emits(lambda packet: b'\x02\x0f' in packet.payload
                                                              # Expecting an HCI Event (code 0x02, length 0x0f)
                                                             )

    def test_le_ad_scan_cert_advertises(self):
        self.dut_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        self.dut_hal.unmask_le_event(hci.SubeventCode.EXTENDED_ADVERTISING_REPORT)
        self.dut_hal.set_random_le_address('0D:05:04:03:02:01')

        self.dut_hal.set_scan_parameters()
        self.dut_hal.start_scanning()

        advertisement = self.cert_hal.create_advertisement(0,
                                                           '0C:05:04:03:02:01',
                                                           min_interval=512,
                                                           max_interval=768,
                                                           peer_address='A6:A5:A4:A3:A2:A1',
                                                           tx_power=0x7f,
                                                           sid=1)
        advertisement.set_data(b'Im_A_Cert')
        advertisement.start()

        assertThat(self.dut_hal.get_hci_event_stream()).emits(lambda packet: b'Im_A_Cert' in packet.payload)

        advertisement.stop()

        self.dut_hal.stop_scanning()

    def test_le_connection_dut_advertises(self):
        self.cert_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        self.cert_hal.set_random_le_address('0C:05:04:03:02:01')
        self.cert_hal.initiate_le_connection('0D:05:04:03:02:01')

        # DUT Advertises
        self.dut_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        advertisement = self.dut_hal.create_advertisement(0, '0D:05:04:03:02:01')
        advertisement.set_data(b'Im_The_DUT')
        advertisement.set_scan_response(b'Im_The_D')
        advertisement.start()

        cert_acl = self.cert_hal.complete_le_connection()
        dut_acl = self.dut_hal.complete_le_connection()

        dut_acl.send_first(b'Just SomeAclData')
        cert_acl.send_first(b'Just SomeMoreAclData')

        assertThat(self.cert_hal.get_acl_stream()).emits(lambda packet: b'SomeAclData' in packet.payload)
        assertThat(self.dut_hal.get_acl_stream()).emits(lambda packet: b'SomeMoreAclData' in packet.payload)

    def test_le_filter_accept_list_connection_cert_advertises(self):
        self.dut_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        self.dut_hal.set_random_le_address('0D:05:04:03:02:01')
        self.dut_hal.add_to_filter_accept_list('0C:05:04:03:02:01')
        self.dut_hal.initiate_le_connection_by_filter_accept_list('BA:D5:A4:A3:A2:A1')

        self.cert_hal.unmask_event(hci.EventCode.LE_META_EVENT)
        advertisement = self.cert_hal.create_advertisement(1,
                                                           '0C:05:04:03:02:01',
                                                           min_interval=512,
                                                           max_interval=768,
                                                           peer_address='A6:A5:A4:A3:A2:A1',
                                                           tx_power=0x7F,
                                                           sid=0)
        advertisement.set_data(b'Im_A_Cert')
        advertisement.start()

        assertThat(self.cert_hal.get_hci_event_stream()).emits(HciMatchers.LeConnectionComplete())
        assertThat(self.dut_hal.get_hci_event_stream()).emits(HciMatchers.LeConnectionComplete())


if __name__ == '__main__':
    test_runner.main()
