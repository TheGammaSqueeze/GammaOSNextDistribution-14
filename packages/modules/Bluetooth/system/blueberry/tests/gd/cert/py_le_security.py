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

import logging

from blueberry.tests.gd.cert.captures import SecurityCaptures
from blueberry.tests.gd.cert.closable import Closable
from blueberry.tests.gd.cert.closable import safeClose
from blueberry.tests.gd.cert.event_stream import EventStream
from blueberry.tests.gd.cert.matchers import SecurityMatchers
from blueberry.tests.gd.cert.truth import assertThat
from datetime import timedelta
from google.protobuf import empty_pb2 as empty_proto
from blueberry.facade.security.facade_pb2 import LeAuthRequirementsMessage
from blueberry.facade.security.facade_pb2 import HelperMsgType


class PyLeSecurity(Closable):
    """
        Abstraction for security tasks and GRPC calls
    """

    _ui_event_stream = None
    _bond_event_stream = None
    _helper_event_stream = None

    def __init__(self, device):
        logging.info("DUT: Init")
        self._device = device
        self._device.wait_channel_ready()
        self._ui_event_stream = EventStream(self._device.security.FetchUiEvents(empty_proto.Empty()))
        self._bond_event_stream = EventStream(self._device.security.FetchBondEvents(empty_proto.Empty()))
        self._helper_event_stream = EventStream(self._device.security.FetchHelperEvents(empty_proto.Empty()))
        self._advertising_callback_event_stream = EventStream(
            self._device.security.FetchAdvertisingCallbackEvents(empty_proto.Empty()))

    def get_ui_stream(self):
        return self._ui_event_stream

    def get_bond_stream(self):
        return self._bond_event_stream

    def get_advertising_callback_event_stream(self):
        return self._advertising_callback_event_stream

    def wait_for_ui_event_passkey(self, timeout=timedelta(seconds=3)):
        display_passkey_capture = SecurityCaptures.DisplayPasskey()
        assertThat(self._ui_event_stream).emits(display_passkey_capture, timeout=timeout)
        return display_passkey_capture.get()

    def wait_device_disconnect(self, address):
        assertThat(self._helper_event_stream).emits(
            SecurityMatchers.HelperMsg(HelperMsgType.DEVICE_DISCONNECTED, address))

    def SetLeAuthRequirements(self, *args, **kwargs):
        return self._device.security.SetLeAuthRequirements(LeAuthRequirementsMessage(*args, **kwargs))

    def close(self):
        if self._ui_event_stream is not None:
            safeClose(self._ui_event_stream)
        else:
            logging.info("DUT: UI Event Stream is None!")

        if self._bond_event_stream is not None:
            safeClose(self._bond_event_stream)
        else:
            logging.info("DUT: Bond Event Stream is None!")

        if self._helper_event_stream is not None:
            safeClose(self._helper_event_stream)
        else:
            logging.info("DUT: Helper Event Stream is None!")

        if self._advertising_callback_event_stream is not None:
            safeClose(self._advertising_callback_event_stream)
        else:
            logging.info("DUT: Advertising Callback Event Stream is None!")
