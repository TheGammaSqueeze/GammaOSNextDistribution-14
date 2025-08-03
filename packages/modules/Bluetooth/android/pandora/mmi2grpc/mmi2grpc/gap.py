# Copyright (C) 2024 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from threading import Thread
from mmi2grpc._helpers import assert_description, match_description
from mmi2grpc._rootcanal import Dongle
from mmi2grpc._proxy import ProfileProxy
from time import sleep
import sys

from pandora_experimental.gatt_grpc import GATT
from pandora_experimental.gatt_pb2 import GattServiceParams, GattCharacteristicParams
from pandora.host_grpc import Host
from pandora.host_pb2 import PUBLIC, RANDOM, DISCOVERABLE_GENERAL, NOT_DISCOVERABLE, DISCOVERABLE_LIMITED, NOT_CONNECTABLE, DataTypes
from pandora.security_grpc import Security, SecurityStorage
from pandora.security_pb2 import LEVEL1, LEVEL2, LE_LEVEL3, PairingEventAnswer


class GAPProxy(ProfileProxy):

    def __init__(self, channel, rootcanal):
        super().__init__(channel)
        self.gatt = GATT(channel)
        self.host = Host(channel)
        self.security = Security(channel)
        self.security_storage = SecurityStorage(channel)
        self.rootcanal = rootcanal

        self.connection = None
        self.pairing_events = None
        self.inquiry_responses = None
        self.scan_responses = None

        self.counter = 0
        self.cached_passkey = None

        self._auto_confirm_requests()

    def test_started(self, test: str, description: str, pts_addr: bytes):
        if test in [
                "GAP/CONN/CPUP/BV-06-C",
        ]:
            self.rootcanal.select_pts_dongle(Dongle.LAIRD_BL654)
        else:
            self.rootcanal.select_pts_dongle(Dongle.CSR_RCK_PTS_DONGLE)

        if test in [
                "GAP/DM/LEP/BV-07-C",
                "GAP/DM/LEP/BV-08-C",
                "GAP/DM/LEP/BV-11-C",
                "GAP/MOD/CON/BV-01-C",
        ]:
            self.host.SetDiscoverabilityMode(mode=DISCOVERABLE_GENERAL)

        return "OK"

    @match_description
    def TSC_MMI_iut_send_hci_connect_request(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please send an HCI connect request to establish a basic rate
        connection( after the IUT discovers the Lower Tester over BR and LE)?.
        """

        if test in [
                "GAP/IDLE/BON/BV-03-C",
                "GAP/IDLE/BON/BV-04-C",
                "GAP/IDLE/BON/BV-05-C",
                "GAP/IDLE/BON/BV-06-C",
                "GAP/SEC/AUT/BV-02-C",
                "GAP/SEC/SEM/BV-05-C",
                "GAP/SEC/SEM/BV-08-C",
                "GAP/SEC/SEM/BV-50-C",
                "GAP/SEC/SEM/BI-27-C",
                "GAP/SEC/SEM/BI-32-C",
                "GAP/EST/LIE/BV-02-C",
        ]:
            # we connect then pair, so we have to pair directly in this MMI
            self.pairing_events = self.security.OnPairing()
            self.connection = self.host.Connect(address=pts_addr).connection
        else:
            self.connection = self.host.Connect(address=pts_addr).connection

        return "OK"

    @assert_description
    def _mmi_222(self, **kwargs):
        """
        Please initiate a BR/EDR security authentication and pairing with
        interaction of HCI commands.

        Press OK to continue.
        """

        # pairing already initiated with Connect() on Android
        self.pairing_events = self.security.OnPairing()

        return "OK"

    @match_description
    def _mmi_2001(self, passkey: str, **kwargs):
        """
        Please verify the passKey is correct: (?P<passkey>[0-9]+)
        """

        for event in self.pairing_events:
            assert event.numeric_comparison == int(passkey), (event, passkey)
            self.pairing_events.send(PairingEventAnswer(event=event, confirm=True))
            return "OK"

        assert False, "did not receive expected pairing event"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_connectable_undirected(self, **kwargs):
        """
        Please send a connectable undirected advertising report.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=PUBLIC,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_handle_for_insufficient_authentication(self, pts_addr: bytes, **kwargs):
        """
        Please enter the handle(2 octet) to the characteristic in the IUT
        database where Insufficient Authentication error will be returned :
        """

        response = self.gatt.RegisterService(service=GattServiceParams(
            uuid="955798ce-3022-455c-b759-ee8edcd73d1a",
            characteristics=[
                GattCharacteristicParams(
                    uuid="cf99ed9b-3c43-4343-b8a7-8afa513752ce",
                    properties=0x02,  # PROPERTY_READ,
                    permissions=0x04,  # PERMISSION_READ_ENCRYPTED_MITM
                ),
            ],
        ))

        self.pairing_events = self.security.OnPairing()

        return handle_format(response.service.characteristics[0].handle)

    @match_description
    def TSC_MMI_the_security_id_is(self, pts_addr: bytes, passkey: str, **kwargs):
        """
        The Secure ID is (?P<passkey>[0-9]*)
        """

        for event in self.pairing_events:
            if event.address == pts_addr and event.passkey_entry_request:
                self.pairing_events.send(PairingEventAnswer(event=event, passkey=int(passkey)))
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_iut_send_le_connect_request(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please send an LE connect request to establish a connection.
        """

        if test == "GAP/DM/BON/BV-01-C":
            # we also begin pairing here if we are not already paired on LE
            if self.counter == 0:
                self.counter += 1
                self.security_storage.DeleteBond(public=pts_addr)
                self.connection = self.host.ConnectLE(own_address_type=RANDOM, public=pts_addr).connection
                self.security.Secure(connection=self.connection, le=LE_LEVEL3)
                return "OK"

        if test == "GAP/SEC/AUT/BV-21-C" and self.connection is not None:
            # no-op since the peer just disconnected from us,
            # so we have immediately auto-connected back to it
            return "OK"

        if test in [
                "GAP/CONN/DCEP/BV-03-C",
                "GAP/CONN/GCEP/BV-02-C",
                "GAP/DM/LEP/BV-06-C",
                "GAP/CONN/GCEP/BV-01-C",
        ]:
            # PTS is not advertising with the local name, use identity address
            address = pts_addr
        else:
            # the PTS sometimes decides to advertise with an RPA, so we do a scan to find its real address
            scans = self.host.Scan()
            for scan in scans:
                adv_address = scan.public if scan.HasField("public") else scan.random
                device_name = scan.data.complete_local_name
                if "pts" in device_name.lower():
                    address = adv_address
                    scans.cancel()
                    break

        self.pairing_events = self.security.OnPairing()
        self.connection = self.host.ConnectLE(own_address_type=RANDOM, public=address).connection

        if test in {"GAP/BOND/BON/BV-04-C"}:
            self.security.Secure(connection=self.connection, le=LE_LEVEL3)

        return "OK"

    @assert_description
    def TSC_MMI_enter_security_id(self, pts_addr: bytes, **kwargs):
        """
        Please enter Secure Id.
        """

        if self.cached_passkey is not None:
            self.log(f"Returning cached passkey entry {self.cached_passkey}")
            return str(self.cached_passkey)

        for event in self.pairing_events:
            if event.address == pts_addr and event.passkey_entry_notification:
                self.log(f"Got passkey entry {event.passkey_entry_notification}")
                self.cached_passkey = event.passkey_entry_notification
                return str(event.passkey_entry_notification)

        assert False

    @match_description
    def TSC_MMI_iut_send_att_service_request(self, pts_addr: bytes, handle: str, **kwargs):
        r"""
        Please send an ATT service request - read or write request with handle
        (?P<handle>[0-9a-e]+) \(octet\).Discover services if needed.
        """

        self.gatt.ReadCharacteristicFromHandle(
            connection=self.connection,
            # They want us to read the characteristic value handle using ATT, but the interface only lets us
            # read the characteristic by its handle. So we offset by one, since in this test the characteristic
            # value handle is one above the characteristic handle itself.
            handle=int(handle, base=16) - 1,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_service_uuid(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Service UUID.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            own_address_type=PUBLIC,
            data=DataTypes(complete_service_class_uuids128=["955798ce-3022-455c-b759-ee8edcd73d1a"],))
        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_local_name(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Local Name.
        """

        self.advertise = self.host.Advertise(own_address_type=PUBLIC,
                                             legacy=True,
                                             data=DataTypes(
                                                 include_complete_local_name=True,
                                                 include_shortened_local_name=True,
                                             ))

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_flags(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Flags.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            connectable=True,
            own_address_type=PUBLIC,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_manufacturer_specific_data(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with Manufacture
        Specific Data.
        """

        self.advertise = self.host.Advertise(own_address_type=PUBLIC,
                                             legacy=True,
                                             data=DataTypes(manufacturer_specific_data=b"d0n't b3 3v1l!",))

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_tx_power_level(self, **kwargs):
        """
        Please prepare IUT to send an advertising report with TX Power Level.
        """

        self.advertise = self.host.Advertise(legacy=True,
                                             own_address_type=PUBLIC,
                                             data=DataTypes(include_tx_power_level=True,))

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_connectable(self, **kwargs):
        """
        Please send a connectable advertising report.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            own_address_type=PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_ADV_IND(self, **kwargs):
        """
        Please send connectable undirected advertising report.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            own_address_type=PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_idle_mode_security_4(self, **kwargs):
        """
        Please confirm that IUT is in Idle mode with security mode 4. Press OK
        when IUT is ready to start device discovery.
        """

        return "OK"

    @match_description
    def TSC_MMI_iut_start_general_inquiry_found(self, pts_addr: bytes, **kwargs):
        """
        Please start general inquiry. Click 'Yes' If IUT does discovers PTS and
        ready for PTS to initiate (a|LE) create connection otherwise click 'No'.
        """

        inquiry_responses = self.host.Inquiry()
        for response in inquiry_responses:
            assert response.address == pts_addr, (response.address, pts_addr)
            inquiry_responses.cancel()
            return "Yes"

        assert False

    @assert_description
    def TSC_MMI_iut_send_att_read_by_type_request_name_request(self, pts_addr: bytes, **kwargs):
        """
        Please start the Name Discovery Procedure to retrieve Device Name from
        the PTS.
        """

        # Android does RNR when connecting for the first time
        self.connection = self.host.Connect(address=pts_addr).connection

        return "OK"

    @match_description
    def TSC_MMI_iut_confirm_device_discovery(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please confirm that IUT has discovered PTS and retrieved its name '?(?P<name>[a-zA-Z\-0-9]*)'?\.?
        """
        #Verifying if the BD Address matches in Inquiry
        inquiry_responses = self.host.Inquiry()
        for response in inquiry_responses:
            assert response.address == pts_addr, (response.address, pts_addr)
            inquiry_responses.cancel()
            return "Yes"

        assert False

    @assert_description
    def TSC_MMI_check_if_iut_support_non_connectable_advertising(self, **kwargs):
        """
        Does the IUT have an ability to send non-connectable advertising report?
        """

        return "Yes"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_general_discoverable_ok_to_continue(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report. Press OK to continue.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=DISCOVERABLE_GENERAL),
            own_address_type=PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_general_discoverable_0203(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report using either non - directed advertising or
        discoverable undirected advertising.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=DISCOVERABLE_GENERAL),
            own_address_type=PUBLIC,
            connectable=True,
        )

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_undirected_connectable_mode_non_discoverable_mode(self, **kwargs):
        """
        Please prepare IUT into non-discoverable mode and send an advertising
        report using connectable undirected advertising.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=NOT_DISCOVERABLE),
            own_address_type=PUBLIC,
            connectable=True,
        )

        return "OK"

    def TSC_MMI_iut_send_advertising_report_event_general_discoverable_00(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report using connectable undirected advertising.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=DISCOVERABLE_GENERAL),
            own_address_type=PUBLIC,
            connectable=True,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_general_discovery(self, **kwargs):
        """
        Please start General Discovery. Press OK to continue.
        """

        self.scan_responses = self.host.Scan()

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_limited_discovery(self, **kwargs):
        """
        Please start Limited Discovery. Press OK to continue.
        """

        self.scan_responses = self.host.Scan()

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_general_discovered_device(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is discovered.
        """

        for response in self.scan_responses:
            assert response.HasField("public")
            # General Discoverability shall be able to check both limited and general advertising
            if response.public == pts_addr:
                self.scan_responses.cancel()
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_iut_confirm_limited_discovered_device(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is discovered.
        """

        for response in self.scan_responses:
            assert response.HasField("public")
            if (response.public == pts_addr and response.data.le_discoverability_mode == DISCOVERABLE_LIMITED):
                self.scan_responses.cancel()
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_iut_confirm_general_discovered_device_not_found(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is NOT discovered.
        """

        discovered = False

        def search():
            nonlocal discovered
            for response in self.scan_responses:
                assert response.HasField("public")
                if (response.public == pts_addr and response.data.le_discoverability_mode == DISCOVERABLE_GENERAL):
                    self.scan_responses.cancel()
                    discovered = True
                    return

        # search for five seconds, if we don't find anything, give up
        worker = Thread(target=search)
        worker.start()
        worker.join(timeout=5)

        assert not discovered

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_limited_discovered_device_not_found(self, pts_addr: bytes, **kwargs):
        """
        Please confirm that PTS is NOT discovered.
        """

        discovered = False

        def search():
            nonlocal discovered
            for response in self.scan_responses:
                assert response.HasField("public")
                if (response.public == pts_addr and response.data.le_discoverability_mode == DISCOVERABLE_LIMITED):
                    self.inquiry_responses.cancel()
                    discovered = True
                    return

        # search for five seconds, if we don't find anything, give up
        worker = Thread(target=search)
        worker.start()
        worker.join(timeout=5)

        assert not discovered

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_non_discoverable(self, **kwargs):
        """
        Please prepare IUT into non-discoverable and non-connectable mode and
        send an advertising report.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            own_address_type=PUBLIC,
        )

        return "OK"

    @assert_description
    def TSC_MMI_set_iut_in_bondable_mode(self, **kwargs):
        """
        Please set IUT into bondable mode. Press OK to continue.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_le_disconnect_request(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please send a disconnect request to terminate connection.
        """
        if test == "GAP/CONN/TERM/BV-01-C":
            self.connection = next(self.advertise).connection
        try:
            self.host.Disconnect(connection=self.connection)
        except Exception:
            pass

        return "OK"

    @match_description
    def TSC_MMI_iut_start_bonding_procedure_bondable(self, test: str, pts_addr: bytes, **kwargs):
        """
        (Please start the Bonding Procedure in bondable mode.|Please configure the IUT into LE Security and start pairing process.)
        """

        if not self.pairing_events:
            self.pairing_events = self.security.OnPairing()

        if not self.connection:
            self.connection = next(self.advertise).connection

        if test == "GAP/DM/BON/BV-01-C":
            # we already started in the previous test
            return "OK"

        if test not in {"GAP/SEC/AUT/BV-21-C"}:
            self.security_storage.DeleteBond(public=pts_addr)

        if test in ["GAP/SEC/SEM/BV-53-C"]:
            self.security.Secure(connection=self.connection, classic=LEVEL1)
        else:

            def secure():
                self.security.Secure(connection=self.connection, le=LE_LEVEL3)

            Thread(target=secure).start()

        return "OK"

    @assert_description
    def TSC_MMI_make_iut_connectable(self, **kwargs):
        """
        Please make IUT connectable. Press OK to continue.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_general_discovery_DM(self, pts_addr: bytes, **kwargs):
        """
        Please start general discovery over BR/EDR and over LE. If IUT discovers
        PTS with both BR/EDR and LE method, press OK.
        """

        discovered_bredr = False

        def search_bredr():
            nonlocal discovered_bredr
            inquiry_responses = self.host.Inquiry()
            for response in inquiry_responses:
                if response.address == pts_addr:
                    inquiry_responses.cancel()
                    discovered_bredr = True
                    return

        bredr_worker = Thread(target=search_bredr)
        bredr_worker.start()

        discovered_le = False

        def search_le():
            nonlocal discovered_le
            scan_responses = self.host.Scan()
            for event in scan_responses:
                address = event.public if event.HasField("public") else event.random
                if (address == pts_addr and event.data.le_discoverability_mode):
                    scan_responses.cancel()
                    discovered_le = True
                    return

        le_worker = Thread(target=search_le)
        le_worker.start()

        # search for five seconds, if we don't find anything, give up
        bredr_worker.join(timeout=5)
        le_worker.join(timeout=5)

        assert discovered_bredr and discovered_le, (discovered_bredr, discovered_le)

        return "OK"

    def TSC_MMI_make_iut_general_discoverable(self, test: str, **kwargs):
        """
        Please make IUT general discoverable. Press OK to continue.
        """

        self.host.SetDiscoverabilityMode(mode=DISCOVERABLE_GENERAL)

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=DISCOVERABLE_GENERAL),
            own_address_type=PUBLIC,
            connectable=True,
        )

        if test in [
                "GAP/SEC/SEM/BI-31-C",
        ]:
            self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_basic_rate_name_discovery_DM(self, pts_addr: bytes, **kwargs):
        """
        Please start device name discovery over BR/EDR . If IUT discovers PTS,
        press OK to continue.
        """

        inquiry_responses = self.host.Inquiry()
        for response in inquiry_responses:
            if response.address == pts_addr:
                inquiry_responses.cancel()
                return "OK"

        assert False

    @assert_description
    def TSC_MMI_make_iut_not_connectable(self, **kwargs):
        """
        Please make IUT not connectable. Press OK to continue.
        """

        self.host.SetDiscoverabilityMode(mode=NOT_DISCOVERABLE)
        self.host.SetConnectabilityMode(mode=NOT_CONNECTABLE)

        return "OK"

    @assert_description
    def TSC_MMI_make_iut_not_discoverable(self, **kwargs):
        """
        Please make IUT not discoverable. Press OK to continue.
        """

        self.host.SetDiscoverabilityMode(mode=NOT_DISCOVERABLE)
        self.host.SetConnectabilityMode(mode=NOT_CONNECTABLE)

        return "OK"

    @assert_description
    def TSC_MMI_press_ok_to_disconnect(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please press ok to disconnect the link.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_att_disconnect_request(self, **kwargs):
        """
        Please send an ATT disconnect request to terminate an L2CAP channel.
        """

        try:
            self.host.Disconnect(connection=self.connection)
        except Exception:
            # we already disconnected, no-op
            pass

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_bonding_procedure_non_bondable(self, pts_addr: bytes, **kwargs):
        """
        Please start the Bonding Procedure in non-bondable mode.
        """

        # No idea how we can bond in non-bondable mode, but this passes the tests...
        self.security.Secure(connection=self.connection, le=LE_LEVEL3)

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_le_connection_update_request_timeout(self, **kwargs):
        """
        Please send an L2CAP Connection Parameter Update request using valid
        parameters and wait for TSPX_iut_connection_parameter_timeout 30000ms
        timeout...
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_perform_direct_connection_establishment_procedure(self, **kwargs):
        """
        Please prepare IUT into the Direct Connection Establishment Procedure.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_perform_general_connection_establishment_procedure(self, **kwargs):
        """
        Please prepare IUT into the General Connection Establishment Procedure.
        Press ok to continue.
        """

        return "OK"

    @match_description
    def TSC_MMI_iut_enter_non_connectable_mode(self, **kwargs):
        """
        Please enter (Non-Connectable|non connectable) mode( and genrate advertising report event)?.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=DISCOVERABLE_GENERAL),
            own_address_type=PUBLIC,
            connectable=False,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_non_connectable_mode_general_discoverable_mode(self, **kwargs):
        """
        Please enter General Discoverable and Non-Connectable mode.
        """

        self.host.SetDiscoverabilityMode(mode=DISCOVERABLE_GENERAL)
        self.host.SetConnectabilityMode(mode=NOT_CONNECTABLE)

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_0203_general_discoverable(self, **kwargs):
        """
        Please send non-connectable undirected advertising report or
        discoverable undirected advertising report with general discoverable
        flags turned on.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=DISCOVERABLE_GENERAL),
            own_address_type=PUBLIC,
            connectable=False,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_advertising_report_event_non_discoverable_and_undirected_connectable(self, **kwargs):
        """
        Please prepare IUT into non-discoverable and connectable mode and send
        an advertising report.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=NOT_DISCOVERABLE),
            own_address_type=PUBLIC,
            connectable=True,
        )

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_undirected_connectable_mode_general_discoverable_mode(self, **kwargs):
        """
        Please prepare IUT into general discoverable mode and send an
        advertising report using connectable undirected advertising.
        """

        self.advertise = self.host.Advertise(
            legacy=True,
            data=DataTypes(le_discoverability_mode=DISCOVERABLE_GENERAL),
            own_address_type=PUBLIC,
            connectable=True,
        )

        return "OK"

    @assert_description
    def TSC_MMI_wait_for_encryption_change_event(self, **kwargs):
        """
        Waiting for HCI_ENCRYPTION_CHANGE_EVENT...
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_security_mode_4(self, **kwargs):
        """
        Please order the IUT to go in connectable mode and in security mode 4.
        Press OK to continue.
        """

        self.pairing_events = self.security.OnPairing()

        return "OK"

    @assert_description
    def _mmi_251(self, **kwargs):
        """
        Please send L2CAP Connection Response to PTS.
        """

        return "OK"

    @assert_description
    def _mmi_230(self, **kwargs):
        """
        Please order the IUT to be in security mode 4. Press OK to make
        connection to Lower Tester.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_start_simple_pairing(self, pts_addr: bytes, **kwargs):
        """
        Please start simple pairing procedure.
        """

        # we have always started this already in the connection, so no-op

        return "OK"

    def TSC_MMI_iut_send_l2cap_connect_request(self, pts_addr: bytes, **kwargs):
        """
        Please initiate BR/EDR security authentication and pairing to establish
        a service level enforced security!
        After that, please create the service
        channel using L2CAP Connection Request.

        Press OK to continue.
        """

        def after_that():
            sleep(5)
            self.host.Connect(address=pts_addr)

        Thread(target=after_that).start()

        return "OK"

    @assert_description
    def TSC_MMI_iut_confirm_lost_bond(self, **kwargs):
        """
        Please confirm that IUT has informed of a lost bond.
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_att_connect_request(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please send an ATT connect request to establish an L2CAP channel.
        """
        self.connection = self.host.ConnectLE(own_address_type=RANDOM, public=pts_addr).connection

        return "OK"

    @match_description
    def TSC_MMI_iut_send_ll_connection_update_request(self, **kwargs):
        """
        Please send a LL Connection Parameter Update request using valid
        parameters.
        (With 0x0032 value set in TSPX_conn_update_int_min
         0x0046
        value set in TSPX_conn_update_int_max
         0x0001 value set in
        TSPX_conn_update_peripheral_latency and
         0x01F4 value set in
        TSPX_conn_update_supervision_timeout)?
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_send_le_connection_update_request(self, **kwargs):
        """
        Please start a Connection Update procedure using valid parameters.
        With 0x0032 value set in TSPX_conn_update_int_min
         0x0046 value set in
        TSPX_conn_update_int_max
         0x0001 value set in
        TSPX_conn_update_peripheral_latency and
         0x01F4 value set in
        TSPX_conn_update_supervision_timeout
        """

        return "OK"

    @assert_description
    def TSC_MMI_iut_enter_handle_for_insufficient_authentication_or_insufficient_encryption(self, **kwargs):
        """
        Please enter the handle to the characteristic in the IUT database where
        Insufficient Authentication or Insufficient Encryption error will be
        returned:
        """

        response = self.gatt.RegisterService(service=GattServiceParams(
            uuid="955798ce-3022-455c-b759-ee8edcd73d1a",
            characteristics=[
                GattCharacteristicParams(
                    uuid="cf99ed9b-3c43-4343-b8a7-8afa513752ce",
                    properties=0x02,  # PROPERTY_READ,
                    permissions=0x04,  # PERMISSION_READ_ENCRYPTED_MITM
                ),
            ],
        ))

        self.pairing_events = self.security.OnPairing()

        return handle_format(response.service.characteristics[0].handle)

    @assert_description
    def TSC_MMI_iut_remove_bonding(self, pts_addr: bytes, **kwargs):
        """
        Please have Upper Tester remove the bonding information of the PTS.
        Press OK to continue.
        """

        self.security_storage.DeleteBond(public=pts_addr)

        return "OK"

    @assert_description
    def _mmi_231(self, test: str, pts_addr: bytes, **kwargs):
        """
        Please start the Bonding Procedure in bondable mode.
        After Bonding
        Procedure is completed, please send a disconnect request to terminate
        connection.
        """

        if test != "GAP/SEC/SEM/BV-08-C":
            # we already started in the Connect MMI
            self.pairing_events = self.security.OnPairing()
            self.security.Secure(connection=self.connection, le=LE_LEVEL3)

        def after_that():
            self.host.WaitConnection()  # this really waits for bonding
            sleep(1)
            self.host.Disconnect(connection=self.connection)

        Thread(target=after_that).start()

        return "OK"

    @match_description
    def TSC_MMI_helper_do_not_find_confirm(self, pts_addr: bytes, passkey: str, **kwargs):
        """
        Please confirm the following number matches IUT: (?P<passkey>[0-9]+).
        """

        for event in self.pairing_events:
            if event.address == pts_addr and event.numeric_comparison == int(passkey):
                self.pairing_events.send(PairingEventAnswer(event=event, confirm=True))
                return "OK"

        assert False

    @assert_description
    def _mmi_208(self, **kwargs):
        """
        Please configure the IUT into LE Security Mode 1 Level 4 and start
        pairing process.
        """

        def secure():
            self.pairing_events = self.security.OnPairing()
            self.security.Secure(connection=self.connection, le=LE_LEVEL3)

        Thread(target=secure).start()

        return "OK"

    @assert_description
    def _mmi_252(self, **kwargs):
        """
        Please send L2CAP Connection Response with Security Blocked to PTS.
        """

        # TODO

        return "OK"

    @assert_description
    def _mmi_261(self, **kwargs):
        """
        Please bring IUT to Security Mode 4 level 2. Press OK to continue.
        """

        # TODO

        return "OK"

    @assert_description
    def _mmi_263(self, **kwargs):
        """
        Please bring IUT to Security Mode 4 level 4. Press OK to continue.
        """

        # TODO

        return "OK"

    @assert_description
    def _mmi_264(self, **kwargs):
        """
        Please send L2CAP Connection Request to PTS.
        """

        # TODO

        return "OK"

    @assert_description
    def _mmi_265(self, **kwargs):
        """
        Please initiate a link encryption with the Lower Tester.
        """

        # TODO

        return "OK"

    @assert_description
    def _mmi_273(self, **kwargs):
        """
        Please trigger channel creation. Expect to perform link encryption
        before channel creation.
        """

        self.security.Secure(connection=self.connection, classic=LEVEL2)

        return "OK"

    @assert_description
    def _mmi_20001(self, **kwargs):
        """
        Please prepare IUT into a connectable mode.

        Description: Verify that
        the Implementation Under Test (IUT) can accept GATT connect request from
        PTS.
        """

        return "OK"

    @assert_description
    def _mmi_20115(self, **kwargs):
        """
        Please initiate a GATT disconnection to the PTS.

        Description: Verify
        that the Implementation Under Test (IUT) can initiate GATT disconnect
        request to PTS.
        """

        # TODO

        return "OK"

    def _auto_confirm_requests(self, times=None):

        def task():
            cnt = 0
            pairing_events = self.security.OnPairing()
            for event in pairing_events:
                if event.WhichOneof('method') in {"just_works", "numeric_comparison"}:
                    if times is None or cnt < times:
                        cnt += 1
                        pairing_events.send(PairingEventAnswer(event=event, confirm=True))

        Thread(target=task).start()


def handle_format(handle):
    return hex(handle)[2:].zfill(4)
