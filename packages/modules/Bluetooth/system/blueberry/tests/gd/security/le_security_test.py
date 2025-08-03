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

import hci_packets as hci
import smp_packets as smp
from blueberry.tests.gd.cert.matchers import SecurityMatchers
from blueberry.tests.gd.cert.metadata import metadata
from blueberry.tests.gd.cert.py_hci import PyHci
from blueberry.tests.gd.cert.py_le_security import PyLeSecurity
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert import gd_base_test
from datetime import timedelta
from blueberry.facade import common_pb2 as common
from blueberry.facade.hci import le_advertising_manager_facade_pb2 as le_advertising_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.security.facade_pb2 import BondMsgType
from blueberry.facade.security.facade_pb2 import OobDataMessage
from blueberry.facade.security.facade_pb2 import UiCallbackMsg
from blueberry.facade.security.facade_pb2 import UiCallbackType
from blueberry.facade.security.facade_pb2 import UiMsgType
from blueberry.facade.security.facade_pb2 import LeIoCapabilityMessage
from blueberry.facade.security.facade_pb2 import LeOobDataPresentMessage
from blueberry.facade.security.facade_pb2 import LeMaximumEncryptionKeySizeMessage
from mobly import asserts
from mobly import test_runner

LeIoCapabilities = LeIoCapabilityMessage.LeIoCapabilities
LeOobDataFlag = LeOobDataPresentMessage.LeOobDataFlag

DISPLAY_ONLY = LeIoCapabilityMessage(capabilities=LeIoCapabilities.DISPLAY_ONLY)
KEYBOARD_ONLY = LeIoCapabilityMessage(capabilities=LeIoCapabilities.KEYBOARD_ONLY)
NO_INPUT_NO_OUTPUT = LeIoCapabilityMessage(capabilities=LeIoCapabilities.NO_INPUT_NO_OUTPUT)
KEYBOARD_DISPLAY = LeIoCapabilityMessage(capabilities=LeIoCapabilities.KEYBOARD_DISPLAY)

OOB_NOT_PRESENT = LeOobDataPresentMessage(data_present=LeOobDataFlag.NOT_PRESENT)
OOB_PRESENT = LeOobDataPresentMessage(data_present=LeOobDataFlag.PRESENT)


