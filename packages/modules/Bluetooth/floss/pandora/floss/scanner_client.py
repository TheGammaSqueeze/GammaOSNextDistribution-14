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
"""Client class to access the Floss scanner interface."""
import copy
import logging
import uuid as uuid_module

from floss.pandora.floss import floss_enums
from floss.pandora.floss import observer_base
from floss.pandora.floss import utils
from gi.repository import GLib


class BluetoothScannerCallbacks:
    """Callbacks for the scanner interface.

    Implement this to observe these callbacks when exporting callbacks via
    register_callback.
    """

    def on_scanner_registered(self, uuid, scanner_id, status):
        """Called when scanner registered.

        Args:
            uuid: The specific uuid to register it.
            scanner_id: Scanner id of scanning set.
            status: floss_enums.GattStatus.
        """
        pass

    def on_scan_result(self, scan_result):
        """Called when execute start_scan().

        Args:
            scan_result: The struct of ScanResult.
        """
        pass

    def on_advertisement_found(self, scanner_id, scan_result):
        """Called when advertisement found.

        Args:
            scanner_id: The scanner ID for scanner.
            scan_result: The struct of ScanResult.
        """
        pass

    def on_advertisement_lost(self, scanner_id, scan_result):
        """Called when advertisement lost.

        Args:
            scanner_id: The scanner ID for scanner.
            scan_result: The struct of ScanResult.
        """
        pass

    def on_suspend_mode_change(self, suspend_mode):
        """Called when suspend mode change.

        Args:
            suspend_mode: The suspend mode of Bluetooth.
        """
        pass


class ScannerObj:
    """The scanner object for Advertisement Monitor Tests.

    This class creates instances of multiple scanners.
    """

    def __init__(self, scanner_id, uuid, status):
        """Construction of a scanner object.

        Args:
            scanner_id: Scanner ID of scanning set.
            uuid: The specific UUID for scanner.
            status: GATT status.
        """
        self.scanner_id = scanner_id
        self.uuid = uuid
        self.status = status
        self.events = {
            'DeviceFound': 0,
            'DeviceLost': 0,
        }
        self.target_devices = []

    def get_event_count(self, event):
        """Reads the event count.

        Args:
            event: Name of the specific event or 'All' for all events.
        Returns:
            Count of a specific event or dict of counts of all events.
        """
        if event == 'All':
            return self.events

        return self.events.get(event)

    def add_event_count(self, event):
        """Increase the event count by one.

        Args:
            event: Name of the event as a string.
        """
        self.events[event] += 1

    def reset_event_count(self, event):
        """Resets the event count.

        Args:
            event: Name of a specific event or 'All' for all events.
            True on success, False otherwise.

        Returns:
            True if success, False otherwise.
        """
        if event == 'All':
            for event_key in self.events:
                self.events[event_key] = 0
            return True

        if event in self.events:
            self.events[event] = 0
            return True

        return False

    def set_target_devices(self, devices):
        """Sets the target devices to the given scanner.

        DeviceFound and DeviceLost will only be counted if it is triggered by a
        target device.

        Args:
            devices: A list of devices in dbus object path.
        """
        self.target_devices = copy.deepcopy(devices)


