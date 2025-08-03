#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
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

import time

from blueberry.tests.gd.cert import gd_base_test
from blueberry.tests.gd.cert.truth import assertThat
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.hci import controller_facade_pb2 as controller_facade
from mobly import test_runner
import hci_packets as hci


class ControllerTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI_INTERFACES', cert_module='HCI_INTERFACES')

    def test_get_addresses(self):
        cert_address = self.cert.hci_controller.GetMacAddressSimple()
        dut_address = self.dut.hci_controller.GetMacAddressSimple()

        assertThat(cert_address).isNotEqualTo(dut_address)
        time.sleep(1)  # This shouldn't be needed b/149120542

    def test_write_local_name(self):
        self.dut.hci_controller.WriteLocalName(controller_facade.NameMsg(name=b'ImTheDUT'))
        self.cert.hci_controller.WriteLocalName(controller_facade.NameMsg(name=b'ImTheCert'))
        cert_name = self.cert.hci_controller.GetLocalNameSimple()
        dut_name = self.dut.hci_controller.GetLocalNameSimple()

        assertThat(dut_name).isEqualTo(b'ImTheDUT')
        assertThat(cert_name).isEqualTo(b'ImTheCert')

    def test_extended_advertising_support(self):
        extended_advertising_supported = self.dut.hci_controller.SupportsBleExtendedAdvertising(empty_proto.Empty())
        if extended_advertising_supported.supported:
            number_of_sets = self.dut.hci_controller.GetLeNumberOfSupportedAdvertisingSets(empty_proto.Empty())
            assertThat(number_of_sets.value).isGreaterThan(5)  # Android threshold for CTS
            supported = self.dut.hci_controller.IsSupportedCommand(
                controller_facade.OpCodeMsg(op_code=int(hci.OpCode.LE_SET_EXTENDED_ADVERTISING_PARAMETERS)))
            assertThat(supported.supported).isEqualTo(True)


if __name__ == '__main__':
    test_runner.main()
