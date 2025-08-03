#!/usr/bin/env python3.4
#
#   Copyright 2019 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import collections
import importlib
import ipaddress
import logging
import numpy
import re
import time
from acts import asserts
from acts import utils
from acts.controllers.android_device import AndroidDevice
from acts.controllers.utils_lib import ssh
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils import ping_utils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils import qcom_utils
from acts_contrib.test_utils.wifi.wifi_performance_test_utils import brcm_utils

from concurrent.futures import ThreadPoolExecutor

SHORT_SLEEP = 1
MED_SLEEP = 6
CHANNELS_6GHz = ['6g{}'.format(4 * x + 1) for x in range(59)]
BAND_TO_CHANNEL_MAP = {
    '2.4GHz': list(range(1, 14)),
    'UNII-1': [36, 40, 44, 48],
    'UNII-2':
    [52, 56, 60, 64, 100, 104, 108, 112, 116, 120, 124, 128, 132, 140],
    'UNII-3': [149, 153, 157, 161, 165],
    '6GHz': CHANNELS_6GHz
}
CHANNEL_TO_BAND_MAP = {
    channel: band
    for band, channels in BAND_TO_CHANNEL_MAP.items() for channel in channels
}


# Decorators
def nonblocking(f):
    """Creates a decorator transforming function calls to non-blocking"""

    def wrap(*args, **kwargs):
        executor = ThreadPoolExecutor(max_workers=1)
        thread_future = executor.submit(f, *args, **kwargs)
        # Ensure resources are freed up when executor ruturns or raises
        executor.shutdown(wait=False)
        return thread_future

    return wrap


def detect_wifi_platform(dut):
    if hasattr(dut, 'wifi_platform'):
        return dut.wifi_platform
    qcom_check = len(dut.get_file_names('/vendor/firmware/wlan/'))
    if qcom_check:
        dut.wifi_platform = 'qcom'
    else:
        dut.wifi_platform = 'brcm'
    return dut.wifi_platform


def detect_wifi_decorator(f):

    def wrap(*args, **kwargs):
        if 'dut' in kwargs:
            dut = kwargs['dut']
        else:
            dut = next(arg for arg in args if type(arg) == AndroidDevice)
        dut_package = 'acts_contrib.test_utils.wifi.wifi_performance_test_utils.{}_utils'.format(
            detect_wifi_platform(dut))
        dut_package = importlib.import_module(dut_package)
        f_decorated = getattr(dut_package, f.__name__, lambda: None)
        return (f_decorated(*args, **kwargs))

    return wrap


# JSON serializer
def serialize_dict(input_dict):
    """Function to serialize dicts to enable JSON output"""
    output_dict = collections.OrderedDict()
    for key, value in input_dict.items():
        output_dict[_serialize_value(key)] = _serialize_value(value)
    return output_dict


def _serialize_value(value):
    """Function to recursively serialize dict entries to enable JSON output"""
    if isinstance(value, tuple):
        return str(value)
    if isinstance(value, numpy.int64):
        return int(value)
    if isinstance(value, numpy.float64):
        return float(value)
    if isinstance(value, list):
        return [_serialize_value(x) for x in value]
    if isinstance(value, numpy.ndarray):
        return [_serialize_value(x) for x in value]
    elif isinstance(value, dict):
        return serialize_dict(value)
    elif type(value) in (float, int, bool, str):
        return value
    else:
        return "Non-serializable object"


def extract_sub_dict(full_dict, fields):
    sub_dict = collections.OrderedDict(
        (field, full_dict[field]) for field in fields)
    return sub_dict


# Miscellaneous Wifi Utilities
def check_skip_conditions(testcase_params, dut, access_point,
                          ota_chamber=None):
    """Checks if test should be skipped."""
    # Check battery level before test
    if not health_check(dut, 10):
        asserts.skip('DUT battery level too low.')
    if not access_point.band_lookup_by_channel(testcase_params['channel']):
        asserts.skip('AP does not support requested channel.')
    if ota_chamber and CHANNEL_TO_BAND_MAP[
            testcase_params['channel']] not in ota_chamber.SUPPORTED_BANDS:
        asserts.skip('OTA chamber does not support requested channel.')
    # Check if 6GHz is supported by checking capabilities in the US.
    if not dut.droid.wifiCheckState():
        wutils.wifi_toggle_state(dut, True)
    iw_list = dut.adb.shell('iw list')
    supports_6ghz = '6135 MHz' in iw_list
    supports_160mhz = 'Supported Channel Width: 160 MHz' in iw_list
    if testcase_params.get('bandwidth', 20) == 160 and not supports_160mhz:
        asserts.skip('DUT does not support 160 MHz networks.')
    if testcase_params.get('channel',
                           6) in CHANNELS_6GHz and not supports_6ghz:
        asserts.skip('DUT does not support 6 GHz band.')