class FlossScannerClient(BluetoothScannerCallbacks):
    """Handles method calls to and callbacks from the scanner interface."""

    SCANNER_SERVICE = 'org.chromium.bluetooth'
    SCANNER_INTERFACE = 'org.chromium.bluetooth.BluetoothGatt'
    SCANNER_OBJECT_PATTERN = '/org/chromium/bluetooth/hci{}/gatt'

    SCANNER_CB_INTF = 'org.chromium.bluetooth.ScannerCallback'
    SCANNER_CB_OBJ_NAME = 'test_scanner_client'
    FLOSS_RESPONSE_LATENCY_SECS = 3

    class ExportedScannerCallbacks(observer_base.ObserverBase):
        """
        <node>
            <interface name="org.chromium.bluetooth.ScannerCallback">
                <method name="OnScannerRegistered">
                    <arg type="ay" name="uuid" direction="in" />
                    <arg type="y" name="scanner_id" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnScanResult">
                    <arg type="a{sv}" name="scan_result" direction="in" />
                </method>
                <method name="OnAdvertisementFound">
                    <arg type="y" name="scanner_id" direction="in" />
                    <arg type="a{sv}" name="scan_result" direction="in" />
                </method>
                <method name="OnAdvertisementLost">
                    <arg type="y" name="scanner_id" direction="in" />
                    <arg type="a{sv}" name="scan_result" direction="in" />
                </method>
                <method name="OnSuspendModeChange">
                    <arg type="u" name="suspend_mode" direction="in" />
                </method>
            </interface>
        </node>
        """

        def __init__(self):
            """Constructs exported callbacks object."""
            observer_base.ObserverBase.__init__(self)

        def OnScannerRegistered(self, uuid, scanner_id, status):
            """Handles scanner registered callback.

            Args:
                uuid: The specific uuid to register it.
                scanner_id: Scanner id of scanning set.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_scanner_registered(uuid, scanner_id, status)

        def OnScanResult(self, scan_result):
            """Handles scan result callback.

            Args:
                scan_result: The struct of ScanResult.
            """
            for observer in self.observers.values():
                observer.on_scan_result(scan_result)

        def OnAdvertisementFound(self, scanner_id, scan_result):
            """Handles advertisement found callback.

            Args:
                scanner_id: The scanner ID for scanner.
                scan_result: The struct of ScanResult.
            """
            for observer in self.observers.values():
                observer.on_advertisement_found(scanner_id, scan_result)

        def OnAdvertisementLost(self, scanner_id, scan_result):
            """Handles advertisement lost callback.

            Args:
                scanner_id: The scanner ID for scanner.
                scan_result: The struct of ScanResult.
            """
            for observer in self.observers.values():
                observer.on_advertisement_lost(scanner_id, scan_result)

        def OnSuspendModeChange(self, suspend_mode):
            """Handles suspend mode change callback.

            Args:
                suspend_mode: The suspend mode of Bluetooth.
            """
            for observer in self.observers.values():
                observer.on_suspend_mode_change(suspend_mode)

    def __init__(self, bus, hci):
        """Constructs the client.

        Args:
            bus: D-Bus bus over which we'll establish connections.
            hci: HCI adapter index. Get this value from `get_default_adapter`
                    on FlossManagerClient.
        """
        self.bus = bus
        self.hci = hci
        self.objpath = self.SCANNER_OBJECT_PATTERN.format(hci)

        # We don't register callbacks by default.
        self.callbacks = None
        self.callback_id = None
        self.register_scanner_results = {}
        self.scanners = {}

    def __del__(self):
        """Destructor."""
        del self.callbacks

    @utils.glib_callback()
    def on_scanner_registered(self, uuid, scanner_id, status):
        """Handles scanner registered callback.

        Args:
            uuid: The specific uuid to register it.
            scanner_id: Scanner id of scanning set.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_scanner_registered: uuid: %s, scanner_id: %s status: %s', uuid, scanner_id, status)

        # The uuid is returned as a list of bytes (128-bit UUID) so
        # we need convert it to uuid object in order to store it in the
        # dictionary as a key.
        uuid_object = uuid_module.UUID(bytes=bytes(uuid))
        self.register_scanner_results[uuid_object] = (scanner_id, status)

        if floss_enums.GattStatus(status) != floss_enums.GattStatus.SUCCESS:
            return

        # Creates a scanner object every time a new scanner registered.
        scanner = ScannerObj(scanner_id, uuid_object, status)
        self.scanners[scanner_id] = scanner

    @utils.glib_callback()
    def on_scan_result(self, scan_result):
        """Handles scan result callback.

        Args:
            scan_result: The struct of ScanResult.
        """
        logging.debug('on_scan_result: scan_result: %s', scan_result)

    @utils.glib_callback()
    def on_advertisement_found(self, scanner_id, scan_result):
        """Handles advertisement found callback.

        Args:
            scanner_id: The scanner ID for scanner.
            scan_result: The struct of ScanResult.
        """
        logging.debug('on_advertisement_found: scanner_id: %s, scan_result: %s', scanner_id, scan_result)

        # Update DeviceFound if the received address device exists in the
        # target_device list.
        if scan_result['address'] in self.scanners[scanner_id].target_devices:
            self.scanners[scanner_id].add_event_count('DeviceFound')

    @utils.glib_callback()
    def on_advertisement_lost(self, scanner_id, scan_result):
        """Handles advertisement lost callback.

        Args:
            scanner_id: The scanner ID for scanner.
            scan_result: The struct of ScanResult.
        """
        logging.debug('on_advertisement_lost: scanner_id: %s, scan_result: %s', scanner_id, scan_result)

        # Update DeviceLost if the received address device exists in the
        # target_device list.
        if scan_result['address'] in self.scanners[scanner_id].target_devices:
            self.scanners[scanner_id].add_event_count('DeviceLost')

    @utils.glib_callback()
    def on_suspend_mode_change(self, suspend_mode):
        """Handles suspend mode change callback.

        Args:
            suspend_mode: The suspend mode of Bluetooth.
        """
        logging.debug('on_suspend_mode_change: suspend_mode: %s', suspend_mode)

    def make_dbus_scan_filter_pattern(self, start_position, ad_type, content):
        """Makes struct for scan filter pattern D-Bus.

        Args:
            start_position: The start position of pattern.
            ad_type: The type of pattern.
            content: The content of pattern.

        Returns:
            Dictionary of scan filter pattern.
        """
        return {
            'start_position': GLib.Variant('y', start_position),
            'ad_type': GLib.Variant('y', ad_type),
            'content': GLib.Variant('ay', content)
        }

    def make_dbus_scan_filter_condition(self, patterns):
        """Makes struct for scan filter condition D-Bus.

        Args:
            patterns: The list of patterns used for conditions.

        Returns:
            Dictionary of scan filter condition.
        """
        return {'patterns': GLib.Variant('aa{sv}', patterns)}

    def make_dbus_scan_filter(self, rssi_high_threshold, rssi_low_threshold, rssi_low_timeout, rssi_sampling_period,
                              condition):
        """Makes struct for scan filter D-Bus.

        Args:
            rssi_high_threshold: RSSI high threshold value.
            rssi_low_threshold: RSSI low threshold value.
            rssi_low_timeout: RSSI low timeout value.
            rssi_sampling_period: The sampling interval in milliseconds.
            condition: Struct of ScanFilterCondition.

        Returns:
            Dictionary of scan filter.
        """
        patterns = []
        for c in condition:
            patterns.append(self.make_dbus_scan_filter_pattern(c['start_position'], c['ad_type'], c['content']))
        return {
            'rssi_high_threshold': GLib.Variant('y', rssi_high_threshold),
            'rssi_low_threshold': GLib.Variant('y', rssi_low_threshold),
            'rssi_low_timeout': GLib.Variant('y', rssi_low_timeout),
            'rssi_sampling_period': GLib.Variant('y', rssi_sampling_period),
            'condition': GLib.Variant('a{sv}', self.make_dbus_scan_filter_condition(patterns))
        }

    def make_dbus_scan_settings(self, interval, window, scan_type):
        """Makes struct for scan settings D-Bus.

        Args:
            interval: The interval value to setting scan.
            window: The window value to setting scan.
            scan_type: The type of scan.

        Returns:
            Dictionary of scan settings.
        """
        return {
            'interval': GLib.Variant('i', interval),
            'window': GLib.Variant('i', window),
            'scan_type': GLib.Variant('u', scan_type)
        }

    @utils.glib_call(False)
    def has_proxy(self):
        """Checks whether scanner proxy can be acquired."""
        return bool(self.proxy())

    def proxy(self):
        """Gets proxy object to scanner interface for method calls."""
        return self.bus.get(self.SCANNER_SERVICE, self.objpath)[self.SCANNER_INTERFACE]

    @utils.glib_call(False)
    def register_scanner_callback(self):
        """Registers scanner callbacks if it doesn't exist."""

        if self.callbacks:
            return True

        # Create and publish callbacks
        self.callbacks = self.ExportedScannerCallbacks()
        self.callbacks.add_observer('scanner_client', self)
        objpath = utils.generate_dbus_cb_objpath(self.SCANNER_CB_OBJ_NAME, self.hci)
        self.bus.register_object(objpath, self.callbacks, None)

        # Register published callbacks with scanner daemon
        self.callback_id = self.proxy().RegisterScannerCallback(objpath)
        return True

    def register_callback_observer(self, name, observer):
        """Add an observer for all callbacks.

        Args:
            name:
                Name of the observer.
            observer:
                Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothScannerCallbacks):
            self.callbacks.add_observer(name, observer)

    def unregister_callback_observer(self, name, observer):
        """Remove an observer for all callbacks.

        Args:
            name:
                Name of the observer.
            observer:
                Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothScannerCallbacks):
            self.callbacks.remove_observer(name, observer)

    def wait_for_on_scanner_registered(self, uuid):
        """Waits for register scanner.

        Args:
            uuid: The specific uuid for scanner.

        Returns:
            scanner_id, status for specific uuid on success,
                 (None, None) otherwise.
        """
        try:
            utils.poll_for_condition(condition=(lambda: uuid in self.register_scanner_results),
                                     timeout=self.FLOSS_RESPONSE_LATENCY_SECS)
        except TimeoutError:
            logging.error('on_scanner_registered not called')
            return None, None
        scanner_id, status = self.register_scanner_results[uuid]

        # Consume the result here because we have no straightforward timing
        # to drop the info. We can't drop it in unregister_scanner because
        # if the advertising failed to start then it makes no sense for the
        # user to call unregister_scanner.
        del self.register_scanner_results[uuid]
        return scanner_id, status

    @utils.glib_call(False)
    def unregister_scanner_callback(self):
        """Unregisters scanner callback for this client.

        Returns:
            True on success, False otherwise.
        """
        return self.proxy().UnregisterScannerCallback(self.callback_id)

    @utils.glib_call(None)
    def register_scanner(self):
        """Registers scanner for the callback id.

        Returns:
            UUID of the registered scanner on success, None otherwise.
        """
        return uuid_module.UUID(bytes=bytes(self.proxy().RegisterScanner(self.callback_id)))

    def register_scanner_sync(self):
        """Registers scanner for the callback id.

        Returns:
             scanner_id of the registered scanner on success, None otherwise.
        """
        uuid = self.register_scanner()

        # Failed if we have issue in D-bus (None).
        if uuid is None:
            logging.error('Failed to register the scanner')
            return None

        scanner_id, status = self.wait_for_on_scanner_registered(uuid)
        if status is None:
            return None

        if floss_enums.GattStatus(status) != floss_enums.GattStatus.SUCCESS:
            logging.error('Failed to register the scanner with id: %s, status = %s', scanner_id, status)
            return None
        return scanner_id

    @utils.glib_call(False)
    def unregister_scanner(self, scanner_id):
        """Unregisters scanner set using scanner id of set.

        Args:
            scanner_id: Scanner id of set scanning.

        Returns:
            True on success, False otherwise.
        """
        del self.scanners[scanner_id]
        return self.proxy().UnregisterScanner(scanner_id)

    @utils.glib_call(False)
    def start_scan(self, scanner_id, settings, scan_filter):
        """Starts scan.

        Args:
            scanner_id: Scanner id of set scanning.
            settings: ScanSettings structure.
            scan_filter: ScanFilter structure.

        Returns:
            True on success, False otherwise.
        """
        status = self.proxy().StartScan(scanner_id, settings, scan_filter)

        if floss_enums.BtStatus(status) != floss_enums.BtStatus.SUCCESS:
            logging.error('Failed to start the scanner with id: %s, status = %s', scanner_id, status)
            return False
        return True

    @utils.glib_call(None)
    def stop_scan(self, scanner_id):
        """Stops scan set using scanner_id.

        Args:
            scanner_id: Scanner id of set scanning.

        Returns:
            floss_enums.BtStatus as int on success, None otherwise.
        """
        return self.proxy().StopScan(scanner_id)

    @utils.glib_call(None)
    def get_scan_suspend_mode(self):
        """Gets scan suspend mode.

        Returns:
            SuspendMode as int on success, None otherwise.
        """
        return self.proxy().GetScanSuspendMode()

    @utils.glib_call(None)
    def is_msft_supported(self):
        """Checks if MSFT supported.

        Returns:
            MSFT capability as boolean on success, None otherwise.
        """
        return self.proxy().IsMsftSupported()

    def get_event_count(self, scanner_id, event):
        """Reads the count of a particular event on the given scanner.

        Args:
            scanner_id: The scanner ID.
            event: Name of the specific event or 'All' for all events.

        Returns:
            Count of the specific event or dict of counts of all events.
        """
        if scanner_id not in self.scanners:
            return None

        return self.scanners[scanner_id].get_event_count(event)

    def reset_event_count(self, scanner_id, event):
        """Resets the count of a particular event on the given scanner.

        Args:
            scanner_id: The scanner ID.
            event: Name of the specific event or 'All' for all events.

        Returns:
            True on success, False otherwise.
        """
        if scanner_id not in self.scanners:
            return False

        return self.scanners[scanner_id].reset_event_count(event)

    def set_target_devices(self, scanner_id, devices):
        """Sets target devices to the given scanner.

        DeviceFound and DeviceLost will only be counted if it is triggered
        by a target device.

        Args:
            scanner_id: The scanner ID.
            devices: A list of devices in dbus object path.

        Returns:
            True on success, False otherwise.
        """
        if scanner_id not in self.scanners:
            return False

        self.scanners[scanner_id].set_target_devices(devices)
        return True

    def remove_monitor(self, scanner_id):
        """Removes the Advertisement Monitor object.

        Args:
            scanner_id: The scanner ID.

        Returns:
            True on success, False otherwise.
        """
        stop_scan = self.stop_scan(scanner_id)
        unregister_scanner = self.unregister_scanner(scanner_id)

        if stop_scan == floss_enums.BtStatus.SUCCESS:
            stop_scan = True
        else:
            return False
        return stop_scan and unregister_scanner
