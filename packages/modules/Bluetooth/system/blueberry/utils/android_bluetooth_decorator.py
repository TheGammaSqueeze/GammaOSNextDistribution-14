"""AndroidBluetoothDecorator class.

This decorator is used for giving an AndroidDevice Bluetooth-specific
functionality.
"""

import datetime
import logging
import os
import queue
import random
import re
import string
import time
from typing import Dict, Any, Text, Optional, Tuple, Sequence, Union, List

from mobly import logger as mobly_logger
from mobly import signals
from mobly import utils
from mobly.controllers import android_device
from mobly.controllers.android_device_lib import adb
from mobly.controllers.android_device_lib import jsonrpc_client_base
from mobly.controllers.android_device_lib.services import sl4a_service

from blueberry.controllers import derived_bt_device
from blueberry.utils import bt_constants
from blueberry.utils import ble_scan_adv_constants
from blueberry.utils import bt_test_utils

# Map for media passthrough commands and the corresponding events.
MEDIA_CMD_MAP = {
    bt_constants.CMD_MEDIA_PAUSE: bt_constants.EVENT_PAUSE_RECEIVED,
    bt_constants.CMD_MEDIA_PLAY: bt_constants.EVENT_PLAY_RECEIVED,
    bt_constants.CMD_MEDIA_SKIP_PREV: bt_constants.EVENT_SKIP_PREV_RECEIVED,
    bt_constants.CMD_MEDIA_SKIP_NEXT: bt_constants.EVENT_SKIP_NEXT_RECEIVED
}

# Timeout for track change and playback state update in second.
MEDIA_UPDATE_TIMEOUT_SEC = 3

# Timeout for the event of Media passthrough commands in second.
MEDIA_EVENT_TIMEOUT_SEC = 1

BT_CONNECTION_WAITING_TIME_SECONDS = 10

ADB_WAITING_TIME_SECONDS = 1

# Common timeout for toggle status in seconds.
COMMON_TIMEOUT_SECONDS = 5

# Local constant
_DATETIME_FMT = '%m-%d %H:%M:%S.%f'

# Interval time between ping requests in second.
PING_INTERVAL_TIME_SEC = 2

# Timeout to wait for ping success in second.
PING_TIMEOUT_SEC = 60

# A URL is used to verify internet by ping request.
TEST_URL = 'http://www.google.com'

# Timeout to wait for device boot success in second.
WAIT_FOR_DEVICE_TIMEOUT_SEC = 180


class DeviceBootError(signals.ControllerError):
    """Exception raised for Android device boot failures."""
    pass


class Error(Exception):
    """Raised when an operation in this module fails."""
    pass


class DiscoveryError(signals.ControllerError):
    """Exception raised for Bluetooth device discovery failures."""
    pass


