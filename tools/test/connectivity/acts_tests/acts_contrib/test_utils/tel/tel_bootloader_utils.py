#!/usr/bin/env python3
#
#   Copyright 2021 - Google
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

import re
import time

from acts.controllers.android_device import SL4A_APK_NAME
from acts.controllers.android_device import list_adb_devices
from acts.controllers.android_device import list_fastboot_devices
from acts_contrib.test_utils.tel.tel_ims_utils import activate_wfc_on_device
from acts_contrib.test_utils.tel.tel_logging_utils import set_qxdm_logger_command
from acts_contrib.test_utils.tel.tel_logging_utils import start_qxdm_logger
from acts_contrib.test_utils.tel.tel_test_utils import abort_all_tests
from acts_contrib.test_utils.tel.tel_test_utils import bring_up_sl4a
from acts_contrib.test_utils.tel.tel_test_utils import refresh_sl4a_session
from acts_contrib.test_utils.tel.tel_test_utils import set_phone_silent_mode
from acts_contrib.test_utils.tel.tel_test_utils import synchronize_device_time
from acts_contrib.test_utils.tel.tel_test_utils import unlock_sim


def fastboot_wipe(ad, skip_setup_wizard=True):
    """Wipe the device in fastboot mode.

    Pull sl4a apk from device. Terminate all sl4a sessions,
    Reboot the device to bootloader, wipe the device by fastboot.
    Reboot the device. wait for device to complete booting
    Re-intall and start an sl4a session.
    """
    status = True
    # Pull sl4a apk from device
    out = ad.adb.shell("pm path %s" % SL4A_APK_NAME)
    result = re.search(r"package:(.*)", out)
    if not result:
        ad.log.error("Couldn't find sl4a apk")
    else:
        sl4a_apk = result.group(1)
        ad.log.info("Get sl4a apk from %s", sl4a_apk)
        ad.pull_files([sl4a_apk], "/tmp/")
    ad.stop_services()
    attempts = 3
    for i in range(1, attempts + 1):
        try:
            if ad.serial in list_adb_devices():
                ad.log.info("Reboot to bootloader")
                ad.adb.reboot("bootloader", ignore_status=True)
                time.sleep(10)
            if ad.serial in list_fastboot_devices():
                ad.log.info("Wipe in fastboot")
                ad.fastboot._w(timeout=300, ignore_status=True)
                time.sleep(30)
                ad.log.info("Reboot in fastboot")
                ad.fastboot.reboot()
            ad.wait_for_boot_completion()
            ad.root_adb()
            if ad.skip_sl4a:
                break
            if ad.is_sl4a_installed():
                break
            ad.log.info("Re-install sl4a")
            ad.adb.shell("settings put global verifier_verify_adb_installs 0")
            ad.adb.install("-r /tmp/base.apk")
            time.sleep(10)
            break
        except Exception as e:
            ad.log.warning(e)
            if i == attempts:
                abort_all_tests(ad.log, str(e))
            time.sleep(5)
    try:
        ad.start_adb_logcat()
    except:
        ad.log.error("Failed to start adb logcat!")
    if skip_setup_wizard:
        ad.exit_setup_wizard()
    if getattr(ad, "qxdm_log", True):
        set_qxdm_logger_command(ad, mask=getattr(ad, "qxdm_log_mask", None))
        start_qxdm_logger(ad)
    if ad.skip_sl4a: return status
    bring_up_sl4a(ad)
    synchronize_device_time(ad)
    set_phone_silent_mode(ad.log, ad)
    # Activate WFC on Verizon, AT&T and Canada operators as per # b/33187374 &
    # b/122327716
    activate_wfc_on_device(ad.log, ad)
    return status


def flash_radio(ad, file_path, skip_setup_wizard=True, sideload_img=True):
    """Flash radio image or modem binary.

    Args:
        file_path: The file path of test radio(radio.img)/binary(modem.bin).
        skip_setup_wizard: Skip Setup Wizard if True.
        sideload_img: True to flash radio, False to flash modem.
    """
    ad.stop_services()
    ad.log.info("Reboot to bootloader")
    ad.adb.reboot_bootloader(ignore_status=True)
    ad.log.info("Sideload radio in fastboot")
    try:
        if sideload_img:
            ad.fastboot.flash("radio %s" % file_path, timeout=300)
        else:
            ad.fastboot.flash("modem %s" % file_path, timeout=300)
    except Exception as e:
        ad.log.error(e)
    ad.fastboot.reboot("bootloader")
    time.sleep(5)
    output = ad.fastboot.getvar("version-baseband")
    result = re.search(r"version-baseband: (\S+)", output)
    if not result:
        ad.log.error("fastboot getvar version-baseband output = %s", output)
        abort_all_tests(ad.log, "Radio version-baseband is not provided")
    fastboot_radio_version_output = result.group(1)
    for _ in range(2):
        try:
            ad.log.info("Reboot in fastboot")
            ad.fastboot.reboot()
            ad.wait_for_boot_completion()
            break
        except Exception as e:
            ad.log.error("Exception error %s", e)
    ad.root_adb()
    adb_radio_version_output = ad.adb.getprop("gsm.version.baseband")
    ad.log.info("adb getprop gsm.version.baseband = %s",
                adb_radio_version_output)
    if fastboot_radio_version_output not in adb_radio_version_output:
        msg = ("fastboot radio version output %s does not match with adb"
               " radio version output %s" % (fastboot_radio_version_output,
                                             adb_radio_version_output))
        abort_all_tests(ad.log, msg)
    if not ad.ensure_screen_on():
        ad.log.error("User window cannot come up")
    ad.start_services(skip_setup_wizard=skip_setup_wizard)
    unlock_sim(ad)


def reset_device_password(ad, device_password=None):
    # Enable or Disable Device Password per test bed config
    unlock_sim(ad)
    screen_lock = ad.is_screen_lock_enabled()
    if device_password:
        try:
            refresh_sl4a_session(ad)
            ad.droid.setDevicePassword(device_password)
        except Exception as e:
            ad.log.warning("setDevicePassword failed with %s", e)
            try:
                ad.droid.setDevicePassword(device_password, "1111")
            except Exception as e:
                ad.log.warning(
                    "setDevicePassword providing previous password error: %s",
                    e)
        time.sleep(2)
        if screen_lock:
            # existing password changed
            return
        else:
            # enable device password and log in for the first time
            ad.log.info("Enable device password")
            ad.adb.wait_for_device(timeout=180)
    else:
        if not screen_lock:
            # no existing password, do not set password
            return
        else:
            # password is enabled on the device
            # need to disable the password and log in on the first time
            # with unlocking with a swipe
            ad.log.info("Disable device password")
            ad.unlock_screen(password="1111")
            refresh_sl4a_session(ad)
            ad.ensure_screen_on()
            try:
                ad.droid.disableDevicePassword()
            except Exception as e:
                ad.log.warning("disableDevicePassword failed with %s", e)
                fastboot_wipe(ad)
            time.sleep(2)
            ad.adb.wait_for_device(timeout=180)
    refresh_sl4a_session(ad)
    if not ad.is_adb_logcat_on:
        ad.start_adb_logcat()