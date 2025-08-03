# Lint as: python3
"""Utils for blue tooth tests.

Partly ported from acts/framework/acts/test_utils/bt/bt_test_utils.py
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging as log
import os
import random
import string
import time
import wave
from queue import Empty
from typing import Optional

from blueberry.tests.gd_sl4a.lib.ble_lib import generate_ble_advertise_objects
from blueberry.tests.gd_sl4a.lib.bt_constants import adv_succ
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_advertise_settings_modes
from blueberry.tests.gd_sl4a.lib.bt_constants import ble_advertise_settings_tx_powers
from blueberry.tests.gd_sl4a.lib.bt_constants import bt_default_timeout
from mobly.controllers.android_device import AndroidDevice


class BtTestUtilsError(Exception):
    pass


def convert_pcm_to_wav(pcm_file_path, wave_file_path, audio_params):
    """Converts raw pcm data into wave file.

    Args:
        pcm_file_path: File path of origin pcm file.
        wave_file_path: File path of converted wave file.
        audio_params: A dict with audio configuration.
    """
    with open(pcm_file_path, 'rb') as pcm_file:
        frames = pcm_file.read()
    write_record_file(wave_file_path, audio_params, frames)


def create_vcf_from_vcard(output_path: str,
                          num_of_contacts: int,
                          first_name: Optional[str] = None,
                          last_name: Optional[str] = None,
                          phone_number: Optional[int] = None) -> str:
    """Creates a vcf file from vCard.

    Args:
        output_path: Path of the output vcf file.
        num_of_contacts: Number of contacts to be generated.
        first_name: First name of the contacts.
        last_name: Last name of the contacts.
        phone_number: Phone number of the contacts.

    Returns:
        vcf_file_path: Path of the output vcf file. E.g.
            "/<output_path>/contacts_<time>.vcf".
    """
    file_name = f'contacts_{int(time.time())}.vcf'
    vcf_file_path = os.path.join(output_path, file_name)
    with open(vcf_file_path, 'w+') as f:
        for i in range(num_of_contacts):
            lines = []
            if first_name is None:
                first_name = 'Person'
            vcard_last_name = last_name
            if last_name is None:
                vcard_last_name = i
            vcard_phone_number = phone_number
            if phone_number is None:
                vcard_phone_number = random.randrange(int(10e10))
            lines.append('BEGIN:VCARD\n')
            lines.append('VERSION:2.1\n')
            lines.append(f'N:{vcard_last_name};{first_name};;;\n')
            lines.append(f'FN:{first_name} {vcard_last_name}\n')
            lines.append(f'TEL;CELL:{vcard_phone_number}\n')
            lines.append(f'EMAIL;PREF:{first_name}{vcard_last_name}@gmail.com\n')
            lines.append('END:VCARD\n')
            f.write(''.join(lines))
    return vcf_file_path


def generate_id_by_size(size, chars=(string.ascii_lowercase + string.ascii_uppercase + string.digits)):
    """Generate random ascii characters of input size and input char types.

    Args:
        size: Input size of string.
        chars: (Optional) Chars to use in generating a random string.

    Returns:
        String of random input chars at the input size.
    """
    return ''.join(random.choice(chars) for _ in range(size))


def get_duration_seconds(wav_file_path):
    """Get duration of most recently recorded file.

    Args:
        wav_file_path: path of the wave file.

    Returns:
        duration (float): duration of recorded file in seconds.
    """
    f = wave.open(wav_file_path, 'r')
    frames = f.getnframes()
    rate = f.getframerate()
    duration = (frames / float(rate))
    f.close()
    return duration


def wait_until(timeout_sec, condition_func, func_args, expected_value, exception=None, interval_sec=0.5):
    """Waits until a function returns a expected value or timeout is reached.

    Example usage:
        ```
        def is_bluetooth_enabled(device) -> bool:
          do something and return something...

        # Waits and checks if Bluetooth is turned on.
        bt_test_utils.wait_until(
            timeout_sec=10,
            condition_func=is_bluetooth_enabled,
            func_args=[dut],
            expected_value=True,
            exception=signals.TestFailure('Failed to turn on Bluetooth.'),
            interval_sec=1)
        ```

    Args:
        timeout_sec: float, max waiting time in seconds.
        condition_func: function, when the condiction function returns the expected
            value, the waiting mechanism will be interrupted.
        func_args: tuple or list, the arguments for the condition function.
        expected_value: a expected value that the condition function returns.
        exception: Exception, an exception will be raised when timed out if needed.
        interval_sec: float, interval time between calls of the condition function
            in seconds.

    Returns:
        True if the function returns the expected value else False.
    """
    start_time = time.time()
    end_time = start_time + timeout_sec
    while time.time() < end_time:
        if condition_func(*func_args) == expected_value:
            return True
        time.sleep(interval_sec)
    args_string = ', '.join(list(map(str, func_args)))
    log.warning('Timed out after %.1fs waiting for "%s(%s)" to be "%s".', timeout_sec, condition_func.__name__,
                args_string, expected_value)
    if exception:
        raise exception
    return False


def write_read_verify_data_sl4a(client_ad, server_ad, msg, binary=False):
    """Verify that the client wrote data to the server Android device correctly.

    Args:
        client_ad: the Android device to perform the write.
        server_ad: the Android device to read the data written.
        msg: the message to write.
        binary: if the msg arg is binary or not.

    Returns:
        True if the data written matches the data read, false if not.
    """
    client_ad.log.info('Write message %s.', msg)
    if binary:
        client_ad.sl4a.bluetoothSocketConnWriteBinary(msg)
    else:
        client_ad.sl4a.bluetoothSocketConnWrite(msg)
    server_ad.log.info('Read message %s.', msg)
    if binary:
        read_msg = server_ad.sl4a.bluetoothSocketConnReadBinary().rstrip('\r\n')
    else:
        read_msg = server_ad.sl4a.bluetoothSocketConnRead()
    log.info('Verify message.')
    if msg != read_msg:
        log.error('Mismatch! Read: %s, Expected: %s', read_msg, msg)
        return False
    log.info('Matched! Read: %s, Expected: %s', read_msg, msg)
    return True


def write_record_file(file_name, audio_params, frames):
    """Writes the recorded audio into the file.

    Args:
        file_name: The file name for writing the recorded audio.
        audio_params: A dict with audio configuration.
        frames: Recorded audio frames.
    """
    log.debug('writing frame to %s', file_name)
    wf = wave.open(file_name, 'wb')
    wf.setnchannels(audio_params['channel'])
    wf.setsampwidth(audio_params.get('sample_width', 1))
    wf.setframerate(audio_params['sample_rate'])
    wf.writeframes(frames)
    wf.close()


def get_mac_address_of_generic_advertisement(scan_device, adv_device, adv_addr_type=None):
    """Start generic advertisement and get it's mac address by LE scanning.

    Args:
        scan_ad: The Android device to use as the scanner.
        adv_device: The Android device to use as the advertiser.
        adv_addr_type: The address type for the advertiser (refer to AdvertiseSettings.java)

    Returns:
        mac_address: The mac address of the advertisement.
        advertise_callback: The advertise callback id of the active
            advertisement.
    """
    adv_device.sl4a.bleSetAdvertiseDataIncludeDeviceName(True)
    adv_device.sl4a.bleSetAdvertiseSettingsAdvertiseMode(ble_advertise_settings_modes['low_latency'])
    adv_device.sl4a.bleSetAdvertiseSettingsIsConnectable(True)
    adv_device.sl4a.bleSetAdvertiseSettingsTxPowerLevel(ble_advertise_settings_tx_powers['high'])

    if adv_addr_type is not None:
        adv_device.sl4a.bleSetAdvertiseSettingsOwnAddressType(adv_addr_type)

    advertise_callback, advertise_data, advertise_settings = (generate_ble_advertise_objects(adv_device.sl4a))
    adv_device.sl4a.bleStartBleAdvertising(advertise_callback, advertise_data, advertise_settings)
    try:
        adv_device.ed.pop_event(adv_succ.format(advertise_callback), bt_default_timeout)
    except Empty as err:
        raise BtTestUtilsError("Advertiser did not start successfully {}".format(err))
    filter_list = scan_device.sl4a.bleGenFilterList()
    scan_settings = scan_device.sl4a.bleBuildScanSetting()
    scan_callback = scan_device.sl4a.bleGenScanCallback()
    scan_device.sl4a.bleSetScanFilterDeviceName(adv_device.sl4a.bluetoothGetLocalName())
    scan_device.sl4a.bleBuildScanFilter(filter_list)
    scan_device.sl4a.bleStartBleScan(filter_list, scan_settings, scan_callback)
    try:
        event = scan_device.sl4a.ed.pop_event("BleScan{}onScanResults".format(scan_callback), bt_default_timeout)
    except Empty as err:
        raise BtTestUtilsError("Scanner did not find advertisement {}".format(err))
    mac_address = event['data']['Result']['deviceInfo']['address']
    return mac_address, advertise_callback, scan_callback


def clear_bonded_devices(ad: AndroidDevice):
    """Clear bonded devices from the input Android device.

    Args:
        ad: the Android device performing the connection.
    Returns:
        True if clearing bonded devices was successful, false if unsuccessful.
    """
    bonded_device_list = ad.sl4a.bluetoothGetBondedDevices()
    while bonded_device_list:
        device_address = bonded_device_list[0]['address']
        if not ad.sl4a.bluetoothUnbond(device_address):
            ad.log.error("Failed to unbond {} from {}".format(device_address, ad.serial))
            return False
        ad.log.info("Successfully unbonded {} from {}".format(device_address, ad.serial))
        #TODO: wait for BOND_STATE_CHANGED intent instead of waiting
        time.sleep(1)

        # If device was first connected using LE transport, after bonding it is
        # accessible through it's LE address, and through it classic address.
        # Unbonding it will unbond two devices representing different
        # "addresses". Attempt to unbond such already unbonded devices will
        # result in bluetoothUnbond returning false.
        bonded_device_list = ad.sl4a.bluetoothGetBondedDevices()
    return True