def validate_network(dut, ssid):
    """Check that DUT has a valid internet connection through expected SSID

    Args:
        dut: android device of interest
        ssid: expected ssid
    """
    try:
        connected = wutils.validate_connection(dut, wait_time=3) is not None
        current_network = dut.droid.wifiGetConnectionInfo()
    except:
        connected = False
        current_network = None
    if connected and current_network['SSID'] == ssid:
        return True
    else:
        return False


def get_server_address(ssh_connection, dut_ip, subnet_mask):
    """Get server address on a specific subnet,

    This function retrieves the LAN or WAN IP of a remote machine used in
    testing. If subnet_mask is set to 'public' it returns a machines global ip,
    else it returns the ip belonging to the dut local network given the dut's
    ip and subnet mask.

    Args:
        ssh_connection: object representing server for which we want an ip
        dut_ip: string in ip address format, i.e., xxx.xxx.xxx.xxx
        subnet_mask: string representing subnet mask (public for global ip)
    """
    ifconfig_out = ssh_connection.run('ifconfig').stdout
    ip_list = re.findall('inet (?:addr:)?(\d+.\d+.\d+.\d+)', ifconfig_out)
    ip_list = [ipaddress.ip_address(ip) for ip in ip_list]

    if subnet_mask == 'public':
        for ip in ip_list:
            # is_global is not used to allow for CGNAT ips in 100.x.y.z range
            if not ip.is_private:
                return str(ip)
    else:
        dut_network = ipaddress.ip_network('{}/{}'.format(dut_ip, subnet_mask),
                                           strict=False)
        for ip in ip_list:
            if ip in dut_network:
                return str(ip)
    logging.error('No IP address found in requested subnet')


# Ping utilities
def get_ping_stats(src_device, dest_address, ping_duration, ping_interval,
                   ping_size):
    """Run ping to or from the DUT.

    The function computes either pings the DUT or pings a remote ip from
    DUT.

    Args:
        src_device: object representing device to ping from
        dest_address: ip address to ping
        ping_duration: timeout to set on the ping process (in seconds)
        ping_interval: time between pings (in seconds)
        ping_size: size of ping packet payload
    Returns:
        ping_result: dict containing ping results and other meta data
    """
    ping_count = int(ping_duration / ping_interval)
    ping_deadline = int(ping_count * ping_interval) + 1
    ping_cmd_linux = 'ping -c {} -w {} -i {} -s {} -D'.format(
        ping_count,
        ping_deadline,
        ping_interval,
        ping_size,
    )

    ping_cmd_macos = 'ping -c {} -t {} -i {} -s {}'.format(
        ping_count,
        ping_deadline,
        ping_interval,
        ping_size,
    )

    if isinstance(src_device, AndroidDevice):
        ping_cmd = '{} {}'.format(ping_cmd_linux, dest_address)
        ping_output = src_device.adb.shell(ping_cmd,
                                           timeout=ping_deadline + SHORT_SLEEP,
                                           ignore_status=True)
    elif isinstance(src_device, ssh.connection.SshConnection):
        platform = src_device.run('uname').stdout
        if 'linux' in platform.lower():
            ping_cmd = 'sudo {} {}'.format(ping_cmd_linux, dest_address)
        elif 'darwin' in platform.lower():
            ping_cmd = "sudo {} {}| while IFS= read -r line; do printf '[%s] %s\n' \"$(gdate '+%s.%N')\" \"$line\"; done".format(
                ping_cmd_macos, dest_address)
        ping_output = src_device.run(ping_cmd,
                                     timeout=ping_deadline + SHORT_SLEEP,
                                     ignore_status=True).stdout
    else:
        raise TypeError('Unable to ping using src_device of type %s.' %
                        type(src_device))
    return ping_utils.PingResult(ping_output.splitlines())