class LeSecurityTest(gd_base_test.GdBaseTestClass):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='SECURITY', cert_module='SECURITY')

    def setup_test(self):
        asserts.skip("Unhandled race condition - Flaky test")
        gd_base_test.GdBaseTestClass.setup_test(self)

        self.dut_security = PyLeSecurity(self.dut)
        self.cert_security = PyLeSecurity(self.cert)
        self.dut_hci = PyHci(self.dut)

        raw_addr = self.dut.hci_controller.GetMacAddress(empty_proto.Empty()).address

        self.dut_address = common.BluetoothAddressWithType(address=common.BluetoothAddress(address=raw_addr),
                                                           type=common.PUBLIC_DEVICE_ADDRESS)
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_PUBLIC_ADDRESS,
            address_with_type=self.dut_address)
        self.dut.security.SetLeInitiatorAddressPolicy(privacy_policy)
        self.cert_address = common.BluetoothAddressWithType(address=common.BluetoothAddress(
            address=self.cert.hci_controller.GetMacAddress(empty_proto.Empty()).address),
                                                            type=common.PUBLIC_DEVICE_ADDRESS)
        cert_privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_PUBLIC_ADDRESS,
            address_with_type=self.cert_address)
        self.cert.security.SetLeInitiatorAddressPolicy(cert_privacy_policy)

    def teardown_test(self):
        return  # test skipped
        self.dut_hci.close()
        self.dut_security.close()
        self.cert_security.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    def _prepare_cert_for_connection(self):
        # DUT Advertises
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_CERT')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_PUBLIC_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)
        create_response = self.cert.hci_le_advertising_manager.CreateAdvertiser(request)

    def _prepare_dut_for_connection(self):
        # DUT Advertises
        gap_name = hci.GapData(data_type=hci.GapDataType.COMPLETE_LOCAL_NAME, data=list(bytes(b'Im_The_DUT')))
        gap_data = le_advertising_facade.GapDataMsg(data=gap_name.serialize())
        config = le_advertising_facade.AdvertisingConfig(
            advertisement=[gap_data],
            interval_min=512,
            interval_max=768,
            advertising_type=le_advertising_facade.AdvertisingEventType.ADV_IND,
            own_address_type=common.USE_PUBLIC_DEVICE_ADDRESS,
            channel_map=7,
            filter_policy=le_advertising_facade.AdvertisingFilterPolicy.ALL_DEVICES)
        request = le_advertising_facade.CreateAdvertiserRequest(config=config)
        create_response = self.dut.hci_le_advertising_manager.CreateAdvertiser(request)

    @metadata(pts_test_id="SM/MAS/PROT/BV-01-C", pts_test_name="SMP Time Out – IUT Initiator")
    def test_le_smp_timeout_iut_initiator(self):
        """
            Verify that the IUT handles the lack of pairing response after 30 seconds when acting as initiator.
        """
        self._prepare_cert_for_connection()
        self.dut.security.CreateBondLe(self.cert_address)
        assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
            BondMsgType.DEVICE_BOND_FAILED, self.cert_address),
                                                              timeout=timedelta(seconds=35))

    @metadata(pts_test_id="SM/SLA/PROT/BV-02-C", pts_test_name="SMP Time Out – IUT Responder")
    def test_le_smp_timeout_iut_responder(self):
        """
            Verify that the IUT handles the lack of pairing response after 30 seconds when acting as initiator.
        """
        self.cert.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeIoCapability(DISPLAY_ONLY)

        self._prepare_dut_for_connection()

        # 1. Lower Tester transmits Pairing Request.
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT,
                                                                                   self.cert_address),
                                                            timeout=timedelta(seconds=35))

        # 2. IUT responds with Pairing Response.
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        # 3. In phase 2, Lower Tester does not issue the expected Pairing Confirm.

        # Here the cert receives DISPLAY_PASSKEY_ENTRY. By not replying to it we make sure Pairing Confirm is never sent
        assertThat(self.cert_security.get_ui_stream()).emits(SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PASSKEY_ENTRY,
                                                                                    self.dut_address),
                                                             timeout=timedelta(seconds=5))

        # 4. IUT times out 30 seconds after issued Pairing Response and reports the failure to the Upper Tester.
        assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
            BondMsgType.DEVICE_BOND_FAILED, self.cert_address),
                                                              timeout=timedelta(seconds=35))

        # 5. After additionally (at least) 10 seconds the Lower Tester issues the expected Pairing Confirm.
        # 6. The IUT closes the connection before receiving the delayed response or does not respond to it when it is received.
        #TODO:
        #assertThat(self.dut_hci.get_event_stream()).emits(HciMatchers.Disconnect())

    @metadata(pts_test_id="SM/MAS/JW/BV-01-C", pts_test_name="Just Works IUT Initiator – Success")
    def test_just_works_iut_initiator(self):
        """
            Verify that the IUT performs the Just Works pairing procedure correctly as central, initiator when both sides do not require MITM protection.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements()

        self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements()

        # 1. IUT transmits Pairing Request command with:
        # a. IO capability set to any IO capability
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq Bonding Flags set to ‘00’ and the MITM flag set to ‘0’ and all the reserved bits are set to ‘0’
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with a Pairing Response command, with:
        # a. IO capability set to “KeyboardDisplay”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq Bonding Flags set to ‘00’, and the MITM flag set to ‘0’ and all the reserved bits are set to ‘0’
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        # 3. IUT and Lower Tester perform phase 2 of the just works pairing procedure and establish an encrypted link with the key generated in phase 2.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/SLA/JW/BV-02-C", pts_test_name="Just Works IUT Responder – Success")
    def test_just_works_iut_responder(self):
        """
            Verify that the IUT is able to perform the Just Works pairing procedure correctly when acting as peripheral, responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements()

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements()

        # 1. Lower Tester transmits Pairing Request command with:
        # a. IO capability set to “NoInputNoOutput”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. MITM flag set to ‘0’ and all reserved bits are set to ‘0’
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        # 2. IUT responds with a Pairing Response command, with:
        # a. IO capability set to any IO capability
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        # IUT and Lower Tester perform phase 2 of the just works pairing and establish an encrypted link with the generated STK.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/SLA/JW/BI-03-C",
              pts_test_name="Just Works IUT Responder – Handle AuthReq flag RFU correctly")
    def test_just_works_iut_responder_auth_req_rfu(self):
        """
            Verify that the IUT is able to perform the Just Works pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as peripheral, responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements()

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1, reserved_bits=2)

        # 1. Lower Tester transmits Pairing Request command with:
        # a. IO Capability set to ”NoInputNoOutput”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. MITM set to ‘0’ and all reserved bits are set to ‘1’
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        # 2. IUT responds with a Pairing Response command, with:
        # a. IO capability set to any IO capability
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. All reserved bits are set to ‘0’
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        # 3. IUT and Lower Tester perform phase 2 of the just works pairing and establish an encrypted link with the generated STK.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/MAS/JW/BI-04-C",
              pts_test_name="Just Works IUT Initiator – Handle AuthReq flag RFU correctly")
    def test_just_works_iut_initiator_auth_req_rfu(self):
        """
            Verify that the IUT is able to perform the Just Works pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as central, initiator.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements()

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1, reserved_bits=3)

        # 1. IUT transmits a Pairing Request command with:
        # a. IO Capability set to any IO Capability
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. All reserved bits are set to ‘0’. For the purposes of this test the Secure Connections bit and the Keypress bits in the AuthReq bonding flag set by the IUT are ignored by the Lower Tester.
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with a Pairing Response command, with:
        # a. IO Capability set to “NoInputNoOutput”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq bonding flag set to the value indicated in the IXIT [7] for ‘Bonding Flags’ and the MITM flag set to ‘0’ and all reserved bits are set to ‘1’. The SC and Keypress bits in the AuthReq bonding flag are set to 0 by the Lower Tester for this test.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        # 3. IUT and Lower Tester perform phase 2 of the just works pairing and establish an encrypted link with the generated STK.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/MAS/SCJW/BV-01-C",
              pts_test_name="Just Works, IUT Initiator, Secure Connections – Success")
    def test_just_works_iut_initiator_secure_connections(self):
        """
            Verify that the IUT supporting LE Secure Connections performs the Just Works or Numeric Comparison pairing procedure correctly as initiator.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(secure_connections=1)

        # 1. IUT transmits Pairing Request command with:
        # a. IO capability set to any IO capability
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq Bonding Flags set to ‘00’, the MITM flag set to either ‘0’ for Just Works or '1' for Numeric Comparison, Secure Connections flag set to '1' and all the reserved bits are set to ‘0’
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with a Pairing Response command, with:
        # a. IO capability set to “KeyboardDisplay”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq Bonding Flags set to ‘00’, the MITM flag set to ‘0’, Secure Connections flag set to '1' and all the reserved bits are set to ‘0’
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        # 3. IUT and Lower Tester perform phase 2 of the Just Works or Numeric Comparison pairing procedure according to the MITM flag and IO capabilities, and establish an encrypted link with the LTK generated in phase 2.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/SLA/SCJW/BV-02-C",
              pts_test_name="Just Works, IUT Responder, Secure Connections – Success")
    def test_just_works_iut_responder_secure_connections(self):
        """
            Verify that the IUT supporting LE Secure Connections is able to perform the Just Works or Numeric Comparison pairing procedure correctly when acting as responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(secure_connections=1)

        # 1. Lower Tester transmits Pairing Request command with:
        # a. IO capability set to “NoInputNoOutput”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq Bonding Flags set to ‘00’, MITM flag set to ‘0’, Secure Connections flag set to '1' and all reserved bits are set to ‘0’
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        # 2. IUT responds with a Pairing Response command, with:
        # a. IO capability set to any IO capability
        # b. AuthReq Bonding Flags set to ‘00’, MITM flag set to either ‘0’ for Just Works or '1' for Numeric Comparison, Secure Connections flag set to '1' and all reserved bits are set to ‘0’
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        # 3. UT and Lower Tester perform phase 2 of the Just Works or Numeric Comparison pairing procedure according to the MITM flag and IO capabilities, and establish an encrypted link with the LTK generated in phase 2.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/SLA/SCJW/BV-03-C",
              pts_test_name="Just Works, IUT Responder, Secure Connections – Handle AuthReq Flag RFU Correctly")
    def test_just_works_iut_responder_secure_connections_auth_req_rfu(self):
        """
            Verify that the IUT is able to perform the Just Works pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as peripheral, responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1, reserved_bits=3)

        # 1. Lower Tester transmits Pairing Request command with:
        # a. IO Capability set to ”NoInputNoOutput”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. MITM set to ‘0’ and all reserved bits are set to a random value.
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        # 2. IUT responds with a Pairing Response command, with:
        # a. IO capability set to any IO capability
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. All reserved bits are set to ‘0’
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        # 3. IUT and Lower Tester perform phase 2 of the Just Works pairing and establish an encrypted link with the generated LTK.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/MAS/SCJW/BV-04-C",
              pts_test_name="Just Works, IUT Initiator, Secure Connections – Handle AuthReq Flag RFU Correctly")
    def test_just_works_iut_initiator_secure_connections_auth_req_rfu(self):
        """
            Verify that the IUT is able to perform the Just Works pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as central, initiator.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1, reserved_bits=3)

        # 1. IUT transmits a Pairing Request command with:
        # a. IO Capability set to any IO Capability
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. All reserved bits are set to ‘0’.
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with a Pairing Response command, with:
        # a. IO Capability set to “NoInputNoOutput”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq bonding flag set to the value indicated in the IXIT [7] for ‘Bonding Flags’ and the MITM flag set to ‘0’ and all reserved bits are set to a random value.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        # 3. IUT and Lower Tester perform phase 2 of the Just Works pairing and establish an encrypted link with the generated LTK.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/MAS/EKS/BV-01-C",
              pts_test_name="IUT initiator, Lower Tester Maximum Encryption Key Size = Min_Encryption_Key_Length")
    def test_min_encryption_key_size_equal_to_max_iut_initiator(self):
        """
            Verify that the IUT uses correct key size during encryption as initiator.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)
        self.dut.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x10))

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1)
        self.cert.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x07))

        # 1. IUT transmits a Pairing Request
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with Pairing Response command with Maximum Encryption Key Size field set to Min_Encryption_Key_Length’.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        # 3. IUT and Lower Tester perform phase 2 of the LE pairing and establish an encrypted link with the key generated in phase 2.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/SLA/EKS/BV-02-C",
              pts_test_name="IUT Responder, Lower Tester Maximum Encryption Key Size = Min_Encryption_Key_Length")
    def test_min_encryption_key_size_equal_to_max_iut_responder(self):
        """
            Verify that the IUT uses correct key size during encryption as responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements()
        self.dut.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x07))

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements()
        self.cert.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x10))

        # 1. Lower Tester initiates Pairing Request command with Maximum Encryption Key Size field set to Min_Encryption_Key_Length’.
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        # 2. IUT responds with Pairing Response command.
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        #3. IUT and Lower Tester perform phase 2 of the LE pairing and establish an encrypted link with the key generated in phase 2.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BONDED, self.cert_address))

    @metadata(pts_test_id="SM/MAS/EKS/BI-01-C",
              pts_test_name="IUT initiator, Lower Tester Maximum Encryption Key Size < Min_Encryption_Key_Length")
    def test_min_encryption_key_size_less_than_min_iut_initiator(self):
        """
            Verify that the IUT checks that the resultant encryption key size is not smaller than the minimum key size.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)
        self.dut.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x10))

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1)
        self.cert.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x06))

        # 1. IUT transmits a Pairing Request
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with Pairing Response command with Maximum Encryption Key Size field set to Min_Encryption_Key_Length-1’.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        # 3. IUT transmits the Pairing Failed command.
        assertThat(self.dut_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BOND_FAILED, self.cert_address,
                                     int(smp.PairingFailedReason.ENCRYPTION_KEY_SIZE)))

    @metadata(pts_test_id="SM/SLA/EKS/BI-02-C",
              pts_test_name="IUT Responder, Lower Tester Maximum Encryption Key Size < Min_Encryption_Key_Length")
    def test_min_encryption_key_size_less_than_min_iut_responder(self):
        """
            Verify that the IUT uses correct key size during encryption as responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements()
        self.dut.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x06))

        self.cert.security.SetLeIoCapability(NO_INPUT_NO_OUTPUT)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements()
        self.cert.security.SetLeMaximumEncryptionKeySize(
            LeMaximumEncryptionKeySizeMessage(maximum_encryption_key_size=0x10))

        # 1. Lower Tester initiates Pairing Request command with Maximum Encryption Key Size field set to Min_Encryption_Key_Length-1.
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        #3. IUT transmits the Pairing Failed command.
        assertThat(self.cert_security.get_bond_stream()).emits(
            SecurityMatchers.BondMsg(BondMsgType.DEVICE_BOND_FAILED, self.dut_address,
                                     int(smp.PairingFailedReason.ENCRYPTION_KEY_SIZE)))

    @metadata(pts_test_id="SM/MAS/SCPK/BV-01-C",
              pts_test_name="Passkey Entry, IUT Initiator, Secure Connections – Success")
    def test_passkey_entry_iut_initiator_secure_connections(self):
        """
            Verify that the IUT supporting LE Secure Connections performs the Passkey Entry pairing procedure correctly as central, initiator.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(DISPLAY_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1)

        # 1. IUT transmits Pairing Request command with:
        # a. IO capability set to “DisplayOnly” or “KeyboardOnly”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq bonding flag set to ‘00’, the MITM flag set to ‘0’ and Secure Connections flag set to '1'. Keypress bit is set to '1' if supported
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with a Pairing Response command, with:
        # a. IO capability set to “KeyboardOnly”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq bonding flag set to ‘00’, the MITM flag set to ‘1’, Secure Connections flag set to '1' and all reserved bits are set to ‘0’. Keypress bit is set to '1' if supported by the IUT.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PASSKEY_ENTRY, self.dut_address))

        # 3. During the phase 2 pairing, the IUT displays the 6-digit passkey while the Lower Tester prompts user to enter the 6-digit passkey. If the IUT’s IO capabilities are “KeyboardOnly” the passkey is not displayed and both IUT and Lower Tester enter the same 6-digit passkey. If Keypress bit is set, pairing keypress notifications are sent by the Lower Tester.
        passkey = self.dut_security.wait_for_ui_event_passkey()

        if passkey == 0:
            print("Passkey did not arrive into test")

        # 4. IUT and Lower Tester use the same 6-digit passkey.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PASSKEY,
                          numeric_value=passkey,
                          unique_id=1,
                          address=self.dut_address))

        # 5. IUT and Lower Tester perform phase 2 of the Passkey Entry pairing procedure and establish an encrypted link with the LTK generated in phase 2.
        assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
            BondMsgType.DEVICE_BONDED, self.cert_address),
                                                              timeout=timedelta(seconds=10))

    @metadata(pts_test_id="SM/SLA/SCPK/BV-02-C",
              pts_test_name="Passkey Entry, IUT Responder, Secure Connections – Success")
    def test_passkey_entry_iut_responder_secure_connections(self):
        """
            Verify that the IUT supporting LE Secure Connections is able to perform the Passkey Entry pairing procedure correctly when acting as peripheral, responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(DISPLAY_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1)

        # 1. Lower Tester transmits Pairing Request command with:
        # a. IO capability set to “KeyboardDisplay”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq bonding flag set to the value indicated in the IXIT [7] for ‘Bonding Flags’, and the MITM flag set to ‘1’ Secure Connections flag set to '1' and all reserved bits are set to ‘0’
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        # 2. IUT responds with a Pairing Response command, with:
        # a. IO capability set to “KeyboardOnly” or “KeyboardDisplay” or “DisplayYesNo” or “DisplayOnly”
        # b. Secure Connections flag set to '1'. Keypress bit is set to '1' if supported by IUT
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        # 3. During the phase 2 passkey pairing process, Lower Tester displays the 6-digit passkey while the IUT prompts user to enter the 6-digit passkey. If the IO capabilities of the IUT are “DisplayYesNo” or “DisplayOnly” the IUT displays the 6-digit passkey while the Lower Tester enters the 6-digit passkey. If Keypress bit is set, pairing keypress notifications are send by the IUT
        passkey = self.dut_security.wait_for_ui_event_passkey()

        if passkey == 0:
            print("Passkey did not arrive into test")

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PASSKEY_ENTRY, self.dut_address))

        # 4. IUT and Lower Tester use the same pre-defined 6-digit passkey.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PASSKEY,
                          numeric_value=passkey,
                          unique_id=1,
                          address=self.dut_address))

        # 5. IUT and Lower Tester perform phase 2 of the LE pairing and establish an encrypted link with the LTK generated in phase 2.
        assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
            BondMsgType.DEVICE_BONDED, self.cert_address),
                                                              timeout=timedelta(seconds=10))

    @metadata(pts_test_id="SM/SLA/SCPK/BV-03-C",
              pts_test_name="Passkey Entry, IUT Responder, Secure Connections – Handle AuthReq Flag RFU Correctly")
    def test_passkey_entry_iut_responder_secure_connections_auth_req_rfu(self):
        """
            Verify that the IUT supporting LE Secure Connections is able to perform the Passkey Entry pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as peripheral, responder.
        """
        self._prepare_dut_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1, reserved_bits=3)

        # 1. Lower Tester transmits Pairing Request command with:
        # a. IO Capability set to ”KeyboardOnly”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. MITM set to ‘1’ and all reserved bits are set to a random value
        self.cert.security.CreateBondLe(self.dut_address)

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

        # 2. IUT responds with a Pairing Response command, with:
        # a. IO Capability set to “KeyboardOnly” or “DisplayOnly”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. All reserved bits are set to ‘0’
        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.cert_address))

        passkey = self.cert_security.wait_for_ui_event_passkey()

        if passkey == 0:
            print("Passkey did not arrive into test")

        assertThat(self.dut_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PASSKEY_ENTRY, self.cert_address))

        self.dut.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PASSKEY,
                          numeric_value=passkey,
                          unique_id=1,
                          address=self.cert_address))

        # 3. IUT and Lower Tester perform phase 2 of the Passkey Entry pairing and establish an encrypted link with the generated LTK.
        assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
            BondMsgType.DEVICE_BONDED, self.cert_address),
                                                              timeout=timedelta(seconds=10))

    @metadata(pts_test_id="SM/MAS/SCPK/BV-04-C",
              pts_test_name="Passkey Entry, IUT Initiator, Secure Connections – Handle AuthReq Flag RFU Correctly")
    def test_passkey_entry_iut_initiator_secure_connections_auth_req_rfu(self):
        """
            Verify that the IUT supporting LE Secure Connections is able to perform the Passkey Entry pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as central, initiator.
        """
        self._prepare_cert_for_connection()

        self.dut.security.SetLeIoCapability(KEYBOARD_DISPLAY)
        self.dut.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.dut_security.SetLeAuthRequirements(secure_connections=1)

        self.cert.security.SetLeIoCapability(KEYBOARD_ONLY)
        self.cert.security.SetLeOobDataPresent(OOB_NOT_PRESENT)
        self.cert_security.SetLeAuthRequirements(mitm=1, secure_connections=1, reserved_bits=3)

        # 1. IUT transmits a Pairing Request command with:
        # a. IO Capability set to “DisplayOnly” or “DisplayYesNo” or “KeyboardOnly” or “KeyboardDisplay”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. All reserved bits are set to ‘0’.
        self.dut.security.CreateBondLe(self.cert_address)

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

        # 2. Lower Tester responds with a Pairing Response command, with:
        # a. IO Capability set to “KeyboardOnly”
        # b. OOB data flag set to 0x00 (OOB Authentication data not present)
        # c. AuthReq bonding flag set to the value indicated in the IXIT [7] for ‘Bonding Flags’ and the MITM flag set to ‘1’ and all reserved bits are set to a random value.
        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                          boolean=True,
                          unique_id=1,
                          address=self.dut_address))

        assertThat(self.cert_security.get_ui_stream()).emits(
            SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PASSKEY_ENTRY, self.dut_address))

        passkey = self.dut_security.wait_for_ui_event_passkey()

        self.cert.security.SendUiCallback(
            UiCallbackMsg(message_type=UiCallbackType.PASSKEY,
                          numeric_value=passkey,
                          unique_id=1,
                          address=self.dut_address))

        # 3.    IUT and Lower Tester perform phase 2 of the Just Works pairing and establish an encrypted link with the generated LTK.
        assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
            BondMsgType.DEVICE_BONDED, self.cert_address),
                                                              timeout=timedelta(seconds=10))

    @metadata(pts_test_id="SM/MAS/SCOB/BV-01-C",
              pts_test_name="Out of Band, IUT Initiator, Secure Connections – Success")
    def test_out_of_band_iut_initiator_secure_connections(self):
        """
            Verify that the IUT supporting LE Secure Connections performs the Out-of-Band pairing procedure correctly as central, initiator.
        """

        oob_combinations = [(OOB_NOT_PRESENT, OOB_PRESENT), (OOB_PRESENT, OOB_NOT_PRESENT), (OOB_PRESENT, OOB_PRESENT)]

        for (dut_oob_flag, cert_oob_flag) in oob_combinations:
            print("oob flag combination dut: " + str(dut_oob_flag) + ", cert: " + str(cert_oob_flag))

            self._prepare_cert_for_connection()

            if dut_oob_flag == LeOobDataFlag.PRESENT:
                oobdata = self.cert.security.GetLeOutOfBandData(empty_proto.Empty())
                self.dut.security.SetOutOfBandData(
                    OobDataMessage(address=self.cert_address,
                                   confirmation_value=oobdata.confirmation_value,
                                   random_value=oobdata.random_value))

            if cert_oob_flag == LeOobDataFlag.PRESENT:
                oobdata = self.dut.security.GetLeOutOfBandData(empty_proto.Empty())
                self.cert.security.SetOutOfBandData(
                    OobDataMessage(address=self.dut_address,
                                   confirmation_value=oobdata.confirmation_value,
                                   random_value=oobdata.random_value))

            self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
            self.dut.security.SetLeOobDataPresent(dut_oob_flag)
            self.dut_security.SetLeAuthRequirements(bond=1, mitm=1, secure_connections=1)

            self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
            self.cert.security.SetLeOobDataPresent(cert_oob_flag)
            self.cert_security.SetLeAuthRequirements(bond=1, mitm=1, secure_connections=1)

            # 1. IUT transmits a Pairing Request command with OOB data flag set to either 0x00 or 0x01, and Secure Connections flag set to '1'.
            self.dut.security.CreateBondLe(self.cert_address)

            assertThat(self.cert_security.get_ui_stream()).emits(
                SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

            # 2. Lower Tester responds with a Pairing Response command with Secure Connections flag set to '1' and OOB data flag set to either 0x00 or 0x01.
            self.cert.security.SendUiCallback(
                UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                              boolean=True,
                              unique_id=1,
                              address=self.dut_address))

            # 3. IUT uses the 128-bit value generated by the Lower Tester as the confirm value. Similarly, the Lower Tester uses the 128-bit value generated by the IUT as the confirm value.

            # 4. IUT and Lower Tester perform phase 2 of the pairing process and establish an encrypted link with an LTK generated using the OOB data in phase 2.
            assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.cert_address),
                                                                  timeout=timedelta(seconds=10))

            assertThat(self.cert_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.dut_address),
                                                                   timeout=timedelta(seconds=10))

            self.dut.security.RemoveBond(self.cert_address)
            self.cert.security.RemoveBond(self.dut_address)

            assertThat(self.dut_security.get_bond_stream()).emits(
                SecurityMatchers.BondMsg(BondMsgType.DEVICE_UNBONDED, self.cert_address))

            self.dut_security.wait_device_disconnect(self.cert_address)
            self.cert_security.wait_device_disconnect(self.dut_address)

    @metadata(pts_test_id="SM/SLA/SCOB/BV-02-C",
              pts_test_name="Out of Band, IUT Responder, Secure Connections – Success")
    def test_out_of_band_iut_responder_secure_connections(self):
        """
            Verify that the IUT supporting LE Secure Connections is able to perform the Out-of-Band pairing procedure correctly when acting as peripheral, responder.
        """

        oob_combinations = [(OOB_NOT_PRESENT, OOB_PRESENT), (OOB_PRESENT, OOB_NOT_PRESENT), (OOB_PRESENT, OOB_PRESENT)]

        for (dut_oob_flag, cert_oob_flag) in oob_combinations:
            print("oob flag combination dut: " + str(dut_oob_flag) + ", cert: " + str(cert_oob_flag))

            self._prepare_dut_for_connection()

            if dut_oob_flag == LeOobDataFlag.PRESENT:
                oobdata = self.cert.security.GetLeOutOfBandData(empty_proto.Empty())
                self.dut.security.SetOutOfBandData(
                    OobDataMessage(address=self.cert_address,
                                   confirmation_value=oobdata.confirmation_value,
                                   random_value=oobdata.random_value))

            if cert_oob_flag == LeOobDataFlag.PRESENT:
                oobdata = self.dut.security.GetLeOutOfBandData(empty_proto.Empty())
                self.cert.security.SetOutOfBandData(
                    OobDataMessage(address=self.dut_address,
                                   confirmation_value=oobdata.confirmation_value,
                                   random_value=oobdata.random_value))

            self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
            self.dut.security.SetLeOobDataPresent(dut_oob_flag)
            self.dut_security.SetLeAuthRequirements(bond=1, mitm=1, secure_connections=1)

            self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
            self.cert.security.SetLeOobDataPresent(cert_oob_flag)
            self.cert_security.SetLeAuthRequirements(bond=1, mitm=1, secure_connections=1)

            # 1. Lower Tester transmits a Pairing Request command with OOB data flag set to either 0x00 or 0x01, and Secure Connections flag set to '1'.
            self.cert.security.CreateBondLe(self.dut_address)

            assertThat(self.dut_security.get_ui_stream()).emits(
                SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

            # 2. IUT responds with a Pairing Response command with Secure Connections flag set to '1' and OOB data flag set to either 0x00 or 0x01.
            self.dut.security.SendUiCallback(
                UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                              boolean=True,
                              unique_id=1,
                              address=self.cert_address))

            # 3. IUT uses the 128-bit value generated by the Lower Tester as the confirm value. Similarly, the Lower Tester uses the 128-bit value generated by the IUT as the confirm value.

            # 4. IUT and Lower Tester perform phase 2 of the pairing process and establish an encrypted link with an LTK generated using the OOB data in phase 2.
            assertThat(self.cert_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.dut_address),
                                                                   timeout=timedelta(seconds=10))

            assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.cert_address),
                                                                  timeout=timedelta(seconds=10))

            self.cert.security.RemoveBond(self.dut_address)
            self.dut.security.RemoveBond(self.cert_address)

            assertThat(self.dut_security.get_bond_stream()).emits(
                SecurityMatchers.BondMsg(BondMsgType.DEVICE_UNBONDED, self.cert_address))

            self.cert_security.wait_device_disconnect(self.dut_address)
            self.dut_security.wait_device_disconnect(self.cert_address)

    @metadata(pts_test_id="SM/SLA/SCOB/BV-03-C",
              pts_test_name="Out of Band, IUT Responder, Secure Connections – Handle AuthReq Flag RFU Correctly")
    def test_out_of_band_iut_responder_secure_connections_auth_req_rfu(self):
        """
            Verify that the IUT supporting LE Secure Connections is able to perform the Out-of-Band pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as peripheral, responder.
        """

        reserved_bits_combinations = [1, 2, 3]

        for reserved_bits in reserved_bits_combinations:
            print("reserved bits in cert dut: " + str(reserved_bits))

            self._prepare_dut_for_connection()

            oobdata = self.cert.security.GetLeOutOfBandData(empty_proto.Empty())
            self.dut.security.SetOutOfBandData(
                OobDataMessage(address=self.cert_address,
                               confirmation_value=oobdata.confirmation_value,
                               random_value=oobdata.random_value))

            oobdata = self.dut.security.GetLeOutOfBandData(empty_proto.Empty())
            self.cert.security.SetOutOfBandData(
                OobDataMessage(address=self.dut_address,
                               confirmation_value=oobdata.confirmation_value,
                               random_value=oobdata.random_value))

            self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
            self.dut.security.SetLeOobDataPresent(OOB_PRESENT)
            self.dut_security.SetLeAuthRequirements(bond=1, mitm=0, secure_connections=1)

            self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
            self.cert.security.SetLeOobDataPresent(OOB_PRESENT)
            self.cert_security.SetLeAuthRequirements(bond=1, mitm=1, secure_connections=1, reserved_bits=reserved_bits)

            # 1. Lower Tester transmits Pairing Request command with:
            # a. IO Capability set to any IO capability
            # b. OOB data flag set to 0x01 (OOB Authentication data from remote device present)
            # c. MITM set to ‘0’, Secure Connections flag is set to '1', and all reserved bits are set to a random value.
            self.cert.security.CreateBondLe(self.dut_address)

            assertThat(self.dut_security.get_ui_stream()).emits(
                SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.cert_address))

            # 2. IUT responds with a Pairing Response command, with:
            # a. IO Capability set to any IO capability
            # b. OOB data flag set to 0x01 (OOB Authentication data present)
            # c. Secure Connections flag is set to '1', All reserved bits are set to ‘0’
            self.dut.security.SendUiCallback(
                UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                              boolean=True,
                              unique_id=1,
                              address=self.cert_address))

            # 3. IUT and Lower Tester perform phase 2 of the OOB authenticated pairing and establish an encrypted link with the generated LTK.

            assertThat(self.cert_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.dut_address),
                                                                   timeout=timedelta(seconds=10))

            assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.cert_address),
                                                                  timeout=timedelta(seconds=10))

            self.cert.security.RemoveBond(self.dut_address)
            self.dut.security.RemoveBond(self.cert_address)

            assertThat(self.dut_security.get_bond_stream()).emits(
                SecurityMatchers.BondMsg(BondMsgType.DEVICE_UNBONDED, self.cert_address))

            self.dut_security.wait_device_disconnect(self.cert_address)
            self.cert_security.wait_device_disconnect(self.dut_address)

    @metadata(pts_test_id="SM/MAS/SCOB/BV-04-C",
              pts_test_name="Out of Band, IUT Initiator, Secure Connections – Handle AuthReq Flag RFU Correctly")
    def test_out_of_band_iut_initiator_secure_connections_auth_req_rfu(self):
        """
            Verify that the IUT supporting LE Secure Connections is able to perform the Out-of-Band pairing procedure when receiving additional bits set in the AuthReq flag. Reserved For Future Use bits are correctly handled when acting as central, initiator.
        """

        reserved_bits_combinations = [1, 2, 3]

        for reserved_bits in reserved_bits_combinations:
            print("reserved bits in cert dut: " + str(reserved_bits))

            self._prepare_cert_for_connection()

            oobdata = self.cert.security.GetLeOutOfBandData(empty_proto.Empty())
            self.dut.security.SetOutOfBandData(
                OobDataMessage(address=self.cert_address,
                               confirmation_value=oobdata.confirmation_value,
                               random_value=oobdata.random_value))

            oobdata = self.dut.security.GetLeOutOfBandData(empty_proto.Empty())
            self.cert.security.SetOutOfBandData(
                OobDataMessage(address=self.dut_address,
                               confirmation_value=oobdata.confirmation_value,
                               random_value=oobdata.random_value))

            self.dut.security.SetLeIoCapability(KEYBOARD_ONLY)
            self.dut.security.SetLeOobDataPresent(OOB_PRESENT)
            self.dut_security.SetLeAuthRequirements(bond=1, mitm=0, secure_connections=1, reserved_bits=0)

            self.cert.security.SetLeIoCapability(DISPLAY_ONLY)
            self.cert.security.SetLeOobDataPresent(OOB_PRESENT)
            self.cert_security.SetLeAuthRequirements(bond=1, mitm=1, secure_connections=1, reserved_bits=reserved_bits)

            # 1. IUT transmits Pairing Request command with:
            # a. IO Capability set to any IO capability
            # b. OOB data flag set to 0x01 (OOB Authentication data present)
            # c. MITM set to ‘0’, Secure Connections flag is set to '1', and all reserved bits are set to ‘0’
            self.dut.security.CreateBondLe(self.cert_address)

            assertThat(self.cert_security.get_ui_stream()).emits(
                SecurityMatchers.UiMsg(UiMsgType.DISPLAY_PAIRING_PROMPT, self.dut_address))

            # 2. Lower Tester responds with a Pairing Response command, with:
            # a. IO Capability set to any IO capability
            # b. OOB data flag set to 0x01 (OOB Authentication data present)
            # c. Secure Connections flag is set to '1', and all reserved bits are set to a random value.
            self.cert.security.SendUiCallback(
                UiCallbackMsg(message_type=UiCallbackType.PAIRING_PROMPT,
                              boolean=True,
                              unique_id=1,
                              address=self.dut_address))

            # 3. IUT and Lower Tester perform phase 2 of the OOB authenticated pairing and establish an encrypted link with the generated LTK.

            assertThat(self.dut_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.cert_address),
                                                                  timeout=timedelta(seconds=10))

            assertThat(self.cert_security.get_bond_stream()).emits(SecurityMatchers.BondMsg(
                BondMsgType.DEVICE_BONDED, self.dut_address),
                                                                   timeout=timedelta(seconds=10))

            self.dut.security.RemoveBond(self.cert_address)
            self.cert.security.RemoveBond(self.dut_address)

            assertThat(self.dut_security.get_bond_stream()).emits(
                SecurityMatchers.BondMsg(BondMsgType.DEVICE_UNBONDED, self.cert_address))

            self.dut_security.wait_device_disconnect(self.cert_address)
            self.cert_security.wait_device_disconnect(self.dut_address)


if __name__ == '__main__':
    test_runner.main()
