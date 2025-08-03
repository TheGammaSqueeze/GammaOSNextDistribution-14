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
"""Client Class to access the Floss GATT client interface."""

import logging

from floss.pandora.floss import floss_enums
from floss.pandora.floss import observer_base
from floss.pandora.floss import utils


class GattClientCallbacks:
    """Callbacks for the GATT client interface.

    Implement this to observe these callbacks when exporting callbacks via register_client.
    """

    def on_client_registered(self, status, scanner_id):
        """Called when GATT client registered.

        Args:
            status: floss_enums.GattStatus.
            scanner_id: Bluetooth GATT scanner id.
        """
        pass

    def on_client_connection_state(self, status, client_id, connected, addr):
        """Called when GATT client connection state changed.

        Args:
            status: floss_enums.GattStatus.
            client_id: Bluetooth GATT client id.
            connected: A boolean value representing whether the device is connected.
            addr: Remote device MAC address.
        """
        pass

    def on_phy_update(self, addr, tx_phy, rx_phy, status):
        """Called when GATT physical type is updated.

        Args:
            addr: Remote device MAC address.
            tx_phy: Transmit physical type.
            rx_phy: Receive physical type.
            status: floss_enums.GattStatus.
        """
        pass

    def on_phy_read(self, addr, tx_phy, rx_phy, status):
        """Called when GATT physical type is read.

        Args:
            addr: Remote device MAC address.
            tx_phy: Transmit physical type.
            rx_phy: Receive physical type.
            status: floss_enums.GattStatus.
        """
        pass

    def on_search_complete(self, addr, services, status):
        """Called when search completed.

        Args:
            addr: Remote device MAC address.
            services: Bluetooth GATT services as list.
            status: floss_enums.GattStatus.
        """
        pass

    def on_characteristic_read(self, addr, status, handle, value):
        """Called when characteristic is read.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Characteristic handle id.
            value: Characteristic value.
        """
        pass

    def on_characteristic_write(self, addr, status, handle):
        """Called when characteristic is written.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Characteristic handle id.
        """
        pass

    def on_execute_write(self, addr, status):
        """Called when execute write.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
        """
        pass

    def on_descriptor_read(self, addr, status, handle, value):
        """Called when descriptor is read.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Descriptor handle id.
            value: Descriptor value.
        """
        pass

    def on_descriptor_write(self, addr, status, handle):
        """Called when descriptor is written.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Descriptor handle id.
        """
        pass

    def on_notify(self, addr, handle, value):
        """Called when notified.

        Args:
            addr: Remote device MAC address.
            handle: Characteristic handle id.
            value: Characteristic value.
        """
        pass

    def on_read_remote_rssi(self, addr, rssi, status):
        """Called when remote RSSI is read.

        Args:
            addr: Remote device MAC address.
            rssi: RSSI value.
            status: floss_enums.GattStatus.
        """
        pass

    def on_configure_mtu(self, addr, mtu, status):
        """Called when MTU is configured.

        Args:
            addr: Remote device MAC address.
            mtu: MTU value.
            status: floss_enums.GattStatus.
        """
        pass

    def on_connection_updated(self, addr, interval, latency, timeout, status):
        """Called when connection updated.

        Args:
            addr: Remote device MAC address.
            interval: Interval in ms.
            latency: Latency in ms.
            timeout: Timeout in ms.
            status: floss_enums.GattStatus.
        """
        pass

    def on_service_changed(self, addr):
        """Called when service changed.

        Args:
            addr: Remote device MAC address.
        """
        pass