@nonblocking
def get_ping_stats_nb(src_device, dest_address, ping_duration, ping_interval,
                      ping_size):
    return get_ping_stats(src_device, dest_address, ping_duration,
                          ping_interval, ping_size)


# Iperf utilities
@nonblocking
def start_iperf_client_nb(iperf_client, iperf_server_address, iperf_args, tag,
                          timeout):
    return iperf_client.start(iperf_server_address, iperf_args, tag, timeout)


def get_iperf_arg_string(duration,
                         reverse_direction,
                         interval=1,
                         traffic_type='TCP',
                         socket_size=None,
                         num_processes=1,
                         udp_throughput='1000M',
                         ipv6=False,
                         udp_length=1448):
    """Function to format iperf client arguments.

    This function takes in iperf client parameters and returns a properly
    formatter iperf arg string to be used in throughput tests.

    Args:
        duration: iperf duration in seconds
        reverse_direction: boolean controlling the -R flag for iperf clients
        interval: iperf print interval
        traffic_type: string specifying TCP or UDP traffic
        socket_size: string specifying TCP window or socket buffer, e.g., 2M
        num_processes: int specifying number of iperf processes
        udp_throughput: string specifying TX throughput in UDP tests, e.g. 100M
        ipv6: boolean controlling the use of IP V6
    Returns:
        iperf_args: string of formatted iperf args
    """
    iperf_args = '-i {} -t {} -J '.format(interval, duration)
    if ipv6:
        iperf_args = iperf_args + '-6 '
    if traffic_type.upper() == 'UDP':
        iperf_args = iperf_args + '-u -b {} -l {} -P {} '.format(
            udp_throughput, udp_length, num_processes)
    elif traffic_type.upper() == 'TCP':
        iperf_args = iperf_args + '-P {} '.format(num_processes)
    if socket_size:
        iperf_args = iperf_args + '-w {} '.format(socket_size)
    if reverse_direction:
        iperf_args = iperf_args + ' -R'
    return iperf_args


# Attenuator Utilities
def atten_by_label(atten_list, path_label, atten_level):
    """Attenuate signals according to their path label.

    Args:
        atten_list: list of attenuators to iterate over
        path_label: path label on which to set desired attenuation
        atten_level: attenuation desired on path
    """
    for atten in atten_list:
        if path_label in atten.path:
            atten.set_atten(atten_level, retry=True)


def get_atten_for_target_rssi(target_rssi, attenuators, dut, ping_server):
    """Function to estimate attenuation to hit a target RSSI.

    This function estimates a constant attenuation setting on all atennuation
    ports to hit a target RSSI. The estimate is not meant to be exact or
    guaranteed.

    Args:
        target_rssi: rssi of interest
        attenuators: list of attenuator ports
        dut: android device object assumed connected to a wifi network.
        ping_server: ssh connection object to ping server
    Returns:
        target_atten: attenuation setting to achieve target_rssi
    """
    logging.info('Searching attenuation for RSSI = {}dB'.format(target_rssi))
    # Set attenuator to 0 dB
    for atten in attenuators:
        atten.set_atten(0, strict=False, retry=True)
    # Start ping traffic
    dut_ip = dut.droid.connectivityGetIPv4Addresses('wlan0')[0]
    # Measure starting RSSI
    ping_future = get_ping_stats_nb(src_device=ping_server,
                                    dest_address=dut_ip,
                                    ping_duration=1.5,
                                    ping_interval=0.02,
                                    ping_size=64)
    current_rssi = get_connected_rssi(dut,
                                      num_measurements=4,
                                      polling_frequency=0.25,
                                      first_measurement_delay=0.5,
                                      disconnect_warning=1,
                                      ignore_samples=1)
    current_rssi = current_rssi['signal_poll_rssi']['mean']
    ping_future.result()
    target_atten = 0
    logging.debug('RSSI @ {0:.2f}dB attenuation = {1:.2f}'.format(
        target_atten, current_rssi))
    within_range = 0
    for idx in range(20):
        atten_delta = max(min(current_rssi - target_rssi, 20), -20)
        target_atten = int((target_atten + atten_delta) * 4) / 4
        if target_atten < 0:
            return 0
        if target_atten > attenuators[0].get_max_atten():
            return attenuators[0].get_max_atten()
        for atten in attenuators:
            atten.set_atten(target_atten, strict=False, retry=True)
        ping_future = get_ping_stats_nb(src_device=ping_server,
                                        dest_address=dut_ip,
                                        ping_duration=1.5,
                                        ping_interval=0.02,
                                        ping_size=64)
        current_rssi = get_connected_rssi(dut,
                                          num_measurements=4,
                                          polling_frequency=0.25,
                                          first_measurement_delay=0.5,
                                          disconnect_warning=1,
                                          ignore_samples=1)
        current_rssi = current_rssi['signal_poll_rssi']['mean']
        ping_future.result()
        logging.info('RSSI @ {0:.2f}dB attenuation = {1:.2f}'.format(
            target_atten, current_rssi))
        if abs(current_rssi - target_rssi) < 1:
            if within_range:
                logging.info(
                    'Reached RSSI: {0:.2f}. Target RSSI: {1:.2f}.'
                    'Attenuation: {2:.2f}, Iterations = {3:.2f}'.format(
                        current_rssi, target_rssi, target_atten, idx))
                return target_atten
            else:
                within_range = True
        else:
            within_range = False
    return target_atten


