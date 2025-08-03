# Copyright 2023 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Client class to access the Floss QA interface."""

import logging

from floss.pandora.floss import observer_base
from floss.pandora.floss import utils


class BluetoothQACallbacks:
    """Callbacks for the QA Interface.

    Implement this to observe these callbacks when exporting callbacks via register_callback.
    """

    def on_fetch_discoverable_mode_completed(self, disc_mode):
        """Called when fetch discoverable mode completed.

        Args:
            disc_mode: BtDiscMode.
        """
        pass

    def on_fetch_connectable_completed(self, connectable):
        """Called when fetch connectable completed.

        Args:
            connectable: A boolean value indicates whether connectable enabled or disabled.
        """
        pass

    def on_set_connectable_completed(self, succeed):
        """Called when set connectable completed.

        Args:
            succeed: A boolean value indicates whether the operation is succeeded.
        """
        pass

    def on_fetch_alias_completed(self, alias):
        """Called when fetch alias completed.

        Args:
            alias: Alias value as string.
        """
        pass

    def on_get_hid_report_completed(self, status):
        """Called when get hid report completed.

        Args:
            status: BtStatus.
        """
        pass

    def on_set_hid_report_completed(self, status):
        """Called when set hid report completed.

        Args:
            status: BtStatus.
        """
        pass

    def on_send_hid_data_completed(self, status):
        """Called when send hid data completed.

        Args:
            status: BtStatus.
        """
        pass


