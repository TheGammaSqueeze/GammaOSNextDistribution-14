# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Client class to access the Floss adapter interface."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

from enum import IntEnum
from gi.repository import GLib
import logging
import math
import random

from autotest_lib.client.cros.bluetooth.floss.observer_base import ObserverBase
from autotest_lib.client.cros.bluetooth.floss.utils import (glib_call,
                                                            glib_callback,
                                                            PropertySet)


class BondState(IntEnum):
    """Bluetooth bonding state."""
    NOT_BONDED = 0
    BONDING = 1
    BONDED = 2


class Transport(IntEnum):
    """Bluetooth transport type."""
    AUTO = 0
    BREDR = 1
    LE = 2


class SspVariant(IntEnum):
    """Bluetooth SSP variant type."""
    PASSKEY_CONFIRMATION = 0
    PASSKEY_ENTRY = 1
    CONSENT = 2
    PASSKEY_NOTIFICATION = 3


class BluetoothCallbacks:
    """Callbacks for the Adapter Interface.

    Implement this to observe these callbacks when exporting callbacks via
    register_callback.
    """
    def on_address_changed(self, addr):
        """Adapter address changed.

        @param addr: New address of the adapter.
        """
        pass

    def on_device_found(self, remote_device):
        """Device found via discovery.

        @param remote_device: Remove device found during discovery session.
        """
        pass

    def on_discovering_changed(self, discovering):
        """Discovering state has changed.

        @param discovering: Whether discovery enabled or disabled.
        """
        pass

    def on_ssp_request(self, remote_device, class_of_device, variant, passkey):
        """Simple secure pairing request for agent to reply.

        @param remote_device: Remote device that is being paired.
        @param class_of_device: Class of device as described in HCI spec.
        @param variant: SSP variant (0-3). [Confirmation, Entry, Consent, Notification]
        @param passkey: Passkey to display (so user can confirm or type it).
        """
        pass

    def on_bond_state_changed(self, status, device_address, state):
        """Bonding/Pairing state has changed for a device.

        @param status: Success (0) or failure reason for bonding.
        @param device_address: This notification is for this BDADDR.
        @param state: Bonding state. 0 = Not bonded, 1 = Bonding, 2 = Bonded.
        """
        pass


class BluetoothConnectionCallbacks:
    """Callbacks for the Device Connection interface.

    Implement this to observe these callbacks when exporting callbacks via
    register_connection_callback
    """
    def on_device_connected(self, remote_device):
        """Notification that a device has completed HCI connection.

        @param remote_device: Remote device that completed HCI connection.
        """
        pass

    def on_device_disconnected(self, remote_device):
        """Notification that a device has completed HCI disconnection.

        @param remote_device: Remote device that completed HCI disconnection.
        """
        pass