class AndroidBluetoothDecorator(android_device.AndroidDevice):
    """Decorates an AndroidDevice with Bluetooth-specific functionality."""

    def __init__(self, ad: android_device.AndroidDevice):
        self._ad = ad
        self._user_params = None
        if not self._ad or not isinstance(self._ad, android_device.AndroidDevice):
            raise TypeError('Must apply AndroidBluetoothDecorator to an ' 'AndroidDevice')
        self.ble_advertise_callback = None
        self.regex_logcat_time = re.compile(r'(?P<datetime>[\d]{2}-[\d]{2} [\d]{2}:[\d]{2}:[\d]{2}.[\d]{3})'
                                            r'[ ]+\d+.*')
        self._regex_bt_crash = re.compile(r'Bluetooth crashed (?P<num_bt_crashes>\d+) times')

    def __getattr__(self, name: Any) -> Any:
        return getattr(self._ad, name)

    def _is_device_connected(self, mac_address):
        """Wrapper method to help with unit testability of this class."""
        return self._ad.sl4a.bluetoothIsDeviceConnected(mac_address)

    def _is_profile_connected(self, mac_address, profile):
        """Checks if the profile is connected."""
        status = None
        pri_ad = self._ad
        if profile == bt_constants.BluetoothProfile.HEADSET_CLIENT:
            status = pri_ad.sl4a.bluetoothHfpClientGetConnectionStatus(mac_address)
        elif profile == bt_constants.BluetoothProfile.A2DP_SINK:
            status = pri_ad.sl4a.bluetoothA2dpSinkGetConnectionStatus(mac_address)
        elif profile == bt_constants.BluetoothProfile.PBAP_CLIENT:
            status = pri_ad.sl4a.bluetoothPbapClientGetConnectionStatus(mac_address)
        elif profile == bt_constants.BluetoothProfile.MAP_MCE:
            connected_devices = self._ad.sl4a.bluetoothMapClientGetConnectedDevices()
            return any(mac_address in device['address'] for device in connected_devices)
        else:
            pri_ad.log.warning('The connection check for profile %s is not supported ' 'yet', profile)
            return False
        return status == bt_constants.BluetoothConnectionStatus.STATE_CONNECTED

    def _get_bluetooth_le_state(self):
        """Wrapper method to help with unit testability of this class."""
        return self._ad.sl4a.bluetoothGetLeState

    def _generate_id_by_size(self, size):
        """Generate string of random ascii letters and digits.

    Args:
      size: required size of string.

    Returns:
      String of random chars.
    """
        return ''.join(random.choice(string.ascii_letters + string.digits) for _ in range(size))

    def _wait_for_bluetooth_manager_state(self, state=None, timeout=10, threshold=5):
        """Waits for Bluetooth normalized state or normalized explicit state.

    Args:
      state: expected Bluetooth state
      timeout: max timeout threshold
      threshold: list len of bt state
    Returns:
      True if successful, false if unsuccessful.
    """
        all_states = []
        start_time = time.time()
        while time.time() < start_time + timeout:
            all_states.append(self._get_bluetooth_le_state())
            if len(all_states) >= threshold:
                # for any normalized state
                if state is None:
                    if len(all_states[-threshold:]) == 1:
                        logging.info('State normalized %s', all_states[-threshold:])
                        return True
                else:
                    # explicit check against normalized state
                    if state in all_states[-threshold:]:
                        return True
            time.sleep(0.5)
        logging.error('Bluetooth state fails to normalize'
                      if state is None else 'Failed to match bluetooth state, current state {} expected state {}'
                      .format(self._get_bluetooth_le_state(), state))
        return False

    def init_setup(self) -> None:
        """Sets up android device for bluetooth tests."""
        self._ad.services.register('sl4a', sl4a_service.Sl4aService)
        self._ad.load_snippet('mbs', 'com.google.android.mobly.snippet.bundled')
        self._ad.adb.shell('setenforce 0')

        # Adds 2 seconds waiting time to see it can fix the NullPointerException
        # when executing the following sl4a.bluetoothStartPairingHelper method.
        time.sleep(2)
        self._ad.sl4a.bluetoothStartPairingHelper()
        self.factory_reset_bluetooth()

    def sl4a_setup(self) -> None:
        """A common setup routine for android device sl4a function.

    Things this method setup:
    1. Set Bluetooth local name to random string of size 4
    2. Disable BLE background scanning.
    """

        sl4a = self._ad.sl4a
        sl4a.bluetoothStartConnectionStateChangeMonitor('')
        setup_result = sl4a.bluetoothSetLocalName(self._generate_id_by_size(4))
        if not setup_result:
            self.log.error('Failed to set device name.')
            return
        sl4a.bluetoothDisableBLE()
        bonded_devices = sl4a.bluetoothGetBondedDevices()
        for b in bonded_devices:
            self.log.info('Removing bond for device {}'.format(b['address']))
            sl4a.bluetoothUnbond(b['address'])

    def set_user_params(self, params: Dict[str, Any]) -> None:
        self._user_params = params

    def get_user_params(self) -> Dict[str, Any]:
        return self._user_params

    def is_sim_state_loaded(self) -> bool:
        """Checks if SIM state is loaded.

    Returns:
      True if SIM state is loaded else False.
    """
        state = self._ad.adb.shell('getprop gsm.sim.state').decode().strip()
        return state == 'LOADED'

    def is_package_installed(self, package_name: str) -> bool:
        """Checks if a package is installed.

    Args:
      package_name: string, a package to be checked.

    Returns:
      True if the package is installed else False.
    """
        # The package is installed if result is 1, not installed if result is 0.
        result = int(self._ad.adb.shell('pm list packages | grep -i %s$ | wc -l' % package_name))
        return bool(result)

    def connect_with_rfcomm(self, other_ad: android_device.AndroidDevice) -> bool:
        """Establishes an RFCOMM connection with other android device.

    Connects this android device (as a client) to the other android device
    (as a server).

    Args:
      other_ad: the Android device accepting the connection from this device.

    Returns:
        True if connection was successful, False if unsuccessful.
    """
        server_address = other_ad.sl4a.bluetoothGetLocalAddress()
        logging.info('Pairing and connecting devices')
        if not self._ad.sl4a.bluetoothDiscoverAndBond(server_address):
            logging.info('Failed to pair and connect devices')
            return False

        # Create RFCOMM connection
        logging.info('establishing RFCOMM connection')
        return self.orchestrate_rfcomm_connection(other_ad)

    def orchestrate_rfcomm_connection(self,
                                      other_ad: android_device.AndroidDevice,
                                      accept_timeout_ms: int = bt_constants.DEFAULT_RFCOMM_TIMEOUT_MS,
                                      uuid: Optional[Text] = None) -> bool:
        """Sets up the RFCOMM connection to another android device.

    It sets up the connection with a Bluetooth Socket connection with other
    device.

    Args:
        other_ad: the Android device accepting the connection from this device.
        accept_timeout_ms: the timeout in ms for the connection.
        uuid: universally unique identifier.

    Returns:
        True if connection was successful, False if unsuccessful.
    """
        if uuid is None:
            uuid = bt_constants.BT_RFCOMM_UUIDS['default_uuid']
        other_ad.sl4a.bluetoothStartPairingHelper()
        self._ad.sl4a.bluetoothStartPairingHelper()
        other_ad.sl4a.bluetoothSocketConnBeginAcceptThreadUuid(uuid, accept_timeout_ms)
        self._ad.sl4a.bluetoothSocketConnBeginConnectThreadUuid(other_ad.sl4a.bluetoothGetLocalAddress(), uuid)

        end_time = time.time() + bt_constants.BT_DEFAULT_TIMEOUT_SECONDS
        test_result = True

        while time.time() < end_time:
            number_socket_connections = len(other_ad.sl4a.bluetoothSocketConnActiveConnections())
            connected = number_socket_connections > 0
            if connected:
                test_result = True
                other_ad.log.info('Bluetooth socket Client Connection Active')
                break
            else:
                test_result = False
            time.sleep(1)
        if not test_result:
            other_ad.log.error('Failed to establish a Bluetooth socket connection')
            return False
        return True

    def wait_for_discovery_success(self, mac_address: str, timeout: float = 30) -> float:
        """Waits for a device to be discovered by AndroidDevice.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      timeout: Number of seconds to wait for device discovery.

    Returns:
      discovery_time: The time it takes to pair in seconds.

    Raises:
      DiscoveryError
    """
        device_start_time = self.get_device_time()
        start_time = time.time()
        event_name = f'Discovery{mac_address}'
        try:
            self._ad.ed.wait_for_event(event_name, lambda x: x['data']['Status'], timeout)
            discovery_time = time.time() - start_time
            return discovery_time

        except queue.Empty:
            # TODO(user): Remove this check when this bug is fixed.
            if self.logcat_filter(device_start_time, event_name):
                self._ad.log.info('Actually the event "%s" was posted within %d seconds.', event_name, timeout)
                return timeout
            raise DiscoveryError('Failed to discover device %s after %d seconds' % (mac_address, timeout))

    def wait_for_pairing_success(self, mac_address: str, timeout: float = 30) -> float:
        """Waits for a device to pair with the AndroidDevice.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      timeout: Number of seconds to wait for the devices to pair.

    Returns:
      pairing_time: The time it takes to pair in seconds.

    Raises:
      ControllerError
    """
        start_time = time.time()
        try:
            self._ad.ed.wait_for_event('Bond%s' % mac_address, lambda x: x['data']['Status'], timeout)
            pairing_time = time.time() - start_time
            return pairing_time

        except queue.Empty:
            raise signals.ControllerError('Failed to bond with device %s after %d seconds' % (mac_address, timeout))

    def wait_for_connection_success(self, mac_address: str, timeout: int = 30) -> float:
        """Waits for a device to connect with the AndroidDevice.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      timeout: Number of seconds to wait for the devices to connect.

    Returns:
      connection_time: The time it takes to connect in seconds.

    Raises:
      ControllerError
    """
        start_time = time.time()
        end_time = start_time + timeout
        while time.time() < end_time:
            if self._is_device_connected(mac_address):
                connection_time = (time.time() - start_time)
                logging.info('Connected device %s in %d seconds', mac_address, connection_time)
                return connection_time

        raise signals.ControllerError('Failed to connect device within %d seconds.' % timeout)

    def factory_reset_bluetooth(self) -> None:
        """Factory resets Bluetooth on an AndroidDevice."""

        logging.info('Factory resetting Bluetooth for AndroidDevice.')
        self._ad.sl4a.bluetoothToggleState(True)
        paired_devices = self._ad.mbs.btGetPairedDevices()
        for device in paired_devices:
            self._ad.sl4a.bluetoothUnbond(device['Address'])
        self._ad.sl4a.bluetoothFactoryReset()
        self._wait_for_bluetooth_manager_state()
        self.wait_for_bluetooth_toggle_state(True)

    def get_device_info(self) -> Dict[str, Any]:
        """Gets the configuration info of an AndroidDevice.

    Returns:
      dict, A dictionary mapping metric keys to their respective values.
    """

        device_info = {
            'device_class': 'AndroidDevice',
            'device_model': self._ad.device_info['model'],
            'hardware_version': self._ad.adb.getprop('ro.boot.hardware.revision'),
            'software_version': self._ad.build_info['build_id'],
            'android_build_type': self._ad.build_info['build_type'],
            'android_build_number': self._ad.adb.getprop('ro.build.version.incremental'),
            'android_release_id': self._ad.build_info['build_id']
        }

        return device_info

    def pair_and_connect_bluetooth(self, mac_address: str, attempts: int = 3,
                                   enable_pairing_retry: bool = True) -> Tuple[float, float]:
        """Pairs and connects an AndroidDevice with a peripheral Bluetooth device.

    Ensures that an AndroidDevice is paired and connected to a peripheral
    device. If the devices are already connected, does nothing. If
    the devices are paired but not connected, connects the devices. If the
    devices are neither paired nor connected, this method pairs and connects the
    devices.

    Suggests to use the retry mechanism on Discovery because it sometimes fail
    even if the devices are testing in shielding. In order to avoid the remote
    device may not respond a incoming pairing request causing to bonding failure
    , it suggests to retry pairing too.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      attempts: Number of attempts to discover and pair the peripheral device.
      enable_pairing_retry: Bool to control whether the retry mechanism is used
          on bonding failure, it's enabled if True.

    Returns:
      pairing_time: The time, in seconds, it takes to pair the devices.
      connection_time: The time, in seconds, it takes to connect the
      devices after pairing is completed.

    Raises:
      DiscoveryError: Raised if failed to discover the peripheral device.
      ControllerError: Raised if failed to bond the peripheral device.
    """

        connected = self._is_device_connected(mac_address)
        pairing_time = 0
        connection_time = 0
        if connected:
            logging.info('Device %s already paired and connected', mac_address)
            return pairing_time, connection_time

        paired_devices = [device['address'] for device in self._ad.sl4a.bluetoothGetBondedDevices()]
        if mac_address in paired_devices:
            self._ad.sl4a.bluetoothConnectBonded(mac_address)
            return pairing_time, self.wait_for_connection_success(mac_address)

        logging.info('Initiate pairing to the device "%s".', mac_address)
        for i in range(attempts):
            self._ad.sl4a.bluetoothDiscoverAndBond(mac_address)
            try:
                self.wait_for_discovery_success(mac_address)
                pairing_time = self.wait_for_pairing_success(mac_address)
                break
            except DiscoveryError:
                if i + 1 < attempts:
                    logging.error('Failed to find the device "%s" on Attempt %d. '
                                  'Retrying discovery...', mac_address, i + 1)
                    continue
                raise DiscoveryError('Failed to find the device "%s".' % mac_address)
            except signals.ControllerError:
                if i + 1 < attempts and enable_pairing_retry:
                    logging.error('Failed to bond the device "%s" on Attempt %d. '
                                  'Retrying pairing...', mac_address, i + 1)
                    continue
                raise signals.ControllerError('Failed to bond the device "%s".' % mac_address)

        connection_time = self.wait_for_connection_success(mac_address)
        return pairing_time, connection_time

    def disconnect_bluetooth(self, mac_address: str, timeout: float = 30) -> float:
        """Disconnects Bluetooth between an AndroidDevice and peripheral device.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      timeout: Number of seconds to wait for the devices to disconnect the
      peripheral device.

    Returns:
      disconnection_time: The time, in seconds, it takes to disconnect the
      peripheral device.

    Raises:
      ControllerError: Raised if failed to disconnect the peripheral device.
    """
        if not self._is_device_connected(mac_address):
            logging.info('Device %s already disconnected', mac_address)
            return 0

        self._ad.sl4a.bluetoothDisconnectConnected(mac_address)
        start_time = time.time()
        end_time = time.time() + timeout
        while time.time() < end_time:
            connected = self._is_device_connected(mac_address)
            if not connected:
                logging.info('Device %s disconnected successfully.', mac_address)
                return time.time() - start_time

        raise signals.ControllerError('Failed to disconnect device within %d seconds.' % timeout)

    def connect_bluetooth(self, mac_address: str, timeout: float = 30) -> float:
        """Connects Bluetooth between an AndroidDevice and peripheral device.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      timeout: Number of seconds to wait for the devices to connect the
      peripheral device.

    Returns:
      connection_time: The time, in seconds, it takes to connect the
      peripheral device.

    Raises:
      ControllerError: Raised if failed to connect the peripheral device.
    """
        if self._is_device_connected(mac_address):
            logging.info('Device %s already connected', mac_address)
            return 0

        self._ad.sl4a.bluetoothConnectBonded(mac_address)
        connect_time = self.wait_for_connection_success(mac_address)

        return connect_time

    def activate_pairing_mode(self) -> None:
        """Activates pairing mode on an AndroidDevice."""
        logging.info('Activating pairing mode on AndroidDevice.')
        self._ad.sl4a.bluetoothMakeDiscoverable()
        self._ad.sl4a.bluetoothStartPairingHelper()

    def activate_ble_pairing_mode(self) -> None:
        """Activates BLE pairing mode on an AndroidDevice."""
        self.ble_advertise_callback = self._ad.sl4a.bleGenBleAdvertiseCallback()
        self._ad.sl4a.bleSetAdvertiseDataIncludeDeviceName(True)
        # Sets advertise mode to low latency.
        self._ad.sl4a.bleSetAdvertiseSettingsAdvertiseMode(ble_scan_adv_constants.BleAdvertiseSettingsMode.LOW_LATENCY)
        self._ad.sl4a.bleSetAdvertiseSettingsIsConnectable(True)
        # Sets TX power level to High.
        self._ad.sl4a.bleSetAdvertiseSettingsTxPowerLevel(ble_scan_adv_constants.BleAdvertiseSettingsTxPower.HIGH)
        advertise_data = self._ad.sl4a.bleBuildAdvertiseData()
        advertise_settings = self._ad.sl4a.bleBuildAdvertiseSettings()
        logging.info('Activating BLE pairing mode on AndroidDevice.')
        self._ad.sl4a.bleStartBleAdvertising(self.ble_advertise_callback, advertise_data, advertise_settings)

    def deactivate_ble_pairing_mode(self) -> None:
        """Deactivates BLE pairing mode on an AndroidDevice."""
        if not self.ble_advertise_callback:
            self._ad.log.debug('BLE pairing mode is not activated.')
            return
        logging.info('Deactivating BLE pairing mode on AndroidDevice.')
        self._ad.sl4a.bleStopBleAdvertising(self.ble_advertise_callback)
        self.ble_advertise_callback = None

    def get_bluetooth_mac_address(self) -> str:
        """Gets Bluetooth mac address of an AndroidDevice."""
        logging.info('Getting Bluetooth mac address for AndroidDevice.')
        mac_address = self._ad.sl4a.bluetoothGetLocalAddress()
        logging.info('Bluetooth mac address of AndroidDevice: %s', mac_address)
        return mac_address

    def scan_and_get_ble_device_address(self, device_name: str, timeout_sec: float = 30) -> str:
        """Searchs a BLE device by BLE scanner and returns it's BLE mac address.

    Args:
      device_name: string, the name of BLE device.
      timeout_sec: int, number of seconds to wait for finding the advertisement.

    Returns:
      String of the BLE mac address.

    Raises:
      ControllerError: Raised if failed to get the BLE device address
    """
        filter_list = self._ad.sl4a.bleGenFilterList()
        scan_settings = self._ad.sl4a.bleBuildScanSetting()
        scan_callback = self._ad.sl4a.bleGenScanCallback()
        self._ad.sl4a.bleSetScanFilterDeviceName(device_name)
        self._ad.sl4a.bleBuildScanFilter(filter_list)
        self._ad.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
        try:
            event = self._ad.ed.pop_event('BleScan%sonScanResults' % scan_callback, timeout_sec)
        except queue.Empty:
            raise signals.ControllerError(
                'Timed out %ds after waiting for phone finding BLE device: %s.' % (timeout_sec, device_name))
        finally:
            self._ad.sl4a.bleStopBleScan(scan_callback)
        return event['data']['Result']['deviceInfo']['address']

    def get_device_name(self) -> str:
        """Gets Bluetooth device name of an AndroidDevice."""
        logging.info('Getting Bluetooth device name for AndroidDevice.')
        device_name = self._ad.sl4a.bluetoothGetLocalName()
        logging.info('Bluetooth device name of AndroidDevice: %s', device_name)
        return device_name

    def is_bluetooth_sco_on(self) -> bool:
        """Checks whether communications use Bluetooth SCO."""
        cmd = 'dumpsys bluetooth_manager | grep "isBluetoothScoOn"'
        get_status = self._ad.adb.shell(cmd)
        if isinstance(get_status, bytes):
            get_status = get_status.decode()
        return 'true' in get_status

    def connect_with_profile(self, snd_ad_mac_address: str, profile: bt_constants.BluetoothProfile) -> bool:
        """Connects with the profile.

    The connection can only be completed after the bluetooth devices are paired.
    To connected with the profile, the bluetooth connection policy is set to
    forbidden first and then set to allowed. The paired bluetooth devices will
    start to make connection. The connection time could be long. The waitting
    time is set to BT_CONNECTION_WAITING_TIME_SECONDS (currently 10 seconds).

    Args:
      snd_ad_mac_address: the mac address of the device accepting connection.
      profile: the profiles to be set

    Returns:
      The profile connection succeed/fail
    """
        if profile == bt_constants.BluetoothProfile.MAP_MCE:
            self._ad.sl4a.bluetoothMapClientConnect(snd_ad_mac_address)
        elif profile == bt_constants.BluetoothProfile.PBAP_CLIENT:
            self.set_profile_policy(snd_ad_mac_address, profile,
                                    bt_constants.BluetoothConnectionPolicy.CONNECTION_POLICY_ALLOWED)
            self._ad.sl4a.bluetoothPbapClientConnect(snd_ad_mac_address)
        else:
            self.set_profile_policy(snd_ad_mac_address, profile,
                                    bt_constants.BluetoothConnectionPolicy.CONNECTION_POLICY_FORBIDDEN)
            self.set_profile_policy(snd_ad_mac_address, profile,
                                    bt_constants.BluetoothConnectionPolicy.CONNECTION_POLICY_ALLOWED)
            self._ad.sl4a.bluetoothConnectBonded(snd_ad_mac_address)
        time.sleep(BT_CONNECTION_WAITING_TIME_SECONDS)
        is_connected = self._is_profile_connected(snd_ad_mac_address, profile)
        self.log.info('The connection between %s and %s for profile %s succeed: %s', self.serial, snd_ad_mac_address,
                      profile, is_connected)
        return is_connected

    def connect_to_snd_with_profile(self,
                                    snd_ad: android_device.AndroidDevice,
                                    profile: bt_constants.BluetoothProfile,
                                    attempts: int = 5) -> bool:
        """Connects pri android device to snd android device with profile.

    Args:
        snd_ad: android device accepting connection
        profile: the profile to be connected
        attempts: Number of attempts to try until failure.

    Returns:
        Boolean of connecting result
    """
        pri_ad = self._ad
        curr_attempts = 0
        snd_ad_mac_address = snd_ad.sl4a.bluetoothGetLocalAddress()
        if not self.is_bt_paired(snd_ad_mac_address):
            self.log.error('Devices %s and %s not paired before connecting', self.serial, snd_ad.serial)
            return False
        while curr_attempts < attempts:
            curr_attempts += 1
            self.log.info('Connection of profile %s at curr attempt %d (total %d)', profile, curr_attempts, attempts)
            if self.connect_with_profile(snd_ad_mac_address, profile):
                self.log.info('Connection between devices %s and %s succeeds at %d try', pri_ad.serial, snd_ad.serial,
                              curr_attempts)
                return True
        self.log.error('Connection of profile %s failed after %d attempts', profile, attempts)
        return False

    def is_bt_paired(self, mac_address: str) -> bool:
        """Check if the bluetooth device with mac_address is paired to ad.

    Args:
      mac_address: the mac address of the bluetooth device for pairing

    Returns:
      True if they are paired
    """
        bonded_info = self._ad.sl4a.bluetoothGetBondedDevices()
        return mac_address in [info['address'] for info in bonded_info]

    def is_a2dp_sink_connected(self, mac_address: str) -> bool:
        """Checks if the Android device connects to a A2DP sink device.

    Args:
      mac_address: String, Bluetooth MAC address of the A2DP sink device.

    Returns:
      True if connected else False.
    """
        connected_devices = self._ad.sl4a.bluetoothA2dpGetConnectedDevices()
        return mac_address in [d['address'] for d in connected_devices]

    def hfp_connect(self, ag_ad: android_device.AndroidDevice) -> bool:
        """Hfp connecting hf android device to ag android device.

    The android device should support the Headset Client profile. For example,
    the android device with git_master-bds-dev build.

    Args:
        ag_ad: Audio Gateway (ag) android device

    Returns:
        Boolean of connecting result
    """
        return self.connect_to_snd_with_profile(ag_ad, bt_constants.BluetoothProfile.HEADSET_CLIENT)

    def a2dp_sink_connect(self, src_ad: android_device.AndroidDevice) -> bool:
        """Connects pri android device to secondary android device.

    The android device should support the A2dp Sink profile. For example, the
    android device with git_master-bds-dev build.

    Args:
      src_ad: A2dp source android device

    Returns:
      Boolean of connecting result
    """
        return self.connect_to_snd_with_profile(src_ad, bt_constants.BluetoothProfile.A2DP_SINK)

    def map_connect(self, map_ad: android_device.AndroidDevice) -> bool:
        """Connects primary device to secondary device via MAP MCE profile.

    The primary device should support the MAP MCE profile. For example,
    the android device with git_master-bds-dev build.

    Args:
        map_ad: AndroidDevice, a android device supporting MAP profile.

    Returns:
        Boolean of connecting result
    """
        return self.connect_to_snd_with_profile(map_ad, bt_constants.BluetoothProfile.MAP_MCE)

    def map_disconnect(self, bluetooth_address: str) -> bool:
        """Disconnects a MAP MSE device with specified Bluetooth MAC address.

    Args:
      bluetooth_address: a connected device's bluetooth address.

    Returns:
      True if the device is disconnected else False.
    """
        self._ad.sl4a.bluetoothMapClientDisconnect(bluetooth_address)
        return bt_test_utils.wait_until(
            timeout_sec=COMMON_TIMEOUT_SECONDS,
            condition_func=self._is_profile_connected,
            func_args=[bluetooth_address, bt_constants.BluetoothProfile.MAP_MCE],
            expected_value=False)

    def pbap_connect(self, pbap_ad: android_device.AndroidDevice) -> bool:
        """Connects primary device to secondary device via PBAP client profile.

    The primary device should support the PBAP client profile. For example,
    the android device with git_master-bds-dev build.

    Args:
        pbap_ad: AndroidDevice, a android device supporting PBAP profile.

    Returns:
        Boolean of connecting result
    """
        return self.connect_to_snd_with_profile(pbap_ad, bt_constants.BluetoothProfile.PBAP_CLIENT)

    def set_bluetooth_tethering(self, status_enabled: bool) -> None:
        """Sets Bluetooth tethering to be specific status.

    Args:
      status_enabled: Bool, Bluetooth tethering will be set to enable if True,
          else disable.
    """
        if self._ad.sl4a.bluetoothPanIsTetheringOn() == status_enabled:
            self._ad.log.info('Already %s Bluetooth tethering.' % ('enabled' if status_enabled else 'disabled'))
            return

        self._ad.log.info('%s Bluetooth tethering.' % ('Enable' if status_enabled else 'Disable'))
        self._ad.sl4a.bluetoothPanSetBluetoothTethering(status_enabled)

        bt_test_utils.wait_until(
            timeout_sec=COMMON_TIMEOUT_SECONDS,
            condition_func=self._ad.sl4a.bluetoothPanIsTetheringOn,
            func_args=[],
            expected_value=status_enabled,
            exception=signals.ControllerError('Failed to %s Bluetooth tethering.' % ('enable'
                                                                                     if status_enabled else 'disable')))

    def set_profile_policy(self, snd_ad_mac_address: str, profile: bt_constants.BluetoothProfile,
                           policy: bt_constants.BluetoothConnectionPolicy) -> None:
        """Sets policy of the profile car related profiles to OFF.

    This avoids autoconnect being triggered randomly. The use of this function
    is encouraged when you're testing individual profiles in isolation.

    Args:
      snd_ad_mac_address: the mac address of the device accepting connection.
      profile: the profiles to be set
      policy: the policy value to be set
    """
        pri_ad = self._ad
        pri_ad_local_name = pri_ad.sl4a.bluetoothGetLocalName()
        pri_ad.log.info('Sets profile %s on %s for %s to policy %s', profile, pri_ad_local_name, snd_ad_mac_address,
                        policy)
        if profile == bt_constants.BluetoothProfile.A2DP:
            pri_ad.sl4a.bluetoothA2dpSetPriority(snd_ad_mac_address, policy.value)
        elif profile == bt_constants.BluetoothProfile.A2DP_SINK:
            pri_ad.sl4a.bluetoothA2dpSinkSetPriority(snd_ad_mac_address, policy.value)
        elif profile == bt_constants.BluetoothProfile.HEADSET_CLIENT:
            pri_ad.sl4a.bluetoothHfpClientSetPriority(snd_ad_mac_address, policy.value)
        elif profile == bt_constants.BluetoothProfile.PBAP_CLIENT:
            pri_ad.sl4a.bluetoothPbapClientSetPriority(snd_ad_mac_address, policy.value)
        elif profile == bt_constants.BluetoothProfile.HID_HOST:
            pri_ad.sl4a.bluetoothHidSetPriority(snd_ad_mac_address, policy.value)
        else:
            pri_ad.log.error('Profile %s not yet supported for policy settings', profile)

    def set_profiles_policy(self, snd_ad: android_device.AndroidDevice,
                            profile_list: Sequence[bt_constants.BluetoothProfile],
                            policy: bt_constants.BluetoothConnectionPolicy) -> None:
        """Sets the policy of said profile(s) on pri_ad for snd_ad.

    Args:
      snd_ad: android device accepting connection
      profile_list: list of the profiles to be set
      policy: the policy to be set
    """
        mac_address = snd_ad.sl4a.bluetoothGetLocalAddress()
        for profile in profile_list:
            self.set_profile_policy(mac_address, profile, policy)

    def set_profiles_policy_off(self, snd_ad: android_device.AndroidDevice,
                                profile_list: Sequence[bt_constants.BluetoothProfile]) -> None:
        """Sets policy of the profiles to OFF.

    This avoids autoconnect being triggered randomly. The use of this function
    is encouraged when you're testing individual profiles in isolation

    Args:
      snd_ad: android device accepting connection
      profile_list: list of the profiles to be turned off
    """
        self.set_profiles_policy(snd_ad, profile_list,
                                 bt_constants.BluetoothConnectionPolicy.CONNECTION_POLICY_FORBIDDEN)

    def wait_for_call_state(self,
                            call_state: Union[int, bt_constants.CallState],
                            timeout_sec: float,
                            wait_interval: int = 3) -> bool:
        """Waits for call state of the device to be changed.

    Args:
      call_state: int, the expected call state. Call state values are:
        0: IDLE
        1: RINGING
        2: OFFHOOK
      timeout_sec: int, number of seconds of expiration time
      wait_interval: int, number of seconds of waiting in each cycle

    Returns:
      True if the call state has been changed else False.
    """
        # TODO(user): Force external call to use CallState instead of int
        if isinstance(call_state, bt_constants.CallState):
            call_state = call_state.value
        expiration_time = time.time() + timeout_sec
        which_cycle = 1
        while time.time() < expiration_time:
            # Waits for the call state change in every cycle.
            time.sleep(wait_interval)
            self._ad.log.info('in cycle %d of waiting for call state %d', which_cycle, call_state)
            if call_state == self._ad.mbs.getTelephonyCallState():
                return True
        self._ad.log.info('The call state did not change to %d before timeout', call_state)
        return False

    def add_call_log(self, call_log_type: Union[int, bt_constants.CallLogType], phone_number: str,
                     call_time: int) -> None:
        """Add call number and time to specified log.

    Args:
      call_log_type: int, number of call log type. Call log type values are:
        1: Incoming call
        2: Outgoing call
        3: Missed call
      phone_number: string, phone number to be added in call log.
      call_time: int, call time to be added in call log.

    Returns:
      None
    """
        # TODO(user): Force external call to use CallLogType instead of int
        if isinstance(call_log_type, bt_constants.CallLogType):
            call_log_type = call_log_type.value
        new_call_log = {}
        new_call_log['type'] = str(call_log_type)
        new_call_log['number'] = phone_number
        new_call_log['time'] = str(call_time)
        self._ad.sl4a.callLogsPut(new_call_log)

    def get_call_volume(self) -> int:
        """Gets current call volume of an AndroidDevice when Bluetooth SCO On.

    Returns:
      An integer specifying the number of current call volume level.

    Raises:
      Error: If the pattern search failed.
    """
        cmd = 'dumpsys audio | grep "STREAM_BLUETOOTH_SCO" | tail -1'
        out = self._ad.adb.shell(cmd).decode()
        pattern = r'(?<=SCO index:)\d+'
        result = re.search(pattern, out)
        if result is None:
            raise Error(f'Pattern "{pattern}" search failed, dump output: {out}')
        return int(result.group())

    def make_phone_call(self, callee: android_device.AndroidDevice, timeout_sec: float = 30) -> None:
        """Make a phone call to callee and check if callee is ringing.

    Args:
      callee: AndroidDevice, The callee in the phone call.
      timeout_sec: int, number of seconds to wait for the callee ringing.

    Raises:
      TestError
    """
        self._ad.sl4a.telecomCallNumber(callee.dimensions['phone_number'])
        is_ringing = callee.wait_for_call_state(bt_constants.CALL_STATE_RINGING, timeout_sec)
        if not is_ringing:
            raise signals.TestError('Timed out after %ds waiting for call state: RINGING' % timeout_sec)

    def wait_for_disconnection_success(self, mac_address: str, timeout: float = 30) -> float:
        """Waits for a device to connect with the AndroidDevice.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      timeout: Number of seconds to wait for the devices to connect.

    Returns:
      connection_time: The time it takes to connect in seconds.

    Raises:
      ControllerError
    """
        start_time = time.time()
        end_time = start_time + timeout
        while time.time() < end_time:
            if not self._ad.sl4a.bluetoothIsDeviceConnected(mac_address):
                disconnection_time = (time.time() - start_time)
                logging.info('Disconnected device %s in %d seconds', mac_address, disconnection_time)
                return disconnection_time

        raise signals.ControllerError('Failed to disconnect device within %d seconds.' % timeout)

    def first_pair_and_connect_bluetooth(self, bt_device: Any) -> None:
        """Pairs and connects an AndroidDevice with a Bluetooth device.

    This method does factory reset bluetooth first and then pairs and connects
    the devices.

    Args:
      bt_device: A device object which implements basic Bluetooth function
        related methods.

    Returns:
      None
    """
        bt_device.factory_reset_bluetooth()
        mac_address = bt_device.get_bluetooth_mac_address()
        bt_device.activate_pairing_mode()
        self.pair_and_connect_bluetooth(mac_address)

    def get_device_time(self) -> str:
        """Get device epoch time and transfer to logcat timestamp format.

    Returns:
      String of the device time.
    """
        return self._ad.adb.shell('date +"%m-%d %H:%M:%S.000"').decode().splitlines()[0]

    def logcat_filter(self, start_time: str, text_filter: str = '') -> str:
        """Returns logcat after a given time.

    This method calls from the android_device logcat service file and filters
    all logcat line prior to the start_time.

    Args:
      start_time: start time in string format of _DATETIME_FMT.
      text_filter: only return logcat lines that include this string.

    Returns:
      A logcat output.

    Raises:
      ValueError Exception if start_time is invalid format.
    """
        try:
            start_time_conv = datetime.datetime.strptime(start_time, _DATETIME_FMT)
        except ValueError as ex:
            logging.error('Invalid time format!')
            raise ex
        logcat_response = ''
        with open(self._ad.adb_logcat_file_path, 'r', errors='replace') \
            as logcat_file:
            post_start_time = False
            for line in logcat_file:
                match = self.regex_logcat_time.match(line)
                if match:
                    if (datetime.datetime.strptime(match.group('datetime'), _DATETIME_FMT) >= start_time_conv):
                        post_start_time = True
                    if post_start_time and line.find(text_filter) >= 0:
                        logcat_response += line
        return logcat_response

    def logcat_filter_message(self, current_time: str, text: str = '') -> str:
        """DEPRECATED Builds the logcat command.

    This method builds the logcat command to check for a specified log
    message after the specified time. If text=None, the logcat returned will be
    unfiltered.

    Args:
      current_time: time cutoff for grepping for the specified
        message, format = ('%m-%d %H:%M:%S.000').
      text: text to search for.

    Returns:
      The response of the logcat filter.
    """
        return self.logcat_filter(current_time, text)

    def send_media_passthrough_cmd(self, command: str,
                                   event_receiver: Optional[android_device.AndroidDevice] = None) -> None:
        """Sends a media passthrough command.

    Args:
      command: string, media passthrough command.
      event_receiver: AndroidDevice, a device which starts
          BluetoothSL4AAudioSrcMBS.

    Raises:
      signals.ControllerError: raised if the event is not received.
    """
        if event_receiver is None:
            event_receiver = self._ad
        self._ad.log.info('Sending Media Passthough: %s' % command)
        self._ad.sl4a.bluetoothMediaPassthrough(command)
        if not event_receiver:
            event_receiver = self._ad
        try:
            event_receiver.ed.pop_event(MEDIA_CMD_MAP[command], MEDIA_EVENT_TIMEOUT_SEC)
        except queue.Empty:
            raise signals.ControllerError(
                'Device "%s" failed to receive the event "%s" '
                'when the command "%s" was sent.' % (event_receiver.serial, MEDIA_CMD_MAP[command], command))

    def pause(self) -> None:
        """Sends the AVRCP command "pause"."""
        self.send_media_passthrough_cmd(bt_constants.CMD_MEDIA_PAUSE)

    def play(self) -> None:
        """Sends the AVRCP command "play"."""
        self.send_media_passthrough_cmd(bt_constants.CMD_MEDIA_PLAY)

    def track_previous(self) -> None:
        """Sends the AVRCP command "skipPrev"."""
        self.send_media_passthrough_cmd(bt_constants.CMD_MEDIA_SKIP_PREV)

    def track_next(self) -> None:
        """Sends the AVRCP command "skipNext"."""
        self.send_media_passthrough_cmd(bt_constants.CMD_MEDIA_SKIP_NEXT)

    def get_current_track_info(self) -> Dict[str, Any]:
        """Returns Dict (Media metadata) representing the current track."""
        return self._ad.sl4a.bluetoothMediaGetCurrentMediaMetaData()

    def get_current_playback_state(self) -> int:
        """Returns Integer representing the current playback state."""
        return self._ad.sl4a.bluetoothMediaGetCurrentPlaybackState()['state']

    def verify_playback_state_changed(self, expected_state: str, exception: Optional[Exception] = None) -> bool:
        """Verifies the playback state is changed to be the expected state.

    Args:
      expected_state: string, the changed state as expected.
      exception: Exception, raised when the state is not changed if needed.
    """
        bt_test_utils.wait_until(
            timeout_sec=MEDIA_UPDATE_TIMEOUT_SEC,
            condition_func=self.get_current_playback_state,
            func_args=[],
            expected_value=expected_state,
            exception=exception,
            interval_sec=1)

    def verify_current_track_changed(self, expected_track: str, exception: Optional[Exception] = None) -> bool:
        """Verifies the Now playing track is changed to be the expected track.

    Args:
      expected_track: string, the changed track as expected.
      exception: Exception, raised when the track is not changed if needed.
    """
        bt_test_utils.wait_until(
            timeout_sec=MEDIA_UPDATE_TIMEOUT_SEC,
            condition_func=self.get_current_track_info,
            func_args=[],
            expected_value=expected_track,
            exception=exception,
            interval_sec=1)

    def verify_avrcp_event(self, event_name: bt_constants.AvrcpEvent, check_time: str, timeout_sec: float = 20) -> bool:
        """Verifies that an AVRCP event was received by an AndroidDevice.

    Checks logcat to verify that an AVRCP event was received after a given
    time.

    Args:
      event_name: enum, AVRCP event name. Currently supports play, pause,
      track_previous, and track_next.
      check_time: string, The earliest desired cutoff time to check the logcat.
      Must be in format '%m-%d %H:%M:%S.000'. Use
      datetime.datetime.now().strftime('%m-%d %H:%M:%S.%f') to get current time
      in this format.
      timeout_sec: int, Number of seconds to wait for the specified AVRCP event
        be found in logcat.

    Raises:
      TestError

    Returns:
      True if the event was received.
    """
        avrcp_events = [
            'State:NOT_PLAYING->PLAYING', 'State:PLAYING->NOT_PLAYING', 'sendMediaKeyEvent: keyEvent=76',
            'sendMediaKeyEvent: keyEvent=75'
        ]
        if event_name.value not in avrcp_events:
            raise signals.TestError('An unexpected AVRCP event is specified.')

        end_time = time.time() + timeout_sec
        while time.time() < end_time:
            if self.logcat_filter_message(check_time, event_name.value):
                logging.info('%s event received successfully.', event_name)
                return True
            time.sleep(1)
        logging.error('AndroidDevice failed to receive %s event.', event_name)
        logging.info('Logcat:\n%s', self.logcat_filter_message(check_time))
        return False

    def add_google_account(self, retries: int = 5) -> bool:
        """Login Google account.

    Args:
        retries: int, the number of retries.

    Returns:
      True if account is added successfully.

    Raises:
      TestError
    """
        for _ in range(retries):
            output = self._ad.adb.shell(
                'am instrument -w -e account "%s" -e password '
                '"%s" -e sync true -e wait-for-checkin false '
                'com.google.android.tradefed.account/.AddAccount' %
                (self._ad.dimensions['google_account'], self._ad.dimensions['google_account_password'])).decode()
            if 'result=SUCCESS' in output:
                logging.info('Google account is added successfully')
                time.sleep(3)  # Wait for account to steady state
                return True
        raise signals.TestError('Failed to add google account: %s' % output)

    def remove_google_account(self, retries: int = 5) -> bool:
        """Remove Google account.

    Args:
        retries: int, the number of retries.

    Returns:
      True if account is removed successfully.

    Raises:
      TestError
    """
        for _ in range(retries):
            output = self._ad.adb.shell('am instrument -w com.google.android.tradefed.account/.RemoveAccounts').decode()
            if 'result=SUCCESS' in output:
                logging.info('Google account is removed successfully')
                return True
            time.sleep(1)  # Buffer between retries.
        raise signals.TestError('Failed to remove google account: %s' % output)

    def detect_and_pull_ssrdump(self, ramdump_type: str = 'ramdump_bt') -> bool:
        """Detect and pull RAMDUMP log.

    Args:
      ramdump_type: str, the partial of file names to search for in ramdump
        files path. 'ramdump_bt' is used for searching Bluetooth ramdump log
        files.

    Returns:
      True if there is a file with file name matching the ramdump type.
    """
        files = self._ad.adb.shell('ls %s' % bt_constants.RAMDUMP_PATH).decode()
        if ramdump_type in files:
            logging.info('RAMDUMP is found.')
            log_name_timestamp = mobly_logger.get_log_file_timestamp()
            destination = os.path.join(self._ad.log_path, 'RamdumpLogs', log_name_timestamp)
            utils.create_dir(destination)
            self._ad.adb.pull([bt_constants.RAMDUMP_PATH, destination])
            return True
        return False

    def get_bt_num_of_crashes(self) -> int:
        """Get number of Bluetooth crash times from bluetooth_manager.

    Returns:
      Number of Bluetooth crashed times.
    """
        out = self._regex_bt_crash.search(self._ad.adb.shell('dumpsys bluetooth_manager').decode())
        # TODO(user): Need to consider the case "out=None" when miss in
        # matching
        return int(out.group('num_bt_crashes'))

    def clean_ssrdump(self) -> None:
        """Clean RAMDUMP log.

    Returns:
      None
    """
        self._ad.adb.shell('rm -rf %s/*' % bt_constants.RAMDUMP_PATH)

    def set_target(self, bt_device: derived_bt_device.BtDevice) -> None:
        """Allows for use to get target device object for target interaction."""
        self._target_device = bt_device

    def wait_for_hsp_connection_state(self,
                                      mac_address: str,
                                      connected: bool,
                                      raise_error: bool = True,
                                      timeout_sec: float = 30) -> bool:
        """Waits for HSP connection to be in a expected state on Android device.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      connected: True if HSP connection state is connected as expected.
      raise_error: Error will be raised if True.
      timeout_sec: Number of seconds to wait for HSP connection state change.

    Returns:
      True if HSP connection state is the expected state.
    """
        expected_state = bt_constants.BluetoothConnectionStatus.STATE_DISCONNECTED
        if connected:
            expected_state = bt_constants.BluetoothConnectionStatus.STATE_CONNECTED
        msg = ('Failed to %s the device "%s" within %d seconds via HSP.' % ('connect' if connected else 'disconnect',
                                                                            mac_address, timeout_sec))
        return bt_test_utils.wait_until(
            timeout_sec=timeout_sec,
            condition_func=self._ad.sl4a.bluetoothHspGetConnectionStatus,
            func_args=[mac_address],
            expected_value=expected_state,
            exception=signals.TestError(msg) if raise_error else None)

    def wait_for_bluetooth_toggle_state(self, enabled: bool = True, timeout_sec: float = 30) -> bool:
        """Waits for Bluetooth to be in an expected state.

    Args:
      enabled: True if Bluetooth status is enabled as expected.
      timeout_sec: Number of seconds to wait for Bluetooth to be in the expected
          state.
    """
        bt_test_utils.wait_until(
            timeout_sec=timeout_sec,
            condition_func=self._ad.mbs.btIsEnabled,
            func_args=[],
            expected_value=enabled,
            exception=signals.TestError('Bluetooth is not %s within %d seconds on the device "%s".' %
                                        ('enabled' if enabled else 'disabled', timeout_sec, self._ad.serial)))

    def wait_for_a2dp_connection_state(self,
                                       mac_address: str,
                                       connected: bool,
                                       raise_error: bool = True,
                                       timeout_sec: float = 30) -> bool:
        """Waits for A2DP connection to be in a expected state on Android device.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      connected: True if A2DP connection state is connected as expected.
      raise_error: Error will be raised if True.
      timeout_sec: Number of seconds to wait for A2DP connection state change.

    Returns:
      True if A2DP connection state is in the expected state.
    """
        msg = ('Failed to %s the device "%s" within %d seconds via A2DP.' % ('connect' if connected else 'disconnect',
                                                                             mac_address, timeout_sec))
        return bt_test_utils.wait_until(
            timeout_sec=timeout_sec,
            condition_func=self.is_a2dp_sink_connected,
            func_args=[mac_address],
            expected_value=connected,
            exception=signals.TestError(msg) if raise_error else None)

    def wait_for_nap_service_connection(self, connected_mac_addr: str, state_connected: bool,
                                        exception: Exception) -> bool:
        """Waits for NAP service connection to be expected state.

    Args:
      connected_mac_addr: String, Bluetooth Mac address is needed to be checked.
      state_connected: Bool, NAP service connection is established as expected
          if True, else terminated as expected.
      exception: Exception, Raised if NAP service connection is not expected
        state.

    Raises:
      exception: Raised if NAP service connection is not expected state.
    """

        def is_device_connected():
            """Returns True if connected else False."""
            connected_devices = self._ad.sl4a.bluetoothPanGetConnectedDevices()
            # Check if the Bluetooth mac address is in the connected device list.
            return connected_mac_addr in [d['address'] for d in connected_devices]

        bt_test_utils.wait_until(
            timeout_sec=bt_constants.NAP_CONNECTION_TIMEOUT_SECS,
            condition_func=is_device_connected,
            func_args=[],
            expected_value=state_connected,
            exception=exception)

    def verify_internet(self,
                        allow_access: bool,
                        exception: Exception,
                        test_url: str = TEST_URL,
                        interval_sec: int = PING_INTERVAL_TIME_SEC,
                        timeout_sec: float = PING_TIMEOUT_SEC) -> bool:
        """Verifies that internet is in expected state.

    Continuously make ping request to a URL for internet verification.

    Args:
      allow_access: Bool, Device can have internet access as expected if True,
          else no internet access as expected.
      exception: Exception, Raised if internet is not in expected state.
      test_url: String, A URL is used to verify internet by ping request.
      interval_sec: Int, Interval time between ping requests in second.
      timeout_sec: Int, Number of seconds to wait for ping success if
        allow_access is True else wait for ping failure if allow_access is
        False.

    Raises:
      exception: Raised if internet is not in expected state.
    """
        self._ad.log.info('Verify that internet %s be used.' % ('can' if allow_access else 'can not'))

        def http_ping():
            """Returns True if http ping success else False."""
            try:
                return bool(self._ad.sl4a.httpPing(test_url))
            except jsonrpc_client_base.ApiError as e:
                # ApiError is raised by httpPing() when no internet.
                self._ad.log.debug(str(e))
            return False

        bt_test_utils.wait_until(
            timeout_sec=timeout_sec,
            condition_func=http_ping,
            func_args=[],
            expected_value=allow_access,
            exception=exception,
            interval_sec=interval_sec)

    def allow_extra_permissions(self) -> None:
        """A method to allow extra permissions.

    This method has no any logics. It is used to skip the operation when it is
    called if a test is not Wear OS use case.
    """

    def is_service_running(self, mac_address: str, timeout_sec: float) -> bool:
        """Checks bluetooth profile state.

       Check bluetooth headset/a2dp profile connection
       status from bluetooth manager log.

    Args:
      mac_address: The Bluetooth mac address of the peripheral device.
      timeout_sec: Number of seconds to wait for the specified message
      be found in bluetooth manager log.

    Returns:
        True: If pattern match with bluetooth_manager_log.
    """
        pattern_headset = (r'\sm\w+e:\sC\w+d')
        pattern_a2dp = (r'StateMachine:.*state=Connected')
        output_headset = self._ad.adb.shell('dumpsys bluetooth_manager | egrep -A20 "Profile: HeadsetService"').decode()
        output_a2dp = self._ad.adb.shell('dumpsys bluetooth_manager | egrep -A30 "Profile: A2dpService"').decode()
        service_type = {'a2dp': ((pattern_a2dp), (output_a2dp)), 'headset': ((pattern_headset), (output_headset))}
        start_time = time.time()
        end_time = start_time + timeout_sec
        while start_time < end_time:
            try:
                match = service_type
                if match and mac_address in service_type:
                    return True
            except adb.AdbError as e:
                logging.exception(e)
            time.sleep(ADB_WAITING_TIME_SECONDS)
        return False

    def connect_wifi_from_other_device_hotspot(self, wifi_hotspot_device: android_device.AndroidDevice) -> None:
        """Turns on 2.4G Wifi hotspot from the other android device and connect on the android device.

    Args:
      wifi_hotspot_device: Android device, turn on 2.4G Wifi hotspot.
    """
        wifi_hotspot_2_4g_config = bt_constants.WIFI_HOTSPOT_2_4G.copy()
        if int(wifi_hotspot_device.build_info['build_version_sdk']) > 29:
            wifi_hotspot_2_4g_config['apBand'] = 1
        # Turn on 2.4G Wifi hotspot on the secondary phone.
        wifi_hotspot_device.sl4a.wifiSetWifiApConfiguration(wifi_hotspot_2_4g_config)
        wifi_hotspot_device.sl4a.connectivityStartTethering(0, False)
        # Connect the 2.4G Wifi on the primary phone.
        self._ad.mbs.wifiEnable()
        self._ad.mbs.wifiConnectSimple(wifi_hotspot_2_4g_config['SSID'], wifi_hotspot_2_4g_config['password'])

    def get_paired_device_supported_codecs(self, mac_address: str) -> List[str]:
        """Gets the supported A2DP codecs of the paired Bluetooth device.

    Gets the supported A2DP codecs of the paired Bluetooth device from bluetooth
    manager log.

    Args:
      mac_address: The Bluetooth mac address of the paired Bluetooth device.

    Returns:
        A list of the A2DP codecs that the paired Bluetooth device supports.
    """
        if not self.is_bt_paired(mac_address):
            raise signals.TestError(f'Devices {self.serial} and {mac_address} are not paired.')
        cmd = (f'dumpsys bluetooth_manager | '
               f'egrep -A12 "A2dpStateMachine for {mac_address}" | '
               f'egrep -A5 "mCodecsSelectableCapabilities"')
        paired_device_selectable_codecs = self._ad.adb.shell(cmd).decode()
        pattern = 'codecName:(.*),mCodecType'
        return re.findall(pattern, paired_device_selectable_codecs)

    def get_current_a2dp_codec(self) -> bt_constants.BluetoothA2dpCodec:
        """Gets current A2DP codec type.

    Returns:
        A number representing the current A2DP codec type.
        Codec type values are:
        0: SBC
        1: AAC
        2: aptX
        3: aptX HD
        4: LDAC
    """
        codec_type = self._ad.sl4a.bluetoothA2dpGetCurrentCodecConfig()['codecType']
        return bt_constants.BluetoothA2dpCodec(codec_type)

    def is_variable_bit_rate_enabled(self) -> bool:
        """Checks if Variable Bit Rate (VBR) support is enabled for A2DP AAC codec.

    Returns:
      True if Variable Bit Rate support is enabled else False.
    """
        return bt_constants.TRUE in self._ad.adb.getprop(bt_constants.AAC_VBR_SUPPORTED_PROPERTY)

    def toggle_variable_bit_rate(self, enabled: bool = True) -> bool:
        """Toggles Variable Bit Rate (VBR) support status for A2DP AAC codec.

    After calling this method, the android device needs to restart Bluetooth for
    taking effect.

    If Variable Bit Rate support status is disabled, the android device will use
    Constant Bit Rate (CBR).

    Args:
      enabled: Enable Variable Bit Rate support if True.

    Returns:
      True if the status is changed successfully else False.
    """
        self._ad.adb.shell(f'su root setprop {bt_constants.AAC_VBR_SUPPORTED_PROPERTY} '
                           f'{bt_constants.TRUE if enabled else bt_constants.FALSE}')
        return enabled == self.is_variable_bit_rate_enabled()

    def pair_and_connect_ble_device(self, peripheral_ble_device: android_device.AndroidDevice) -> None:
        """Pairs Android phone with BLE device.

    Initiates pairing from the phone and checks if it is bonded and connected to
    the BLE device.

    Args:
      peripheral_ble_device: An android device. AndroidDevice instance to pair
        and connect with.

    Raises:
      signals.ControllerError: raised if it failed to connect BLE device.
    """
        peripheral_ble_device.activate_ble_pairing_mode()
        mac_address = self.scan_and_get_ble_device_address(peripheral_ble_device.get_device_name())
        self.pair_and_connect_bluetooth(mac_address)

    def toggle_charging(self, enabled: bool) -> None:
        """Toggles charging on the device.

    Args:
      enabled: Enable charging if True.
    """
        set_value = '0' if enabled else '1'
        config_file = bt_constants.CHARGING_CONTROL_CONFIG_DICT[self._ad.build_info['hardware']]
        self._ad.adb.shell(f'echo {set_value} > {config_file}')

    def enable_airplane_mode(self, wait_secs=1) -> None:
        """Enables airplane mode on device.

    Args:
      wait_secs: float, the amount of time to wait after sending the airplane
        mode broadcast.
    Returns:
        None
    """
        self._ad.adb.shell(['settings', 'put', 'global', 'airplane_mode_on', '1'])
        self._ad.adb.shell(['am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE', '--ez', 'state', 'true'])
        time.sleep(wait_secs)

    def disable_airplane_mode(self, wait_secs=1) -> None:
        """Disables airplane mode on device.

    Args:
      wait_secs: float, the amount of time to wait after sending the airplane
        mode broadcast.
    Returns:
        None
    """
        self._ad.adb.shell(['settings', 'put', 'global', 'airplane_mode_on', '0'])
        self._ad.adb.shell(['am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE', '--ez', 'state', 'false'])
        time.sleep(wait_secs)

    def disable_verity_check(self) -> None:
        """Disables Android dm verity check.

    Returns:
      None
    """
        if 'verity is already disabled' in str(self._ad.adb.disable_verity()):
            return
        self._ad.reboot()
        self._ad.root_adb()
        self._ad.wait_for_boot_completion()
        self._ad.adb.remount()
