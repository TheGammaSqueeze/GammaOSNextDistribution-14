#!/usr/bin/env python3
#
#   Copyright 2017 Google, Inc.
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
import time
from acts import utils
from acts.libs.proc import job
from acts.controllers.ap_lib import bridge_interface as bi
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts.controllers.adb_lib.error import AdbCommandError
from acts.controllers.ap_lib import hostapd_security
from acts.controllers.ap_lib import hostapd_ap_preset

# http://www.secdev.org/projects/scapy/
# On ubuntu, sudo pip3 install scapy
import scapy.all as scapy

GET_FROM_PHONE = 'get_from_dut'
GET_FROM_AP = 'get_from_ap'
ENABLED_MODULATED_DTIM = 'gEnableModulatedDTIM='
MAX_MODULATED_DTIM = 'gMaxLIModulatedDTIM='

CHRE_WIFI_SCAN_TYPE = {
    'active': 'active',
    'passive': 'passive',
    'activePassiveDfs': 'active_passive_dfs',
    'noPreference': 'no_preference'
}

CHRE_WIFI_RADIO_CHAIN = {
    'lowLatency': 'low_latency',
    'lowPower': 'low_power',
    'highAccuracy': 'high_accuracy'
}

CHRE_WIFI_CHANNEL_SET = {
    'all': 'all',
    'nonDfs': 'non_dfs'
}


def change_dtim(ad, gEnableModulatedDTIM, gMaxLIModulatedDTIM=10):
    """Function to change the DTIM setting in the phone.

    Args:
        ad: the target android device, AndroidDevice object
        gEnableModulatedDTIM: Modulated DTIM, int
        gMaxLIModulatedDTIM: Maximum modulated DTIM, int
    """
    ad.log.info('Sets dtim to {}'.format(gEnableModulatedDTIM))

    # In P21 the dtim setting method changed and an AdbCommandError will take
    # place to get ini_file_phone. Thus add try/except block for the old method.
    # If error occurs, use change_dtim_adb method later. Otherwise, first trying
    # to find the ini file with DTIM settings
    try:
        ini_file_phone = ad.adb.shell('ls /vendor/firmware/wlan/*/*.ini')

    except AdbCommandError as e:

        # Gets AdbCommandError, change dtim later with change_dtim_adb merthod.
        # change_dtim_adb requires that wifi connection is on.
        ad.log.info('Gets AdbCommandError, change dtim with change_dtim_adb.')
        change_dtim_adb(ad, gEnableModulatedDTIM)
        return 0

    ini_file_local = ini_file_phone.split('/')[-1]

    # Pull the file and change the DTIM to desired value
    ad.adb.pull('{} {}'.format(ini_file_phone, ini_file_local))

    with open(ini_file_local, 'r') as fin:
        for line in fin:
            if ENABLED_MODULATED_DTIM in line:
                gE_old = line.strip('\n')
                gEDTIM_old = line.strip(ENABLED_MODULATED_DTIM).strip('\n')
            if MAX_MODULATED_DTIM in line:
                gM_old = line.strip('\n')
                gMDTIM_old = line.strip(MAX_MODULATED_DTIM).strip('\n')
    fin.close()
    if int(gEDTIM_old) == gEnableModulatedDTIM and int(
            gMDTIM_old) == gMaxLIModulatedDTIM:
        ad.log.info('Current DTIM is already the desired value,'
                    'no need to reset it')
        return 0

    gE_new = ENABLED_MODULATED_DTIM + str(gEnableModulatedDTIM)
    gM_new = MAX_MODULATED_DTIM + str(gMaxLIModulatedDTIM)

    sed_gE = 'sed -i \'s/{}/{}/g\' {}'.format(gE_old, gE_new, ini_file_local)
    sed_gM = 'sed -i \'s/{}/{}/g\' {}'.format(gM_old, gM_new, ini_file_local)
    job.run(sed_gE)
    job.run(sed_gM)

    # Push the file to the phone
    push_file_to_phone(ad, ini_file_local, ini_file_phone)
    ad.log.info('DTIM changes checked in and rebooting...')
    ad.reboot()
    # Wait for auto-wifi feature to start
    time.sleep(20)
    ad.adb.shell('dumpsys battery set level 100')
    ad.log.info('DTIM updated and device back from reboot')
    return 1