def get_current_atten_dut_chain_map(attenuators,
                                    dut,
                                    ping_server,
                                    ping_from_dut=False):
    """Function to detect mapping between attenuator ports and DUT chains.

    This function detects the mapping between attenuator ports and DUT chains
    in cases where DUT chains are connected to only one attenuator port. The
    function assumes the DUT is already connected to a wifi network. The
    function starts by measuring per chain RSSI at 0 attenuation, then
    attenuates one port at a time looking for the chain that reports a lower
    RSSI.

    Args:
        attenuators: list of attenuator ports
        dut: android device object assumed connected to a wifi network.
        ping_server: ssh connection object to ping server
        ping_from_dut: boolean controlling whether to ping from or to dut
    Returns:
        chain_map: list of dut chains, one entry per attenuator port
    """
    # Set attenuator to 0 dB
    for atten in attenuators:
        atten.set_atten(0, strict=False, retry=True)
    # Start ping traffic
    dut_ip = dut.droid.connectivityGetIPv4Addresses('wlan0')[0]
    if ping_from_dut:
        ping_future = get_ping_stats_nb(dut, ping_server._settings.hostname,
                                        11, 0.02, 64)
    else:
        ping_future = get_ping_stats_nb(ping_server, dut_ip, 11, 0.02, 64)
    # Measure starting RSSI
    base_rssi = get_connected_rssi(dut, 4, 0.25, 1)
    chain0_base_rssi = base_rssi['chain_0_rssi']['mean']
    chain1_base_rssi = base_rssi['chain_1_rssi']['mean']
    if chain0_base_rssi < -70 or chain1_base_rssi < -70:
        logging.warning('RSSI might be too low to get reliable chain map.')
    # Compile chain map by attenuating one path at a time and seeing which
    # chain's RSSI degrades
    chain_map = []
    for test_atten in attenuators:
        # Set one attenuator to 30 dB down
        test_atten.set_atten(30, strict=False, retry=True)
        # Get new RSSI
        test_rssi = get_connected_rssi(dut, 4, 0.25, 1)
        # Assign attenuator to path that has lower RSSI
        if chain0_base_rssi > -70 and chain0_base_rssi - test_rssi[
                'chain_0_rssi']['mean'] > 10:
            chain_map.append('DUT-Chain-0')
        elif chain1_base_rssi > -70 and chain1_base_rssi - test_rssi[
                'chain_1_rssi']['mean'] > 10:
            chain_map.append('DUT-Chain-1')
        else:
            chain_map.append(None)
        # Reset attenuator to 0
        test_atten.set_atten(0, strict=False, retry=True)
    ping_future.result()
    logging.debug('Chain Map: {}'.format(chain_map))
    return chain_map


