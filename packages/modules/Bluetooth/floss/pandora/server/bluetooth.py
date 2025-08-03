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
"""All functions relative to the bluetooth procedure."""

import logging
import threading
import traceback

from floss.pandora.floss import adapter_client
from floss.pandora.floss import advertising_client
from floss.pandora.floss import manager_client
from floss.pandora.floss import qa_client
from floss.pandora.floss import scanner_client
from floss.pandora.floss import gatt_client
from floss.pandora.floss import gatt_server
from floss.pandora.floss import floss_enums
from floss.pandora.floss import utils
from gi.repository import GLib
import pydbus


class Bluetooth(object):
    """A bluetooth facade exposes all bluetooth functions."""

    # Default to this adapter during init. We will initialize to the correct
    # default adapter after the manager client is initialized.
    DEFAULT_ADAPTER = 0

    # Time to sleep between polls
    ADAPTER_CLIENT_POLL_INTERVAL = 0.1

    # How long we wait for the adapter to come up after we start it.
    ADAPTER_DAEMON_TIMEOUT_SEC = 20

    # How long we wait for the manager daemon to come up after we start it.
    DAEMON_TIMEOUT_SEC = 5

    # Default scanner settings
    SCANNER_INTERVAL = 0
    SCANNER_WINDOW = 0
    SCANNER_SCAN_TYPE = 0

    FAKE_GATT_APP_ID = '12345678123456781234567812345678'

    def __init__(self):
        self.setup_mainloop()

        # self state
        self.is_clean = False

        # DBUS clients
        self.manager_client = manager_client.FlossManagerClient(self.bus)
        self.adapter_client = adapter_client.FlossAdapterClient(self.bus, self.DEFAULT_ADAPTER)
        self.advertising_client = advertising_client.FlossAdvertisingClient(self.bus, self.DEFAULT_ADAPTER)
        self.scanner_client = scanner_client.FlossScannerClient(self.bus, self.DEFAULT_ADAPTER)
        self.qa_client = qa_client.FlossQAClient(self.bus, self.DEFAULT_ADAPTER)
        self.gatt_client = gatt_client.FlossGattClient(self.bus, self.DEFAULT_ADAPTER)
        self.gatt_server = gatt_server.FlossGattServer(self.bus, self.DEFAULT_ADAPTER)

    def __del__(self):
        if not self.is_clean:
            self.cleanup()

    def cleanup(self):
        self.mainloop_quit.set()
        self.mainloop.quit()
        self.is_clean = True

    def setup_mainloop(self):
        """Start mainloop thread in background.

        This will also initialize a few
        other variables (self.bus, self.mainloop, self.event_context) that may
        be necessary for proper operation.

        Raises:
            RuntimeError: if we timeout to wait for the mainloop ready.
        """

        self.mainloop_quit = threading.Event()
        self.mainloop_ready = threading.Event()
        self.thread = threading.Thread(name=utils.GLIB_THREAD_NAME, target=Bluetooth.mainloop_thread, args=(self,))
        self.thread.start()

        # Wait for mainloop to be ready
        if not self.mainloop_ready.wait(timeout=5):
            raise RuntimeError('Unable to initialize GLib mainloop')

    def mainloop_thread(self):
        # Set up mainloop. All subsequent buses and connections will use this
        # mainloop. We also use a separate main context to avoid multithreading
        # issues.
        GLib.threads_init()
        self.mainloop = GLib.MainLoop()

        # Set up bus connection
        self.bus = pydbus.SystemBus()

        # Set thread ready
        self.mainloop_ready.set()

        while not self.mainloop_quit.is_set():
            self.mainloop.run()

    def register_clients_callback(self):
        """Registers callback for all interfaces.

        Returns:
            True on success, False otherwise.
        """
        if not self.adapter_client.register_callbacks():
            logging.error('adapter_client: Failed to register callbacks')
            return False
        if not self.advertising_client.register_advertiser_callback():
            logging.error('advertising_client: Failed to register advertiser callbacks')
            return False
        if not self.scanner_client.register_scanner_callback():
            logging.error('scanner_client: Failed to register callbacks')
            return False
        if not self.qa_client.register_qa_callback():
            logging.error('qa_client: Failed to register callbacks')
            return False
        if not self.gatt_client.register_client(self.FAKE_GATT_APP_ID, False):
            logging.error('gatt_client: Failed to register callbacks')
            return False
        if not self.gatt_server.register_server(self.FAKE_GATT_APP_ID, False):
            logging.error('gatt_server: Failed to register callbacks')
            return False
        return True

    def is_bluetoothd_proxy_valid(self):
        """Checks whether the proxy objects for Floss are ok and registers client callbacks."""

        proxy_ready = all([
            self.manager_client.has_proxy(),
            self.adapter_client.has_proxy(),
            self.advertising_client.has_proxy(),
            self.scanner_client.has_proxy(),
            self.qa_client.has_proxy(),
            self.gatt_client.has_proxy(),
            self.gatt_server.has_proxy(),
        ])

        if not proxy_ready:
            logging.info('Some proxy has not yet ready.')
            return False

        return self.register_clients_callback()

    def set_powered(self, powered: bool):
        """Set the power of bluetooth adapter and bluetooth clients.

        Args:
            powered: Power on or power off.

        Returns:
            bool: True if success, False otherwise.
        """
        default_adapter = self.manager_client.get_default_adapter()

        def _is_adapter_down(client):
            return lambda: not client.has_proxy()

        if powered:
            # FIXME: Close rootcanal will cause manager_client failed call has_default_adapter.
            # if not self.manager_client.has_default_adapter():
            #     logging.warning('set_powered: Default adapter not available.')
            #     return False
            self.manager_client.start(default_adapter)

            self.adapter_client = adapter_client.FlossAdapterClient(self.bus, default_adapter)
            self.advertising_client = advertising_client.FlossAdvertisingClient(self.bus, default_adapter)
            self.scanner_client = scanner_client.FlossScannerClient(self.bus, default_adapter)
            self.qa_client = qa_client.FlossQAClient(self.bus, default_adapter)
            self.gatt_client = gatt_client.FlossGattClient(self.bus, default_adapter)
            self.gatt_server = gatt_server.FlossGattServer(self.bus, default_adapter)

            try:
                utils.poll_for_condition(
                    condition=lambda: self.is_bluetoothd_proxy_valid() and self.adapter_client.get_address(),
                    desc='Wait for adapter start',
                    sleep_interval=self.ADAPTER_CLIENT_POLL_INTERVAL,
                    timeout=self.ADAPTER_DAEMON_TIMEOUT_SEC)
            except TimeoutError as e:
                logging.error('timeout: error starting adapter daemon: %s', e)
                logging.error(traceback.format_exc())
                return False
        else:
            self.manager_client.stop(default_adapter)
            try:
                utils.poll_for_condition(condition=_is_adapter_down(self.adapter_client),
                                         desc='Wait for adapter stop',
                                         sleep_interval=self.ADAPTER_CLIENT_POLL_INTERVAL,
                                         timeout=self.ADAPTER_DAEMON_TIMEOUT_SEC)
            except TimeoutError as e:
                logging.error('timeout: error stopping adapter daemon: %s', e)
                logging.error(traceback.format_exc())
                return False
        return True

    def reset(self):
        if not self.set_powered(False):
            return False

        if not self.set_powered(True):
            return False
        return True

    def get_address(self):
        return self.adapter_client.get_address()

    def get_remote_type(self, address):
        return self.adapter_client.get_remote_property(address, 'Type')

    def is_connected(self, address):
        return self.adapter_client.is_connected(address)

    def is_bonded(self, address):
        return self.adapter_client.is_bonded(address)

    def is_discovering(self):
        return self.adapter_client.is_discovering()

    def set_discoverable(self, mode, duration=60):
        return self.adapter_client.set_property('Discoverable', mode, duration)

    def create_bond(self, address, transport):
        return self.adapter_client.create_bond(address, transport)

    def remove_bond(self, address):
        return self.adapter_client.remove_bond(address)

    def get_bonded_devices(self):
        return self.adapter_client.get_bonded_devices()

    def forget_device(self, address):
        return self.adapter_client.forget_device(address)

    def set_pin(self, address, accept, pin_code):
        return self.adapter_client.set_pin(address, accept, pin_code)

    def set_pairing_confirmation(self, address, accept):
        return self.adapter_client.set_pairing_confirmation(address, accept)

    def connect_device(self, address):
        return self.adapter_client.connect_all_enabled_profiles(address)

    def disconnect_device(self, address):
        return self.adapter_client.disconnect_all_enabled_profiles(address)

    def start_discovery(self):
        if self.adapter_client.is_discovering():
            logging.warning('Adapter is already discovering.')
            return True
        return self.adapter_client.start_discovery()

    def stop_discovery(self):
        if not self.adapter_client.is_discovering():
            logging.warning('Discovery is already stopped.')
            return True
        return self.adapter_client.stop_discovery()

    def start_advertising_set(self, parameters, advertise_data, scan_response, periodic_parameters, periodic_data,
                              duration, max_ext_adv_events):
        parameters = self.advertising_client.make_dbus_advertising_set_parameters(parameters)
        advertise_data = self.advertising_client.make_dbus_advertise_data(advertise_data)
        scan_response = utils.make_kv_optional_value(self.advertising_client.make_dbus_advertise_data(scan_response))
        periodic_parameters = utils.make_kv_optional_value(
            self.advertising_client.make_dbus_periodic_advertising_parameters(periodic_parameters))
        periodic_data = utils.make_kv_optional_value(self.advertising_client.make_dbus_advertise_data(periodic_data))

        return self.advertising_client.start_advertising_set(parameters, advertise_data, scan_response,
                                                             periodic_parameters, periodic_data, duration,
                                                             max_ext_adv_events)

    def stop_advertising_set(self, advertiser_id):
        return self.advertising_client.stop_advertising_set(advertiser_id)

    def register_scanner(self):
        return self.scanner_client.register_scanner()

    def start_scan(self, scanner_id, settings=None, scan_filter=None):
        if settings is None:
            settings = self.scanner_client.make_dbus_scan_settings(self.SCANNER_INTERVAL, self.SCANNER_WINDOW,
                                                                   self.SCANNER_SCAN_TYPE)
        return self.scanner_client.start_scan(scanner_id, settings, scan_filter)

    def stop_scan(self, scanner_id):
        if not self.scanner_client.remove_monitor(scanner_id):
            logging.error('Failed to stop scanning.')
            return False
        return True

    def set_hid_report(self, addr, report_type, report):
        return self.qa_client.set_hid_report(addr, report_type, report)

    def read_characteristic(self, address, handle, auth_re):
        return self.gatt_client.read_characteristic(address, handle, auth_re)

    def read_descriptor(self, address, handle, auth_req):
        return self.gatt_client.read_descriptor(address, handle, auth_req)

    def discover_services(self, address):
        return self.gatt_client.discover_services(address)

    def get_bond_state(self, address):
        self.adapter_client.get_bond_state(address)

    def fetch_remote(self, address):
        return self.adapter_client.fetch_remote_uuids(address)

    def get_remote_uuids(self, address):
        return self.adapter_client.get_remote_property(address, 'Uuids')

    def btif_gattc_discover_service_by_uuid(self, address, uuid):
        return self.gatt_client.btif_gattc_discover_service_by_uuid(address, uuid)

    def configure_mtu(self, address, mtu):
        return self.gatt_client.configure_mtu(address, mtu)

    def refresh_device(self, address):
        return self.gatt_client.refresh_device(address)

    def write_descriptor(self, address, handle, auth_req, value):
        return self.gatt_client.write_descriptor(address, handle, auth_req, value)

    def write_characteristic(self, address, handle, write_type, auth_req, value):
        return self.gatt_client.write_characteristic(address, handle, write_type, auth_req, value)

    def gatt_connect(self, address, is_direct, transport):
        return self.gatt_client.connect_client(address, is_direct, transport)

    def set_connectable(self, mode):
        return self.qa_client.set_connectable(mode)

    def read_using_characteristic_uuid(self, address, uuid, start_handle, end_handle, auth_req):
        return self.gatt_client.read_using_characteristic_uuid(address, uuid, start_handle, end_handle, auth_req)

    def register_for_notification(self, address, handle, enable):
        return self.gatt_client.register_for_notification(address, handle, enable)

    def add_service(self, service):
        service = self.gatt_server.make_dbus_service(service)
        return self.gatt_server.add_service(service)

    def is_encrypted(self, address):
        connection_state = self.adapter_client.get_connection_state(address)
        if connection_state is None:
            logging.error('Failed to get connection state for address: %s', address)
            return False
        return connection_state > floss_enums.BtConnectionState.CONNECTED_ONLY
