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
"""Client class to access the Floss adapter interface."""

import logging
import uuid as uuid_module

from floss.pandora.floss import floss_enums
from floss.pandora.floss import observer_base
from floss.pandora.floss import utils
from gi.repository import GLib


class BluetoothCallbacks:
    """Callbacks for the Adapter Interface.

    Implement this to observe these callbacks when exporting callbacks via
    register_callback.
    """

    def on_address_changed(self, addr):
        """Adapter address changed.

        Args:
            addr: New address of the adapter.
        """
        pass

    def on_device_found(self, remote_device):
        """Device found via discovery.

        Args:
            remote_device: Remove device found during discovery session.
        """
        pass

    def on_discovering_changed(self, discovering):
        """Discovering state has changed.

        Args:
            discovering: Whether discovery enabled or disabled.
        """
        pass

    def on_ssp_request(self, remote_device, class_of_device, variant, passkey):
        """Simple secure pairing request for agent to reply.

        Args:
            remote_device:
                Remote device that is being paired.
            class_of_device:
                Class of device as described in HCI spec.
            variant:
                SSP variant (0-3). [Confirmation, Entry, Consent, Notification]
            passkey:
                Passkey to display (so user can confirm or type it).
        """
        pass

    def on_pin_request(self, remote_device, cod, min_16_digit):
        """When there is a pin request to display the event to client.

        Args:
            remote_device:
                Remote device that is being paired.
            cod:
                Class of device as described in HCI spec.
            min_16_digit:
                True if the pin is 16 digit, False otherwise.
        """
        pass

    def on_pin_display(self, remote_device, pincode):
        """When there is a auto-gen pin to display the event to client.

        Args:
            remote_device:
                Remote device that is being paired.
            pincode:
                PIN code to display.
        """
        pass

    def on_bond_state_changed(self, status, address, state):
        """Bonding/Pairing state has changed for a device.

        Args:
            status:
                Success (0) or failure reason for bonding.
            address:
                This notification is for this BDADDR.
            state:
                Bonding state. 0 = Not bonded, 1 = Bonding, 2 = Bonded.
        """
        pass

    def on_device_properties_changed(self, remote_device, props):
        """Device properties changed for a remote device.

        Args:
            remote_device:
                Remote device that is being searched.
            props:
                Remote device properties.

        """
        pass

class BluetoothConnectionCallbacks:
    """Callbacks for the Device Connection interface.

    Implement this to observe these callbacks when exporting callbacks via
    register_connection_callback
    """

    def on_device_connected(self, remote_device):
        """Notification that a device has completed HCI connection.

        Args:
            remote_device: Remote device that completed HCI connection.
        """
        pass

    def on_device_disconnected(self, remote_device):
        """Notification that a device has completed HCI disconnection.

        Args:
            remote_device: Remote device that completed HCI disconnection.
        """
        pass


