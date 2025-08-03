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

from blueberry.tests.gd.cert.matchers import HciMatchers, NeighborMatchers
from blueberry.tests.gd.cert.py_hci import PyHci
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert.py_neighbor import PyNeighbor
from blueberry.facade.neighbor import facade_pb2 as neighbor_facade
from blueberry.tests.gd.cert import gd_base_test
from mobly import test_runner
import hci_packets as hci


class NeighborTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI_INTERFACES', cert_module='HCI')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)
        self.cert_hci = PyHci(self.cert, acl_streaming=True)
        self.cert_hci.send_command(hci.WriteScanEnable(scan_enable=hci.ScanEnable.INQUIRY_AND_PAGE_SCAN))
        self.cert_name = b'Im_A_Cert'
        self.cert_address = self.cert_hci.read_own_address()
        self.cert_name += b'@' + repr(self.cert_address).encode('utf-8')
        self.dut_neighbor = PyNeighbor(self.dut)

    def teardown_test(self):
        self.cert_hci.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def _set_name(self):
        padded_name = self.cert_name
        while len(padded_name) < 248:
            padded_name = padded_name + b'\0'
        self.cert_hci.send_command(hci.WriteLocalName(local_name=padded_name))

        assertThat(self.cert_hci.get_event_stream()).emits(HciMatchers.CommandComplete(hci.OpCode.WRITE_LOCAL_NAME))

    def test_inquiry_from_dut(self):
        inquiry_msg = neighbor_facade.InquiryMsg(inquiry_mode=neighbor_facade.DiscoverabilityMode.GENERAL,
                                                 result_mode=neighbor_facade.ResultMode.STANDARD,
                                                 length_1_28s=30,
                                                 max_results=0)
        session = self.dut_neighbor.set_inquiry_mode(inquiry_msg)
        self.cert_hci.send_command(hci.WriteScanEnable(scan_enable=hci.ScanEnable.INQUIRY_AND_PAGE_SCAN))
        assertThat(session).emits(NeighborMatchers.InquiryResult(self.cert_address))

    def test_inquiry_rssi_from_dut(self):
        inquiry_msg = neighbor_facade.InquiryMsg(inquiry_mode=neighbor_facade.DiscoverabilityMode.GENERAL,
                                                 result_mode=neighbor_facade.ResultMode.RSSI,
                                                 length_1_28s=31,
                                                 max_results=0)
        session = self.dut_neighbor.set_inquiry_mode(inquiry_msg)
        self.cert_hci.send_command(hci.WriteScanEnable(scan_enable=hci.ScanEnable.INQUIRY_AND_PAGE_SCAN))
        assertThat(session).emits(NeighborMatchers.InquiryResultwithRssi(self.cert_address))

    def test_inquiry_extended_from_dut(self):
        self._set_name()
        self.cert_hci.send_command(
            hci.WriteExtendedInquiryResponse(fec_required=hci.FecRequired.NOT_REQUIRED,
                                             extended_inquiry_response=[
                                                 hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME,
                                                             data=list(bytes(self.cert_name)))
                                             ]))
        inquiry_msg = neighbor_facade.InquiryMsg(inquiry_mode=neighbor_facade.DiscoverabilityMode.GENERAL,
                                                 result_mode=neighbor_facade.ResultMode.EXTENDED,
                                                 length_1_28s=32,
                                                 max_results=0)
        session = self.dut_neighbor.set_inquiry_mode(inquiry_msg)
        self.cert_hci.send_command(hci.WriteScanEnable(scan_enable=hci.ScanEnable.INQUIRY_AND_PAGE_SCAN))
        assertThat(session).emits(NeighborMatchers.ExtendedInquiryResult(self.cert_address))

    def test_remote_name(self):
        self._set_name()
        session = self.dut_neighbor.get_remote_name(repr(self.cert_address))
        session.verify_name(self.cert_name)


if __name__ == '__main__':
    test_runner.main()
