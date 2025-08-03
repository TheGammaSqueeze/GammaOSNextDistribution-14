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

import logging

from blueberry.tests.gd.cert.py_security import PySecurity
from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.gd.cert import gd_base_test
from blueberry.tests.gd.security.cert_security import CertSecurity
from blueberry.facade import common_pb2 as common
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.hci import controller_facade_pb2 as controller_facade
from blueberry.facade.hci import le_initiator_address_facade_pb2 as le_initiator_address_facade
from blueberry.facade.l2cap.classic.facade_pb2 import ClassicSecurityPolicy
from blueberry.facade.neighbor import facade_pb2 as neighbor_facade
from blueberry.facade.security.facade_pb2 import AuthenticationRequirements
from blueberry.facade.security.facade_pb2 import BondMsgType
from blueberry.facade.security.facade_pb2 import IoCapabilities
from blueberry.facade.security.facade_pb2 import OobDataPresent
from blueberry.facade.security.facade_pb2 import UiMsgType
from mobly import test_runner


class SecurityTest(gd_base_test.GdBaseTestClass):
    """
        Collection of tests that each sample results from
        different (unique) combinations of io capabilities, authentication requirements, and oob data.
    """

    _io_capabilities_name_lookup = {
        IoCapabilities.DISPLAY_ONLY: "DISPLAY_ONLY",
        IoCapabilities.DISPLAY_YES_NO_IO_CAP: "DISPLAY_YES_NO_IO_CAP",
        #IoCapabilities.KEYBOARD_ONLY:"KEYBOARD_ONLY",
        IoCapabilities.NO_INPUT_NO_OUTPUT: "NO_INPUT_NO_OUTPUT",
    }

    _auth_reqs_name_lookup = {
        AuthenticationRequirements.NO_BONDING: "NO_BONDING",
        AuthenticationRequirements.NO_BONDING_MITM_PROTECTION: "NO_BONDING_MITM_PROTECTION",
        AuthenticationRequirements.DEDICATED_BONDING: "DEDICATED_BONDING",
        AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION: "DEDICATED_BONDING_MITM_PROTECTION",
        AuthenticationRequirements.GENERAL_BONDING: "GENERAL_BONDING",
        AuthenticationRequirements.GENERAL_BONDING_MITM_PROTECTION: "GENERAL_BONDING_MITM_PROTECTION",
    }

    # Possible IO Capabilities
    io_capabilities = (
        IoCapabilities.DISPLAY_ONLY,
        IoCapabilities.DISPLAY_YES_NO_IO_CAP,
        # TODO(optedoblivion): Uncomment when Passkey Entry is implemented in ClassicPairingHandler
        #IoCapabilities.KEYBOARD_ONLY,
        IoCapabilities.NO_INPUT_NO_OUTPUT)

    # Possible Authentication Requirements
    auth_reqs = (AuthenticationRequirements.NO_BONDING, AuthenticationRequirements.NO_BONDING_MITM_PROTECTION,
                 AuthenticationRequirements.DEDICATED_BONDING,
                 AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION,
                 AuthenticationRequirements.GENERAL_BONDING, AuthenticationRequirements.GENERAL_BONDING_MITM_PROTECTION)

    # Possible Out-of-Band data options
    oob_present = (
        OobDataPresent.NOT_PRESENT,
        # TODO(optedoblivion): Uncomment when OOB is implemented in root canal
        #"P192_PRESENT",
        #"P256_PRESENT",
        #"P192_AND_256_PRESENT"
    )

    mitm_auth_reqs = (AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION,
                      AuthenticationRequirements.GENERAL_BONDING_MITM_PROTECTION,
                      AuthenticationRequirements.NO_BONDING_MITM_PROTECTION)

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='SECURITY', cert_module='L2CAP')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.setup_test(self)

        self.dut.neighbor.EnablePageScan(neighbor_facade.EnableMsg(enabled=True))
        self.cert.neighbor.EnablePageScan(neighbor_facade.EnableMsg(enabled=True))

        self.dut.name = b'DUT Device'
        self.dut.address = self.dut.hci_controller.GetMacAddress(empty_proto.Empty()).address
        self.cert.name = b'Cert Device'
        self.cert.address = self.cert.hci_controller.GetMacAddress(empty_proto.Empty()).address

        # TODO(optedoblivion): Make this happen in PySecurity or GdDevice
        self.dut.hci_controller.WriteLocalName(controller_facade.NameMsg(name=self.dut.name))
        self.cert.hci_controller.WriteLocalName(controller_facade.NameMsg(name=self.cert.name))

        self.dut_security = PySecurity(self.dut)
        self.cert_security = CertSecurity(self.cert)

        self.dut_address = common.BluetoothAddressWithType(
            address=common.BluetoothAddress(address=bytes(b'DD:05:04:03:02:01')), type=common.RANDOM_DEVICE_ADDRESS)
        privacy_policy = le_initiator_address_facade.PrivacyPolicy(
            address_policy=le_initiator_address_facade.AddressPolicy.USE_STATIC_ADDRESS,
            address_with_type=self.dut_address)
        self.dut.security.SetLeInitiatorAddressPolicy(privacy_policy)

    def teardown_test(self):
        self.dut_security.close()
        self.cert_security.close()
        gd_base_test.GdBaseTestClass.teardown_test(self)

    # Initiates the numeric comparison test
    def _run_ssp_numeric_comparison(self, initiator, responder, init_ui_response, resp_ui_response,
                                    expected_init_ui_event, expected_resp_ui_event, expected_init_bond_event,
                                    expected_resp_bond_event):
        initiator.enable_secure_simple_pairing()
        responder.enable_secure_simple_pairing()
        initiator.create_bond(responder.get_address(), common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self._verify_ssp_numeric_comparison(initiator, responder, init_ui_response, resp_ui_response,
                                            expected_init_ui_event, expected_resp_ui_event, expected_init_bond_event,
                                            expected_resp_bond_event)

    # Verifies the events for the numeric comparion test
    def _verify_ssp_numeric_comparison(self, initiator, responder, init_ui_response, resp_ui_response,
                                       expected_init_ui_event, expected_resp_ui_event, expected_init_bond_event,
                                       expected_resp_bond_event):

        def on_responder_reply():
            initiator.on_user_input(responder.get_address(), init_ui_response, expected_init_ui_event)

        responder.accept_pairing(initiator.get_address(), resp_ui_response, init_ui_response, on_responder_reply)
        initiator.wait_for_bond_event(expected_init_bond_event)
        responder.wait_for_bond_event(expected_resp_bond_event)

    def _run_ssp_oob(self, initiator, responder, init_ui_response, resp_ui_response, expected_init_ui_event,
                     expected_resp_ui_event, expected_init_bond_event, expected_resp_bond_event, p192_oob_data,
                     p256_oob_data):
        initiator.enable_secure_simple_pairing()
        responder.enable_secure_simple_pairing()
        initiator.create_bond_out_of_band(responder.get_address(),
                                          common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS, p192_oob_data,
                                          p256_oob_data)
        self._verify_ssp_oob(initiator, responder, init_ui_response, resp_ui_response, expected_init_ui_event,
                             expected_resp_ui_event, expected_init_bond_event, expected_resp_bond_event, p192_oob_data,
                             p256_oob_data)

    # Verifies the events for the numeric comparion test
    def _verify_ssp_oob(self, initiator, responder, init_ui_response, resp_ui_response, expected_init_ui_event,
                        expected_resp_ui_event, expected_init_bond_event, expected_resp_bond_event, p192_oob_data,
                        p256_oob_data):
        responder.accept_oob_pairing(initiator.get_address())
        initiator.on_user_input(responder.get_address(), init_ui_response, expected_init_ui_event)
        initiator.wait_for_bond_event(expected_init_bond_event)
        responder.wait_for_bond_event(expected_resp_bond_event)

    def _run_ssp_passkey(self, initiator, responder, expected_init_bond_event, expected_resp_bond_event):
        initiator.enable_secure_simple_pairing()
        responder.enable_secure_simple_pairing()
        initiator.create_bond(responder.get_address(), common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self._verify_ssp_passkey(initiator, responder, expected_init_bond_event, expected_resp_bond_event)

    def _verify_ssp_passkey(self, initiator, responder, expected_init_bond_event, expected_resp_bond_event):
        responder.send_io_caps(initiator.get_address())
        passkey = initiator.wait_for_passkey(responder.get_address())
        responder.input_passkey(initiator.get_address(), passkey)
        initiator.wait_for_bond_event(expected_init_bond_event)
        responder.wait_for_bond_event(expected_resp_bond_event)

    def _run_pin(self, initiator, responder, expected_init_bond_event, expected_resp_bond_event):
        initiator.create_bond(responder.get_address(), common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self._verify_pin(initiator, responder, expected_init_bond_event, expected_resp_bond_event)

    def _verify_pin(self, initiator, responder, expected_init_bond_event, expected_resp_bond_event):
        pin = b'123456789A'
        logging.info("pin: %s" % pin)
        initiator.input_pin(responder.get_address(), pin)
        responder.input_pin(initiator.get_address(), pin)
        initiator.wait_for_bond_event(expected_init_bond_event)
        responder.wait_for_bond_event(expected_resp_bond_event)

    def test_setup_teardown(self):
        """
            Make sure our setup and teardown is sane
        """
        pass

    # no_input_no_output + no_input_no_output is JustWorks no confirmation
    def test_dut_initiated_no_input_no_output_no_input_no_output_twice_bond_and_enforce(self):
        # Arrange
        self.dut_security.set_io_capabilities(IoCapabilities.NO_INPUT_NO_OUTPUT)
        self.dut_security.set_authentication_requirements(AuthenticationRequirements.DEDICATED_BONDING)
        self.cert_security.set_io_capabilities(IoCapabilities.NO_INPUT_NO_OUTPUT)
        self.cert_security.set_authentication_requirements(AuthenticationRequirements.DEDICATED_BONDING)

        # Act and Assert
        self._run_ssp_numeric_comparison(
            initiator=self.dut_security,
            responder=self.cert_security,
            init_ui_response=True,
            resp_ui_response=True,
            expected_init_ui_event=None,
            expected_resp_ui_event=None,
            expected_init_bond_event=BondMsgType.DEVICE_BONDED,
            expected_resp_bond_event=None)

        self.dut_security.enforce_security_policy(self.cert.address,
                                                  common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS,
                                                  ClassicSecurityPolicy.ENCRYPTED_TRANSPORT)

        # TODO: We verify enforcement when we make sure EncryptionChange is received on DUT

    # no_input_no_output + no_input_no_output is JustWorks no confirmation
    def test_dut_initiated_no_input_no_output_no_input_no_output_twice_with_remove_bond(self):
        # Arrange
        self.dut_security.set_io_capabilities(IoCapabilities.NO_INPUT_NO_OUTPUT)
        self.dut_security.set_authentication_requirements(AuthenticationRequirements.DEDICATED_BONDING)
        self.cert_security.set_io_capabilities(IoCapabilities.NO_INPUT_NO_OUTPUT)
        self.cert_security.set_authentication_requirements(AuthenticationRequirements.DEDICATED_BONDING)

        # Act and Assert
        self._run_ssp_numeric_comparison(
            initiator=self.dut_security,
            responder=self.cert_security,
            init_ui_response=True,
            resp_ui_response=True,
            expected_init_ui_event=None,
            expected_resp_ui_event=None,
            expected_init_bond_event=BondMsgType.DEVICE_BONDED,
            expected_resp_bond_event=None)

        self.dut_security.remove_bond(self.cert.address, common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.cert_security.remove_bond(self.cert.address, common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.dut_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)
        self.cert_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)

        self.dut_security.wait_for_disconnect_event()
        self.cert_security.wait_for_disconnect_event()

        # Act and Assert
        self._run_ssp_numeric_comparison(
            initiator=self.dut_security,
            responder=self.cert_security,
            init_ui_response=True,
            resp_ui_response=True,
            expected_init_ui_event=None,
            expected_resp_ui_event=None,
            expected_init_bond_event=BondMsgType.DEVICE_BONDED,
            expected_resp_bond_event=None)

        self.dut_security.remove_bond(self.cert.address, common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.cert_security.remove_bond(self.cert.address, common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.dut_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)
        self.cert_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)

        self.dut_security.wait_for_disconnect_event()
        self.cert_security.wait_for_disconnect_event()

    def test_successful_dut_initiated_ssp_numeric_comparison(self):
        test_count = len(self.io_capabilities) * len(self.auth_reqs) * len(self.oob_present) * len(
            self.io_capabilities) * len(self.auth_reqs) * len(self.oob_present)
        logging.info("Loading %d test combinations" % test_count)
        i = 0
        for dut_io_capability in self.io_capabilities:
            for dut_auth_reqs in self.auth_reqs:
                for dut_oob_present in self.oob_present:
                    for cert_io_capability in self.io_capabilities:
                        for cert_auth_reqs in self.auth_reqs:
                            for cert_oob_present in self.oob_present:
                                i = i + 1
                                logging.info("")
                                logging.info("===================================================")
                                logging.info("Running test %d of %d" % (i, test_count))
                                logging.info("DUT Test Config: %s ; %s ; %s " % (self._io_capabilities_name_lookup.get(
                                    dut_io_capability, "ERROR"), self._auth_reqs_name_lookup.get(
                                        dut_auth_reqs, "ERROR"), dut_oob_present))
                                logging.info(
                                    "CERT Test Config: %s ; %s ; %s " %
                                    (self._io_capabilities_name_lookup.get(cert_io_capability, "ERROR"),
                                     self._auth_reqs_name_lookup.get(cert_auth_reqs, "ERROR"), cert_oob_present))
                                logging.info("===================================================")
                                logging.info("")
                                self.dut_security.set_io_capabilities(dut_io_capability)
                                self.dut_security.set_authentication_requirements(dut_auth_reqs)
                                self.cert_security.set_io_capabilities(cert_io_capability)
                                self.cert_security.set_authentication_requirements(cert_auth_reqs)
                                init_ui_response = True
                                resp_ui_response = True
                                expected_init_ui_event = None  # None is auto accept
                                expected_resp_ui_event = None  # None is auto accept
                                expected_init_bond_event = BondMsgType.DEVICE_BONDED
                                expected_resp_bond_event = None
                                if dut_io_capability == IoCapabilities.DISPLAY_ONLY:
                                    if cert_io_capability == IoCapabilities.DISPLAY_YES_NO_IO_CAP:
                                        expected_resp_ui_event = UiMsgType.DISPLAY_YES_NO_WITH_VALUE
                                        if dut_auth_reqs in self.mitm_auth_reqs or cert_auth_reqs in self.mitm_auth_reqs:
                                            expected_init_bond_event = BondMsgType.DEVICE_BOND_FAILED
                                    elif cert_io_capability == IoCapabilities.KEYBOARD_ONLY:
                                        expected_resp_ui_event = UiMsgType.DISPLAY_PASSKEY_ENTRY
                                    elif cert_io_capability == IoCapabilities.DISPLAY_ONLY:
                                        if dut_auth_reqs in self.mitm_auth_reqs or cert_auth_reqs in self.mitm_auth_reqs:
                                            expected_init_bond_event = BondMsgType.DEVICE_BOND_FAILED
                                    elif cert_io_capability == IoCapabilities.NO_INPUT_NO_OUTPUT:
                                        if dut_auth_reqs in self.mitm_auth_reqs or cert_auth_reqs in self.mitm_auth_reqs:
                                            expected_init_bond_event = BondMsgType.DEVICE_BOND_FAILED
                                elif dut_io_capability == IoCapabilities.DISPLAY_YES_NO_IO_CAP:
                                    expected_init_ui_event = UiMsgType.DISPLAY_YES_NO_WITH_VALUE
                                    if cert_io_capability == IoCapabilities.DISPLAY_YES_NO_IO_CAP:
                                        expected_resp_ui_event = UiMsgType.DISPLAY_YES_NO_WITH_VALUE
                                    elif cert_io_capability == IoCapabilities.KEYBOARD_ONLY:
                                        expected_init_ui_event = UiMsgType.DISPLAY_PASSKEY
                                        expected_resp_ui_event = UiMsgType.DISPLAY_PASSKEY_ENTRY
                                    elif cert_io_capability == IoCapabilities.NO_INPUT_NO_OUTPUT:
                                        expected_init_ui_event = UiMsgType.DISPLAY_YES_NO  # No value
                                elif dut_io_capability == IoCapabilities.KEYBOARD_ONLY:
                                    expected_init_ui_event = UiMsgType.DISPLAY_PASSKEY_ENTRY
                                    if cert_io_capability == IoCapabilities.DISPLAY_ONLY:
                                        expected_resp_ui_event = UiMsgType.DISPLAY_PASSKEY
                                    elif cert_io_capability == IoCapabilities.DISPLAY_YES_NO_IO_CAP:
                                        expected_resp_ui_event = UiMsgType.DISPLAY_PASSKEY_ENTRY
                                    elif cert_io_capability == IoCapabilities.KEYBOARD_ONLY:
                                        expected_resp_ui_event = UiMsgType.DISPLAY_PASSKEY_ENTRY
                                    elif cert_io_capability == IoCapabilities.NO_INPUT_NO_OUTPUT:
                                        if dut_auth_reqs in self.mitm_auth_reqs or cert_auth_reqs in self.mitm_auth_reqs:
                                            expected_init_bond_event = BondMsgType.DEVICE_BOND_FAILED
                                elif dut_io_capability == IoCapabilities.NO_INPUT_NO_OUTPUT:
                                    if cert_io_capability == IoCapabilities.DISPLAY_YES_NO_IO_CAP:
                                        expected_resp_ui_event = UiMsgType.DISPLAY_YES_NO  # No value

                                    if dut_auth_reqs in self.mitm_auth_reqs or cert_auth_reqs in self.mitm_auth_reqs:
                                        expected_init_bond_event = BondMsgType.DEVICE_BOND_FAILED

                                if cert_oob_present == OobDataPresent.NOT_PRESENT:
                                    self._run_ssp_numeric_comparison(
                                        initiator=self.dut_security,
                                        responder=self.cert_security,
                                        init_ui_response=init_ui_response,
                                        resp_ui_response=resp_ui_response,
                                        expected_init_ui_event=expected_init_ui_event,
                                        expected_resp_ui_event=expected_resp_ui_event,
                                        expected_init_bond_event=expected_init_bond_event,
                                        expected_resp_bond_event=expected_resp_bond_event)
                                else:
                                    logging.error("Code path not yet implemented")
                                    assertThat(False).isTrue()

                                self.dut_security.remove_bond(self.cert_security.get_address(),
                                                              common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
                                self.cert_security.remove_bond(self.dut_security.get_address(),
                                                               common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)

                                self.dut_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)
                                self.cert_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)

                                self.dut_security.wait_for_disconnect_event()
                                self.cert_security.wait_for_disconnect_event()

    def test_enable_secure_simple_pairing(self):
        self.dut_security.enable_secure_simple_pairing()
        self.cert_security.enable_secure_simple_pairing()

    def test_enable_secure_connections(self):
        self.dut_security.enable_secure_simple_pairing()
        self.cert_security.enable_secure_simple_pairing()
        self.dut_security.enable_secure_connections()
        self.cert_security.enable_secure_connections()

    def test_get_oob_data_from_dut_controller_p192_present(self):
        oob_data = self.dut_security.get_oob_data_from_controller(OobDataPresent.P192_PRESENT)
        assertThat(len(oob_data)).isEqualTo(4)
        has192C = not all([i == 0 for i in oob_data[0]])
        has192R = not all([i == 0 for i in oob_data[1]])
        has256C = not all([i == 0 for i in oob_data[2]])
        has256R = not all([i == 0 for i in oob_data[3]])
        assertThat(has192C).isTrue()
        assertThat(has192R).isTrue()
        assertThat(has256C).isFalse()
        assertThat(has256R).isFalse()

    def test_get_oob_data_from_cert_controller_not_present(self):
        oob_data = self.cert_security.get_oob_data_from_controller(OobDataPresent.NOT_PRESENT)
        assertThat(len(oob_data)).isEqualTo(4)
        has192C = not all([i == 0 for i in oob_data[0]])
        has192R = not all([i == 0 for i in oob_data[1]])
        has256C = not all([i == 0 for i in oob_data[2]])
        has256R = not all([i == 0 for i in oob_data[3]])
        assertThat(has192C).isFalse()
        assertThat(has192R).isFalse()
        assertThat(has256C).isFalse()
        assertThat(has256R).isFalse()

    def test_get_oob_data_from_cert_controller_p192_present_no_secure_connections(self):
        oob_data = self.cert_security.get_oob_data_from_controller(OobDataPresent.P192_PRESENT)
        assertThat(len(oob_data)).isEqualTo(4)
        has192C = not all([i == 0 for i in oob_data[0]])
        has192R = not all([i == 0 for i in oob_data[1]])
        has256C = not all([i == 0 for i in oob_data[2]])
        has256R = not all([i == 0 for i in oob_data[3]])
        assertThat(has192C).isTrue()
        assertThat(has192R).isTrue()
        assertThat(has256C).isFalse()
        assertThat(has256R).isFalse()

    def test_get_oob_data_from_cert_controller_p192_present(self):
        self.cert_security.enable_secure_simple_pairing()
        self.cert_security.enable_secure_connections()
        oob_data = self.cert_security.get_oob_data_from_controller(OobDataPresent.P192_PRESENT)
        assertThat(len(oob_data)).isEqualTo(4)
        has192C = not all([i == 0 for i in oob_data[0]])
        has192R = not all([i == 0 for i in oob_data[1]])
        has256C = not all([i == 0 for i in oob_data[2]])
        has256R = not all([i == 0 for i in oob_data[3]])
        assertThat(has192C).isTrue()
        assertThat(has192R).isTrue()
        assertThat(has256C).isFalse()
        assertThat(has256R).isFalse()

    def test_get_oob_data_from_cert_controller_p256_present(self):
        self.cert_security.enable_secure_simple_pairing()
        self.cert_security.enable_secure_connections()
        oob_data = self.cert_security.get_oob_data_from_controller(OobDataPresent.P256_PRESENT)
        assertThat(len(oob_data)).isEqualTo(4)
        has192C = not all([i == 0 for i in oob_data[0]])
        has192R = not all([i == 0 for i in oob_data[1]])
        has256C = not all([i == 0 for i in oob_data[2]])
        has256R = not all([i == 0 for i in oob_data[3]])
        assertThat(has192C).isFalse()
        assertThat(has192R).isFalse()
        assertThat(has256C).isTrue()
        assertThat(has256R).isTrue()

    def test_get_oob_data_from_cert_controller_p192_and_p256_present(self):
        self.cert_security.enable_secure_simple_pairing()
        self.cert_security.enable_secure_connections()
        oob_data = self.cert_security.get_oob_data_from_controller(OobDataPresent.P192_AND_256_PRESENT)
        assertThat(len(oob_data)).isEqualTo(4)
        has192C = not all([i == 0 for i in oob_data[0]])
        has192R = not all([i == 0 for i in oob_data[1]])
        has256C = not all([i == 0 for i in oob_data[2]])
        has256R = not all([i == 0 for i in oob_data[3]])
        assertThat(has192C).isTrue()
        assertThat(has192R).isTrue()
        assertThat(has256C).isTrue()
        assertThat(has256R).isTrue()

    def test_successful_dut_initiated_ssp_oob(self):
        dut_io_capability = IoCapabilities.NO_INPUT_NO_OUTPUT
        cert_io_capability = IoCapabilities.NO_INPUT_NO_OUTPUT
        dut_auth_reqs = AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION
        cert_auth_reqs = AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION
        cert_oob_present = OobDataPresent.P192_PRESENT
        self.dut_security.enable_secure_simple_pairing()
        self.dut_security.enable_secure_connections()
        self.cert_security.enable_secure_simple_pairing()
        self.cert_security.enable_secure_connections()
        self.dut_security.set_io_capabilities(dut_io_capability)
        self.dut_security.set_authentication_requirements(dut_auth_reqs)
        self.cert_security.set_io_capabilities(cert_io_capability)
        self.cert_security.set_authentication_requirements(cert_auth_reqs)
        init_ui_response = True
        resp_ui_response = True
        expected_init_ui_event = None  # None is auto accept
        expected_resp_ui_event = None  # None is auto accept
        expected_init_bond_event = BondMsgType.DEVICE_BONDED
        expected_resp_bond_event = None
        # get_oob_data returns a tuple of bytes (p192c,p192r,p256c,p256r)
        local_oob_data = self.cert_security.get_oob_data_from_controller(cert_oob_present)
        p192_oob_data = local_oob_data[0:2]
        p256_oob_data = local_oob_data[2:4]
        self._run_ssp_oob(
            initiator=self.dut_security,
            responder=self.cert_security,
            init_ui_response=init_ui_response,
            resp_ui_response=resp_ui_response,
            expected_init_ui_event=expected_init_ui_event,
            expected_resp_ui_event=expected_resp_ui_event,
            expected_init_bond_event=expected_init_bond_event,
            expected_resp_bond_event=expected_resp_bond_event,
            p192_oob_data=p192_oob_data,
            p256_oob_data=p256_oob_data)
        self.dut_security.remove_bond(self.cert_security.get_address(),
                                      common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.cert_security.remove_bond(self.dut_security.get_address(),
                                       common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.dut_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)
        self.cert_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)
        self.dut_security.wait_for_disconnect_event()
        self.cert_security.wait_for_disconnect_event()

    def test_successful_dut_initiated_ssp_keyboard(self):
        dut_io_capability = IoCapabilities.DISPLAY_YES_NO_IO_CAP
        dut_auth_reqs = AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION
        dut_oob_present = OobDataPresent.NOT_PRESENT
        cert_io_capability = IoCapabilities.KEYBOARD_ONLY
        cert_auth_reqs = AuthenticationRequirements.DEDICATED_BONDING_MITM_PROTECTION
        cert_oob_present = OobDataPresent.NOT_PRESENT
        self.dut_security.set_io_capabilities(dut_io_capability)
        self.dut_security.set_authentication_requirements(dut_auth_reqs)
        self.cert_security.set_io_capabilities(cert_io_capability)
        self.cert_security.set_authentication_requirements(cert_auth_reqs)

        self._run_ssp_passkey(
            initiator=self.dut_security,
            responder=self.cert_security,
            expected_init_bond_event=BondMsgType.DEVICE_BONDED,
            expected_resp_bond_event=BondMsgType.DEVICE_BONDED)

        self.dut_security.remove_bond(self.cert_security.get_address(),
                                      common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.cert_security.remove_bond(self.dut_security.get_address(),
                                       common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)

        self.dut_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)
        self.cert_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)

        self.dut_security.wait_for_disconnect_event()
        self.cert_security.wait_for_disconnect_event()

    def test_successful_dut_initiated_pin(self):
        self.dut_security.set_io_capabilities(IoCapabilities.DISPLAY_YES_NO_IO_CAP)
        self.dut_security.set_authentication_requirements(AuthenticationRequirements.DEDICATED_BONDING)

        self._run_pin(
            initiator=self.dut_security,
            responder=self.cert_security,
            expected_init_bond_event=BondMsgType.DEVICE_BONDED,
            expected_resp_bond_event=BondMsgType.DEVICE_BONDED)

        self.dut_security.remove_bond(self.cert_security.get_address(),
                                      common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)
        self.cert_security.remove_bond(self.dut_security.get_address(),
                                       common.BluetoothAddressTypeEnum.PUBLIC_DEVICE_ADDRESS)

        self.dut_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)
        self.cert_security.wait_for_bond_event(BondMsgType.DEVICE_UNBONDED)

        self.dut_security.wait_for_disconnect_event()
        self.cert_security.wait_for_disconnect_event()

    def test_make_sure_oob_data_different(self):
        oob_data = self.dut_security.get_oob_data_from_controller(OobDataPresent.P192_PRESENT)
        oob_data2 = self.dut_security.get_oob_data_from_controller(OobDataPresent.P192_PRESENT)
        assertThat(oob_data).isNotEqualTo(oob_data2)


if __name__ == '__main__':
    test_runner.main()