def get_full_rf_connection_map(attenuators,
                               dut,
                               ping_server,
                               networks,
                               ping_from_dut=False):
    """Function to detect per-network connections between attenuator and DUT.

    This function detects the mapping between attenuator ports and DUT chains
    on all networks in its arguments. The function connects the DUT to each
    network then calls get_current_atten_dut_chain_map to get the connection
    map on the current network. The function outputs the results in two formats
    to enable easy access when users are interested in indexing by network or
    attenuator port.

    Args:
        attenuators: list of attenuator ports
        dut: android device object assumed connected to a wifi network.
        ping_server: ssh connection object to ping server
        networks: dict of network IDs and configs
    Returns:
        rf_map_by_network: dict of RF connections indexed by network.
        rf_map_by_atten: list of RF connections indexed by attenuator
    """
    for atten in attenuators:
        atten.set_atten(0, strict=False, retry=True)

    rf_map_by_network = collections.OrderedDict()
    rf_map_by_atten = [[] for atten in attenuators]
    for net_id, net_config in networks.items():
        wutils.reset_wifi(dut)
        wutils.wifi_connect(dut,
                            net_config,
                            num_of_tries=1,
                            assert_on_fail=False,
                            check_connectivity=False)
        rf_map_by_network[net_id] = get_current_atten_dut_chain_map(
            attenuators, dut, ping_server, ping_from_dut)
        for idx, chain in enumerate(rf_map_by_network[net_id]):
            if chain:
                rf_map_by_atten[idx].append({
                    'network': net_id,
                    'dut_chain': chain
                })
    logging.debug('RF Map (by Network): {}'.format(rf_map_by_network))
    logging.debug('RF Map (by Atten): {}'.format(rf_map_by_atten))

    return rf_map_by_network, rf_map_by_atten


# Generic device utils
def get_dut_temperature(dut):
    """Function to get dut temperature.

    The function fetches and returns the reading from the temperature sensor
    used for skin temperature and thermal throttling.

    Args:
        dut: AndroidDevice of interest
    Returns:
        temperature: device temperature. 0 if temperature could not be read
    """
    candidate_zones = [
        '/sys/devices/virtual/thermal/tz-by-name/skin-therm/temp',
        '/sys/devices/virtual/thermal/tz-by-name/sdm-therm-monitor/temp',
        '/sys/devices/virtual/thermal/tz-by-name/sdm-therm-adc/temp',
        '/sys/devices/virtual/thermal/tz-by-name/back_therm/temp',
        '/dev/thermal/tz-by-name/quiet_therm/temp'
    ]
    for zone in candidate_zones:
        try:
            temperature = int(dut.adb.shell('cat {}'.format(zone)))
            break
        except:
            temperature = 0
    if temperature == 0:
        logging.debug('Could not check DUT temperature.')
    elif temperature > 100:
        temperature = temperature / 1000
    return temperature


def wait_for_dut_cooldown(dut, target_temp=50, timeout=300):
    """Function to wait for a DUT to cool down.

    Args:
        dut: AndroidDevice of interest
        target_temp: target cooldown temperature
        timeout: maxt time to wait for cooldown
    """
    start_time = time.time()
    while time.time() - start_time < timeout:
        temperature = get_dut_temperature(dut)
        if temperature < target_temp:
            break
        time.sleep(SHORT_SLEEP)
    elapsed_time = time.time() - start_time
    logging.debug('DUT Final Temperature: {}C. Cooldown duration: {}'.format(
        temperature, elapsed_time))


def health_check(dut, batt_thresh=5, temp_threshold=53, cooldown=1):
    """Function to check health status of a DUT.

    The function checks both battery levels and temperature to avoid DUT
    powering off during the test.

    Args:
        dut: AndroidDevice of interest
        batt_thresh: battery level threshold
        temp_threshold: temperature threshold
        cooldown: flag to wait for DUT to cool down when overheating
    Returns:
        health_check: boolean confirming device is healthy
    """
    health_check = True
    battery_level = utils.get_battery_level(dut)
    if battery_level < batt_thresh:
        logging.warning('Battery level low ({}%)'.format(battery_level))
        health_check = False
    else:
        logging.debug('Battery level = {}%'.format(battery_level))

    temperature = get_dut_temperature(dut)
    if temperature > temp_threshold:
        if cooldown:
            logging.warning(
                'Waiting for DUT to cooldown. ({} C)'.format(temperature))
            wait_for_dut_cooldown(dut, target_temp=temp_threshold - 5)
        else:
            logging.warning('DUT Overheating ({} C)'.format(temperature))
            health_check = False
    else:
        logging.debug('DUT Temperature = {} C'.format(temperature))
    return health_check