class FlossAdapterClient(BluetoothCallbacks, BluetoothConnectionCallbacks):
    """Handles method calls to and callbacks from the Adapter interface."""

    ADAPTER_SERVICE = 'org.chromium.bluetooth'
    ADAPTER_INTERFACE = 'org.chromium.bluetooth.Bluetooth'
    ADAPTER_OBJECT_PATTERN = '/org/chromium/bluetooth/hci{}/adapter'
    ADAPTER_CB_INTF = 'org.chromium.bluetooth.BluetoothCallback'
    ADAPTER_CB_OBJ_NAME = 'test_adapter_client'
    ADAPTER_CONN_CB_INTF = 'org.chromium.bluetooth.BluetoothConnectionCallback'
    ADAPTER_CONN_CB_OBJ_NAME = 'test_connection_client'
    QA_INTERFACE = 'org.chromium.bluetooth.BluetoothQA'
    QA_LEGACY_INTERFACE = 'org.chromium.bluetooth.BluetoothQALegacy'

    DISCONNECTION_TIMEOUT = 5

    @staticmethod
    def parse_dbus_device(remote_device_dbus):
        """Parse a dbus variant dict as a remote device.

        Args:
            remote_device_dbus: Variant dict with signature a{sv}.

        Returns:
            Parsing success, BluetoothDevice tuple
        """
        if 'address' in remote_device_dbus and 'name' in remote_device_dbus:
            return True, (str(remote_device_dbus['address']), str(remote_device_dbus['name']))

        return False, None

    class ExportedAdapterCallbacks(observer_base.ObserverBase):
        """
        <node>
            <interface name="org.chromium.bluetooth.BluetoothCallback">
                <method name="OnAddressChanged">
                    <arg type="s" name="addr" direction="in" />
                </method>
                <method name="OnDeviceFound">
                    <arg type="a{sv}" name="remote_device_dbus" direction="in" />
                </method>
                <method name="OnDiscoveringChanged">
                    <arg type="b" name="discovering" direction="in" />
                </method>
                <method name="OnSspRequest">
                    <arg type="a{sv}" name="remote_device_dbus" direction="in" />
                    <arg type="u" name="class_of_device" direction="in" />
                    <arg type="u" name="variant" direction="in" />
                    <arg type="u" name="passkey" direction="in" />
                </method>
                <method name="OnPinRequest">
                    <arg type="a{sv}" name="remote_device_dbus" direction="in" />
                    <arg type="u" name="cod" direction="in" />
                    <arg type="b" name="min_16_digit" direction="in" />
                </method>
                <method name="OnPinDisplay">
                    <arg type="a{sv}" name="remote_device_dbus" direction="in" />
                    <arg type="s" name="pincode" direction="in" />
                </method>
                <method name="OnBondStateChanged">
                    <arg type="u" name="status" direction="in" />
                    <arg type="s" name="address" direction="in" />
                    <arg type="u" name="state" direction="in" />
                </method>
                <method name="OnDevicePropertiesChanged">
                    <arg type="a{sv}" name="remote_device" direction="in" />
                    <arg type="au" name="props" direction="in" />
                </method>
            </interface>
        </node>
        """

        def __init__(self):
            """Construct exported callbacks object."""
            observer_base.ObserverBase.__init__(self)

        def OnAddressChanged(self, addr):
            """Handle address changed callbacks."""
            for observer in self.observers.values():
                observer.on_address_changed(addr)

        def OnDeviceFound(self, remote_device_dbus):
            """Handle device found from discovery."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(remote_device_dbus)
            if not parsed:
                logging.debug('OnDeviceFound parse error: {}'.format(remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_device_found(remote_device)

        def OnDiscoveringChanged(self, discovering):
            """Handle discovering state changed."""
            for observer in self.observers.values():
                observer.on_discovering_changed(bool(discovering))

        def OnSspRequest(self, remote_device_dbus, class_of_device, variant, passkey):
            """Handle pairing/bonding request to agent."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(remote_device_dbus)
            if not parsed:
                logging.error('OnSspRequest parse error: {}'.format(remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_ssp_request(remote_device, class_of_device, variant, passkey)

        def OnPinRequest(self, remote_device_dbus, cod, min_16_digit):
            """Handle PIN request callback."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(remote_device_dbus)
            if not parsed:
                logging.error('OnPinRequest parse error: {}'.format(remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_pin_request(remote_device, cod, min_16_digit)

        def OnPinDisplay(self, remote_device_dbus, pincode):
            """Handle PIN display callback."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(remote_device_dbus)
            if not parsed:
                logging.error('OnPinDisplay parse error: {}'.format(remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_pin_display(remote_device, pincode)

        def OnBondStateChanged(self, status, address, state):
            """Handle bond state changed callbacks."""
            for observer in self.observers.values():
                observer.on_bond_state_changed(status, address, state)

        def OnDevicePropertiesChanged(self, remote_device, props):
            """Handle device properties changed callbacks."""
            for observer in self.observers.values():
                observer.on_device_properties_changed(remote_device, props)

    class ExportedConnectionCallbacks(observer_base.ObserverBase):
        """
        <node>
            <interface name="org.chromium.bluetooth.BluetoothConnectionCallback">
                <method name="OnDeviceConnected">
                    <arg type="a{sv}" name="remote_device_dbus" direction="in" />
                </method>
                <method name="OnDeviceDisconnected">
                    <arg type="a{sv}" name="remote_device_dbus" direction="in" />
                </method>
            </interface>
        </node>
        """

        def __init__(self, bus, object_path):
            """Construct exported connection callbacks object."""
            observer_base.ObserverBase.__init__(self)

        def OnDeviceConnected(self, remote_device_dbus):
            """Handle device connected."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(remote_device_dbus)
            if not parsed:
                logging.debug('OnDeviceConnected parse error: {}'.format(remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_device_connected(remote_device)

        def OnDeviceDisconnected(self, remote_device_dbus):
            """Handle device disconnected."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(remote_device_dbus)
            if not parsed:
                logging.debug('OnDeviceDisconnected parse error: {}'.format(remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_device_disconnected(remote_device)

    def __init__(self, bus, hci):
        """Construct the client.

        Args:
            bus:
                DBus bus over which we'll establish connections.
            hci:
                HCI adapter index. Get this value from `get_default_adapter`
                on FlossManagerClient.
        """
        self.bus = bus
        self.hci = hci
        self.objpath = self.ADAPTER_OBJECT_PATTERN.format(hci)

        # We don't register callbacks by default.
        self.callbacks = None
        self.connection_callbacks = None

        # Locally cached values
        self.known_devices = {}
        self.discovering = False

        # Initialize properties when registering callbacks (we know proxy is
        # valid at this point).
        self.properties = None
        self.remote_properties = None

    def __del__(self):
        """Destructor."""
        del self.callbacks
        del self.connection_callbacks

    def _make_device(self, address, name, bond_state=None, connected=None):
        """Make a device dict."""
        return {
            'address': address,
            'name': name,
            'bond_state': bond_state,
            'connected': connected,
        }

    @utils.glib_callback()
    def on_device_found(self, remote_device):
        """Remote device was found as part of discovery."""
        address, name = remote_device

        # Update a new device
        if address not in self.known_devices:
            self.known_devices[address] = self._make_device(address, name)
        # Update name if previous cached value didn't have a name
        elif not self.known_devices[address]:
            self.known_devices[address]['name'] = name

    @utils.glib_callback()
    def on_discovering_changed(self, discovering):
        """Discovering state has changed."""
        # Ignore a no-op
        if self.discovering == discovering:
            return

        # Cache the value
        self.discovering = discovering

        # If we are freshly starting discoveyr, clear all locally cached known
        # devices (that are not bonded or connected)
        if discovering:
            # Filter known devices to currently bonded or connected devices
            self.known_devices = {
                key: value
                for key, value in self.known_devices.items()
                if value.get('bond_state', 0) or value.get('connected', False)
            }

    @utils.glib_callback()
    def on_bond_state_changed(self, status, address, state):
        """Bond state has changed."""
        # You can bond unknown devices if it was previously bonded
        if address not in self.known_devices:
            self.known_devices[address] = self._make_device(address, '', bond_state=state)
        else:
            self.known_devices[address]['bond_state'] = state

    @utils.glib_callback()
    def on_device_connected(self, remote_device):
        """Remote device connected hci."""
        address, name = remote_device
        if address not in self.known_devices:
            self.known_devices[address] = self._make_device(address, name, connected=True)
        else:
            self.known_devices[address]['connected'] = True

    @utils.glib_callback()
    def on_device_disconnected(self, remote_device):
        """Remote device disconnected hci."""
        address, name = remote_device
        if address not in self.known_devices:
            self.known_devices[address] = self._make_device(address, name, connected=False)
        else:
            self.known_devices[address]['connected'] = False

    @utils.glib_callback()
    def on_device_properties_changed(self, remote_device, props):
        """Device properties changed for a remote device.

        Args:
            remote_device:
                Remote device that is being searched.
            props:
                Remote device properties.

        """
        pass

    def _make_dbus_device(self, address, name):
        return {'address': GLib.Variant('s', address), 'name': GLib.Variant('s', name)}

    @utils.glib_call(False)
    def has_proxy(self):
        """Checks whether adapter proxy can be acquired."""
        return bool(self.proxy())

    def proxy(self):
        """Gets proxy object to adapter interface for method calls."""
        return self.bus.get(self.ADAPTER_SERVICE, self.objpath)[self.ADAPTER_INTERFACE]

    def qa_proxy(self):
        """Gets proxy object to QA interface for method calls."""
        return self.bus.get(self.ADAPTER_SERVICE, self.objpath)[self.QA_INTERFACE]

    # TODO(b/227405934): Not sure we want GetRemoteRssi on adapter api since
    #                    it's unlikely to be accurate over time. Use a mock for
    #                    testing for now.
    def get_mock_remote_rssi(self, device):
        """Gets mock value for remote device rssi."""
        return -50

    def register_properties(self):
        """Registers a property set for this client."""
        self.properties = utils.PropertySet({
            'Address': (self.proxy().GetAddress, None),
            'Name': (self.proxy().GetName, self.proxy().SetName),
            'Alias': (self._get_alias, None),
            'Modalias': (self._get_modalias, None),
            'Class': (self.proxy().GetBluetoothClass, self.proxy().SetBluetoothClass),
            'Uuids': (self._get_uuids, None),
            'Discoverable': (self.proxy().GetDiscoverable, self.proxy().SetDiscoverable),
            'DiscoverableTimeout': (self.proxy().GetDiscoverableTimeout, None),
            'IsMultiAdvertisementSupported': (self.proxy().IsMultiAdvertisementSupported, None),
            'IsLeExtendedAdvertisingSupported': (self.proxy().IsLeExtendedAdvertisingSupported, None)
        })

        self.remote_properties = utils.PropertySet({
            'Name': (self.proxy().GetRemoteName, None),
            'Type': (self.proxy().GetRemoteType, None),
            'Alias': (self.proxy().GetRemoteAlias, None),
            'Class': (self.proxy().GetRemoteClass, None),
            'WakeAllowed': (self.proxy().GetRemoteWakeAllowed, None),
            'Uuids': (self.proxy().GetRemoteUuids, None),
            'RSSI': (self.get_mock_remote_rssi, None),
        })

    def _get_alias(self):
        """Gets the adapter's alias name.

        It tries BluetoothQA interface first. In case it fails, use
        BluetoothQALegacy interface instead.

        Returns:
            Alias name of the adapter.
        """
        return self.qa_proxy().GetAlias()

    def _get_modalias(self):
        """Gets the adapter modalias name.

        It tries BluetoothQA interface first. In case it fails, use
        BluetoothQALegacy interface instead.

        Returns:
            Modalias name of the adapter.
        """
        return self.qa_proxy().GetModalias()

    def _get_uuids(self):
        """Gets the UUIDs from the D-Bus.

        If D-Bus returns UUID as list of integers, converts the value to UUID
        string.

        Returns:
            List of UUIDs in string representation.
        """

        uuids = self.proxy().GetUuids()

        # Type check: uuids should be subscriptable.
        try:
            first_uuid = uuids[0]
        except TypeError:
            return []

        if isinstance(first_uuid, str):
            return uuids

        uuid_list = []
        for uuid in uuids:
            uuid_hex = ''.join('{:02x}'.format(m) for m in uuid)
            uuid_list.append(str(uuid_module.UUID(uuid_hex)))
        return uuid_list

    @utils.glib_call(False)
    def register_callbacks(self):
        """Registers callbacks for this client.

        This will also initialize properties and populate the list of bonded
        devices since this should be the first thing that gets called after we
        know that the adapter client has a valid proxy object.

        Returns:
            True.
        """
        # Make sure properties are registered
        if not self.properties:
            self.register_properties()

        # Prevent callback registration multiple times
        if self.callbacks and self.connection_callbacks:
            return True

        # Reset known devices
        self.known_devices.clear()

        if not self.callbacks:
            # Create and publish callbacks
            self.callbacks = self.ExportedAdapterCallbacks()
            self.callbacks.add_observer('adapter_client', self)
            objpath = utils.generate_dbus_cb_objpath(self.ADAPTER_CB_OBJ_NAME, self.hci)
            self.bus.register_object(objpath, self.callbacks, None)

            # Register published callback with adapter daemon
            self.proxy().RegisterCallback(objpath)

        if not self.connection_callbacks:
            self.connection_callbacks = self.ExportedConnectionCallbacks(self.bus, objpath)
            self.connection_callbacks.add_observer('adapter_client', self)
            objpath = utils.generate_dbus_cb_objpath(self.ADAPTER_CONN_CB_OBJ_NAME, self.hci)
            self.bus.register_object(objpath, self.connection_callbacks, None)

            self.proxy().RegisterConnectionCallback(objpath)

        # Add bonded devices as known devices and set their initial connection
        # state
        bonded_devices = self.proxy().GetBondedDevices()
        for device in bonded_devices:
            (success, devtuple) = FlossAdapterClient.parse_dbus_device(device)
            if success:
                (address, name) = devtuple
                dev = self.known_devices.get(address,
                                             self._make_device(address, name, bond_state=floss_enums.BondState.BONDED))
                if dev['bond_state'] is None:
                    dev['bond_state'] = floss_enums.BondState.BONDED
                    logging.info('[%s:%s] initially bonded.', address, name)

                if dev['connected'] is None:
                    cstate = self.proxy().GetConnectionState(self._make_dbus_device(address, name))
                    dev['connected'] = bool(cstate > 0)
                    logging.info('[%s:%s] initially connection state: %d.', address, name, cstate)

                self.known_devices[address] = dev

        return True

    def register_callback_observer(self, name, observer):
        """Add an observer for all callbacks.

        Args:
            name: Name of the observer.
            observer: Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothCallbacks):
            self.callbacks.add_observer(name, observer)

        if isinstance(observer, BluetoothConnectionCallbacks):
            self.connection_callbacks.add_observer(name, observer)

    def unregister_callback_observer(self, name, observer):
        """Remove an observer for all callbacks.

        Args:
            name:
                Name of the observer.
            observer:
                Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothCallbacks):
            self.callbacks.remove_observer(name, observer)

        if isinstance(observer, BluetoothConnectionCallbacks):
            self.connection_callbacks.remove_observer(name, observer)

    @utils.glib_call('')
    def get_address(self):
        """Gets the adapter's current address."""
        return str(self.proxy().GetAddress())

    @utils.glib_call('')
    def get_name(self):
        """Gets the adapter's name."""
        return str(self.proxy().GetName())

    @utils.glib_call(None)
    def get_property(self, prop_name):
        """Gets property by name."""
        return self.properties.get(prop_name)

    def get_properties(self):
        """Gets all adapter properties.

        Returns:
            A dict of adapter's property names and properties.
        """
        return {p: self.get_property(p) for p in self.properties.get_property_names()}

    def get_discoverable_timeout(self):
        """Gets the adapter's discoverable timeout."""
        return self.proxy().GetDiscoverableTimeout()

    @utils.glib_call(None)
    def get_remote_property(self, address, prop_name):
        """Gets remote device property by name."""
        name = 'Test device'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return self.remote_properties.get(prop_name, remote_device)

    @utils.glib_call(None)
    def set_property(self, prop_name, *args):
        """Sets property by name."""
        return self.properties.set(prop_name, *args)

    @utils.glib_call(None)
    def set_remote_property(self, address, prop_name, *args):
        """Sets remote property by name."""
        name = 'Test device'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return self.properties.set(prop_name, remote_device, *args)

    @utils.glib_call(None)
    def is_le_extended_advertising_supported(self):
        """Is LE extended advertising supported?

        Returns:
            True on success, False on failure, None on DBus error.
        """
        return bool(self.proxy().IsLeExtendedAdvertisingSupported())

    @utils.glib_call(None)
    def is_multi_advertisement_supported(self):
        """Checks if multiple advertisements are supported.

        Returns:
            True on success, False on failure, None on DBus error.
        """
        return bool(self.proxy().IsMultiAdvertisementSupported())

    @utils.glib_call(False)
    def start_discovery(self):
        """Starts discovery session.

        Returns:
            True on success, False on failure, None on DBus error.
        """
        return bool(self.proxy().StartDiscovery())

    @utils.glib_call(False)
    def stop_discovery(self):
        """Stops discovery session.

        Returns:
            True on success, False on failure, None on DBus error.
        """
        return bool(self.proxy().CancelDiscovery())

    @utils.glib_call(False)
    def is_wbs_supported(self):
        """Is WBS supported?

        Returns:
            True on success, False on failure, None on DBus error.
        """
        return bool(self.proxy().IsWbsSupported())

    @utils.glib_call(False)
    def is_discovering(self):
        """Is adapter discovering?"""
        return bool(self.discovering)

    @utils.glib_call(False)
    def has_device(self, address):
        """Checks to see if device with address is known."""
        return address in self.known_devices

    def is_bonded(self, address):
        """Checks if the given address is currently fully bonded."""
        return address in self.known_devices and self.known_devices[address].get(
            'bond_state', floss_enums.BondState.NOT_BONDED) == floss_enums.BondState.BONDED

    @utils.glib_call(False)
    def create_bond(self, address, transport):
        """Creates bond with target address.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return bool(self.proxy().CreateBond(remote_device, int(transport)))

    @utils.glib_call(False)
    def cancel_bond(self, address):
        """Call cancel bond with no additional checks. Prefer |forget_device|.

        Args:
            address: Device to cancel bond.

        Returns:
            Result of |CancelBondProcess|.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return bool(self.proxy().CancelBond(remote_device))

    @utils.glib_call(False)
    def remove_bond(self, address):
        """Call remove bond with no additional checks. Prefer |forget_device|.

        Args:
            address: Device to remove bond.

        Returns:
            Result of |RemoveBond|.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return bool(self.proxy().RemoveBond(remote_device))

    @utils.glib_call(None)
    def get_bond_state(self, address):
        """Gets remote device bond state.

        Args:
            address: Device to get bond status.

        Returns:
            True on success, False on failure, None on DBus error.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return bool(self.proxy().GetBondState(remote_device))

    @utils.glib_call(None)
    def fetch_remote_uuids(self, address):
        """Gets remote device service uuids.

        Args:
            address: Device to cancel bond.

        Returns:
            True on success, False on failure, None on DBus error.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return self.proxy().FetchRemoteUuids(remote_device)

    @utils.glib_call(None)
    def get_bonded_devices(self):
        """Get all bonded devices.

        Returns:
            List of device addresses; None on DBus error.
        """
        return self.proxy().GetBondedDevices()

    @utils.glib_call(False)
    def forget_device(self, address):
        """Forgets device from local cache and removes bonding.

        If a device is currently bonding or bonded, it will cancel or remove the
        bond to totally remove this device.

        Args:
            address: Device address to forget.

        Returns:
            True if device was known and was removed.
            False if device was unknown or removal failed.
        """
        if address not in self.known_devices:
            return False

        # Remove the device from known devices first
        device = self.known_devices[address]
        del self.known_devices[address]

        remote_device = self._make_dbus_device(device['address'], device['name'])

        # Extra actions if bond state is not NOT_BONDED
        if device['bond_state'] == floss_enums.BondState.BONDING:
            return bool(self.proxy().CancelBondProcess(remote_device))
        elif device['bond_state'] == floss_enums.BondState.BONDED:
            return bool(self.proxy().RemoveBond(remote_device))

        return True

    @utils.glib_call(False)
    def set_pin(self, address, accept, pin_code):
        """Set pin on bonding device.

        Args:
            address: Device address to reply.
            accept: True to accept the pin request, False to reject the pin request.
            pin_code: PIN code to reply. The PIN code is a list of up to 16
                      integers.
        """
        if address not in self.known_devices:
            logging.debug('[%s] Unknown device in set_pin', address)
            return False

        device = self.known_devices[address]
        remote_device = self._make_dbus_device(address, device['name'])

        return self.proxy().SetPin(remote_device, accept, pin_code)

    @utils.glib_call(False)
    def set_pairing_confirmation(self, address, accept):
        """Confirm that a pairing should be completed on a bonding device."""
        # Device should be known or already `Bonding`
        if address not in self.known_devices:
            logging.debug('[%s] Unknown device in set_pairing_confirmation', address)
            return False

        device = self.known_devices[address]
        remote_device = self._make_dbus_device(address, device['name'])

        return bool(self.proxy().SetPairingConfirmation(remote_device, accept))

    def get_connected_devices_count(self):
        """Gets the number of known, connected devices."""
        return sum([1 for x in self.known_devices.values() if x.get('connected', False)])

    def is_connected(self, address):
        """Checks whether a device is connected."""
        return address in self.known_devices and self.known_devices[address].get('connected', False)

    @utils.glib_call(False)
    def connect_all_enabled_profiles(self, address):
        """Connect all enabled profiles for target address."""
        device = self._make_dbus_device(address, self.known_devices.get(address, {}).get('name', 'Test device'))
        return bool(self.proxy().ConnectAllEnabledProfiles(device))

    @utils.glib_call(False)
    def disconnect_all_enabled_profiles(self, address):
        """Disconnect all enabled profiles for target address."""
        device = self._make_dbus_device(address, self.known_devices.get(address, {}).get('name', 'Test device'))
        return bool(self.proxy().DisconnectAllEnabledProfiles(device))

    @utils.glib_call(None)
    def get_connection_state(self, address):
        """Gets connection state."""
        device = self._make_dbus_device(address, self.known_devices.get(address, {}).get('name', 'Test device'))
        return self.proxy().GetConnectionState(device)

    def wait_for_device_disconnected(self, address):
        """Waits for the device become disconnected."""

        def device_disconnected(self):
            return not self.known_devices.get(address, {}).get('connected', True)

        try:
            utils.poll_for_condition(condition=(lambda: device_disconnected(self)), timeout=self.DISCONNECTION_TIMEOUT)
            return True
        except TimeoutError:
            logging.error('on_device_disconnected not called')
            return False

    def disconnect_device(self, address):
        """Disconnect a specific address."""
        return self.disconnect_all_enabled_profiles(address) and self.wait_for_device_disconnected(address)