def change_dtim_adb(ad, gEnableModulatedDTIM):
    """Function to change the DTIM setting in the P21 phone.

        This method should be run after connecting wifi.

    Args:
        ad: the target android device, AndroidDevice object
        gEnableModulatedDTIM: Modulated DTIM, int
    """
    ad.log.info('Changes DTIM to {} with adb'.format(gEnableModulatedDTIM))
    ad.adb.root()
    screen_status = ad.adb.shell('dumpsys nfc | grep Screen')
    screen_is_on = 'ON_UNLOCKED' in screen_status

    # To read the dtim with 'adb shell wl bcn_li_dtim', the screen should be off
    if screen_is_on:
        ad.log.info('The screen is on. Set it to off before change dtim')
        ad.droid.goToSleepNow()
        time_limit_seconds = 60
        _wait_screen_off(ad, time_limit_seconds)

    old_dtim = _read_dtim_adb(ad)
    ad.log.info('The dtim before change is {}'.format(old_dtim))
    try:
        if int(old_dtim) == gEnableModulatedDTIM:
            ad.log.info('Current DTIM is already the desired value,'
                        'no need to reset it')
            if screen_is_on:
                ad.log.info('Changes the screen to the original on status')
                ad.droid.wakeUpNow()
            return
    except Exception as e:
        ad.log.info('old_dtim is not available from adb')

    current_dtim = _set_dtim(ad, gEnableModulatedDTIM)
    ad.log.info(
        'Old DTIM is {}, current DTIM is {}'.format(old_dtim, current_dtim))
    if screen_is_on:
        ad.log.info('Changes the screen to the original on status')
        ad.droid.wakeUpNow()

def _set_dtim(ad, gEnableModulatedDTIM):
    out = ad.adb.shell("halutil -dtim_config {}".format(gEnableModulatedDTIM))
    ad.log.info('set dtim to {}, stdout: {}'.format(
        gEnableModulatedDTIM, out))
    return _read_dtim_adb(ad)

def _read_dtim_adb(ad):
    try:
        old_dtim = ad.adb.shell('wl bcn_li_dtim')
        return old_dtim
    except Exception as e:
        ad.log.info('When reading dtim get error {}'.format(e))
        return 'The dtim value is not available from adb'

def _wait_screen_off(ad, time_limit_seconds):
    while time_limit_seconds > 0:
        screen_status = ad.adb.shell('dumpsys nfc | grep Screen')
        if 'OFF_UNLOCKED' in screen_status:
            ad.log.info('The screen status is {}'.format(screen_status))
            return
        time.sleep(1)
        time_limit_seconds -= 1
    raise TimeoutError('Timed out while waiting the screen off after {} '
                       'seconds.'.format(time_limit_seconds))


def push_file_to_phone(ad, file_local, file_phone):
    """Function to push local file to android phone.

    Args:
        ad: the target android device
        file_local: the locla file to push
        file_phone: the file/directory on the phone to be pushed
    """
    ad.adb.root()
    cmd_out = ad.adb.remount()
    if 'Permission denied' in cmd_out:
        ad.log.info('Need to disable verity first and reboot')
        ad.adb.disable_verity()
        time.sleep(1)
        ad.reboot()
        ad.log.info('Verity disabled and device back from reboot')
        ad.adb.root()
        ad.adb.remount()
    time.sleep(1)
    ad.adb.push('{} {}'.format(file_local, file_phone))


def ap_setup(ap, network, bandwidth=80, dtim_period=None):
    """Set up the whirlwind AP with provided network info.

    Args:
        ap: access_point object of the AP
        network: dict with information of the network, including ssid, password
                 bssid, channel etc.
        bandwidth: the operation bandwidth for the AP, default 80MHz
        dtim_period: the dtim period of access point
    Returns:
        brconfigs: the bridge interface configs
    """
    log = logging.getLogger()
    bss_settings = []
    ssid = network[wutils.WifiEnums.SSID_KEY]
    if "password" in network.keys():
        password = network["password"]
        security = hostapd_security.Security(
            security_mode="wpa", password=password)
    else:
        security = hostapd_security.Security(security_mode=None, password=None)
    channel = network["channel"]
    config = hostapd_ap_preset.create_ap_preset(
        channel=channel,
        ssid=ssid,
        dtim_period=dtim_period,
        security=security,
        bss_settings=bss_settings,
        vht_bandwidth=bandwidth,
        profile_name='whirlwind',
        iface_wlan_2g=ap.wlan_2g,
        iface_wlan_5g=ap.wlan_5g)
    config_bridge = ap.generate_bridge_configs(channel)
    brconfigs = bi.BridgeInterfaceConfigs(config_bridge[0], config_bridge[1],
                                          config_bridge[2])
    ap.bridge.startup(brconfigs)
    ap.start_ap(config)
    log.info("AP started on channel {} with SSID {}".format(channel, ssid))
    return brconfigs


def run_iperf_client_nonblocking(ad, server_host, extra_args=""):
    """Start iperf client on the device with nohup.

    Return status as true if iperf client start successfully.
    And data flow information as results.

    Args:
        ad: the android device under test
        server_host: Address of the iperf server.
        extra_args: A string representing extra arguments for iperf client,
            e.g. "-i 1 -t 30".

    """
    log = logging.getLogger()
    ad.adb.shell_nb("nohup >/dev/null 2>&1 sh -c 'iperf3 -c {} {} &'".format(
        server_host, extra_args))
    log.info("IPerf client started")