class FlossAdapterClient(BluetoothCallbacks, BluetoothConnectionCallbacks):
    """Handles method calls to and callbacks from the Adapter interface."""

    ADAPTER_SERVICE = 'org.chromium.bluetooth'
    ADAPTER_INTERFACE = 'org.chromium.bluetooth.Bluetooth'
    ADAPTER_OBJECT_PATTERN = '/org/chromium/bluetooth/hci{}/adapter'
    ADAPTER_CB_INTF = 'org.chromium.bluetooth.BluetoothCallback'
    ADAPTER_CB_OBJ_PATTERN = '/org/chromium/bluetooth/hci{}/test_adapter_client{}'
    ADAPTER_CONN_CB_INTF = 'org.chromium.bluetooth.BluetoothConnectionCallback'
    ADAPTER_CONN_CB_OBJ_PATTERN = '/org/chromium/bluetooth/hci{}/test_connection_client{}'

    @staticmethod
    def parse_dbus_device(remote_device_dbus):
        """Parse a dbus variant dict as a remote device.

        @param remote_device_dbus: Variant dict with signature a{sv}.

        @return Parsing success, BluetoothDevice tuple
        """
        if 'address' in remote_device_dbus and 'name' in remote_device_dbus:
            return True, (str(remote_device_dbus['address']),
                          str(remote_device_dbus['name']))

        return False, None

    class ExportedAdapterCallbacks(ObserverBase):
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
                <method name="OnBondStateChanged">
                    <arg type="u" name="status" direction="in" />
                    <arg type="s" name="address" direction="in" />
                    <arg type="u" name="state" direction="in" />
                </method>
            </interface>
        </node>
        """

        def __init__(self):
            """Construct exported callbacks object.
            """
            ObserverBase.__init__(self)

        def OnAddressChanged(self, addr):
            """Handle address changed callbacks."""
            for observer in self.observers.values():
                observer.on_address_changed(addr)

        def OnDeviceFound(self, remote_device_dbus):
            """Handle device found from discovery."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(
                    remote_device_dbus)
            if not parsed:
                logging.debug('OnDeviceFound parse error: {}'.format(
                        remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_device_found(remote_device)

        def OnDiscoveringChanged(self, discovering):
            """Handle discovering state changed."""
            for observer in self.observers.values():
                observer.on_discovering_changed(bool(discovering))

        def OnSspRequest(self, remote_device_dbus, class_of_device, variant,
                         passkey):
            """Handle pairing/bonding request to agent."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(
                    remote_device_dbus)
            if not parsed:
                logging.debug('OnSspRequest parse error: {}'.format(
                        remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_ssp_request(remote_device, class_of_device,
                                        variant, passkey)

        def OnBondStateChanged(self, status, address, state):
            """Handle bond state changed callbacks."""
            for observer in self.observers.values():
                observer.on_bond_state_changed(status, address, state)

    class ExportedConnectionCallbacks(ObserverBase):
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
            """Construct exported connection callbacks object.
            """
            ObserverBase.__init__(self)

        def OnDeviceConnected(self, remote_device_dbus):
            """Handle device connected."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(
                    remote_device_dbus)
            if not parsed:
                logging.debug('OnDeviceConnected parse error: {}'.format(
                        remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_device_connected(remote_device)

        def OnDeviceDisconnected(self, remote_device_dbus):
            """Handle device disconnected."""
            parsed, remote_device = FlossAdapterClient.parse_dbus_device(
                    remote_device_dbus)
            if not parsed:
                logging.debug('OnDeviceDisconnected parse error: {}'.format(
                        remote_device_dbus))
                return

            for observer in self.observers.values():
                observer.on_device_disconnected(remote_device)

    def __init__(self, bus, hci):
        """Construct the client.

        @param bus: DBus bus over which we'll establish connections.
        @param hci: HCI adapter index. Get this value from `get_default_adapter`
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
        """Destructor"""
        del self.callbacks
        del self.connection_callbacks

    def _make_device(self,
                     address,
                     name,
                     bond_state=BondState.NOT_BONDED,
                     connected=False):
        """Make a device dict."""
        return {
                'address': address,
                'name': name,
                'bond_state': bond_state,
                'connected': connected,
        }

    @glib_callback()
    def on_device_found(self, remote_device):
        """Remote device was found as part of discovery."""
        address, name = remote_device

        # Update a new device
        if not address in self.known_devices:
            self.known_devices[address] = self._make_device(address, name)
        # Update name if previous cached value didn't have a name
        elif not self.known_devices[address]:
            self.known_devices[address]['name'] = name

    @glib_callback()
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
                    if value.get('bond_state', 0) > 0
                    or value.get('connected', False)
            }

    @glib_callback()
    def on_bond_state_changed(self, status, address, state):
        """Bond state has changed."""
        # You can bond unknown devices if it was previously bonded
        if not address in self.known_devices:
            self.known_devices[address] = self._make_device(
                    address,
                    '',
                    bond_state=state,
            )
        else:
            self.known_devices[address]['bond_state'] = state

    @glib_callback()
    def on_device_connected(self, remote_device):
        """Remote device connected hci."""
        address, name = remote_device
        if not address in self.known_devices:
            self.known_devices[address] = self._make_device(address,
                                                            name,
                                                            connected=True)
        else:
            self.known_devices[address]['connected'] = True

    @glib_callback()
    def on_device_disconnected(self, remote_device):
        """Remote device disconnected hci."""
        address, name = remote_device
        if not address in self.known_devices:
            self.known_devices[address] = self._make_device(address,
                                                            name,
                                                            connected=False)
        else:
            self.known_devices[address]['connected'] = False

    def _make_dbus_device(self, address, name):
        return {
                'address': GLib.Variant('s', address),
                'name': GLib.Variant('s', name)
        }

    @glib_call(False)
    def has_proxy(self):
        """Checks whether adapter proxy can be acquired."""
        return bool(self.proxy())

    def proxy(self):
        """Gets proxy object to adapter interface for method calls."""
        return self.bus.get(self.ADAPTER_SERVICE,
                            self.objpath)[self.ADAPTER_INTERFACE]

    # TODO(b/227405934): Not sure we want GetRemoteRssi on adapter api since
    #                    it's unlikely to be accurate over time. Use a mock for
    #                    testing for now.
    def get_mock_remote_rssi(self, device):
        """Gets mock value for remote device rssi."""
        return -50

    def register_properties(self):
        """Registers a property set for this client."""
        self.properties = PropertySet({
                'Address': (self.proxy().GetAddress, None),
                'Name': (self.proxy().GetName, self.proxy().SetName),
                'Class': (self.proxy().GetBluetoothClass,
                          self.proxy().SetBluetoothClass),
                'Uuids': (self.proxy().GetUuids, None),
                'Discoverable':
                (self.proxy().GetDiscoverable, self.proxy().SetDiscoverable),
        })

        self.remote_properties = PropertySet({
                'Name': (self.proxy().GetRemoteName, None),
                'Type': (self.proxy().GetRemoteType, None),
                'Alias': (self.proxy().GetRemoteAlias, None),
                'Class': (self.proxy().GetRemoteClass, None),
                'RSSI': (self.get_mock_remote_rssi, None),
        })

    @glib_call(False)
    def register_callbacks(self):
        """Registers callbacks for this client.

        This will also initialize properties and populate the list of bonded
        devices since this should be the first thing that gets called after we
        know that the adapter client has a valid proxy object.
        """
        # Make sure properties are registered
        if not self.properties:
            self.register_properties()

        # Prevent callback registration multiple times
        if self.callbacks and self.connection_callbacks:
            return True

        # Generate a random number between 1-1000
        rnumber = math.floor(random.random() * 1000 + 1)

        # Reset known devices to just bonded devices and their connection
        # states.
        self.known_devices.clear()
        bonded_devices = self.proxy().GetBondedDevices()
        for device in bonded_devices:
            (success, devtuple) = FlossAdapterClient.parse_dbus_device(device)
            if success:
                (address, name) = devtuple
                cstate = self.proxy().GetConnectionState(
                        self._make_dbus_device(address, name))
                logging.info('[%s:%s] initially bonded. Connected = %d',
                             address, name, cstate)
                self.known_devices[address] = self._make_device(
                        address,
                        name,
                        bond_state=BondState.BONDED,
                        connected=bool(cstate > 0))

        if not self.callbacks:
            # Create and publish callbacks
            self.callbacks = self.ExportedAdapterCallbacks()
            self.callbacks.add_observer('adapter_client', self)
            objpath = self.ADAPTER_CB_OBJ_PATTERN.format(self.hci, rnumber)
            self.bus.register_object(objpath, self.callbacks, None)

            # Register published callback with adapter daemon
            self.proxy().RegisterCallback(objpath)

        if not self.connection_callbacks:
            self.connection_callbacks = self.ExportedConnectionCallbacks(
                    self.bus, objpath)
            self.connection_callbacks.add_observer('adapter_client', self)
            objpath = self.ADAPTER_CONN_CB_OBJ_PATTERN.format(
                    self.hci, rnumber)
            self.bus.register_object(objpath, self.connection_callbacks, None)

            self.proxy().RegisterConnectionCallback(objpath)

        return True

    def register_callback_observer(self, name, observer):
        """Add an observer for all callbacks.

        @param name: Name of the observer.
        @param observer: Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothCallbacks):
            self.callbacks.add_observer(name, observer)

        if isinstance(observer, BluetoothConnectionCallbacks):
            self.connection_callbacks.add_observer(name, observer)

    def unregister_callback_observer(self, name, observer):
        """Remove an observer for all callbacks.

        @param name: Name of the observer.
        @param observer: Observer that implements all callback classes.
        """
        if isinstance(observer, BluetoothCallbacks):
            self.callbacks.remove_observer(name, observer)

        if isinstance(observer, BluetoothConnectionCallbacks):
            self.connection_callbacks.remove_observer(name, observer)

    @glib_call('')
    def get_address(self):
        """Gets the adapter's current address."""
        return str(self.proxy().GetAddress())

    @glib_call('')
    def get_name(self):
        """Gets the adapter's name."""
        return str(self.proxy().GetName())

    @glib_call(None)
    def get_property(self, prop_name):
        """Gets property by name."""
        return self.properties.get(prop_name)

    @glib_call(None)
    def get_remote_property(self, address, prop_name):
        """Gets remote device property by name."""
        name = 'Test device'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return self.remote_properties.get(prop_name, remote_device)

    @glib_call(None)
    def set_property(self, prop_name, *args):
        """Sets property by name."""
        return self.properties.set(prop_name, *args)

    @glib_call(None)
    def set_remote_property(self, address, prop_name, *args):
        """Sets remote property by name."""
        name = 'Test device'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return self.properties.set(prop_name, remote_device, *args)

    @glib_call(False)
    def start_discovery(self):
        """Starts discovery session."""
        return bool(self.proxy().StartDiscovery())

    @glib_call(False)
    def stop_discovery(self):
        """Stops discovery session."""
        return bool(self.proxy().CancelDiscovery())

    @glib_call(False)
    def is_discovering(self):
        """Is adapter discovering?"""
        return bool(self.discovering)

    @glib_call(False)
    def has_device(self, address):
        """Checks to see if device with address is known."""
        return address in self.known_devices

    def is_bonded(self, address):
        """Checks if the given address is currently fully bonded."""
        return address in self.known_devices and self.known_devices[
                address].get('bond_state',
                             BondState.NOT_BONDED) == BondState.BONDED

    @glib_call(False)
    def create_bond(self, address, transport):
        """Creates bond with target address.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return bool(self.proxy().CreateBond(remote_device, int(transport)))

    @glib_call(False)
    def cancel_bond(self, address):
        """Call cancel bond with no additional checks. Prefer |forget_device|.

        @param address: Device to cancel bond.
        @returns Result of |CancelBondProcess|.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return bool(self.proxy().CancelBond(remote_device))

    @glib_call(False)
    def remove_bond(self, address):
        """Call remove bond with no additional checks. Prefer |forget_device|.

        @param address: Device to remove bond.
        @returns Result of |RemoveBond|.
        """
        name = 'Test bond'
        if address in self.known_devices:
            name = self.known_devices[address]['name']

        remote_device = self._make_dbus_device(address, name)
        return bool(self.proxy().RemoveBond(remote_device))

    @glib_call(False)
    def forget_device(self, address):
        """Forgets device from local cache and removes bonding.

        If a device is currently bonding or bonded, it will cancel or remove the
        bond to totally remove this device.

        @return
            True if device was known and was removed.
            False if device was unknown or removal failed.
        """
        if address not in self.known_devices:
            return False

        # Remove the device from known devices first
        device = self.known_devices[address]
        del self.known_devices[address]

        remote_device = self._make_dbus_device(device['address'],
                                               device['name'])

        # Extra actions if bond state is not NOT_BONDED
        if device['bond_state'] == BondState.BONDING:
            return bool(self.proxy().CancelBondProcess(remote_device))
        elif device['bond_state'] == BondState.BONDED:
            return bool(self.proxy().RemoveBond(remote_device))

        return True

    @glib_call(False)
    def set_pairing_confirmation(self, address, accept):
        """Confirm that a pairing should be completed on a bonding device."""
        # Device should be known or already `Bonding`
        if address not in self.known_devices:
            logging.debug('[%s] Unknown device in set_pairing_confirmation',
                          address)
            return False

        device = self.known_devices[address]
        remote_device = self._make_dbus_device(address, device['name'])

        return bool(self.proxy().SetPairingConfirmation(remote_device, accept))

    def get_connected_devices_count(self):
        """Gets the number of known, connected devices."""
        return sum([
                1 for x in self.known_devices.values()
                if x.get('connected', False)
        ])

    def is_connected(self, address):
        """Checks whether a device is connected."""
        return address in self.known_devices and self.known_devices[
                address].get('connected', False)

    @glib_call(False)
    def connect_all_enabled_profiles(self, address):
        """Connect all enabled profiles for target address."""
        device = self._make_dbus_device(
                address,
                self.known_devices.get(address, {}).get('name', 'Test device'))
        return bool(self.proxy().ConnectAllEnabledProfiles(device))

    @glib_call(False)
    def disconnect_all_enabled_profiles(self, address):
        """Disconnect all enabled profiles for target address."""
        device = self._make_dbus_device(
                address,
                self.known_devices.get(address, {}).get('name', 'Test device'))
        return bool(self.proxy().DisconnectAllEnabledProfiles(device))