# Wifi Device Utils
def empty_rssi_result():
    return collections.OrderedDict([('data', []), ('mean', float('nan')),
                                    ('stdev', float('nan'))])


@nonblocking
def get_connected_rssi_nb(dut,
                          num_measurements=1,
                          polling_frequency=SHORT_SLEEP,
                          first_measurement_delay=0,
                          disconnect_warning=True,
                          ignore_samples=0,
                          interface='wlan0'):
    return get_connected_rssi(dut, num_measurements, polling_frequency,
                              first_measurement_delay, disconnect_warning,
                              ignore_samples, interface)


@detect_wifi_decorator
def get_connected_rssi(dut,
                       num_measurements=1,
                       polling_frequency=SHORT_SLEEP,
                       first_measurement_delay=0,
                       disconnect_warning=True,
                       ignore_samples=0,
                       interface='wlan0'):
    """Gets all RSSI values reported for the connected access point/BSSID.

    Args:
        dut: android device object from which to get RSSI
        num_measurements: number of scans done, and RSSIs collected
        polling_frequency: time to wait between RSSI measurements
        disconnect_warning: boolean controlling disconnection logging messages
        ignore_samples: number of leading samples to ignore
    Returns:
        connected_rssi: dict containing the measurements results for
        all reported RSSI values (signal_poll, per chain, etc.) and their
        statistics
    """


@nonblocking
def get_scan_rssi_nb(dut, tracked_bssids, num_measurements=1):
    return get_scan_rssi(dut, tracked_bssids, num_measurements)


@detect_wifi_decorator
def get_scan_rssi(dut, tracked_bssids, num_measurements=1):
    """Gets scan RSSI for specified BSSIDs.

    Args:
        dut: android device object from which to get RSSI
        tracked_bssids: array of BSSIDs to gather RSSI data for
        num_measurements: number of scans done, and RSSIs collected
    Returns:
        scan_rssi: dict containing the measurement results as well as the
        statistics of the scan RSSI for all BSSIDs in tracked_bssids
    """


@detect_wifi_decorator
def get_sw_signature(dut):
    """Function that checks the signature for wifi firmware and config files.

    Returns:
        bdf_signature: signature consisting of last three digits of bdf cksums
        fw_signature: floating point firmware version, i.e., major.minor
    """


@detect_wifi_decorator
def get_country_code(dut):
    """Function that returns the current wifi country code."""


@detect_wifi_decorator
def push_config(dut, config_file):
    """Function to push Wifi BDF files

    This function checks for existing wifi bdf files and over writes them all,
    for simplicity, with the bdf file provided in the arguments. The dut is
    rebooted for the bdf file to take effect

    Args:
        dut: dut to push bdf file to
        config_file: path to bdf_file to push
    """


@detect_wifi_decorator
def start_wifi_logging(dut):
    """Function to start collecting wifi-related logs"""


@detect_wifi_decorator
def stop_wifi_logging(dut):
    """Function to start collecting wifi-related logs"""


@detect_wifi_decorator
def push_firmware(dut, firmware_files):
    """Function to push Wifi firmware files

    Args:
        dut: dut to push bdf file to
        firmware_files: path to wlanmdsp.mbn file
        datamsc_file: path to Data.msc file
    """


@detect_wifi_decorator
def disable_beamforming(dut):
    """Function to disable beamforming."""


@detect_wifi_decorator
def set_nss_capability(dut, nss):
    """Function to set number of spatial streams supported."""


@detect_wifi_decorator
def set_chain_mask(dut, chain_mask):
    """Function to set DUT chain mask.

    Args:
        dut: android device
        chain_mask: desired chain mask in [0, 1, '2x2']
    """


# Link layer stats utilities
class LinkLayerStats():

    def __new__(self, dut, llstats_enabled=True):
        if detect_wifi_platform(dut) == 'qcom':
            return qcom_utils.LinkLayerStats(dut, llstats_enabled)
        else:
            return brcm_utils.LinkLayerStats(dut, llstats_enabled)