class FlossQAClient(BluetoothQACallbacks):
    """Handles method calls to and callbacks from the QA interface."""

    QA_SERVICE = 'org.chromium.bluetooth'
    QA_INTERFACE = 'org.chromium.bluetooth.BluetoothQA'
    QA_OBJECT_PATTERN = '/org/chromium/bluetooth/hci{}/qa'
    QA_CB_INTF = 'org.chromium.bluetooth.QACallback'
    QA_CB_OBJ_NAME = 'test_qa_client'

    class ExportedQACallbacks(observer_base.ObserverBase):
        """
        <node>
            <interface name="org.chromium.bluetooth.QACallback">
                <method name="OnFetchDiscoverableModeComplete">
                    <arg type="u" name="disc_mode" direction="in" />
                </method>
                <method name="OnFetchConnectableComplete">
                    <arg type="b" name="connectable" direction="in" />
                </method>
                <method name="OnSetConnectableComplete">
                    <arg type="b" name="succeed" direction="in" />
                </method>
                <method name="OnFetchAliasComplete">
                    <arg type="s" name="alias" direction="in" />
                </method>
                <method name="OnGetHIDReportComplete">
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnSetHIDReportComplete">
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnSendHIDDataComplete">
                    <arg type="u" name="status" direction="in" />
                </method>
            </interface>
        </node>
        """

        def __init__(self):
            """Constructs exported callbacks object."""
            observer_base.ObserverBase.__init__(self)

        def OnFetchDiscoverableModeComplete(self, disc_mode):
            """Handles fetch discoverable mode complete callback.

            Args:
                disc_mode: BtDiscMode.
            """
            for observer in self.observers.values():
                observer.on_fetch_discoverable_mode_completed(disc_mode)

        def OnFetchConnectableComplete(self, connectable):
            """Handles fetch connectable complete callback.

            Args:
                connectable: A boolean value indicates whether connectable enabled or disabled.
            """
            for observer in self.observers.values():
                observer.on_fetch_connectable_completed(connectable)

        def OnSetConnectableComplete(self, succeed):
            """Handles set connectable complete callback.

            Args:
                succeed: A boolean value indicates whether the operation is succeeded.
            """
            for observer in self.observers.values():
                observer.on_set_connectable_completed(succeed)

        def OnFetchAliasComplete(self, alias):
            """Handles fetch alias complete callback.

            Args:
                alias: Alias value as string.
            """
            for observer in self.observers.values():
                observer.on_fetch_alias_completed(alias)

        def OnGetHIDReportComplete(self, status):
            """Handles get HID report complete callback.

            Args:
                status: BtStatus.
            """
            for observer in self.observers.values():
                observer.on_get_hid_report_completed(status)

        def OnSetHIDReportComplete(self, status):
            """Handles set HID report complete callback.

            Args:
                status: BtStatus.
            """
            for observer in self.observers.values():
                observer.on_set_hid_report_completed(status)

        def OnSendHIDDataComplete(self, status):
            """Handles send HID data complete callback.

            Args:
                status: BtStatus.
            """
            for observer in self.observers.values():
                observer.on_send_hid_data_completed(status)

    def __init__(self, bus, hci):
        """Constructs the client.

        Args:
            bus: D-Bus bus over which we'll establish connections.
            hci: HCI adapter index. Get this value from `get_default_adapter` on FlossManagerClient.
        """
        self.bus = bus
        self.hci = hci
        self.objpath = self.QA_OBJECT_PATTERN.format(hci)

        # We don't register callbacks by default.
        self.callbacks = None
        self.callback_id = None

    def __del__(self):
        """Destructor."""
        del self.callbacks

    @utils.glib_callback()
    def on_fetch_discoverable_mode_completed(self, disc_mode):
        """Handles fetch discoverable mode completed callback.

        Args:
            disc_mode: BtDiscMode.
        """
        logging.debug('on_fetch_discoverable_mode_completed: disc_mode: %s', disc_mode)

    @utils.glib_callback()
    def on_fetch_connectable_completed(self, connectable):
        """Handles fetch connectable completed callback.

        Args:
            connectable: A boolean value indicates whether connectable enabled or disabled.
        """
        logging.debug('on_fetch_connectable_completed: connectable: %s', connectable)

    @utils.glib_callback()
    def on_set_connectable_completed(self, succeed):
        """Handles set connectable completed callback.

        Args:
             succeed: A boolean value indicates whether the operation is succeeded.
        """
        logging.debug('on_set_connectable_completed: succeed: %s', succeed)

    @utils.glib_callback()
    def on_fetch_alias_completed(self, alias):
        """Handles fetch alias completed callback.

        Args:
            alias: Alias value as string.
        """
        logging.debug('on_fetch_alias_completed: alias: %s', alias)

    @utils.glib_callback()
    def on_get_hid_report_completed(self, status):
        """Handles get HID report completed callback.

        Args:
            status: BtStatus.
        """
        logging.debug('on_get_hid_report_completed: status: %s', status)

    @utils.glib_callback()
    def on_set_hid_report_completed(self, status):
        """Handles set HID report completed callback.

        Args:
            status: BtStatus.
        """
        logging.debug('on_set_hid_report_completed: status: %s', status)

    @utils.glib_callback()
    def on_send_hid_data_completed(self, status):
        """Handles send HID data completed callback.

        Args:
            status: BtStatus.
        """
        logging.debug('on_send_hid_data_completed: status: %s', status)

    @utils.glib_call(False)
    def has_proxy(self):
        """Checks whether QA proxy can be acquired."""
        return bool(self.proxy())

    def proxy(self):
        """Gets proxy object to QA interface for method calls."""
        return self.bus.get(self.QA_SERVICE, self.objpath)[self.QA_INTERFACE]

    @utils.glib_call(False)
    def register_qa_callback(self):
        """Registers QA callbacks if it doesn't exist."""

        if self.callbacks:
            return True

        # Create and publish callbacks
        self.callbacks = self.ExportedQACallbacks()
        self.callbacks.add_observer('QA_client', self)
        objpath = utils.generate_dbus_cb_objpath(self.QA_CB_OBJ_NAME, self.hci)
        self.bus.register_object(objpath, self.callbacks, None)

        # Register published callbacks with QA daemon
        self.callback_id = self.proxy().RegisterQACallback(objpath)
        return True

    @utils.glib_call(False)
    def unregister_qa_callback(self):
        """Unregisters QA callbacks for this client.

        Returns:
            True on success, False otherwise.
        """
        return self.proxy().UnregisterQACallback(self.callback_id)

    def register_callback_observer(self, name, observer):
        """Add an observer for all callbacks.

        Args:
            name: Name of the observer.
            observer: Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothQACallbacks):
            self.callbacks.add_observer(name, observer)

    def unregister_callback_observer(self, name, observer):
        """Remove an observer for all callbacks.

        Args:
            name: Name of the observer.
            observer: Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothQACallbacks):
            self.callbacks.remove_observer(name, observer)

    @utils.glib_call(False)
    def add_media_player(self, name, browsing_supported):
        """Adds media player.

        Args:
            name: Media player name.
            browsing_supported: A boolean value indicates whether browsing_supported or not.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().AddMediaPlayer(name, browsing_supported)
        return True

    @utils.glib_call(False)
    def rfcomm_send_msc(self, dlci, addr):
        """Sends MSC command over RFCOMM to the remote device.

        Args:
            dlci: The Data Link Control Identifier (DLCI) for the RFCOMM channel.
            addr: The Bluetooth address of the remote device.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().RfcommSendMsc(dlci, addr)
        return True

    @utils.glib_call(False)
    def fetch_discoverable_mode(self):
        """Fetches discoverable mode.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().FetchDiscoverableMode()
        return True

    @utils.glib_call(False)
    def fetch_connectable(self):
        """Fetches connectable.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().FetchConnectable()
        return True

    @utils.glib_call(False)
    def set_connectable(self, mode):
        """Sets connectable mode.

        Args:
            mode: A boolean value indicates whether connectable mode enabled or disabled.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().SetConnectable(mode)
        return True

    @utils.glib_call(False)
    def fetch_alias(self):
        """Fetches alias.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().FetchAlias()
        return True

    @utils.glib_call(None)
    def get_modalias(self):
        """Gets modalias.

        Returns:
            Modalias value on success, None otherwise.
        """
        return self.proxy().GetModalias()

    @utils.glib_call(False)
    def get_hid_report(self, addr, report_type, report_id):
        """Gets HID report on the remote device.

        Args:
            addr: The Bluetooth address of the remote device.
            report_type: The type of HID report.
            report_id: The id of HID report.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().FetchHIDReport(addr, report_type, report_id)
        return True

    @utils.glib_call(False)
    def set_hid_report(self, addr, report_type, report):
        """Sets HID report to the remote device.

        Args:
            addr: The Bluetooth address of the remote device.
            report_type: The type of HID report.
            report: The HID report to be set.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().SetHIDReport(addr, report_type, report)
        return True

    @utils.glib_call(False)
    def send_hid_data(self, addr, data):
        """Sends HID report data to the remote device.

        Args:
            addr: The Bluetooth address of the remote device.
            data: The HID report data to be sent.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().SendHIDData(addr, data)
        return True