def get_wifi_rssi(ad):
    """Get the RSSI of the device.

    Args:
        ad: the android device under test
    Returns:
        RSSI: the rssi level of the device
    """
    RSSI = ad.droid.wifiGetConnectionInfo()['rssi']
    return RSSI


def get_phone_ip(ad):
    """Get the WiFi IP address of the phone.

    Args:
        ad: the android device under test
    Returns:
        IP: IP address of the phone for WiFi, as a string
    """
    IP = ad.droid.connectivityGetIPv4Addresses('wlan0')[0]

    return IP


def get_phone_mac(ad):
    """Get the WiFi MAC address of the phone.

    Args:
        ad: the android device under test
    Returns:
        mac: MAC address of the phone for WiFi, as a string
    """
    mac = ad.droid.wifiGetConnectionInfo()["mac_address"]

    return mac


def get_phone_ipv6(ad):
    """Get the WiFi IPV6 address of the phone.

    Args:
        ad: the android device under test
    Returns:
        IPv6: IPv6 address of the phone for WiFi, as a string
    """
    IPv6 = ad.droid.connectivityGetLinkLocalIpv6Address('wlan0')[:-6]

    return IPv6


def wait_for_dhcp(interface_name):
    """Wait the DHCP address assigned to desired interface.

    Getting DHCP address takes time and the wait time isn't constant. Utilizing
    utils.timeout to keep trying until success

    Args:
        interface_name: desired interface name
    Returns:
        ip: ip address of the desired interface name
    Raise:
        TimeoutError: After timeout, if no DHCP assigned, raise
    """
    log = logging.getLogger()
    reset_host_interface(interface_name)
    start_time = time.time()
    time_limit_seconds = 60
    ip = '0.0.0.0'
    while start_time + time_limit_seconds > time.time():
        ip = scapy.get_if_addr(interface_name)
        if ip == '0.0.0.0':
            time.sleep(1)
        else:
            log.info(
                'DHCP address assigned to %s as %s' % (interface_name, ip))
            return ip
    raise TimeoutError('Timed out while getting if_addr after %s seconds.' %
                       time_limit_seconds)


def reset_host_interface(intferface_name):
    """Reset the host interface.

    Args:
        intferface_name: the desired interface to reset
    """
    log = logging.getLogger()
    intf_down_cmd = 'ifconfig %s down' % intferface_name
    intf_up_cmd = 'ifconfig %s up' % intferface_name
    try:
        job.run(intf_down_cmd)
        time.sleep(10)
        job.run(intf_up_cmd)
        log.info('{} has been reset'.format(intferface_name))
    except job.Error:
        raise Exception('No such interface')


def bringdown_host_interface(intferface_name):
    """Reset the host interface.

    Args:
        intferface_name: the desired interface to reset
    """
    log = logging.getLogger()
    intf_down_cmd = 'ifconfig %s down' % intferface_name
    try:
        job.run(intf_down_cmd)
        time.sleep(2)
        log.info('{} has been brought down'.format(intferface_name))
    except job.Error:
        raise Exception('No such interface')


def create_pkt_config(test_class):
    """Creates the config for generating multicast packets

    Args:
        test_class: object with all networking paramters

    Returns:
        Dictionary with the multicast packet config
    """
    addr_type = (scapy.IPV6_ADDR_LINKLOCAL
                 if test_class.ipv6_src_type == 'LINK_LOCAL' else
                 scapy.IPV6_ADDR_GLOBAL)

    mac_dst = test_class.mac_dst
    if GET_FROM_PHONE in test_class.mac_dst:
        mac_dst = get_phone_mac(test_class.dut)

    ipv4_dst = test_class.ipv4_dst
    if GET_FROM_PHONE in test_class.ipv4_dst:
        ipv4_dst = get_phone_ip(test_class.dut)

    ipv6_dst = test_class.ipv6_dst
    if GET_FROM_PHONE in test_class.ipv6_dst:
        ipv6_dst = get_phone_ipv6(test_class.dut)

    ipv4_gw = test_class.ipv4_gwt
    if GET_FROM_AP in test_class.ipv4_gwt:
        ipv4_gw = test_class.access_point.ssh_settings.hostname

    pkt_gen_config = {
        'interf': test_class.pkt_sender.interface,
        'subnet_mask': test_class.sub_mask,
        'src_mac': test_class.mac_src,
        'dst_mac': mac_dst,
        'src_ipv4': test_class.ipv4_src,
        'dst_ipv4': ipv4_dst,
        'src_ipv6': test_class.ipv6_src,
        'src_ipv6_type': addr_type,
        'dst_ipv6': ipv6_dst,
        'gw_ipv4': ipv4_gw
    }
    return pkt_gen_config