class FlossGattClient(GattClientCallbacks):
    """Handles method calls and callbacks from the GATT client interface."""

    ADAPTER_SERVICE = 'org.chromium.bluetooth'
    GATT_CLIENT_INTERFACE = 'org.chromium.bluetooth.BluetoothGatt'
    GATT_OBJECT_PATTERN = '/org/chromium/bluetooth/hci{}/gatt'
    GATT_CB_OBJ_NAME = 'test_gatt_client'
    CB_EXPORTED_INTF = 'org.chromium.bluetooth.BluetoothGattCallback'
    FLOSS_RESPONSE_LATENCY_SECS = 3

    class ExportedGattClientCallbacks(observer_base.ObserverBase):
        """
        <node>
            <interface name="org.chromium.bluetooth.BluetoothGattCallback">
                <method name="OnClientRegistered">
                    <arg type="u" name="status" direction="in" />
                    <arg type="i" name="scanner_id" direction="in" />
                </method>
                <method name="OnClientConnectionState">
                    <arg type="u" name="status" direction="in" />
                    <arg type="i" name="client_id" direction="in" />
                    <arg type="b" name="connected" direction="in" />
                    <arg type="s" name="addr" direction="in" />
                </method>
                <method name="OnPhyUpdate">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="u" name="tx_phy" direction="in" />
                    <arg type="u" name="rx_phy" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnPhyRead">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="u" name="tx_phy" direction="in" />
                    <arg type="u" name="rx_phy" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnSearchComplete">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="aa{sv}" name="services" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnCharacteristicRead">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="u" name="status" direction="in" />
                    <arg type="i" name="handle" direction="in" />
                    <arg type="ay" name="value" direction="in" />
                </method>
                <method name="OnCharacteristicWrite">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="u" name="status" direction="in" />
                    <arg type="i" name="handle" direction="in" />
                </method>
                <method name="OnExecuteWrite">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnDescriptorRead">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="u" name="status" direction="in" />
                    <arg type="i" name="handle" direction="in" />
                    <arg type="ay" name="value" direction="in" />
                </method>
                <method name="OnDescriptorWrite">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="u" name="status" direction="in" />
                    <arg type="i" name="handle" direction="in" />
                </method>
                <method name="OnNotify">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="i" name="handle" direction="in" />
                    <arg type="ay" name="value" direction="in" />
                </method>
                <method name="OnReadRemoteRssi">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="i" name="rssi" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnConfigureMtu">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="i" name="mtu" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnConnectionUpdated">
                    <arg type="s" name="addr" direction="in" />
                    <arg type="i" name="interval" direction="in" />
                    <arg type="i" name="latency" direction="in" />
                    <arg type="i" name="timeout" direction="in" />
                    <arg type="u" name="status" direction="in" />
                </method>
                <method name="OnServiceChanged">
                    <arg type="s" name="addr" direction="in" />
                </method>

            </interface>
        </node>
        """

        def __init__(self):
            """Constructs exported callbacks object."""
            observer_base.ObserverBase.__init__(self)

        def OnClientRegistered(self, status, scanner_id):
            """Handles client registration callback.

            Args:
                status: floss_enums.GattStatus.
                scanner_id: Bluetooth GATT scanner id.
            """
            for observer in self.observers.values():
                observer.on_client_registered(status, scanner_id)

        def OnClientConnectionState(self, status, client_id, connected, addr):
            """Handles client connection state callback.

            Args:
                status: floss_enums.GattStatus.
                client_id: Bluetooth GATT client id.
                connected: A boolean value representing whether the device is connected.
                addr: Remote device MAC address.
            """
            for observer in self.observers.values():
                observer.on_client_connection_state(status, client_id, connected, addr)

        def OnPhyUpdate(self, addr, tx_phy, rx_phy, status):
            """Handles GATT physical type update callback.

            Args:
                addr: Remote device MAC address.
                tx_phy: Transmit physical type.
                rx_phy: Receive physical type.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_phy_update(addr, tx_phy, rx_phy, status)

        def OnPhyRead(self, addr, tx_phy, rx_phy, status):
            """Handles GATT physical type read callback.

            Args:
                addr: Remote device MAC address.
                tx_phy: Transmit physical type.
                rx_phy: Receive physical type.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_phy_read(addr, tx_phy, rx_phy, status)

        def OnSearchComplete(self, addr, services, status):
            """Handles search complete callback.

            Args:
                addr: Remote device MAC address.
                services: Bluetooth GATT services as list.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_search_complete(addr, services, status)

        def OnCharacteristicRead(self, addr, status, handle, value):
            """Handles characteristic read callback.

            Args:
                addr: Remote device MAC address.
                status: floss_enums.GattStatus.
                handle: Characteristic handle id.
                value: Characteristic value.
            """
            for observer in self.observers.values():
                observer.on_characteristic_read(addr, status, handle, value)

        def OnCharacteristicWrite(self, addr, status, handle):
            """Handles characteristic write callback.

            Args:
                addr: Remote device MAC address.
                status: floss_enums.GattStatus.
                handle: Characteristic handle id.
            """
            for observer in self.observers.values():
                observer.on_characteristic_write(addr, status, handle)

        def OnExecuteWrite(self, addr, status):
            """Handles write execution callbacks.

            Args:
                addr: Remote device MAC address.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_execute_write(addr, status)

        def OnDescriptorRead(self, addr, status, handle, value):
            """Handles descriptor read callback.

            Args:
                addr: Remote device MAC address.
                status: floss_enums.GattStatus.
                handle: Descriptor handle id.
                value: Descriptor value.
            """
            for observer in self.observers.values():
                observer.on_descriptor_read(addr, status, handle, value)

        def OnDescriptorWrite(self, addr, status, handle):
            """Handles descriptor write callback.

            Args:
                addr: Remote device MAC address.
                status: floss_enums.GattStatus.
                handle: Descriptor handle id.
            """
            for observer in self.observers.values():
                observer.on_descriptor_write(addr, status, handle)

        def OnNotify(self, addr, handle, value):
            """Handles notification callback.

            Args:
                addr: Remote device MAC address.
                handle: Characteristic handle id.
                value: Characteristic value.
            """
            for observer in self.observers.values():
                observer.on_notify(addr, handle, value)

        def OnReadRemoteRssi(self, addr, rssi, status):
            """Handles remote RSSI value read callback.

            Args:
                addr: Remote device MAC address.
                rssi: RSSI value.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_read_remote_rssi(addr, rssi, status)

        def OnConfigureMtu(self, addr, mtu, status):
            """Handles MTU configuration callback.

            Args:
                addr: Remote device MAC address.
                mtu: MTU value.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_configure_mtu(addr, mtu, status)

        def OnConnectionUpdated(self, addr, interval, latency, timeout, status):
            """Handles connection update callback.

            Args:
                addr: Remote device MAC address.
                interval: Interval in ms.
                latency: Latency in ms.
                timeout: Timeout in ms.
                status: floss_enums.GattStatus.
            """
            for observer in self.observers.values():
                observer.on_connection_updated(addr, interval, latency, timeout, status)

        def OnServiceChanged(self, addr):
            """Handles service changed callback.

            Args:
                addr: Remote device MAC address.
            """
            for observer in self.observers.values():
                observer.on_service_changed(addr)

    def __init__(self, bus, hci):
        """Constructs the client.

        Args:
            bus: D-Bus bus over which we'll establish connections.
            hci: HCI adapter index. Get this value from `get_default_adapter` on FlossManagerClient.
        """

        self.bus = bus
        self.hci = hci
        self.callbacks = None
        self.callback_id = None
        self.objpath = self.GATT_OBJECT_PATTERN.format(hci)
        self.client_id = None
        self.gatt_services = {}
        self.connected_clients = {}

    def __del__(self):
        """Destructor."""
        del self.callbacks

    @utils.glib_callback()
    def on_client_registered(self, status, scanner_id):
        """Handles client registration callback.

        Args:
            status: floss_enums.GattStatus.
            scanner_id: Bluetooth GATT scanner id.
        """
        logging.debug('on_client_registered: status: %s, scanner_id: %s', status, scanner_id)

        if status != floss_enums.GattStatus.SUCCESS:
            logging.error('Failed to register client with id: %s, status = %s', scanner_id, status)
            return
        self.client_id = scanner_id

    @utils.glib_callback()
    def on_client_connection_state(self, status, client_id, connected, addr):
        """Handles client connection state callback.

        Args:
            status: floss_enums.GattStatus.
            client_id: Bluetooth GATT client id.
            connected: A boolean value representing whether the device is connected.
            addr: Remote device MAC address.
        """
        logging.debug('on_client_connection_state: status: %s, client_id: %s, '
                      'connected: %s, addr: %s', status, client_id, connected, addr)
        if status != floss_enums.GattStatus.SUCCESS:
            return
        self.connected_clients[addr] = connected

    @utils.glib_callback()
    def on_phy_update(self, addr, tx_phy, rx_phy, status):
        """Handles physical type update callback.

        Args:
            addr: Remote device MAC address.
            tx_phy: Transmit physical type.
            rx_phy: Receive physical type.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_phy_update: addr: %s, tx_phy: %s, rx_phy: %s, status: %s', addr, tx_phy, rx_phy, status)

    @utils.glib_callback()
    def on_phy_read(self, addr, tx_phy, rx_phy, status):
        """Handles physical type read callback.

        Args:
            addr: Remote device MAC address.
            tx_phy: Transmit physical type.
            rx_phy: Receive physical type.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_phy_read: addr: %s, tx_phy: %s, rx_phy: %s, status: %s', addr, tx_phy, rx_phy, status)

    @utils.glib_callback()
    def on_search_complete(self, addr, services, status):
        """Handles search complete callback.

        Args:
            addr: Remote device MAC address.
            services: Bluetooth GATT services as list.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_search_complete: addr: %s, services: %s, status: %s', addr, services, status)
        if status != floss_enums.GattStatus.SUCCESS:
            logging.error('Failed to complete search')
            return
        self.gatt_services[addr] = services

    @utils.glib_callback()
    def on_characteristic_read(self, addr, status, handle, value):
        """Handles characteristic read callback.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Characteristic handle id.
            value: Characteristic value.
        """
        logging.debug('on_characteristic_read: addr: %s, status: %s, handle: %s, '
                      'value: %s', addr, status, handle, value)

    @utils.glib_callback()
    def on_characteristic_write(self, addr, status, handle):
        """Handles characteristic write callback.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Characteristic handle id.
        """
        logging.debug('on_characteristic_write: addr: %s, status: %s, handle: %s', addr, status, handle)

    @utils.glib_callback()
    def on_execute_write(self, addr, status):
        """Handles write execution callbacks.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_execute_write: addr: %s, status: %s', addr, status)

    @utils.glib_callback()
    def on_descriptor_read(self, addr, status, handle, value):
        """Handles descriptor read callback.

        Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Descriptor handle id.
            value: Descriptor value.
        """
        logging.debug('on_descriptor_read: addr: %s, status: %s, handle: %s, value: %s', addr, status, handle, value)

    @utils.glib_callback()
    def on_descriptor_write(self, addr, status, handle):
        """Handles descriptor write callback.

       Args:
            addr: Remote device MAC address.
            status: floss_enums.GattStatus.
            handle: Descriptor handle id.
        """
        logging.debug('on_descriptor_write: addr: %s, status: %s, handle: %s', addr, status, handle)

    @utils.glib_callback()
    def on_notify(self, addr, handle, value):
        """Handles notification callback.

        Args:
            addr: Remote device MAC address.
            handle: Characteristic handle id.
            value: Characteristic value.
        """
        logging.debug('on_notify: addr: %s, handle: %s, value: %s', addr, handle, value)

    @utils.glib_callback()
    def on_read_remote_rssi(self, addr, rssi, status):
        """Handles remote RSSI value read callback.

        Args:
            addr: Remote device MAC address.
            rssi: RSSI value.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_read_remote_rssi: addr: %s, rssi: %s, status: %s', addr, rssi, status)

    @utils.glib_callback()
    def on_configure_mtu(self, addr, mtu, status):
        """Handles MTU configuration callback.

        Args:
            addr: Remote device MAC address.
            mtu: MTU value.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_configure_mtu: addr: %s, mtu: %s, status: %s', addr, mtu, status)

    @utils.glib_callback()
    def on_connection_updated(self, addr, interval, latency, timeout, status):
        """Handles connection update callback.

        Args:
            addr: Remote device MAC address.
            interval: Interval in ms.
            latency: Latency in ms.
            timeout: Timeout in ms.
            status: floss_enums.GattStatus.
        """
        logging.debug('on_connection_updated: addr: %s, interval: %s, latency: %s, '
                      'timeout: %s, status: %s', addr, interval, latency, timeout, status)

    @utils.glib_callback()
    def on_service_changed(self, addr):
        """Handles service changed callback.

        Args:
            addr: Remote device MAC address.
        """
        logging.debug('on_service_changed: addr: %s', addr)

    @utils.glib_call(False)
    def has_proxy(self):
        """Checks whether GATT Client can be acquired."""
        return bool(self.proxy())

    def proxy(self):
        """Gets proxy object to GATT Client interface for method calls."""
        return self.bus.get(self.ADAPTER_SERVICE, self.objpath)[self.GATT_CLIENT_INTERFACE]

    @utils.glib_call(False)
    def register_client(self, app_uuid, eatt_support):
        """Registers GATT client callbacks if one doesn't already exist.

        Args:
            app_uuid: GATT application uuid.
            eatt_support: A boolean value that indicates whether eatt is supported.

        Returns:
            True on success, False otherwise.
        """
        # Callbacks already registered
        if self.callbacks:
            return True
        # Create and publish callbacks
        self.callbacks = self.ExportedGattClientCallbacks()
        self.callbacks.add_observer('gatt_testing_client', self)
        objpath = utils.generate_dbus_cb_objpath(self.GATT_CB_OBJ_NAME, self.hci)
        self.bus.register_object(objpath, self.callbacks, None)
        # Register published callbacks with adapter daemon
        self.callback_id = self.proxy().RegisterClient(app_uuid, objpath, eatt_support)
        return True

    @utils.glib_call(False)
    def unregister_client(self):
        """Unregisters GATT client.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().UnregisterClient(self.client_id)
        return True

    def register_callback_observer(self, name, observer):
        """Add an observer for all callbacks.

        Args:
            name: Name of the observer.
            observer: Observer that implements all callback classes.
        """
        if isinstance(observer, GattClientCallbacks):
            self.callbacks.add_observer(name, observer)

    def unregister_callback_observer(self, name, observer):
        """Remove an observer for all callbacks.

        Args:
            name: Name of the observer.
            observer: Observer that implements all callback classes.
        """
        if isinstance(observer, GattClientCallbacks):
            self.callbacks.remove_observer(name, observer)

    @utils.glib_call(False)
    def connect_client(self,
                       address,
                       is_direct=False,
                       transport=floss_enums.BtTransport.LE,
                       opportunistic=False,
                       phy=floss_enums.LePhy.PHY1M):
        """Connects GATT client.

        Args:
            address: Remote device MAC address.
            is_direct: A boolean value represent direct status.
            transport: floss_enums.BtTransport type.
            opportunistic: A boolean value represent opportunistic status.
            phy: floss_enums.LePhy type.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ClientConnect(self.client_id, address, is_direct, transport, opportunistic, phy)
        return True

    @utils.glib_call(False)
    def disconnect_client(self, address):
        """Disconnects GATT client.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ClientDisconnect(self.client_id, address)
        return True

    @utils.glib_call(False)
    def refresh_device(self, address):
        """Refreshes device.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().RefreshDevice(self.client_id, address)
        return True

    @utils.glib_call(False)
    def discover_services(self, address):
        """Discovers remote device GATT services.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().DiscoverServices(self.client_id, address)
        return True

    @utils.glib_call(False)
    def discover_service_by_uuid(self, address, uuid):
        """Discovers remote device GATT services by UUID.

        Args:
            address: Remote device MAC address.
            uuid: The service UUID as a string.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().DiscoverServiceByUuid(self.client_id, address, uuid)
        return True

    @utils.glib_call(False)
    def btif_gattc_discover_service_by_uuid(self, address, uuid):
        """Discovers remote device GATT services by UUID from btif layer.

        Args:
            address: Remote device MAC address.
            uuid: The service UUID as a string.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().BtifGattcDiscoverServiceByUuid(self.client_id, address, uuid)
        return True

    @utils.glib_call(False)
    def read_characteristic(self, address, handle, auth_req):
        """Reads GATT characteristic.

        Args:
            address: Remote device MAC address.
            handle: Characteristic handle id.
            auth_req: Authentication requirements value.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ReadCharacteristic(self.client_id, address, handle, auth_req)
        return True

    @utils.glib_call(False)
    def read_using_characteristic_uuid(self, address, uuid, start_handle, end_handle, auth_req):
        """Reads remote device GATT characteristic by UUID.

        Args:
            address: Remote device MAC address.
            uuid: The characteristic UUID as a string.
            start_handle: Characteristic start handle id.
            end_handle: Characteristic end handle id.
            auth_req: Authentication requirements value.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ReadUsingCharacteristicUuid(self.client_id, address, uuid, start_handle, end_handle, auth_req)
        return True

    @utils.glib_call(False)
    def read_descriptor(self, address, handle, auth_req):
        """Reads remote device GATT descriptor.

        Args:
            address: Remote device MAC address.
            handle: Descriptor handle id.
            auth_req: Authentication requirements value.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ReadDescriptor(self.client_id, address, handle, auth_req)
        return True

    @utils.glib_call(False)
    def write_descriptor(self, address, handle, auth_req, value):
        """Writes remote device GATT descriptor.

        Args:
            address: Remote device MAC address.
            handle: Descriptor handle id.
            auth_req: Authentication requirements value.
            value: Descriptor value to write.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().WriteDescriptor(self.client_id, address, handle, auth_req, value)
        return True

    @utils.glib_call(None)
    def write_characteristic(self, address, handle, write_type, auth_req, value):
        """Writes remote device GATT characteristic.

        Args:
            address: Remote device MAC address.
            handle: Characteristic handle id.
            write_type: Characteristic write type.
            auth_req: Authentication requirements value.
            value: Characteristic value to write.

        Returns:
            GattWriteRequestStatus on success, None otherwise.
        """
        return self.proxy().WriteCharacteristic(self.client_id, address, handle, write_type, auth_req, value)

    @utils.glib_call(False)
    def register_for_notification(self, address, handle, enable):
        """Registers for notification.

        Args:
            address: Remote device MAC address.
            handle: Characteristic handle id.
            enable: Boolean value represents enabling or disabling notify.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().RegisterForNotification(self.client_id, address, handle, enable)
        return True

    @utils.glib_call(False)
    def begin_reliable_write(self, address):
        """Begins a reliable write transaction.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().BeginReliableWrite(self.client_id, address)
        return True

    @utils.glib_call(False)
    def end_reliable_write(self, address, execute):
        """Ends the reliable write transaction.

        Args:
            address: Remote device MAC address.
            execute: Boolean to execute or not.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().EndReliableWrite(self.client_id, address, execute)
        return True

    @utils.glib_call(False)
    def read_remote_rssi(self, address):
        """Reads remote device RSSI.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ReadRemoteRssi(self.client_id, address)
        return True

    @utils.glib_call(False)
    def configure_mtu(self, address, mtu):
        """Configures MTU value.

        Args:
            address: Remote device MAC address.
            mtu: MTU value.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ConfigureMtu(self.client_id, address, mtu)
        return True

    @utils.glib_call(False)
    def update_connection_parameter(self, address, min_interval, max_interval, latency, timeout, min_ce_len,
                                    max_ce_len):
        """Updates connection parameters.

        Args:
            address: Remote device MAC address.
            min_interval: Minimum interval in ms.
            max_interval: Maximum interval in ms.
            latency: Latency interval in ms.
            timeout: Timeout interval in ms.
            min_ce_len: Connection event minimum length in ms.
            max_ce_len: Connection event maximum length in ms.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ConnectionParameterUpdate(self.client_id, address, min_interval, max_interval, latency, timeout,
                                               min_ce_len, max_ce_len)
        return True

    @utils.glib_call(False)
    def set_preferred_phy(self, address, tx_phy, rx_phy, phy_options):
        """Sets remote device preferred physical options.

        Args:
            address: Remote device MAC address.
            tx_phy: Transmit physical type.
            rx_phy: Receive physical type.
            phy_options: Physical options to use for connection.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ClientSetPreferredPhy(self.client_id, address, tx_phy, rx_phy, phy_options)
        return True

    @utils.glib_call(False)
    def read_phy(self, address):
        """Reads remote device physical setting.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        self.proxy().ClientReadPhy(self.client_id, address)
        return True

    def wait_for_client_connected(self, address):
        """Waits for GATT client to be connected.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        try:
            utils.poll_for_condition(condition=lambda: self.connected_clients.get(address),
                                     timeout=self.FLOSS_RESPONSE_LATENCY_SECS)
            return True

        except utils.TimeoutError:
            logging.error('on_client_connection_state not called')
            return False

    def wait_for_search_complete(self, address):
        """Waits for GATT search to be completed.

        Args:
            address: Remote device MAC address.

        Returns:
            True on success, False otherwise.
        """
        try:
            utils.poll_for_condition(condition=lambda: address in self.gatt_services,
                                     timeout=self.FLOSS_RESPONSE_LATENCY_SECS)
            return True

        except utils.TimeoutError:
            logging.error('on_search_complete not called')
            return False

    def connect_client_sync(self, address):
        """Connects GATT client.

        Args:
            address: Remote device MAC address.

        Returns:
            Client id on success, None otherwise.
        """
        self.connect_client(address=address)
        if not self.wait_for_client_connected(address):
            return None
        return self.connected_clients[address]

    def discover_services_sync(self, address):
        """Discovers remote device GATT services.

        Args:
            address: Remote device MAC address.

        Returns:
            Remote device GATT services as a list on success, None otherwise.
        """
        self.discover_services(address)
        if not self.wait_for_search_complete(address):
            return None
        return self.gatt_services[address]

    def register_callback_observer(self, name, observer):
        """Adds an observer for all callbacks.

        Args:
            name: Name of the observer.
            observer: Observer that implements all callback classes.
        """
        if isinstance(observer, GattClientCallbacks):
            self.callbacks.add_observer(name, observer)

    def unregister_callback_observer(self, name, observer):
        """Removes an observer for all callbacks.

        Args:
            name: Name of the observer.
            observer: Observer that implements all callback classes.
        """
        if isinstance(observer, GattClientCallbacks):
            self.callbacks.remove_observer(name, observer)
