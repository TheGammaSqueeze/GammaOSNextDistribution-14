#!/usr/bin/env python3
#
#   Copyright 2020 - Google
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
import time
import re
import os
import pathlib
import math
import shutil
import fnmatch
import posixpath
import subprocess
import tempfile
import functools
from retry import retry
from collections import namedtuple
from datetime import datetime, timedelta
from xml.etree import ElementTree
from contextlib import contextmanager
from statistics import median

from acts import utils
from acts import asserts
from acts import signals
from acts.libs.proc import job
from acts.controllers.adb_lib.error import AdbCommandError
from acts.controllers.android_device import list_adb_devices
from acts.controllers.android_device import list_fastboot_devices
from acts.controllers.android_device import DEFAULT_QXDM_LOG_PATH
from acts.controllers.android_device import SL4A_APK_NAME
from acts_contrib.test_utils.gnss.gnss_measurement import GnssMeasurement
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.tel import tel_logging_utils as tlutils
from acts_contrib.test_utils.tel import tel_test_utils as tutils
from acts_contrib.test_utils.gnss import device_doze
from acts_contrib.test_utils.gnss import gnssstatus_utils
from acts_contrib.test_utils.gnss import gnss_constant
from acts_contrib.test_utils.gnss import supl
from acts_contrib.test_utils.instrumentation.device.command.instrumentation_command_builder import InstrumentationCommandBuilder
from acts_contrib.test_utils.instrumentation.device.command.instrumentation_command_builder import InstrumentationTestCommandBuilder
from acts.utils import get_current_epoch_time
from acts.utils import epoch_to_human_time
from acts_contrib.test_utils.gnss.gnss_defines import BCM_GPS_XML_PATH
from acts_contrib.test_utils.gnss.gnss_defines import BCM_NVME_STO_PATH

WifiEnums = wutils.WifiEnums
FIRST_FIXED_MAX_WAITING_TIME = 60
UPLOAD_TO_SPONGE_PREFIX = "TestResult "
PULL_TIMEOUT = 300
GNSSSTATUS_LOG_PATH = (
    "/storage/emulated/0/Android/data/com.android.gpstool/files/")
QXDM_MASKS = ["GPS.cfg", "GPS-general.cfg", "default.cfg"]
TTFF_REPORT = namedtuple(
    "TTFF_REPORT", "utc_time ttff_loop ttff_sec ttff_pe ttff_ant_cn "
                   "ttff_base_cn ttff_haccu")
TRACK_REPORT = namedtuple(
    "TRACK_REPORT", "l5flag pe ant_top4cn ant_cn base_top4cn base_cn device_time report_time")
LOCAL_PROP_FILE_CONTENTS = """\
log.tag.LocationManagerService=VERBOSE
log.tag.GnssLocationProvider=VERBOSE
log.tag.GnssMeasurementsProvider=VERBOSE
log.tag.GpsNetInitiatedHandler=VERBOSE
log.tag.GnssNetInitiatedHandler=VERBOSE
log.tag.GnssNetworkConnectivityHandler=VERBOSE
log.tag.ConnectivityService=VERBOSE
log.tag.ConnectivityManager=VERBOSE
log.tag.GnssVisibilityControl=VERBOSE
log.tag.NtpTimeHelper=VERBOSE
log.tag.NtpTrustedTime=VERBOSE
log.tag.GnssPsdsDownloader=VERBOSE
log.tag.Gnss=VERBOSE
log.tag.GnssConfiguration=VERBOSE"""
LOCAL_PROP_FILE_CONTENTS_FOR_WEARABLE = """\
log.tag.ImsPhone=VERBOSE
log.tag.GsmCdmaPhone=VERBOSE
log.tag.Phone=VERBOSE
log.tag.GCoreFlp=VERBOSE"""
TEST_PACKAGE_NAME = "com.google.android.apps.maps"
LOCATION_PERMISSIONS = [
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION"
]
GNSSTOOL_PACKAGE_NAME = "com.android.gpstool"
GNSSTOOL_PERMISSIONS = [
    "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.ACCESS_COARSE_LOCATION",
    "android.permission.CALL_PHONE",
    "android.permission.WRITE_CONTACTS",
    "android.permission.CAMERA",
    "android.permission.WRITE_EXTERNAL_STORAGE",
    "android.permission.READ_CONTACTS",
    "android.permission.ACCESS_BACKGROUND_LOCATION"
]
DISABLE_LTO_FILE_CONTENTS = """\
LONGTERM_PSDS_SERVER_1="http://"
LONGTERM_PSDS_SERVER_2="http://"
LONGTERM_PSDS_SERVER_3="http://"
NORMAL_PSDS_SERVER="http://"
REALTIME_PSDS_SERVER="http://"
"""
DISABLE_LTO_FILE_CONTENTS_R = """\
XTRA_SERVER_1="http://"
XTRA_SERVER_2="http://"
XTRA_SERVER_3="http://"
"""
_BRCM_DUTY_CYCLE_PATTERN = re.compile(r".*PGLOR,\d+,STA.*")
_WEARABLE_QCOM_VENDOR_REGEX = re.compile(r"init.svc.qcom")
_GPS_ELAPSED_REALTIME_DIFF_TOLERANCE = 500_000

class GnssTestUtilsError(Exception):
    pass


def remount_device(ad):
    """Remount device file system to read and write.

    Args:
        ad: An AndroidDevice object.
    """
    for retries in range(5):
        ad.root_adb()
        if ad.adb.getprop("ro.boot.veritymode") == "enforcing":
            ad.adb.disable_verity()
            reboot(ad)
        remount_result = ad.adb.remount()
        ad.log.info("Attempt %d - %s" % (retries + 1, remount_result))
        if "remount succeeded" in remount_result:
            break


def reboot(ad):
    """Reboot device and check if mobile data is available.

    Args:
        ad: An AndroidDevice object.
    """
    ad.log.info("Reboot device to make changes take effect.")
    # TODO(diegowchung): remove the timeout setting after p23 back to normal
    ad.reboot(timeout=600)
    ad.unlock_screen(password=None)
    if not is_mobile_data_on(ad):
        set_mobile_data(ad, True)
    utils.sync_device_time(ad)


def enable_gnss_verbose_logging(ad):
    """Enable GNSS VERBOSE Logging and persistent logcat.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    ad.log.info("Enable GNSS VERBOSE Logging and persistent logcat.")
    if check_chipset_vendor_by_qualcomm(ad):
        ad.adb.shell("echo -e '\nDEBUG_LEVEL = 5' >> /vendor/etc/gps.conf")
    else:
        ad.adb.shell("echo LogEnabled=true >> /data/vendor/gps/libgps.conf")
        ad.adb.shell("chown gps.system /data/vendor/gps/libgps.conf")
    if is_device_wearable(ad):
       PROP_CONTENTS = LOCAL_PROP_FILE_CONTENTS + LOCAL_PROP_FILE_CONTENTS_FOR_WEARABLE
    else:
        PROP_CONTENTS = LOCAL_PROP_FILE_CONTENTS
    ad.adb.shell("echo %r >> /data/local.prop" % PROP_CONTENTS)
    ad.adb.shell("chmod 644 /data/local.prop")
    ad.adb.shell("setprop persist.logd.logpersistd.size 20000")
    ad.adb.shell("setprop persist.logd.size 16777216")
    ad.adb.shell("setprop persist.vendor.radio.adb_log_on 1")
    ad.adb.shell("setprop persist.logd.logpersistd logcatd")
    ad.adb.shell("setprop log.tag.copresGcore VERBOSE")
    ad.adb.shell("sync")


def get_am_flags(value):
    """Returns the (value, type) flags for a given python value."""
    if type(value) is bool:
        return str(value).lower(), 'boolean'
    elif type(value) is str:
        return value, 'string'
    raise ValueError("%s should be either 'boolean' or 'string'" % value)


def enable_compact_and_particle_fusion_log(ad):
    """Enable CompactLog, FLP particle fusion log and disable gms
    location-based quake monitoring.

    Args:
        ad: An AndroidDevice object.
    """
    ad.root_adb()
    ad.log.info("Enable FLP flags and Disable GMS location-based quake "
                "monitoring.")
    overrides = {
        'compact_log_enabled': True,
        'flp_use_particle_fusion': True,
        'flp_particle_fusion_extended_bug_report': True,
        'flp_event_log_size': '86400',
        'proks_config': '28',
        'flp_particle_fusion_bug_report_window_sec': '86400',
        'flp_particle_fusion_bug_report_max_buffer_size': '86400',
        'seismic_data_collection': False,
        'Ealert__enable': False,
    }
    for flag, python_value in overrides.items():
        value, type = get_am_flags(python_value)
        cmd = ("am broadcast -a com.google.android.gms.phenotype.FLAG_OVERRIDE "
               "--es package com.google.android.location --es user \* "
               "--esa flags %s --esa values %s --esa types %s "
               "com.google.android.gms" % (flag, value, type))
        ad.adb.shell(cmd, ignore_status=True)
    ad.adb.shell("am force-stop com.google.android.gms")
    ad.adb.shell("am broadcast -a com.google.android.gms.INITIALIZE")


def disable_xtra_throttle(ad):
    """Disable XTRA throttle will have no limit to download XTRA data.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    ad.log.info("Disable XTRA Throttle.")
    ad.adb.shell("echo -e '\nXTRA_TEST_ENABLED=1' >> /vendor/etc/gps.conf")
    ad.adb.shell("echo -e '\nXTRA_THROTTLE_ENABLED=0' >> /vendor/etc/gps.conf")


def enable_supl_mode(ad):
    """Enable SUPL back on for next test item.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    ad.log.info("Enable SUPL mode.")
    ad.adb.shell("echo -e '\nSUPL_MODE=1' >> /etc/gps_debug.conf")


def disable_supl_mode(ad):
    """Kill SUPL to test XTRA/LTO only test item.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    ad.log.info("Disable SUPL mode.")
    ad.adb.shell("echo -e '\nSUPL_MODE=0' >> /etc/gps_debug.conf")
    if not check_chipset_vendor_by_qualcomm(ad):
        supl.set_supl_over_wifi_state(ad, False)


def enable_vendor_orbit_assistance_data(ad):
    """Enable vendor assistance features.
        For Qualcomm: Enable XTRA
        For Broadcom: Enable LTO

    Args:
        ad: An AndroidDevice object.
    """
    ad.root_adb()
    if check_chipset_vendor_by_qualcomm(ad):
        disable_xtra_throttle(ad)
        reboot(ad)
    elif is_device_wearable(ad):
        lto_mode_wearable(ad, True)
    else:
        lto_mode(ad, True)


def disable_vendor_orbit_assistance_data(ad):
    """Disable vendor assistance features.

    For Qualcomm: disable XTRA
    For Broadcom: disable LTO

    Args:
        ad: An AndroidDevice object.
    """
    ad.root_adb()
    if check_chipset_vendor_by_qualcomm(ad):
        disable_qualcomm_orbit_assistance_data(ad)
    elif is_device_wearable(ad):
        lto_mode_wearable(ad, False)
    else:
        lto_mode(ad, False)

def gla_mode(ad, state: bool):
    """Enable or disable Google Location Accuracy feature.

    Args:
        ad: An AndroidDevice object.
        state: True to enable GLA, False to disable GLA.
    """
    ad.root_adb()
    if state:
        ad.adb.shell('settings put global assisted_gps_enabled 1')
        ad.log.info("Modify current GLA Mode to MS_BASED mode")
    else:
        ad.adb.shell('settings put global assisted_gps_enabled 0')
        ad.log.info("Modify current GLA Mode to standalone mode")

    out = int(ad.adb.shell("settings get global assisted_gps_enabled"))
    if out == 1:
        ad.log.info("GLA is enabled, MS_BASED mode")
    else:
        ad.log.info("GLA is disabled, standalone mode")


def disable_qualcomm_orbit_assistance_data(ad):
    """Disable assiatance features for Qualcomm project.

    Args:
        ad: An AndroidDevice object.
    """
    ad.log.info("Disable XTRA-daemon until next reboot.")
    ad.adb.shell("killall xtra-daemon", ignore_status=True)


def disable_private_dns_mode(ad):
    """Due to b/118365122, it's better to disable private DNS mode while
       testing. 8.8.8.8 private dns sever is unstable now, sometimes server
       will not response dns query suddenly.

    Args:
        ad: An AndroidDevice object.
    """
    tutils.get_operator_name(ad.log, ad, subId=None)
    if ad.adb.shell("settings get global private_dns_mode") != "off":
        ad.log.info("Disable Private DNS mode.")
        ad.adb.shell("settings put global private_dns_mode off")


def _init_device(ad):
    """Init GNSS test devices.

    Args:
        ad: An AndroidDevice object.
    """
    check_location_service(ad)
    enable_gnss_verbose_logging(ad)
    prepare_gps_overlay(ad)
    set_screen_always_on(ad)
    ad.log.info("Setting Bluetooth state to False")
    ad.droid.bluetoothToggleState(False)
    set_wifi_and_bt_scanning(ad, True)
    disable_private_dns_mode(ad)
    init_gtw_gpstool(ad)
    if is_device_wearable(ad):
        disable_battery_defend(ad)


def prepare_gps_overlay(ad):
    """Set pixellogger gps log mask to
    resolve gps logs unreplayable from brcm vendor
    """
    if not check_chipset_vendor_by_qualcomm(ad):
        overlay_file = "/data/vendor/gps/overlay/gps_overlay.xml"
        xml_file = generate_gps_overlay_xml(ad)
        try:
            ad.log.info("Push gps_overlay to device")
            ad.adb.push(xml_file, overlay_file)
            ad.adb.shell(f"chmod 777 {overlay_file}")
        finally:
            xml_folder = os.path.abspath(os.path.join(xml_file, os.pardir))
            shutil.rmtree(xml_folder)


def generate_gps_overlay_xml(ad):
    """For r11 devices, the overlay setting is 'Replayable default'
    For other brcm devices, the setting is 'Replayable debug'

    Returns:
        path to the xml file
    """
    root_attrib = {
        "xmlns": "http://www.glpals.com/",
        "xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance",
        "xsi:schemaLocation": "http://www.glpals.com/ glconfig.xsd",
    }
    sub_attrib = {"EnableOnChipStopNotification": "true"}
    if not is_device_wearable(ad):
        sub_attrib["LogPriMask"] = "LOG_DEBUG"
        sub_attrib["LogFacMask"] = "LOG_GLLIO | LOG_GLLAPI | LOG_NMEA | LOG_RAWDATA"
        sub_attrib["OnChipLogPriMask"] = "LOG_DEBUG"
        sub_attrib["OnChipLogFacMask"] = "LOG_GLLIO | LOG_GLLAPI | LOG_NMEA | LOG_RAWDATA"

    temp_path = tempfile.mkdtemp()
    xml_file = os.path.join(temp_path, "gps_overlay.xml")

    root = ElementTree.Element('glgps')
    for key, value in root_attrib.items():
        root.attrib[key] = value

    ad.log.debug("Sub attrib is %s", sub_attrib)

    sub = ElementTree.SubElement(root, 'gll')
    for key, value in sub_attrib.items():
        sub.attrib[key] = value

    xml = ElementTree.ElementTree(root)
    xml.write(xml_file, xml_declaration=True, encoding="utf-8", method="xml")
    return xml_file


def connect_to_wifi_network(ad, network):
    """Connection logic for open and psk wifi networks.

    Args:
        ad: An AndroidDevice object.
        network: Dictionary with network info.
    """
    SSID = network[WifiEnums.SSID_KEY]
    ad.ed.clear_all_events()
    wutils.reset_wifi(ad)
    wutils.start_wifi_connection_scan_and_ensure_network_found(ad, SSID)
    for i in range(5):
        wutils.wifi_connect(ad, network, check_connectivity=False)
        # Validates wifi connection with ping_gateway=False to avoid issue like
        # b/254913994.
        if wutils.validate_connection(ad, ping_gateway=False):
            ad.log.info("WiFi connection is validated")
            return
    raise signals.TestError("Failed to connect WiFi")

def set_wifi_and_bt_scanning(ad, state=True):
    """Set Wi-Fi and Bluetooth scanning on/off in Settings -> Location

    Args:
        ad: An AndroidDevice object.
        state: True to turn on "Wi-Fi and Bluetooth scanning".
            False to turn off "Wi-Fi and Bluetooth scanning".
    """
    ad.root_adb()
    if state:
        ad.adb.shell("settings put global wifi_scan_always_enabled 1")
        ad.adb.shell("settings put global ble_scan_always_enabled 1")
        ad.log.info("Wi-Fi and Bluetooth scanning are enabled")
    else:
        ad.adb.shell("settings put global wifi_scan_always_enabled 0")
        ad.adb.shell("settings put global ble_scan_always_enabled 0")
        ad.log.info("Wi-Fi and Bluetooth scanning are disabled")


def check_location_service(ad):
    """Set location service on.
       Verify if location service is available.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    utils.set_location_service(ad, True)
    ad.adb.shell("cmd location set-location-enabled true")
    location_mode = int(ad.adb.shell("settings get secure location_mode"))
    ad.log.info("Current Location Mode >> %d" % location_mode)
    if location_mode != 3:
        raise signals.TestError("Failed to turn Location on")


def delete_device_folder(ad, folder):
    ad.log.info("Folder to be deleted: %s" % folder)
    folder_contents = ad.adb.shell(f"ls {folder}", ignore_status=True)
    ad.log.debug("Contents to be deleted: %s" % folder_contents)
    ad.adb.shell("rm -rf %s" % folder, ignore_status=True)


def remove_pixel_logger_folder(ad):
    if check_chipset_vendor_by_qualcomm(ad):
        folder = "/sdcard/Android/data/com.android.pixellogger/files/logs/diag_logs"
    else:
        folder = "/sdcard/Android/data/com.android.pixellogger/files/logs/gps/"

    delete_device_folder(ad, folder)


def clear_logd_gnss_qxdm_log(ad):
    """Clear /data/misc/logd,
    /storage/emulated/0/Android/data/com.android.gpstool/files and
    /data/vendor/radio/diag_logs/logs from previous test item then reboot.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    ad.log.info("Clear Logd, GNSS and PixelLogger Log from previous test item.")
    folders_should_be_removed = ["/data/misc/logd"]
    ad.adb.shell(
        'find %s -name "*.txt" -type f -delete' % GNSSSTATUS_LOG_PATH,
        ignore_status=True)
    if check_chipset_vendor_by_qualcomm(ad):
        output_path = posixpath.join(DEFAULT_QXDM_LOG_PATH, "logs")
        folders_should_be_removed += [output_path]
    else:
        always_on_logger_log_path = ("/data/vendor/gps/logs")
        folders_should_be_removed += [always_on_logger_log_path]
    for folder in folders_should_be_removed:
        delete_device_folder(ad, folder)
    remove_pixel_logger_folder(ad)
    if not is_device_wearable(ad):
        reboot(ad)


def get_gnss_qxdm_log(ad, qdb_path=None):
    """Get /storage/emulated/0/Android/data/com.android.gpstool/files and
    /data/vendor/radio/diag_logs/logs for test item.

    Args:
        ad: An AndroidDevice object.
        qdb_path: The path of qdsp6m.qdb on different projects.
    """
    log_path = ad.device_log_path
    os.makedirs(log_path, exist_ok=True)
    gnss_log_name = "gnssstatus_log_%s_%s" % (ad.model, ad.serial)
    gnss_log_path = posixpath.join(log_path, gnss_log_name)
    os.makedirs(gnss_log_path, exist_ok=True)
    ad.log.info("Pull GnssStatus Log to %s" % gnss_log_path)
    ad.adb.pull("%s %s" % (GNSSSTATUS_LOG_PATH + ".", gnss_log_path),
                timeout=PULL_TIMEOUT, ignore_status=True)
    shutil.make_archive(gnss_log_path, "zip", gnss_log_path)
    shutil.rmtree(gnss_log_path, ignore_errors=True)
    if check_chipset_vendor_by_qualcomm(ad):
        output_path = (
            "/sdcard/Android/data/com.android.pixellogger/files/logs/"
            "diag_logs/.")
    else:
        output_path = (
            "/sdcard/Android/data/com.android.pixellogger/files/logs/gps/.")
    qxdm_log_name = "PixelLogger_%s_%s" % (ad.model, ad.serial)
    qxdm_log_path = posixpath.join(log_path, qxdm_log_name)
    os.makedirs(qxdm_log_path, exist_ok=True)
    ad.log.info("Pull PixelLogger Log %s to %s" % (output_path,
                                                   qxdm_log_path))
    ad.adb.pull("%s %s" % (output_path, qxdm_log_path),
                timeout=PULL_TIMEOUT, ignore_status=True)
    if check_chipset_vendor_by_qualcomm(ad):
        for path in qdb_path:
            output = ad.adb.pull("%s %s" % (path, qxdm_log_path),
                                 timeout=PULL_TIMEOUT, ignore_status=True)
            if "No such file or directory" in output:
                continue
            break
    shutil.make_archive(qxdm_log_path, "zip", qxdm_log_path)
    shutil.rmtree(qxdm_log_path, ignore_errors=True)


def set_mobile_data(ad, state):
    """Set mobile data on or off and check mobile data state.

    Args:
        ad: An AndroidDevice object.
        state: True to enable mobile data. False to disable mobile data.
    """
    ad.root_adb()
    if state:
        if is_device_wearable(ad):
            ad.log.info("Enable wearable mobile data.")
            ad.adb.shell("settings put global cell_on 1")
        else:
            ad.log.info("Enable mobile data via RPC call.")
            ad.droid.telephonyToggleDataConnection(True)
    else:
        if is_device_wearable(ad):
            ad.log.info("Disable wearable mobile data.")
            ad.adb.shell("settings put global cell_on 0")
        else:
            ad.log.info("Disable mobile data via RPC call.")
            ad.droid.telephonyToggleDataConnection(False)
    time.sleep(5)
    ret_val = is_mobile_data_on(ad)
    if state and ret_val:
        ad.log.info("Mobile data is enabled and set to %s" % ret_val)
    elif not state and not ret_val:
        ad.log.info("Mobile data is disabled and set to %s" % ret_val)
    else:
        ad.log.error("Mobile data is at unknown state and set to %s" % ret_val)


def gnss_trigger_modem_ssr_by_adb(ad, dwelltime=60):
    """Trigger modem SSR crash by adb and verify if modem crash and recover
    successfully.

    Args:
        ad: An AndroidDevice object.
        dwelltime: Waiting time for modem reset. Default is 60 seconds.

    Returns:
        True if success.
        False if failed.
    """
    begin_time = get_current_epoch_time()
    ad.root_adb()
    cmds = ("echo restart > /sys/kernel/debug/msm_subsys/modem",
            r"echo 'at+cfun=1,1\r' > /dev/at_mdm0")
    for cmd in cmds:
        ad.log.info("Triggering modem SSR crash by %s" % cmd)
        output = ad.adb.shell(cmd, ignore_status=True)
        if "No such file or directory" in output:
            continue
        break
    time.sleep(dwelltime)
    ad.send_keycode("HOME")
    logcat_results = ad.search_logcat("SSRObserver", begin_time)
    if logcat_results:
        for ssr in logcat_results:
            if "mSubsystem='modem', mCrashReason" in ssr["log_message"]:
                ad.log.debug(ssr["log_message"])
                ad.log.info("Triggering modem SSR crash successfully.")
                return True
        raise signals.TestError("Failed to trigger modem SSR crash")
    raise signals.TestError("No SSRObserver found in logcat")


def gnss_trigger_modem_ssr_by_mds(ad, dwelltime=60):
    """Trigger modem SSR crash by mds tool and verify if modem crash and recover
    successfully.

    Args:
        ad: An AndroidDevice object.
        dwelltime: Waiting time for modem reset. Default is 60 seconds.

    Returns:
        ssr_crash_time: The epoch time SSR is crashed
    """
    mds_check = ad.adb.shell("pm path com.google.mdstest")
    if not mds_check:
        raise signals.TestError("MDS Tool is not properly installed.")
    ad.root_adb()
    cmd = ('am instrument -w -e request "4b 25 03 00" '
           '"com.google.mdstest/com.google.mdstest.instrument'
           '.ModemCommandInstrumentation"')
    ad.log.info("Triggering modem SSR crash by MDS")
    ssr_crash_time = get_current_epoch_time()
    output = ad.adb.shell(cmd, ignore_status=True)
    ad.log.debug(output)
    time.sleep(dwelltime)
    ad.send_keycode("HOME")
    if "SUCCESS" in output:
        ad.log.info("Triggering modem SSR crash by MDS successfully.")
    else:
        raise signals.TestError(
            "Failed to trigger modem SSR crash by MDS. \n%s" % output)
    return ssr_crash_time


def check_xtra_download(ad, begin_time):
    """Verify XTRA download success log message in logcat.

    Args:
        ad: An AndroidDevice object.
        begin_time: test begin time

    Returns:
        True: xtra_download if XTRA downloaded and injected successfully
        otherwise return False.
    """
    ad.send_keycode("HOME")
    if check_chipset_vendor_by_qualcomm(ad):
        xtra_results = ad.search_logcat("XTRA download success. "
                                        "inject data into modem", begin_time)
        if xtra_results:
            ad.log.debug("%s" % xtra_results[-1]["log_message"])
            ad.log.info("XTRA downloaded and injected successfully.")
            return True
        ad.log.error("XTRA downloaded FAIL.")
    else:
        if is_device_wearable(ad):
            lto_results = ad.search_logcat("GnssLocationProvider: "
                                           "calling native_inject_psds_data", begin_time)
        else:
            lto_results = ad.search_logcat("GnssPsdsAidl: injectPsdsData: "
                                           "psdsType: 1", begin_time)
        if lto_results:
            ad.log.debug("%s" % lto_results[-1]["log_message"])
            ad.log.info("LTO downloaded and injected successfully.")
            return True
        ad.log.error("LTO downloaded and inject FAIL.")
    return False


def pull_package_apk(ad, package_name):
    """Pull apk of given package_name from device.

    Args:
        ad: An AndroidDevice object.
        package_name: Package name of apk to pull.

    Returns:
        The temp path of pulled apk.
    """
    out = ad.adb.shell("pm path %s" % package_name)
    result = re.search(r"package:(.*)", out)
    if not result:
        raise signals.TestError("Couldn't find apk of %s" % package_name)
    else:
        apk_source = result.group(1)
        ad.log.info("Get apk of %s from %s" % (package_name, apk_source))
        apk_path = tempfile.mkdtemp()
        ad.pull_files([apk_source], apk_path)
    return apk_path


def pull_gnss_cfg_file(ad, file):
    """Pull given gnss cfg file from device.

    Args:
        ad: An AndroidDevice object.
        file: CFG file in device to pull.

    Returns:
        The temp path of pulled gnss cfg file in host.
    """
    ad.root_adb()
    host_dest = tempfile.mkdtemp()
    ad.pull_files(file, host_dest)
    for path_key in os.listdir(host_dest):
        if fnmatch.fnmatch(path_key, "*.cfg"):
            gnss_cfg_file = os.path.join(host_dest, path_key)
            break
    else:
        raise signals.TestError("No cfg file is found in %s" % host_dest)
    return gnss_cfg_file


def reinstall_package_apk(ad, package_name, apk_path):
    """Reinstall apk of given package_name.

    Args:
        ad: An AndroidDevice object.
        package_name: Package name of apk.
        apk_path: The temp path of pulled apk.
    """
    for path_key in os.listdir(apk_path):
        if fnmatch.fnmatch(path_key, "*.apk"):
            apk_path = os.path.join(apk_path, path_key)
            break
    else:
        raise signals.TestError("No apk is found in %s" % apk_path)
    ad.log.info("Re-install %s with path: %s" % (package_name, apk_path))
    ad.adb.shell("settings put global verifier_verify_adb_installs 0")
    ad.adb.install("-r -d -g --user 0 %s" % apk_path)
    package_check = ad.adb.shell("pm path %s" % package_name)
    if not package_check:
        tutils.abort_all_tests(
            ad.log, "%s is not properly re-installed." % package_name)
    ad.log.info("%s is re-installed successfully." % package_name)


def init_gtw_gpstool(ad):
    """Init GTW_GPSTool apk.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    gpstool_path = pull_package_apk(ad, "com.android.gpstool")
    reinstall_package_apk(ad, "com.android.gpstool", gpstool_path)
    shutil.rmtree(gpstool_path, ignore_errors=True)


def fastboot_factory_reset(ad, state=True):
    """Factory reset the device in fastboot mode.
       Pull sl4a apk from device. Terminate all sl4a sessions,
       Reboot the device to bootloader,
       factory reset the device by fastboot.
       Reboot the device. wait for device to complete booting
       Re-install and start an sl4a session.

    Args:
        ad: An AndroidDevice object.
        State: True for exit_setup_wizard, False for not exit_setup_wizard.

    Returns:
        True if factory reset process complete.
    """
    status = True
    mds_path = ""
    gnss_cfg_file = ""
    gnss_cfg_path = "/vendor/etc/mdlog"
    default_gnss_cfg = "/vendor/etc/mdlog/DEFAULT+SECURITY+FULLDPL+GPS.cfg"
    sl4a_path = pull_package_apk(ad, SL4A_APK_NAME)
    gpstool_path = pull_package_apk(ad, "com.android.gpstool")
    if check_chipset_vendor_by_qualcomm(ad):
        mds_path = pull_package_apk(ad, "com.google.mdstest")
        gnss_cfg_file = pull_gnss_cfg_file(ad, default_gnss_cfg)
    stop_pixel_logger(ad)
    ad.stop_services()
    for i in range(1, 4):
        try:
            if ad.serial in list_adb_devices():
                ad.log.info("Reboot to bootloader")
                ad.adb.reboot("bootloader", ignore_status=True)
                time.sleep(10)
            if ad.serial in list_fastboot_devices():
                ad.log.info("Factory reset in fastboot")
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
            if is_device_wearable(ad):
                ad.log.info("Wait 5 mins for wearable projects system busy time.")
                time.sleep(300)
            reinstall_package_apk(ad, SL4A_APK_NAME, sl4a_path)
            reinstall_package_apk(ad, "com.android.gpstool", gpstool_path)
            if check_chipset_vendor_by_qualcomm(ad):
                reinstall_package_apk(ad, "com.google.mdstest", mds_path)
                ad.push_system_file(gnss_cfg_file, gnss_cfg_path)
            time.sleep(10)
            break
        except Exception as e:
            ad.log.error(e)
            if i == attempts:
                tutils.abort_all_tests(ad.log, str(e))
            time.sleep(5)
    try:
        ad.start_adb_logcat()
    except Exception as e:
        ad.log.error(e)
    if state:
        ad.exit_setup_wizard()
    if ad.skip_sl4a:
        return status
    tutils.bring_up_sl4a(ad)
    for path in [sl4a_path, gpstool_path, mds_path, gnss_cfg_file]:
        shutil.rmtree(path, ignore_errors=True)
    return status


def clear_aiding_data_by_gtw_gpstool(ad):
    """Launch GTW GPSTool and Clear all GNSS aiding data.
       Wait 5 seconds for GTW GPStool to clear all GNSS aiding
       data properly.

    Args:
        ad: An AndroidDevice object.
    """
    if not check_chipset_vendor_by_qualcomm(ad):
        delete_lto_file(ad)
    ad.log.info("Launch GTW GPSTool and Clear all GNSS aiding data")
    ad.adb.shell("am start -S -n com.android.gpstool/.GPSTool --es mode clear")
    time.sleep(10)


def start_gnss_by_gtw_gpstool(ad,
                              state,
                              api_type="gnss",
                              bgdisplay=False,
                              freq=0,
                              lowpower=False,
                              meas=False):
    """Start or stop GNSS on GTW_GPSTool.

    Args:
        ad: An AndroidDevice object.
        state: True to start GNSS. False to Stop GNSS.
        api_type: Different API for location fix. Use gnss/flp/nmea
        bgdisplay: true to run GTW when Display off. false to not run GTW when
          Display off.
        freq: An integer to set location update frequency.
        meas: A Boolean to set GNSS measurement registration.
        lowpower: A boolean to set GNSS LowPowerMode.
    """
    cmd = "am start -S -n com.android.gpstool/.GPSTool --es mode gps"
    if not state:
        ad.log.info("Stop %s on GTW_GPSTool." % api_type)
        cmd = "am broadcast -a com.android.gpstool.stop_gps_action"
    else:
        options = ("--es type {} --ei freq {} --ez BG {} --ez meas {} --ez "
                   "lowpower {}").format(api_type, freq, bgdisplay, meas, lowpower)
        cmd = cmd + " " + options
    ad.adb.shell(cmd, ignore_status=True, timeout = 300)
    time.sleep(3)


def process_gnss_by_gtw_gpstool(ad,
                                criteria,
                                api_type="gnss",
                                clear_data=True,
                                meas_flag=False,
                                freq=0,
                                bg_display=False):
    """Launch GTW GPSTool and Clear all GNSS aiding data
       Start GNSS tracking on GTW_GPSTool.

    Args:
        ad: An AndroidDevice object.
        criteria: Criteria for current test item.
        api_type: Different API for location fix. Use gnss/flp/nmea
        clear_data: True to clear GNSS aiding data. False is not to. Default
        set to True.
        meas_flag: True to enable GnssMeasurement. False is not to. Default
        set to False.
        freq: An integer to set location update frequency. Default set to 0.
        bg_display: To enable GPS tool bg display or not

    Returns:
        First fix datetime obj

    Raises:
        signals.TestFailure: when first fixed is over criteria or not even get first fixed
    """
    retries = 3
    for i in range(retries):
        if not ad.is_adb_logcat_on:
            ad.start_adb_logcat()
        check_adblog_functionality(ad)
        check_location_runtime_permissions(
            ad, GNSSTOOL_PACKAGE_NAME, GNSSTOOL_PERMISSIONS)
        begin_time = get_current_epoch_time()
        if clear_data:
            clear_aiding_data_by_gtw_gpstool(ad)
        ad.log.info("Start %s on GTW_GPSTool - attempt %d" % (api_type.upper(),
                                                              i+1))
        start_gnss_by_gtw_gpstool(ad, state=True, api_type=api_type, meas=meas_flag, freq=freq,
                                  bgdisplay=bg_display)
        for _ in range(10 + criteria):
            logcat_results = ad.search_logcat("First fixed", begin_time)
            if logcat_results:
                ad.log.debug(logcat_results[-1]["log_message"])
                first_fixed = int(logcat_results[-1]["log_message"].split()[-1])
                ad.log.info("%s First fixed = %.3f seconds" %
                            (api_type.upper(), first_fixed/1000))
                if (first_fixed/1000) <= criteria:
                    return logcat_results[-1]["datetime_obj"]
                start_gnss_by_gtw_gpstool(ad, state=False, api_type=api_type)
                raise signals.TestFailure("Fail to get %s location fixed "
                                          "within %d seconds criteria."
                                          % (api_type.upper(), criteria))
            time.sleep(1)
        check_current_focus_app(ad)
        start_gnss_by_gtw_gpstool(ad, state=False, api_type=api_type)
    raise signals.TestFailure("Fail to get %s location fixed within %d "
                              "attempts." % (api_type.upper(), retries))


def start_ttff_by_gtw_gpstool(ad,
                              ttff_mode,
                              iteration,
                              aid_data=False,
                              raninterval=False,
                              mininterval=10,
                              maxinterval=40,
                              hot_warm_sleep=300,
                              timeout=60):
    """Identify which TTFF mode for different test items.

    Args:
        ad: An AndroidDevice object.
        ttff_mode: TTFF Test mode for current test item.
        iteration: Iteration of TTFF cycles.
        aid_data: Boolean for identify aid_data existed or not
        raninterval: Boolean for identify random interval of TTFF in enable or not.
        mininterval: Minimum value of random interval pool. The unit is second.
        maxinterval: Maximum value of random interval pool. The unit is second.
        hot_warm_sleep: Wait time for acquiring Almanac.
        timeout: TTFF time out. The unit is second.
    Returns:
        latest_start_time: (Datetime) the start time of latest successful TTFF
    """
    begin_time = get_current_epoch_time()
    ad.log.debug("[start_ttff] Search logcat start time: %s" % begin_time)
    if (ttff_mode == "hs" or ttff_mode == "ws") and not aid_data:
        ad.log.info("Wait {} seconds to start TTFF {}...".format(
            hot_warm_sleep, ttff_mode.upper()))
        time.sleep(hot_warm_sleep)
    if ttff_mode == "cs":
        ad.log.info("Start TTFF Cold Start...")
        time.sleep(3)
    elif ttff_mode == "csa":
        ad.log.info("Start TTFF CSWith Assist...")
        time.sleep(3)
    for i in range(1, 4):
        try:
            ad.log.info(f"Before sending TTFF gms version is {get_gms_version(ad)}")
            ad.adb.shell("am broadcast -a com.android.gpstool.ttff_action "
                         "--es ttff {} --es cycle {}  --ez raninterval {} "
                         "--ei mininterval {} --ei maxinterval {}".format(
                         ttff_mode, iteration, raninterval, mininterval,
                         maxinterval))
        except job.TimeoutError:
            # If this is the last retry and we still get timeout error, raises the timeoutError.
            if i == 3:
                raise
            # Currently we encounter lots of timeout issue in Qualcomm devices. But so far we don't
            # know the root cause yet. In order to continue the test, we ignore the timeout for
            # retry.
            ad.log.warn("Send TTFF command timeout.")
            ad.log.info(f"Current gms version is {get_gms_version(ad)}")
            # Wait 2 second to retry
            time.sleep(2)
            continue
        time.sleep(1)
        result = ad.search_logcat("act=com.android.gpstool.start_test_action", begin_time)
        if result:
            ad.log.debug("TTFF start log %s" % result)
            latest_start_time = max(list(map(lambda x: x['datetime_obj'], result)))
            ad.log.info("Send TTFF start_test_action successfully.")
            return latest_start_time
    else:
        check_current_focus_app(ad)
        raise signals.TestError("Fail to send TTFF start_test_action.")


def gnss_tracking_via_gtw_gpstool(ad,
                                  criteria,
                                  api_type="gnss",
                                  testtime=60,
                                  meas_flag=False,
                                  freq=0,
                                  is_screen_off=False):
    """Start GNSS/FLP tracking tests for input testtime on GTW_GPSTool.

    Args:
        ad: An AndroidDevice object.
        criteria: Criteria for current TTFF.
        api_type: Different API for location fix. Use gnss/flp/nmea
        testtime: Tracking test time for minutes. Default set to 60 minutes.
        meas_flag: True to enable GnssMeasurement. False is not to. Default
        set to False.
        freq: An integer to set location update frequency. Default set to 0.
        is_screen_off: whether to turn off during tracking

    Returns:
        The datetime obj of first fixed
    """
    first_fixed_time = process_gnss_by_gtw_gpstool(
        ad, criteria=criteria, api_type=api_type, meas_flag=meas_flag, freq=freq,
        bg_display=is_screen_off)
    ad.log.info("Start %s tracking test for %d minutes" % (api_type.upper(),
                                                           testtime))
    begin_time = get_current_epoch_time()
    with set_screen_status(ad, off=is_screen_off):
        wait_n_mins_for_gnss_tracking(ad, begin_time, testtime, api_type)
        ad.log.info("Successfully tested for %d minutes" % testtime)
    start_gnss_by_gtw_gpstool(ad, state=False, api_type=api_type)

    return first_fixed_time


def wait_n_mins_for_gnss_tracking(ad, begin_time, testtime, api_type="gnss",
                                  ignore_hal_crash=False):
    """Waits for GNSS tracking to finish and detect GNSS crash during the waiting time.

    Args:
        ad: An AndroidDevice object.
        begin_time: The start time of tracking.
        api_type: Different API for location fix. Use gnss/flp/nmea
        testtime: Tracking test time for minutes.
        ignore_hal_crash: To ignore HAL crash error no not.
    """
    while get_current_epoch_time() - begin_time < testtime * 60 * 1000:
        detect_crash_during_tracking(ad, begin_time, api_type, ignore_hal_crash)
        # add sleep here to avoid too many request and cause device not responding
        time.sleep(1)

def run_ttff_via_gtw_gpstool(ad, mode, criteria, test_cycle, true_location,
                             raninterval: bool = False, mininterval: int = 10,
                             maxinterval: int = 40):
    """Run GNSS TTFF test with selected mode and parse the results.

    Args:
        mode: "cs", "ws" or "hs"
        criteria: Criteria for the TTFF.

    Returns:
        ttff_data: A dict of all TTFF data.
    """
    # Before running TTFF, we will run tracking and try to get first fixed.
    # But the TTFF before TTFF doesn't apply to any criteria, so we set a maximum value.
    process_gnss_by_gtw_gpstool(ad, criteria=FIRST_FIXED_MAX_WAITING_TIME)
    ttff_start_time = start_ttff_by_gtw_gpstool(ad,
                                                mode,
                                                iteration=test_cycle,
                                                raninterval=raninterval,
                                                mininterval=mininterval,
                                                maxinterval=maxinterval)
    ttff_data = process_ttff_by_gtw_gpstool(ad, ttff_start_time, true_location)
    result = check_ttff_data(ad, ttff_data, gnss_constant.TTFF_MODE.get(mode), criteria)
    asserts.assert_true(
        result, "TTFF %s fails to reach designated criteria: %d "
                "seconds." % (gnss_constant.TTFF_MODE.get(mode), criteria))
    return ttff_data

def parse_gtw_gpstool_log(ad, true_position, api_type="gnss", validate_gnssstatus=False):
    """Process GNSS/FLP API logs from GTW GPSTool and output track_data to
    test_run_info for ACTS plugin to parse and display on MobileHarness as
    Property.

    Args:
        ad: An AndroidDevice object.
        true_position: Coordinate as [latitude, longitude] to calculate
        position error.
        api_type: Different API for location fix. Use gnss/flp/nmea
        validate_gnssstatus: Validate gnssstatus or not

    Returns:
        A dict of location reported from GPSTool
            {<utc_time>: TRACK_REPORT, ...}
    """
    gnssstatus_count = 0
    test_logfile = {}
    track_data = {}
    ant_top4_cn = 0
    ant_cn = 0
    base_top4_cn = 0
    base_cn = 0
    track_lat = 0
    track_long = 0
    l5flag = "false"
    gps_datetime_pattern = re.compile("(\d{4}/\d{2}/\d{2} \d{2}:\d{2}:\d{2}\.\d{0,5})")
    gps_datetime_format = "%Y/%m/%d %H:%M:%S.%f"
    file_count = int(ad.adb.shell("find %s -type f -iname *.txt | wc -l"
                                  % GNSSSTATUS_LOG_PATH))
    if file_count != 1:
        ad.log.warn("%d API logs exist." % file_count)
    dir_file = ad.adb.shell("ls -tr %s" % GNSSSTATUS_LOG_PATH).split()
    for path_key in dir_file:
        if fnmatch.fnmatch(path_key, "*.txt"):
            logpath = posixpath.join(GNSSSTATUS_LOG_PATH, path_key)
            out = ad.adb.shell("wc -c %s" % logpath)
            file_size = int(out.split(" ")[0])
            if file_size < 2000:
                ad.log.info("Skip log %s due to log size %d bytes" %
                            (path_key, file_size))
                continue
            test_logfile = logpath
    if not test_logfile:
        raise signals.TestError("Failed to get test log file in device.")
    lines = ad.adb.shell("cat %s" % test_logfile).split("\n")
    gnss_svid_container = gnssstatus_utils.GnssSvidContainer()
    for line in lines:
        if line.startswith('Fix'):
            try:
                gnss_status = gnssstatus_utils.GnssStatus(line)
                gnssstatus_count += 1
            except gnssstatus_utils.RegexParseException as e:
                ad.log.warn(e)
                continue

            gnss_svid_container.add_satellite(gnss_status)
            if validate_gnssstatus:
                gnss_status.validate_gnssstatus()

        if "Antenna_History Avg Top4" in line:
            ant_top4_cn = float(line.split(":")[-1].strip())
        elif "Antenna_History Avg" in line:
            ant_cn = float(line.split(":")[-1].strip())
        elif "Baseband_History Avg Top4" in line:
            base_top4_cn = float(line.split(":")[-1].strip())
        elif "Baseband_History Avg" in line:
            base_cn = float(line.split(":")[-1].strip())
        elif "L5 used in fix" in line:
            l5flag = line.split(":")[-1].strip()
        elif "Latitude" in line:
            track_lat = float(line.split(":")[-1].strip())
        elif "Longitude" in line:
            track_long = float(line.split(":")[-1].strip())
        elif "Read:" in line:
            target = re.search(gps_datetime_pattern, line)
            device_time = datetime.strptime(target.group(1), gps_datetime_format)
        elif "Time" in line:
            target = re.search(gps_datetime_pattern, line)
            track_utc = target.group(1)
            report_time = datetime.strptime(track_utc, gps_datetime_format)
            if track_utc in track_data.keys():
                continue
            pe = calculate_position_error(track_lat, track_long, true_position)
            track_data[track_utc] = TRACK_REPORT(l5flag=l5flag,
                                                 pe=pe,
                                                 ant_top4cn=ant_top4_cn,
                                                 ant_cn=ant_cn,
                                                 base_top4cn=base_top4_cn,
                                                 base_cn=base_cn,
                                                 device_time=device_time,
                                                 report_time=report_time,
                                                 )
    ad.log.info("Total %d gnssstatus samples verified" %gnssstatus_count)
    ad.log.debug(track_data)
    prop_basename = UPLOAD_TO_SPONGE_PREFIX + f"{api_type.upper()}_tracking_"
    time_list = sorted(track_data.keys())
    l5flag_list = [track_data[key].l5flag for key in time_list]
    pe_list = [float(track_data[key].pe) for key in time_list]
    ant_top4cn_list = [float(track_data[key].ant_top4cn) for key in time_list]
    ant_cn_list = [float(track_data[key].ant_cn) for key in time_list]
    base_top4cn_list = [float(track_data[key].base_top4cn) for key in time_list]
    base_cn_list = [float(track_data[key].base_cn) for key in time_list]
    ad.log.info(prop_basename+"StartTime %s" % time_list[0].replace(" ", "-"))
    ad.log.info(prop_basename+"EndTime %s" % time_list[-1].replace(" ", "-"))
    ad.log.info(prop_basename+"TotalFixPoints %d" % len(time_list))
    ad.log.info(prop_basename+"L5FixRate "+'{percent:.2%}'.format(
        percent=l5flag_list.count("true")/len(l5flag_list)))
    ad.log.info(prop_basename+"AvgDis %.1f" % (sum(pe_list)/len(pe_list)))
    ad.log.info(prop_basename+"MaxDis %.1f" % max(pe_list))
    ad.log.info(prop_basename+"Ant_AvgTop4Signal %.1f" % ant_top4cn_list[-1])
    ad.log.info(prop_basename+"Ant_AvgSignal %.1f" % ant_cn_list[-1])
    ad.log.info(prop_basename+"Base_AvgTop4Signal %.1f" % base_top4cn_list[-1])
    ad.log.info(prop_basename+"Base_AvgSignal %.1f" % base_cn_list[-1])
    _log_svid_info(gnss_svid_container, prop_basename, ad)
    return track_data


def verify_gps_time_should_be_close_to_device_time(ad, tracking_result):
    """Check the time gap between GPS time and device time.

    In normal cases, the GPS time should be close to device time. But if GPS week rollover happens,
    the GPS time may goes back to 20 years ago. In order to capture this issue, we assert the time
    diff between the GPS time and device time.

    Args:
        ad: The device under test.
        tracking_result: The result we get from GNSS tracking.
    """
    ad.log.info("Validating GPS/Device time difference")
    max_time_diff_in_seconds = 3.0 if is_device_wearable() else 2.0
    exceed_report = []
    for report in tracking_result.values():
        time_diff_in_seconds = abs((report.report_time - report.device_time).total_seconds())
        if time_diff_in_seconds > max_time_diff_in_seconds:
            message = (f"GPS time: {report.report_time}  Device time: {report.device_time} "
                       f"diff: {time_diff_in_seconds}")
            exceed_report.append(message)
    fail_message = (f"The following items exceed {max_time_diff_in_seconds}s\n" +
                     "\n".join(exceed_report))
    asserts.assert_false(exceed_report, msg=fail_message)


def validate_location_fix_rate(ad, location_reported, run_time, fix_rate_criteria):
    """Check location reported count

    The formula is "total_fix_points / (run_time * 60)"
    When the result is lower than fix_rate_criteria, fail the test case

    Args:
        ad: AndroidDevice object
        location_reported: (Enumerate) Contains the reported location
        run_time: (int) How many minutes do we need to verify
        fix_rate_criteria: The threshold of the pass criteria
            if we expect fix rate to be 99%, then fix_rate_criteria should be 0.99
    """
    ad.log.info("Validating fix rate")
    pass_criteria = run_time * 60 * fix_rate_criteria
    actual_location_count = len(location_reported)

    # The fix rate may exceed 100% occasionally, to standardlize the result
    # set maximum fix rate to 100%
    actual_fix_rate = min(1, (actual_location_count / (run_time * 60)))
    actual_fix_rate_percentage = f"{actual_fix_rate:.0%}"

    log_prefix = UPLOAD_TO_SPONGE_PREFIX + f"FIX_RATE_"
    ad.log.info("%sresult %s" % (log_prefix, actual_fix_rate_percentage))
    ad.log.debug("Actual location count %s" % actual_location_count)

    fail_message = (f"Fail to meet criteria. Expect to have at least {pass_criteria} location count"
                    f" Actual: {actual_location_count}")
    asserts.assert_true(pass_criteria <= actual_location_count, msg=fail_message)


def _log_svid_info(container, log_prefix, ad):
    """Write GnssSvidContainer svid information into logger
    Args:
        container: A GnssSvidContainer object
        log_prefix:
            A prefix used to specify the log will be upload to dashboard
        ad: An AndroidDevice object
    """
    for sv_type, svids in container.used_in_fix.items():
        message = f"{log_prefix}{sv_type} {len(svids)}"
        ad.log.info(message)
        ad.log.debug("Satellite used in fix %s ids are: %s", sv_type, svids)

    for sv_type, svids in container.not_used_in_fix.items():
        ad.log.debug("Satellite not used in fix %s ids are: %s", sv_type, svids)


def process_ttff_by_gtw_gpstool(ad, begin_time, true_position, api_type="gnss"):
    """Process TTFF and record results in ttff_data.

    Args:
        ad: An AndroidDevice object.
        begin_time: test begin time.
        true_position: Coordinate as [latitude, longitude] to calculate
        position error.
        api_type: Different API for location fix. Use gnss/flp/nmea

    Returns:
        ttff_data: A dict of all TTFF data.
    """
    ttff_lat = 0
    ttff_lon = 0
    utc_time = epoch_to_human_time(get_current_epoch_time())
    ttff_data = {}
    ttff_loop_time = get_current_epoch_time()
    while True:
        if get_current_epoch_time() - ttff_loop_time >= 120000:
            raise signals.TestError("Fail to search specific GPSService "
                                    "message in logcat. Abort test.")
        if not ad.is_adb_logcat_on:
            ad.start_adb_logcat()
        logcat_results = ad.search_logcat("write TTFF log", ttff_loop_time)
        if logcat_results:
            ttff_loop_time = get_current_epoch_time()
            ttff_log = logcat_results[-1]["log_message"].split()
            ttff_loop = int(ttff_log[8].split(":")[-1])
            ttff_sec = float(ttff_log[11])
            if ttff_sec != 0.0:
                ttff_ant_cn = float(ttff_log[18].strip("]"))
                ttff_base_cn = float(ttff_log[25].strip("]"))
                if api_type == "gnss":
                    gnss_results = ad.search_logcat("GPSService: Check item",
                                                    begin_time)
                    if gnss_results:
                        ad.log.debug(gnss_results[-1]["log_message"])
                        gnss_location_log = \
                            gnss_results[-1]["log_message"].split()
                        ttff_lat = float(
                            gnss_location_log[8].split("=")[-1].strip(","))
                        ttff_lon = float(
                            gnss_location_log[9].split("=")[-1].strip(","))
                        loc_time = int(
                            gnss_location_log[10].split("=")[-1].strip(","))
                        utc_time = epoch_to_human_time(loc_time)
                        ttff_haccu = float(
                            gnss_location_log[11].split("=")[-1].strip(","))
                elif api_type == "flp":
                    flp_results = ad.search_logcat("GPSService: FLP Location",
                                                   begin_time)
                    if flp_results:
                        ad.log.debug(flp_results[-1]["log_message"])
                        flp_location_log = flp_results[-1][
                            "log_message"].split()
                        ttff_lat = float(flp_location_log[8].split(",")[0])
                        ttff_lon = float(flp_location_log[8].split(",")[1])
                        ttff_haccu = float(flp_location_log[9].split("=")[1])
                        utc_time = epoch_to_human_time(get_current_epoch_time())
            else:
                ttff_ant_cn = float(ttff_log[19].strip("]"))
                ttff_base_cn = float(ttff_log[26].strip("]"))
                ttff_lat = 0
                ttff_lon = 0
                ttff_haccu = 0
                utc_time = epoch_to_human_time(get_current_epoch_time())
            ad.log.debug("TTFF Loop %d - (Lat, Lon) = (%s, %s)" % (ttff_loop,
                                                                   ttff_lat,
                                                                   ttff_lon))
            ttff_pe = calculate_position_error(
                ttff_lat, ttff_lon, true_position)
            ttff_data[ttff_loop] = TTFF_REPORT(utc_time=utc_time,
                                               ttff_loop=ttff_loop,
                                               ttff_sec=ttff_sec,
                                               ttff_pe=ttff_pe,
                                               ttff_ant_cn=ttff_ant_cn,
                                               ttff_base_cn=ttff_base_cn,
                                               ttff_haccu=ttff_haccu)
            ad.log.info("UTC Time = %s, Loop %d = %.1f seconds, "
                        "Position Error = %.1f meters, "
                        "Antenna Average Signal = %.1f dbHz, "
                        "Baseband Average Signal = %.1f dbHz, "
                        "Horizontal Accuracy = %.1f meters" % (utc_time,
                                                                 ttff_loop,
                                                                 ttff_sec,
                                                                 ttff_pe,
                                                                 ttff_ant_cn,
                                                                 ttff_base_cn,
                                                                 ttff_haccu))
        stop_gps_results = ad.search_logcat("stop gps test", begin_time)
        if stop_gps_results:
            ad.send_keycode("HOME")
            break
        crash_result = ad.search_logcat("Force finishing activity "
                                        "com.android.gpstool/.GPSTool",
                                        begin_time)
        if crash_result:
            raise signals.TestError("GPSTool crashed. Abort test.")
        # wait 5 seconds to avoid logs not writing into logcat yet
        time.sleep(5)
    return ttff_data


def check_ttff_data(ad, ttff_data, ttff_mode, criteria):
    """Verify all TTFF results from ttff_data.

    Args:
        ad: An AndroidDevice object.
        ttff_data: TTFF data of secs, position error and signal strength.
        ttff_mode: TTFF Test mode for current test item.
        criteria: Criteria for current test item.

    Returns:
        True: All TTFF results are within criteria.
        False: One or more TTFF results exceed criteria or Timeout.
    """
    ad.log.info("%d iterations of TTFF %s tests finished."
                % (len(ttff_data.keys()), ttff_mode))
    ad.log.info("%s PASS criteria is %d seconds" % (ttff_mode, criteria))
    ad.log.debug("%s TTFF data: %s" % (ttff_mode, ttff_data))
    if len(ttff_data.keys()) == 0:
        ad.log.error("GTW_GPSTool didn't process TTFF properly.")
        raise ValueError("No ttff loop is done")

    ttff_property_key_and_value(ad, ttff_data, ttff_mode)

    if any(float(ttff_data[key].ttff_sec) == 0.0 for key in ttff_data.keys()):
        ad.log.error("One or more TTFF %s Timeout" % ttff_mode)
        return False
    elif any(float(ttff_data[key].ttff_sec) >= criteria for key in
             ttff_data.keys()):
        ad.log.error("One or more TTFF %s are over test criteria %d seconds"
                     % (ttff_mode, criteria))
        return False
    ad.log.info("All TTFF %s are within test criteria %d seconds."
                % (ttff_mode, criteria))
    return True


def ttff_property_key_and_value(ad, ttff_data, ttff_mode):
    """Output ttff_data to test_run_info for ACTS plugin to parse and display
    on MobileHarness as Property.

    Args:
        ad: An AndroidDevice object.
        ttff_data: TTFF data of secs, position error and signal strength.
        ttff_mode: TTFF Test mode for current test item.
    """
    timeout_ttff = 61
    prop_basename = "TestResult "+ttff_mode.replace(" ", "_")+"_TTFF_"
    sec_list = [float(ttff_data[key].ttff_sec) for key in ttff_data.keys()]
    pe_list = [float(ttff_data[key].ttff_pe) for key in ttff_data.keys()]
    ant_cn_list = [float(ttff_data[key].ttff_ant_cn) for key in
                   ttff_data.keys()]
    base_cn_list = [float(ttff_data[key].ttff_base_cn) for key in
                    ttff_data.keys()]
    haccu_list = [float(ttff_data[key].ttff_haccu) for key in
                    ttff_data.keys()]
    timeoutcount = sec_list.count(0.0)
    sec_list = sorted(sec_list)
    if len(sec_list) == timeoutcount:
        median_ttff = avgttff = timeout_ttff
    else:
        avgttff = sum(sec_list)/(len(sec_list) - timeoutcount)
        median_ttff = median(sec_list)
    if timeoutcount != 0:
        maxttff = timeout_ttff
    else:
        maxttff = max(sec_list)
    avgdis = sum(pe_list)/len(pe_list)
    maxdis = max(pe_list)
    ant_avgcn = sum(ant_cn_list)/len(ant_cn_list)
    base_avgcn = sum(base_cn_list)/len(base_cn_list)
    avg_haccu = sum(haccu_list)/len(haccu_list)
    ad.log.info(prop_basename+"AvgTime %.1f" % avgttff)
    ad.log.info(prop_basename+"MedianTime %.1f" % median_ttff)
    ad.log.info(prop_basename+"MaxTime %.1f" % maxttff)
    ad.log.info(prop_basename+"TimeoutCount %d" % timeoutcount)
    ad.log.info(prop_basename+"AvgDis %.1f" % avgdis)
    ad.log.info(prop_basename+"MaxDis %.1f" % maxdis)
    ad.log.info(prop_basename+"Ant_AvgSignal %.1f" % ant_avgcn)
    ad.log.info(prop_basename+"Base_AvgSignal %.1f" % base_avgcn)
    ad.log.info(prop_basename+"Avg_Horizontal_Accuracy %.1f" % avg_haccu)


def calculate_position_error(latitude, longitude, true_position):
    """Use haversine formula to calculate position error base on true location
    coordinate.

    Args:
        latitude: latitude of location fixed in the present.
        longitude: longitude of location fixed in the present.
        true_position: [latitude, longitude] of true location coordinate.

    Returns:
        position_error of location fixed in the present.
    """
    radius = 6371009
    dlat = math.radians(latitude - true_position[0])
    dlon = math.radians(longitude - true_position[1])
    a = math.sin(dlat/2) * math.sin(dlat/2) + \
        math.cos(math.radians(true_position[0])) * \
        math.cos(math.radians(latitude)) * math.sin(dlon/2) * math.sin(dlon/2)
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return radius * c


def launch_google_map(ad):
    """Launch Google Map via intent.

    Args:
        ad: An AndroidDevice object.
    """
    ad.log.info("Launch Google Map.")
    try:
        if is_device_wearable(ad):
            cmd = ("am start -S -n com.google.android.apps.maps/"
                   "com.google.android.apps.gmmwearable.MainActivity")
        else:
            cmd = ("am start -S -n com.google.android.apps.maps/"
                   "com.google.android.maps.MapsActivity")
        ad.adb.shell(cmd)
        ad.send_keycode("BACK")
        ad.force_stop_apk("com.google.android.apps.maps")
        ad.adb.shell(cmd)
    except Exception as e:
        ad.log.error(e)
        raise signals.TestError("Failed to launch google map.")
    check_current_focus_app(ad)


def check_current_focus_app(ad):
    """Check to see current focused window and app.

    Args:
        ad: An AndroidDevice object.
    Returns:
        string: the current focused window / app
    """
    time.sleep(1)
    current = ad.adb.shell(
        "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'")
    ad.log.debug("\n"+current)
    return current


def check_location_api(ad, retries):
    """Verify if GnssLocationProvider API reports location.

    Args:
        ad: An AndroidDevice object.
        retries: Retry time.

    Returns:
        True: GnssLocationProvider API reports location.
        otherwise return False.
    """
    for i in range(retries):
        begin_time = get_current_epoch_time()
        ad.log.info("Try to get location report from GnssLocationProvider API "
                    "- attempt %d" % (i+1))
        while get_current_epoch_time() - begin_time <= 30000:
            logcat_results = ad.search_logcat("reportLocation", begin_time)
            if logcat_results:
                ad.log.info("%s" % logcat_results[-1]["log_message"])
                ad.log.info("GnssLocationProvider reports location "
                            "successfully.")
                return True
        if not ad.is_adb_logcat_on:
            ad.start_adb_logcat()
    ad.log.error("GnssLocationProvider is unable to report location.")
    return False


def check_network_location(ad, retries, location_type, criteria=30):
    """Verify if NLP reports location after requesting via GPSTool.

    Args:
        ad: An AndroidDevice object.
        retries: Retry time.
        location_type: cell or wifi.
        criteria: expected nlp return time, default 30 seconds

    Returns:
        True: NLP reports location.
        otherwise return False.
    """
    criteria = criteria * 1000
    search_pattern = ("GPSTool : networkLocationType = %s" % location_type)
    for i in range(retries):
        # Capture the begin time 1 seconds before due to time gap.
        begin_time = get_current_epoch_time() - 1000
        ad.log.info("Try to get NLP status - attempt %d" % (i+1))
        ad.adb.shell(
            "am start -S -n com.android.gpstool/.GPSTool --es mode nlp")
        while get_current_epoch_time() - begin_time <= criteria:
            # Search pattern in 1 second interval
            time.sleep(1)
            result = ad.search_logcat(search_pattern, begin_time)
            if result:
                ad.log.info("Pattern Found: %s." % result[-1]["log_message"])
                ad.send_keycode("BACK")
                return True
        if not ad.is_adb_logcat_on:
            ad.start_adb_logcat()
        ad.send_keycode("BACK")
    ad.log.error("Unable to report network location \"%s\"." % location_type)
    return False


def set_attenuator_gnss_signal(ad, attenuator, atten_value):
    """Set attenuation value for different GNSS signal.

    Args:
        ad: An AndroidDevice object.
        attenuator: The attenuator object.
        atten_value: attenuation value
    """
    try:
        ad.log.info(
            "Set attenuation value to \"%d\" for GNSS signal." % atten_value)
        attenuator[0].set_atten(atten_value)
    except Exception as e:
        ad.log.error(e)


def set_battery_saver_mode(ad, state):
    """Enable or disable battery saver mode via adb.

    Args:
        ad: An AndroidDevice object.
        state: True is enable Battery Saver mode. False is disable.
    """
    ad.root_adb()
    if state:
        ad.log.info("Enable Battery Saver mode.")
        ad.adb.shell("cmd battery unplug")
        ad.adb.shell("settings put global low_power 1")
    else:
        ad.log.info("Disable Battery Saver mode.")
        ad.adb.shell("settings put global low_power 0")
        ad.adb.shell("cmd battery reset")


def set_gnss_qxdm_mask(ad, masks):
    """Find defined gnss qxdm mask and set as default logging mask.

    Args:
        ad: An AndroidDevice object.
        masks: Defined gnss qxdm mask.
    """
    try:
        for mask in masks:
            if not tlutils.find_qxdm_log_mask(ad, mask):
                continue
            tlutils.set_qxdm_logger_command(ad, mask)
            break
    except Exception as e:
        ad.log.error(e)
        raise signals.TestError("Failed to set any QXDM masks.")


def start_youtube_video(ad, url=None, retries=0):
    """Start youtube video and verify if audio is in music state.

    Args:
        ad: An AndroidDevice object.
        url: Youtube video url.
        retries: Retry times if audio is not in music state.

    Returns:
        True if youtube video is playing normally.
        False if youtube video is not playing properly.
    """
    for i in range(retries):
        ad.log.info("Open an youtube video - attempt %d" % (i+1))
        cmd = ("am start -n com.google.android.youtube/"
               "com.google.android.youtube.UrlActivity -d \"%s\"" % url)
        ad.adb.shell(cmd)
        time.sleep(2)
        out = ad.adb.shell(
            "dumpsys activity | grep NewVersionAvailableActivity")
        if out:
            ad.log.info("Skip Youtube New Version Update.")
            ad.send_keycode("BACK")
        if tutils.wait_for_state(ad.droid.audioIsMusicActive, True, 15, 1):
            ad.log.info("Started a video in youtube, audio is in MUSIC state")
            return True
        ad.log.info("Force-Stop youtube and reopen youtube again.")
        ad.force_stop_apk("com.google.android.youtube")
    check_current_focus_app(ad)
    raise signals.TestError("Started a video in youtube, "
                            "but audio is not in MUSIC state")


def get_gms_version(ad):
    cmd = "dumpsys package com.google.android.gms | grep versionName"
    return ad.adb.shell(cmd).split("\n")[0].split("=")[1]


def get_baseband_and_gms_version(ad, extra_msg=""):
    """Get current radio baseband and GMSCore version of AndroidDevice object.

    Args:
        ad: An AndroidDevice object.
        extra_msg: Extra message before or after the change.
    """
    mpss_version = ""
    brcm_gps_version = ""
    brcm_sensorhub_version = ""
    try:
        build_version = ad.adb.getprop("ro.build.id")
        baseband_version = ad.adb.getprop("gsm.version.baseband")
        gms_version = get_gms_version(ad)
        if check_chipset_vendor_by_qualcomm(ad):
            mpss_version = ad.adb.shell(
                "cat /sys/devices/soc0/images | grep MPSS | cut -d ':' -f 3")
        else:
            brcm_gps_version = ad.adb.shell("cat /data/vendor/gps/chip.info")
            sensorhub_version = ad.adb.shell(
                "cat /vendor/firmware/SensorHub.patch | grep ChangeList")
            brcm_sensorhub_version = re.compile(
                r'<ChangeList=(\w+)>').search(sensorhub_version).group(1)
        if not extra_msg:
            ad.log.info("TestResult Build_Version %s" % build_version)
            ad.log.info("TestResult Baseband_Version %s" % baseband_version)
            ad.log.info(
                "TestResult GMS_Version %s" % gms_version.replace(" ", ""))
            if check_chipset_vendor_by_qualcomm(ad):
                ad.log.info("TestResult MPSS_Version %s" % mpss_version)
            else:
                ad.log.info("TestResult GPS_Version %s" % brcm_gps_version)
                ad.log.info(
                    "TestResult SensorHub_Version %s" % brcm_sensorhub_version)
        else:
            ad.log.info(
                "%s, Baseband_Version = %s" % (extra_msg, baseband_version))
    except Exception as e:
        ad.log.error(e)


def start_toggle_gnss_by_gtw_gpstool(ad, iteration):
    """Send toggle gnss off/on start_test_action

    Args:
        ad: An AndroidDevice object.
        iteration: Iteration of toggle gnss off/on cycles.
    """
    msg_list = []
    begin_time = get_current_epoch_time()
    try:
        for i in range(1, 4):
            ad.adb.shell("am start -S -n com.android.gpstool/.GPSTool "
                         "--es mode toggle --es cycle %d" % iteration)
            time.sleep(1)
            if is_device_wearable(ad):
                # Wait 20 seconds for Wearable low performance time.
                time.sleep(20)
                if ad.search_logcat("ToggleGPS onResume",
                                begin_time):
                    ad.log.info("Send ToggleGPS start_test_action successfully.")
                    break
            elif ad.search_logcat("cmp=com.android.gpstool/.ToggleGPS",
                                begin_time):
                ad.log.info("Send ToggleGPS start_test_action successfully.")
                break
        else:
            check_current_focus_app(ad)
            raise signals.TestError("Fail to send ToggleGPS "
                                    "start_test_action within 3 attempts.")
        time.sleep(2)
        if is_device_wearable(ad):
            test_start = ad.search_logcat("GPSService: create toggle GPS log",
                                      begin_time)
        else:
            test_start = ad.search_logcat("GPSTool_ToggleGPS: startService",
                                      begin_time)
        if test_start:
            ad.log.info(test_start[-1]["log_message"].split(":")[-1].strip())
        else:
            raise signals.TestError("Fail to start toggle GPS off/on test.")
        # Every iteration is expected to finish within 4 minutes.
        while get_current_epoch_time() - begin_time <= iteration * 240000:
            crash_end = ad.search_logcat("Force finishing activity "
                                         "com.android.gpstool/.GPSTool",
                                         begin_time)
            if crash_end:
                raise signals.TestError("GPSTool crashed. Abort test.")
            toggle_results = ad.search_logcat("GPSTool : msg", begin_time)
            if toggle_results:
                for toggle_result in toggle_results:
                    msg = toggle_result["log_message"]
                    if not msg in msg_list:
                        ad.log.info(msg.split(":")[-1].strip())
                        msg_list.append(msg)
                    if "timeout" in msg:
                        raise signals.TestFailure("Fail to get location fixed "
                                                  "within 60 seconds.")
                    if "Test end" in msg:
                        raise signals.TestPass("Completed quick toggle GNSS "
                                               "off/on test.")
        raise signals.TestFailure("Fail to finish toggle GPS off/on test "
                                  "within %d minutes" % (iteration * 4))
    finally:
        ad.send_keycode("HOME")


def grant_location_permission(ad, option):
    """Grant or revoke location related permission.

    Args:
        ad: An AndroidDevice object.
        option: Boolean to grant or revoke location related permissions.
    """
    action = "grant" if option else "revoke"
    for permission in LOCATION_PERMISSIONS:
        ad.log.info(
            "%s permission:%s on %s" % (action, permission, TEST_PACKAGE_NAME))
        ad.adb.shell("pm %s %s %s" % (action, TEST_PACKAGE_NAME, permission))


def check_location_runtime_permissions(ad, package, permissions):
    """Check if runtime permissions are granted on selected package.

    Args:
        ad: An AndroidDevice object.
        package: Apk package name to check.
        permissions: A list of permissions to be granted.
    """
    for _ in range(3):
        location_runtime_permission = ad.adb.shell(
            "dumpsys package %s | grep ACCESS_FINE_LOCATION" % package)
        if "true" not in location_runtime_permission:
            ad.log.info("ACCESS_FINE_LOCATION is NOT granted on %s" % package)
            for permission in permissions:
                ad.log.debug("Grant %s on %s" % (permission, package))
                ad.adb.shell("pm grant %s %s" % (package, permission))
        else:
            ad.log.info("ACCESS_FINE_LOCATION is granted on %s" % package)
            break
    else:
        raise signals.TestError(
            "Fail to grant ACCESS_FINE_LOCATION on %s" % package)


def install_mdstest_app(ad, mdsapp):
    """
        Install MDS test app in DUT

        Args:
            ad: An Android Device Object
            mdsapp: Installation path of MDSTest app
    """
    if not ad.is_apk_installed("com.google.mdstest"):
        ad.adb.install("-r %s" % mdsapp, timeout=300, ignore_status=True)


def write_modemconfig(ad, mdsapp, nvitem_dict, modemparfile):
    """
        Modify the NV items using modem_tool.par
        Note: modem_tool.par

        Args:
            ad:  An Android Device Object
            mdsapp: Installation path of MDSTest app
            nvitem_dict: dictionary of NV items and values.
            modemparfile: modem_tool.par path.
    """
    ad.log.info("Verify MDSTest app installed in DUT")
    install_mdstest_app(ad, mdsapp)
    os.system("chmod 777 %s" % modemparfile)
    for key, value in nvitem_dict.items():
        if key.isdigit():
            op_name = "WriteEFS"
        else:
            op_name = "WriteNV"
        ad.log.info("Modifying the NV{!r} using {}".format(key, op_name))
        job.run("{} --op {} --item {} --data '{}'".
                format(modemparfile, op_name, key, value))
        time.sleep(2)


def verify_modemconfig(ad, nvitem_dict, modemparfile):
    """
        Verify the NV items using modem_tool.par
        Note: modem_tool.par

        Args:
            ad:  An Android Device Object
            nvitem_dict: dictionary of NV items and values
            modemparfile: modem_tool.par path.
    """
    os.system("chmod 777 %s" % modemparfile)
    for key, value in nvitem_dict.items():
        if key.isdigit():
            op_name = "ReadEFS"
        else:
            op_name = "ReadNV"
        # Sleeptime to avoid Modem communication error
        time.sleep(5)
        result = job.run(
            "{} --op {} --item {}".format(modemparfile, op_name, key))
        output = str(result.stdout)
        ad.log.info("Actual Value for NV{!r} is {!r}".format(key, output))
        if not value.casefold() in output:
            ad.log.error("NV Value is wrong {!r} in {!r}".format(value, result))
            raise ValueError(
                "could not find {!r} in {!r}".format(value, result))


def check_ttff_pe(ad, ttff_data, ttff_mode, pe_criteria):
    """Verify all TTFF results from ttff_data.

    Args:
        ad: An AndroidDevice object.
        ttff_data: TTFF data of secs, position error and signal strength.
        ttff_mode: TTFF Test mode for current test item.
        pe_criteria: Criteria for current test item.

    """
    ad.log.info("%d iterations of TTFF %s tests finished."
                % (len(ttff_data.keys()), ttff_mode))
    ad.log.info("%s PASS criteria is %f meters" % (ttff_mode, pe_criteria))
    ad.log.debug("%s TTFF data: %s" % (ttff_mode, ttff_data))

    if len(ttff_data.keys()) == 0:
        ad.log.error("GTW_GPSTool didn't process TTFF properly.")
        raise signals.TestFailure("GTW_GPSTool didn't process TTFF properly.")

    elif any(float(ttff_data[key].ttff_pe) >= pe_criteria for key in
             ttff_data.keys()):
        ad.log.error("One or more TTFF %s are over test criteria %f meters"
                     % (ttff_mode, pe_criteria))
        raise signals.TestFailure("GTW_GPSTool didn't process TTFF properly.")
    else:
        ad.log.info("All TTFF %s are within test criteria %f meters." % (
            ttff_mode, pe_criteria))
        return True


def check_adblog_functionality(ad):
    """Restart adb logcat if system can't write logs into file after checking
    adblog file size.

    Args:
        ad: An AndroidDevice object.
    """
    logcat_path = os.path.join(ad.device_log_path, "adblog_%s_debug.txt" %
                               ad.serial)
    if not os.path.exists(logcat_path):
        raise signals.TestError("Logcat file %s does not exist." % logcat_path)
    original_log_size = os.path.getsize(logcat_path)
    ad.log.debug("Original adblog size is %d" % original_log_size)
    time.sleep(.5)
    current_log_size = os.path.getsize(logcat_path)
    ad.log.debug("Current adblog size is %d" % current_log_size)
    if current_log_size == original_log_size:
        ad.log.warn("System can't write logs into file. Restart adb "
                    "logcat process now.")
        ad.stop_adb_logcat()
        ad.start_adb_logcat()


def build_instrumentation_call(package,
                               runner,
                               test_methods=None,
                               options=None):
    """Build an instrumentation call for the tests

    Args:
        package: A string to identify test package.
        runner: A string to identify test runner.
        test_methods: A dictionary contains {class_name, test_method}.
        options: A dictionary constant {key, value} param for test.

    Returns:
        An instrumentation call command.
    """
    if test_methods is None:
        test_methods = {}
        cmd_builder = InstrumentationCommandBuilder()
    else:
        cmd_builder = InstrumentationTestCommandBuilder()
    if options is None:
        options = {}
    cmd_builder.set_manifest_package(package)
    cmd_builder.set_runner(runner)
    cmd_builder.add_flag("-w")
    for class_name, test_method in test_methods.items():
        cmd_builder.add_test_method(class_name, test_method)
    for option_key, option_value in options.items():
        cmd_builder.add_key_value_param(option_key, option_value)
    return cmd_builder.build()


def check_chipset_vendor_by_qualcomm(ad):
    """Check if chipset vendor is by Qualcomm.

    Args:
        ad: An AndroidDevice object.

    Returns:
        True if it's by Qualcomm. False irf not.
    """
    if is_device_wearable(ad):
        props = str(ad.adb.shell("getprop"))
        return True if _WEARABLE_QCOM_VENDOR_REGEX.search(props) else False
    else:
        soc = str(ad.adb.shell("getprop gsm.version.ril-impl"))
        ad.log.debug("SOC = %s" % soc)
        return "Qualcomm" in soc


def delete_lto_file(ad):
    """Delete downloaded LTO files.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    status = ad.adb.shell("rm -rf /data/vendor/gps/lto*")
    ad.log.info("Delete downloaded LTO files.\n%s" % status)


def lto_mode(ad, state):
    """Enable or Disable LTO mode.

    Args:
        ad: An AndroidDevice object.
        state: True to enable. False to disable.
    """
    server_list = ["LONGTERM_PSDS_SERVER_1",
                   "LONGTERM_PSDS_SERVER_2",
                   "LONGTERM_PSDS_SERVER_3",
                   "NORMAL_PSDS_SERVER",
                   "REALTIME_PSDS_SERVER"]
    delete_lto_file(ad)
    if state:
        tmp_path = tempfile.mkdtemp()
        ad.pull_files("/etc/gps_debug.conf", tmp_path)
        gps_conf_path = os.path.join(tmp_path, "gps_debug.conf")
        gps_conf_file = open(gps_conf_path, "r")
        lines = gps_conf_file.readlines()
        gps_conf_file.close()
        fout = open(gps_conf_path, "w")
        for line in lines:
            for server in server_list:
                if server in line:
                    line = line.replace(line, "")
            fout.write(line)
        fout.close()
        ad.push_system_file(gps_conf_path, "/etc/gps_debug.conf")
        ad.log.info("Push back modified gps_debug.conf")
        ad.log.info("LTO/RTO/RTI enabled")
        shutil.rmtree(tmp_path, ignore_errors=True)
    else:
        ad.adb.shell("echo %r >> /etc/gps_debug.conf" %
                     DISABLE_LTO_FILE_CONTENTS)
        ad.log.info("LTO/RTO/RTI disabled")
    reboot(ad)


def lto_mode_wearable(ad, state):
    """Enable or Disable LTO mode for wearable in Android R release.

    Args:
        ad: An AndroidDevice object.
        state: True to enable. False to disable.
    """
    rto_enable = '    RtoEnable="true"\n'
    rto_disable = '    RtoEnable="false"\n'
    rti_enable = '    RtiEnable="true"\n'
    rti_disable = '    RtiEnable="false"\n'
    sync_lto_enable = '    HttpDirectSyncLto="true"\n'
    sync_lto_disable = '    HttpDirectSyncLto="false"\n'
    server_list = ["XTRA_SERVER_1", "XTRA_SERVER_2", "XTRA_SERVER_3"]
    delete_lto_file(ad)
    tmp_path = tempfile.mkdtemp()
    ad.pull_files("/vendor/etc/gnss/gps.xml", tmp_path)
    gps_xml_path = os.path.join(tmp_path, "gps.xml")
    gps_xml_file = open(gps_xml_path, "r")
    lines = gps_xml_file.readlines()
    gps_xml_file.close()
    fout = open(gps_xml_path, "w")
    for line in lines:
        if state:
            if rto_disable in line:
                line = line.replace(line, rto_enable)
                ad.log.info("RTO enabled")
            elif rti_disable in line:
                line = line.replace(line, rti_enable)
                ad.log.info("RTI enabled")
            elif sync_lto_disable in line:
                line = line.replace(line, sync_lto_enable)
                ad.log.info("LTO sync enabled")
        else:
            if rto_enable in line:
                line = line.replace(line, rto_disable)
                ad.log.info("RTO disabled")
            elif rti_enable in line:
                line = line.replace(line, rti_disable)
                ad.log.info("RTI disabled")
            elif sync_lto_enable in line:
                line = line.replace(line, sync_lto_disable)
                ad.log.info("LTO sync disabled")
        fout.write(line)
    fout.close()
    ad.push_system_file(gps_xml_path, "/vendor/etc/gnss/gps.xml")
    ad.log.info("Push back modified gps.xml")
    shutil.rmtree(tmp_path, ignore_errors=True)
    if state:
        xtra_tmp_path = tempfile.mkdtemp()
        ad.pull_files("/etc/gps_debug.conf", xtra_tmp_path)
        gps_conf_path = os.path.join(xtra_tmp_path, "gps_debug.conf")
        gps_conf_file = open(gps_conf_path, "r")
        lines = gps_conf_file.readlines()
        gps_conf_file.close()
        fout = open(gps_conf_path, "w")
        for line in lines:
            for server in server_list:
                if server in line:
                    line = line.replace(line, "")
            fout.write(line)
        fout.close()
        ad.push_system_file(gps_conf_path, "/etc/gps_debug.conf")
        ad.log.info("Push back modified gps_debug.conf")
        ad.log.info("LTO/RTO/RTI enabled")
        shutil.rmtree(xtra_tmp_path, ignore_errors=True)
    else:
        ad.adb.shell(
            "echo %r >> /etc/gps_debug.conf" % DISABLE_LTO_FILE_CONTENTS_R)
        ad.log.info("LTO/RTO/RTI disabled")


def start_pixel_logger(ad, max_log_size_mb=100, max_number_of_files=500):
    """adb to start pixel logger for GNSS logging.

    Args:
        ad: An AndroidDevice object.
        max_log_size_mb: Determines when to create a new log file if current
            one reaches the size limit.
        max_number_of_files: Determines how many log files can be saved on DUT.
    """
    retries = 3
    start_timeout_sec = 60
    default_gnss_cfg = "/vendor/etc/mdlog/DEFAULT+SECURITY+FULLDPL+GPS.cfg"
    if check_chipset_vendor_by_qualcomm(ad):
        start_cmd = ("am startservice -a com.android.pixellogger."
                     "service.logging.LoggingService.ACTION_START_LOGGING "
                     "-e intent_key_cfg_path '%s' "
                     "--ei intent_key_max_log_size_mb %d "
                     "--ei intent_key_max_number_of_files %d" %
                     (default_gnss_cfg, max_log_size_mb, max_number_of_files))
    else:
        start_cmd = ("am startservice -a com.android.pixellogger."
                     "service.logging.LoggingService.ACTION_START_LOGGING "
                     "-e intent_logger brcm_gps "
                     "--ei intent_key_max_log_size_mb %d "
                     "--ei intent_key_max_number_of_files %d" %
                     (max_log_size_mb, max_number_of_files))
    for attempt in range(retries):
        begin_time = get_current_epoch_time() - 3000
        ad.log.info("Start Pixel Logger - Attempt %d" % (attempt + 1))
        ad.adb.shell(start_cmd)
        while get_current_epoch_time() - begin_time <= start_timeout_sec * 1000:
            if not ad.is_adb_logcat_on:
                ad.start_adb_logcat()
            if check_chipset_vendor_by_qualcomm(ad):
                start_result = ad.search_logcat(
                    "ModemLogger: Start logging", begin_time)
            else:
                start_result = ad.search_logcat("startRecording", begin_time)
            if start_result:
                ad.log.info("Pixel Logger starts recording successfully.")
                return True
        stop_pixel_logger(ad)
    else:
        ad.log.warn("Pixel Logger fails to start recording in %d seconds "
                    "within %d attempts." % (start_timeout_sec, retries))


def stop_pixel_logger(ad):
    """adb to stop pixel logger for GNSS logging.

    Args:
        ad: An AndroidDevice object.
    """
    retries = 3
    stop_timeout_sec = 60
    zip_timeout_sec = 30
    if check_chipset_vendor_by_qualcomm(ad):
        stop_cmd = ("am startservice -a com.android.pixellogger."
                    "service.logging.LoggingService.ACTION_STOP_LOGGING")
    else:
        stop_cmd = ("am startservice -a com.android.pixellogger."
                    "service.logging.LoggingService.ACTION_STOP_LOGGING "
                    "-e intent_logger brcm_gps")
    for attempt in range(retries):
        begin_time = get_current_epoch_time() - 3000
        ad.log.info("Stop Pixel Logger - Attempt %d" % (attempt + 1))
        ad.adb.shell(stop_cmd)
        while get_current_epoch_time() - begin_time <= stop_timeout_sec * 1000:
            if not ad.is_adb_logcat_on:
                ad.start_adb_logcat()
            stop_result = ad.search_logcat(
                "LoggingService: Stopping service", begin_time)
            if stop_result:
                ad.log.info("Pixel Logger stops successfully.")
                zip_end_time = time.time() + zip_timeout_sec
                while time.time() < zip_end_time:
                    zip_file_created = ad.search_logcat(
                        "FileUtil: Zip file has been created", begin_time)
                    if zip_file_created:
                        ad.log.info("Pixel Logger created zip file "
                                    "successfully.")
                        return True
                else:
                    ad.log.warn("Pixel Logger failed to create zip file.")
                    return False
        ad.force_stop_apk("com.android.pixellogger")
    else:
        ad.log.warn("Pixel Logger fails to stop in %d seconds within %d "
                    "attempts." % (stop_timeout_sec, retries))


def launch_eecoexer(ad):
    """Launch EEcoexer.

    Args:
        ad: An AndroidDevice object.
    Raise:
        signals.TestError if DUT fails to launch EEcoexer
    """
    launch_cmd = ("am start -a android.intent.action.MAIN -n"
                  "com.google.eecoexer"
                  "/.MainActivity")
    ad.adb.shell(launch_cmd)
    try:
        ad.log.info("Launch EEcoexer.")
    except Exception as e:
        ad.log.error(e)
        raise signals.TestError("Failed to launch EEcoexer.")


def execute_eecoexer_function(ad, eecoexer_args):
    """Execute EEcoexer commands.

    Args:
        ad: An AndroidDevice object.
        eecoexer_args: EEcoexer function arguments
    """
    cat_index = eecoexer_args.split(',')[:2]
    cat_index = ','.join(cat_index)
    enqueue_cmd = ("am broadcast -a com.google.eecoexer.action.LISTENER"
                   " --es sms_body ENQUEUE,{}".format(eecoexer_args))
    exe_cmd = ("am broadcast -a com.google.eecoexer.action.LISTENER"
               " --es sms_body EXECUTE")
    wait_for_cmd = ("am broadcast -a com.google.eecoexer.action.LISTENER"
                   " --es sms_body WAIT_FOR_COMPLETE,{}".format(cat_index))
    ad.log.info("EEcoexer Add Enqueue: {}".format(eecoexer_args))
    ad.adb.shell(enqueue_cmd)
    ad.log.info("EEcoexer Excute.")
    ad.adb.shell(exe_cmd)
    ad.log.info("Wait EEcoexer for complete")
    ad.adb.shell(wait_for_cmd)


def get_process_pid(ad, process_name):
    """Gets the process PID

    Args:
        ad: The device under test
        process_name: The name of the process

    Returns:
        The PID of the process
    """
    command = f"ps -A | grep {process_name} |  awk '{{print $2}}'"
    pid = ad.adb.shell(command)
    return pid


def restart_gps_daemons(ad, service):
    """Restart GPS daemons by killing services of gpsd, lhd and scd.

    Args:
        ad: An AndroidDevice object.

    Returns:
        kill_start_time: The time GPSd being killed.
    """
    kill_start_time = 0
    ad.root_adb()
    ad.log.info("Kill GPS daemon \"%s\"" % service)
    service_pid = get_process_pid(ad, service)
    ad.log.debug("%s PID: %s" % (service, service_pid))
    ad.adb.shell(f"kill -9 {service_pid}")
    kill_start_time = get_current_epoch_time()
    new_pid, recover_time = get_new_pid_process_time(ad, service_pid, service, 20)

    ad.log.info("GPS daemon \"%s\" restarts successfully. PID from %s to %s" % (
        service, service_pid, new_pid))
    ad.log.info("\t- \"%s\" process recovered time: %d ms" % (service, recover_time))
    return kill_start_time


def get_new_pid_process_time(ad, origin_pid, process_name, timeout):
    """Get the new process PID and the time it took for restarting

    Args:
        ad: An AndroidDevice object.
        origin_pid: The original pid of specified process
        process_name: Name of process
        timeout: Timeout of checking

    Returns:
        1. How long takes for restarting the specified process.
        2. New PID
    """
    begin_time = get_current_epoch_time()
    pid = None
    while not pid and get_current_epoch_time() - begin_time < timeout * 1000:
        pid = get_process_pid(ad, process_name)
        if pid and origin_pid != pid:
            ad.log.debug("%s new PID: %s" % (process_name, pid))
            return pid, get_current_epoch_time() - begin_time
    raise ValueError("Unable to restart \"%s\"" % process_name)


def get_gpsd_update_time(ad, begin_time, dwelltime=30):
    """Get the UTC time of first GPSd status update shows up after begin_time

    Args:
        ad: An AndroidDevice object.
        begin_time: The start time of the log.
        dwelltime: Waiting time for gnss status update. Default is 30 seconds.

    Returns:
        The datetime object which indicates when is first GPSd status update
    """
    ad.log.info("Checking GNSS status after %s",
                datetime.fromtimestamp( begin_time / 1000))
    time.sleep(dwelltime)

    gnss_status = ad.search_logcat("Gnss status update",
                                    begin_time=begin_time)
    if not gnss_status:
        raise ValueError("No \"GNSS status update\" found in logs.")
    ad.log.info("GNSS status update found.")
    return int(gnss_status[0]["datetime_obj"].timestamp() * 1000)


def get_location_fix_time_via_gpstool_log(ad, begin_time):
    """Get the UTC time of location update with given
    device time from the log output by GPSTool.

    Args:
        ad: An AndroidDevice object.
        begin_time: The start time of the log.

    Returns:
        The datetime object which indicates when is the
        first location fix time shows up
    """
    location_fix_time = ad.search_logcat("GPSService: Time",
                                         begin_time=begin_time)
    if not location_fix_time:
        raise ValueError("No \"Location fix time\" found in logs.")
    return int(location_fix_time[0]["datetime_obj"].timestamp() * 1000)


def get_gps_process_and_kill_function_by_vendor(ad):
    """Get process to be killed by vendor and
    return the kill function accordingly

    Args:
        ad: An AndroidDevice object.

    Returns:
        killed_processes: What processes to be killed
        functions: The methods for killing each process
    """
    if check_chipset_vendor_by_qualcomm(ad):
        ad.log.info("Triggered modem SSR")
        return {"ssr": functools.partial(gnss_trigger_modem_ssr_by_mds, ad=ad)}
    else:
        ad.log.info("Triggered restarting GPS daemons")
        return {"gpsd":  functools.partial(restart_gps_daemons, ad=ad, service="gpsd"),
                "scd": functools.partial(restart_gps_daemons, ad=ad, service="scd"),
                "lhd": functools.partial(restart_gps_daemons, ad=ad, service="lhd"),}


def is_device_wearable(ad):
    """Check device is wearable project or not.

    Args:
        ad: An AndroidDevice object.
    """
    package = ad.adb.getprop("ro.cw.home_package_names")
    ad.log.debug("[ro.cw.home_package_names]: [%s]" % package)
    return "wearable" in package


def is_mobile_data_on(ad):
    """Check if mobile data of device is on.

    Args:
        ad: An AndroidDevice object.
    """
    if is_device_wearable(ad):
        cell_on = ad.adb.shell("settings get global cell_on")
        ad.log.debug("Current mobile status is %s" % cell_on)
        return "1" in cell_on
    else:
        return ad.droid.telephonyIsDataEnabled()


def human_to_epoch_time(human_time):
    """Convert human readable time to epoch time.

    Args:
        human_time: Human readable time. (Ex: 2020-08-04 13:24:28.900)

    Returns:
        epoch: Epoch time in milliseconds.
    """
    if "/" in human_time:
        human_time.replace("/", "-")
    try:
        epoch_start = datetime.utcfromtimestamp(0)
        if "." in human_time:
            epoch_time = datetime.strptime(human_time, "%Y-%m-%d %H:%M:%S.%f")
        else:
            epoch_time = datetime.strptime(human_time, "%Y-%m-%d %H:%M:%S")
        epoch = int((epoch_time - epoch_start).total_seconds() * 1000)
        return epoch
    except ValueError:
        return None


def _get_dpo_info_from_logcat(ad, begin_time):
    """Gets the DPO info from logcat.

    Args:
        ad: The device under test.
        begin_time: The start time of the log.
    """
    dpo_results = ad.search_logcat("HardwareClockDiscontinuityCount",
                                   begin_time)
    if not dpo_results:
        raise signals.TestError(
            "No \"HardwareClockDiscontinuityCount\" is found in logs.")
    return dpo_results


def check_dpo_rate_via_gnss_meas(ad, begin_time, dpo_threshold):
    """Check DPO engage rate through "HardwareClockDiscontinuityCount" in
    GnssMeasurement callback.

    Args:
        ad: An AndroidDevice object.
        begin_time: test begin time.
        dpo_threshold: The value to set threshold. (Ex: dpo_threshold = 60)
    """
    time_regex = r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}.\d{3})'
    dpo_results = _get_dpo_info_from_logcat(ad, begin_time)
    ad.log.info(dpo_results[0]["log_message"])
    ad.log.info(dpo_results[-1]["log_message"])
    start_time = re.compile(
        time_regex).search(dpo_results[0]["log_message"]).group(1)
    end_time = re.compile(
        time_regex).search(dpo_results[-1]["log_message"]).group(1)
    gnss_start_epoch = human_to_epoch_time(start_time)
    gnss_stop_epoch = human_to_epoch_time(end_time)
    test_time_in_sec = round((gnss_stop_epoch - gnss_start_epoch) / 1000) + 1
    first_dpo_count = int(dpo_results[0]["log_message"].split()[-1])
    final_dpo_count = int(dpo_results[-1]["log_message"].split()[-1])
    dpo_rate = ((final_dpo_count - first_dpo_count)/test_time_in_sec)
    dpo_engage_rate = "{percent:.2%}".format(percent=dpo_rate)
    ad.log.info("DPO is ON for %d seconds during %d seconds test." % (
        final_dpo_count - first_dpo_count, test_time_in_sec))
    ad.log.info("TestResult DPO_Engage_Rate " + dpo_engage_rate)
    threshold = "{percent:.0%}".format(percent=dpo_threshold / 100)
    asserts.assert_true(dpo_rate * 100 > dpo_threshold,
                        "DPO only engaged %s in %d seconds test with "
                        "threshold %s." % (dpo_engage_rate,
                                           test_time_in_sec,
                                           threshold))


def parse_brcm_nmea_log(ad, nmea_pattern, brcm_error_log_allowlist, stop_logger=True):
    """Parse specific NMEA pattern out of BRCM NMEA log.

    Args:
        ad: An AndroidDevice object.
        nmea_pattern: Specific NMEA pattern to parse.
        brcm_error_log_allowlist: Benign error logs to exclude.
        stop_logger: To stop pixel logger or not.

    Returns:
        brcm_log_list: A list of specific NMEA pattern logs.
    """
    brcm_log_list = []
    brcm_log_error_pattern = ["lhd: FS: Start Failsafe dump", "E slog"]
    brcm_error_log_list = []
    pixellogger_path = (
        "/sdcard/Android/data/com.android.pixellogger/files/logs/gps/.")
    if not isinstance(nmea_pattern, re.Pattern):
        nmea_pattern = re.compile(nmea_pattern)

    with tempfile.TemporaryDirectory() as tmp_dir:
        try:
            ad.pull_files(pixellogger_path, tmp_dir)
        except AdbCommandError:
            raise FileNotFoundError("No pixel logger folders found")

        # Although we don't rely on the zip file, stop pixel logger here to avoid
        # wasting resources.
        if stop_logger:
            stop_pixel_logger(ad)

        tmp_path = pathlib.Path(tmp_dir)
        log_folders = sorted([x for x in tmp_path.iterdir() if x.is_dir()])
        if not log_folders:
            raise FileNotFoundError("No BRCM logs found.")
        # The folder name is a string of datetime, the latest one will be in the last index.
        gl_logs = log_folders[-1].glob("**/gl*.log")

        for nmea_log_path in gl_logs:
            ad.log.info("Parsing log pattern of \"%s\" in %s" % (nmea_pattern,
                                                                 nmea_log_path))
            with open(nmea_log_path, "r", encoding="UTF-8", errors="ignore") as lines:
                for line in lines:
                    line = line.strip()
                    if nmea_pattern.fullmatch(line):
                        brcm_log_list.append(line)
                    for attr in brcm_log_error_pattern:
                        if attr in line:
                            benign_log = False
                            for regex_pattern in brcm_error_log_allowlist:
                                if re.search(regex_pattern, line):
                                    benign_log = True
                                    ad.log.debug("\"%s\" is in allow-list and removed "
                                                "from error." % line)
                            if not benign_log:
                                brcm_error_log_list.append(line)

    brcm_error_log = "".join(brcm_error_log_list)
    return brcm_log_list, brcm_error_log


def _get_power_mode_log_from_pixel_logger(ad, brcm_error_log_allowlist, stop_pixel_logger=True):
    """Gets the power log from pixel logger.

    Args:
        ad: The device under test.
        brcm_error_log_allow_list: The allow list to ignore certain error in pixel logger.
        stop_pixel_logger: To disable pixel logger when getting the log.
    """
    pglor_list, brcm_error_log = parse_brcm_nmea_log(
        ad, _BRCM_DUTY_CYCLE_PATTERN, brcm_error_log_allowlist, stop_pixel_logger)
    if not pglor_list:
        raise signals.TestFailure("Fail to get DPO logs from pixel logger")

    return pglor_list, brcm_error_log


def check_dpo_rate_via_brcm_log(ad, dpo_threshold, brcm_error_log_allowlist):
    """Check DPO engage rate through "$PGLOR,11,STA" in BRCM Log.
    D - Disabled, Always full power.
    F - Enabled, now in full power mode.
    S - Enabled, now in power save mode.
    H - Host off load mode.

    Args:
        ad: An AndroidDevice object.
        dpo_threshold: The value to set threshold. (Ex: dpo_threshold = 60)
        brcm_error_log_allowlist: Benign error logs to exclude.
    """
    always_full_power_count = 0
    full_power_count = 0
    power_save_count = 0
    pglor_list, brcm_error_log = _get_power_mode_log_from_pixel_logger(ad, brcm_error_log_allowlist)

    for pglor in pglor_list:
        power_res = re.compile(r',P,(\w),').search(pglor).group(1)
        if power_res == "D":
            always_full_power_count += 1
        elif power_res == "F":
            full_power_count += 1
        elif power_res == "S":
            power_save_count += 1
    ad.log.info(sorted(pglor_list)[0])
    ad.log.info(sorted(pglor_list)[-1])
    ad.log.info("TestResult Total_Count %d" % len(pglor_list))
    ad.log.info("TestResult Always_Full_Power_Count %d" %
                always_full_power_count)
    ad.log.info("TestResult Full_Power_Mode_Count %d" % full_power_count)
    ad.log.info("TestResult Power_Save_Mode_Count %d" % power_save_count)
    dpo_rate = (power_save_count / len(pglor_list))
    dpo_engage_rate = "{percent:.2%}".format(percent=dpo_rate)
    ad.log.info("Power Save Mode is ON for %d seconds during %d seconds test."
                % (power_save_count, len(pglor_list)))
    ad.log.info("TestResult DPO_Engage_Rate " + dpo_engage_rate)
    threshold = "{percent:.0%}".format(percent=dpo_threshold / 100)
    asserts.assert_true((dpo_rate * 100 > dpo_threshold) and not brcm_error_log,
                        "Power Save Mode only engaged %s in %d seconds test "
                        "with threshold %s.\nAbnormal behavior found as below."
                        "\n%s" % (dpo_engage_rate,
                                  len(pglor_list),
                                  threshold,
                                  brcm_error_log))


def process_pair(watch, phone):
    """Pair phone to watch via Bluetooth in OOBE.

    Args:
        watch: A wearable project.
        phone: A pixel phone.
    """
    check_location_service(phone)
    utils.sync_device_time(phone)
    bt_model_name = watch.adb.getprop("ro.product.model")
    bt_sn_name = watch.adb.getprop("ro.serialno")
    bluetooth_name = bt_model_name +" " + bt_sn_name[10:]
    fastboot_factory_reset(watch, False)
    # TODO (chenstanley)Need to re-structure for better code and test flow instead of simply waiting
    watch.log.info("Wait 1 min for wearable system busy time.")
    time.sleep(60)
    watch.adb.shell("input keyevent 4")
    # Clear Denali paired data in phone.
    phone.adb.shell("pm clear com.google.android.gms")
    phone.adb.shell("pm clear com.google.android.apps.wear.companion")
    phone.adb.shell("am start -S -n com.google.android.apps.wear.companion/"
                        "com.google.android.apps.wear.companion.application.RootActivity")
    uia_click(phone, "Continue")
    uia_click(phone, "More")
    uia_click(phone, "I agree")
    uia_click(phone, "I accept")
    uia_click(phone, bluetooth_name)
    uia_click(phone, "Pair")
    uia_click(phone, "Skip")
    uia_click(phone, "Next")
    uia_click(phone, "Skip")
    uia_click(phone, "Done")
    # TODO (chenstanley)Need to re-structure for better code and test flow instead of simply waiting
    watch.log.info("Wait 3 mins for complete pairing process.")
    time.sleep(180)
    set_screen_always_on(watch)
    check_location_service(watch)
    enable_gnss_verbose_logging(watch)


def is_bluetooth_connected(watch, phone):
    """Check if device's Bluetooth status is connected or not.

    Args:
    watch: A wearable project
    phone: A pixel phone.
    """
    return watch.droid.bluetoothIsDeviceConnected(phone.droid.bluetoothGetLocalAddress())


def detect_crash_during_tracking(ad, begin_time, api_type, ignore_hal_crash=False):
    """Check if GNSS or GPSTool crash happened druing GNSS Tracking.

    Args:
    ad: An AndroidDevice object.
    begin_time: Start Time to check if crash happened in logs.
    api_type: Using GNSS or FLP reading method in GNSS tracking.
    ignore_hal_crash: In BRCM devices, once the HAL is being killed, it will write error/fatal logs.
      Ignore this error if the error logs are expected.
    """
    gnss_crash_list = [".*Fatal signal.*gnss",
                       ".*Fatal signal.*xtra"]
    if not ignore_hal_crash:
        gnss_crash_list += [".*Fatal signal.*gpsd", ".*F DEBUG.*gnss"]
    if not ad.is_adb_logcat_on:
        ad.start_adb_logcat()
    for attr in gnss_crash_list:
        gnss_crash_result = ad.adb.shell(
            "logcat -d | grep -E -i '%s'" % attr, ignore_status=True, timeout = 300)
        if gnss_crash_result:
            start_gnss_by_gtw_gpstool(ad, state=False, api_type=api_type)
            raise signals.TestFailure(
                "Test failed due to GNSS HAL crashed. \n%s" %
                gnss_crash_result)
    gpstool_crash_result = ad.search_logcat("Force finishing activity "
                                            "com.android.gpstool/.GPSTool",
                                            begin_time)
    if gpstool_crash_result:
            raise signals.TestError("GPSTool crashed. Abort test.")


def is_wearable_btwifi(ad):
    """Check device is wearable btwifi sku or not.

    Args:
        ad: An AndroidDevice object.
    """
    package = ad.adb.getprop("ro.product.product.name")
    ad.log.debug("[ro.product.product.name]: [%s]" % package)
    # temp solution. Will check with dev team if there is a command to check.
    return "btwifi" in package or ad.model == 'aurora'


def compare_watch_phone_location(ad,watch_file, phone_file):
    """Compare watch and phone's FLP location to see if the same or not.

    Args:
        ad: An AndroidDevice object.
        watch_file: watch's FLP locations
        phone_file: phone's FLP locations
    """
    not_match_location_counts = 0
    not_match_location = []
    for watch_key, watch_value in watch_file.items():
        if phone_file.get(watch_key):
            lat_ads = abs(float(watch_value[0]) - float(phone_file[watch_key][0]))
            lon_ads = abs(float(watch_value[1]) - float(phone_file[watch_key][1]))
            if lat_ads > 0.000002 or lon_ads > 0.000002:
                not_match_location_counts += 1
                not_match_location += (watch_key, watch_value, phone_file[watch_key])
    if not_match_location_counts > 0:
        ad.log.info("There are %s not match locations: %s" %(not_match_location_counts, not_match_location))
        ad.log.info("Watch's locations are not using Phone's locations.")
        return False
    else:
        ad.log.info("Watch's locations are using Phone's location.")
        return True


def check_tracking_file(ad):
    """Check tracking file in device and save "Latitude", "Longitude", and "Time" information.

    Args:
        ad: An AndroidDevice object.

    Returns:
        location_reports: A dict with [latitude, longitude]
    """
    location_reports = dict()
    test_logfile = {}
    file_count = int(ad.adb.shell("find %s -type f -iname *.txt | wc -l"
                                  % GNSSSTATUS_LOG_PATH))
    if file_count != 1:
        ad.log.error("%d API logs exist." % file_count)
    dir_file = ad.adb.shell("ls %s" % GNSSSTATUS_LOG_PATH).split()
    for path_key in dir_file:
        if fnmatch.fnmatch(path_key, "*.txt"):
            logpath = posixpath.join(GNSSSTATUS_LOG_PATH, path_key)
            out = ad.adb.shell("wc -c %s" % logpath)
            file_size = int(out.split(" ")[0])
            if file_size < 10:
                ad.log.info("Skip log %s due to log size %d bytes" %
                            (path_key, file_size))
                continue
            test_logfile = logpath
    if not test_logfile:
        raise signals.TestError("Failed to get test log file in device.")
    lines = ad.adb.shell("cat %s" % test_logfile).split("\n")
    for file_data in lines:
        if "Latitude:" in file_data:
            file_lat = ("%.6f" %float(file_data[9:]))
        elif "Longitude:" in file_data:
            file_long = ("%.6f" %float(file_data[11:]))
        elif "Time:" in file_data:
            file_time = (file_data[17:25])
            location_reports[file_time] = [file_lat, file_long]
    return location_reports


def uia_click(ad, matching_text):
    """Use uiautomator to click objects.

    Args:
        ad: An AndroidDevice object.
        matching_text: Text of the target object to click
    """
    if ad.uia(textMatches=matching_text).wait.exists(timeout=60000):

        ad.uia(textMatches=matching_text).click()
        ad.log.info("Click button %s" % matching_text)
    else:
        ad.log.error("No button named %s" % matching_text)


def delete_bcm_nvmem_sto_file(ad):
    """Delete BCM's NVMEM ephemeris gldata.sto.

    Args:
        ad: An AndroidDevice object.
    """
    remount_device(ad)
    rm_cmd = "rm -rf {}".format(BCM_NVME_STO_PATH)
    status = ad.adb.shell(rm_cmd)
    ad.log.info("Delete BCM's NVMEM ephemeris files.\n%s" % status)


def bcm_gps_xml_update_option(
    ad, child_tag, items_to_update={}, items_to_delete=[], gps_xml_path=BCM_GPS_XML_PATH):
    """Updates gps.xml attributes.

    The process will go through update first then delete.

    Args:
        ad: Device under test.
        child_tag: (str) Which child node should be updated.
        items_to_update: (dict) The attributes to be updated.
        items_to_delete: (list) The attributes to be deleted.
        gps_xml_path: (str) The gps.xml file path. Default is BCM_GPS_XML_PATH.
    """
    remount_device(ad)
    # to prevent adding nso into xml file
    ElementTree.register_namespace("", "http://www.glpals.com/")
    with tempfile.TemporaryDirectory() as temp_dir:
        local_xml = os.path.join(temp_dir, "gps.xml.ori")
        modified_xml = os.path.join(temp_dir, "gps.xml")
        ad.pull_files(gps_xml_path, local_xml)
        xml_data = ElementTree.parse(local_xml)
        root_data = xml_data.getroot()
        child_node = None

        for node in root_data:
            if node.tag.endswith(child_tag):
                child_node = node
                break

        if child_node is None:
            raise LookupError(f"Couldn't find node with {child_tag}")

        for key, value in items_to_update.items():
            child_node.attrib[key] = value

        for key in items_to_delete:
            if key in child_node.attrib:
                child_node.attrib.pop(key)

        xml_data.write(modified_xml, xml_declaration=True, encoding="utf-8", method="xml")
        ad.push_system_file(modified_xml, gps_xml_path)
    ad.log.info("Finish modify gps.xml")

def bcm_gps_ignore_warmstandby(ad):
    """ remove warmstandby setting in BCM gps.xml to reset tracking filter
    Args:
        ad: An AndroidDevice object.
    """
    search_line_tag = 'gll'
    delete_line_str = ['WarmStandbyTimeout1Seconds', 'WarmStandbyTimeout2Seconds']
    bcm_gps_xml_update_option(ad,
                              child_tag=search_line_tag,
                              items_to_delete=delete_line_str)

def bcm_gps_ignore_rom_alm(ad):
    """ Update BCM gps.xml with ignoreRomAlm="True"
    Args:
        ad: An AndroidDevice object.
    """
    search_line_tag = 'hal'
    append_line_str = {"IgnoreJniTime":"true",
                       "AutoColdStartSignal":"SIMULATED"}
    bcm_gps_xml_update_option(ad, child_tag=search_line_tag, items_to_update=append_line_str)

    search_line_tag = "gll"
    append_line_str = {"IgnoreRomAlm":"true"}
    bcm_gps_xml_update_option(ad, child_tag=search_line_tag, items_to_update=append_line_str)


def check_inject_time(ad):
    """Check if watch could get the UTC time.

    Args:
        ad: An AndroidDevice object.
    """
    for i in range(1, 6):
        time.sleep(10)
        inject_time_results = ad.search_logcat("GPSIC.OUT.gps_inject_time")
        ad.log.info("Check time injected - attempt %s" % i)
        if inject_time_results:
            ad.log.info("Time is injected successfully.")
            return True
    raise signals.TestFailure("Fail to get time injected within %s attempts." % i)

def recover_paired_status(watch, phone):
    """Recover Bluetooth paired status if not paired.

    Args:
        watch: A wearable project.
        phone: A pixel phone.
    """
    for _ in range(3):
        watch.log.info("Switch Bluetooth Off-On to recover paired status.")
        for status in (False, True):
            watch.droid.bluetoothToggleState(status)
            phone.droid.bluetoothToggleState(status)
            # TODO (chenstanley)Need to re-structure for better code and test flow instead of simply waiting
            watch.log.info("Wait for Bluetooth auto re-connect.")
            time.sleep(10)
        if is_bluetooth_connected(watch, phone):
            watch.log.info("Success to recover paired status.")
            return True
    raise signals.TestFailure("Fail to recover BT paired status in 3 attempts.")

def push_lhd_overlay(ad):
    """Push lhd_overlay.conf to device in /data/vendor/gps/overlay/

    ad:
        ad: An AndroidDevice object.
    """
    overlay_name = "lhd_overlay.conf"
    overlay_asset = ad.adb.shell("ls /data/vendor/gps/overlay/")
    if overlay_name in overlay_asset:
        ad.log.info(f"{overlay_name} already in device, skip.")
        return

    temp_path = tempfile.mkdtemp()
    file_path = os.path.join(temp_path, overlay_name)
    lhd_content = 'Lhe477xDebugFlags=RPC:FACILITY=2097151:LOG_INFO:STDOUT_PUTS:STDOUT_LOG\n'\
                  'LogLevel=*:E\nLogLevel=*:W\nLogLevel=*:I\nLog=LOGCAT\nLogEnabled=true\n'
    overlay_path = "/data/vendor/gps/overlay/"
    with open(file_path, "w") as f:
        f.write(lhd_content)
    ad.log.info("Push lhd_overlay to device")
    ad.adb.push(file_path, overlay_path)


def disable_ramdump(ad):
    """Disable ramdump so device will reboot when about to enter ramdump

    Once device enter ramdump, it will take a while to generate dump file
    The process may take a while and block all the tests.
    By disabling the ramdump mode, device will reboot instead of entering ramdump mode

    Args:
        ad: An AndroidDevice object.
    """
    ad.log.info("Enter bootloader mode")
    ad.stop_services()
    ad.adb.reboot("bootloader")
    for _ in range(1,9):
        if ad.is_bootloader:
            break
        time.sleep(1)
    else:
        raise signals.TestFailure("can't enter bootloader mode")
    ad.log.info("Disable ramdump")
    ad.fastboot.oem("ramdump disable")
    ad.fastboot.reboot()
    ad.wait_for_boot_completion()
    ad.root_adb()
    tutils.bring_up_sl4a(ad)
    ad.start_adb_logcat()


def get_device_time(ad):
    """Get current datetime from device

    Args:
        ad: An AndroidDevice object.

    Returns:
        datetime object
    """
    result = ad.adb.shell("date +\"%Y-%m-%d %T.%3N\"")
    return datetime.strptime(result, "%Y-%m-%d %H:%M:%S.%f")


def ensure_power_manager_is_dozing(ad, begin_time):
    """Check if power manager is in dozing
    When device is sleeping, power manager should goes to doze mode.
    To ensure that, we check the log every 1 second (maximum to 3 times)

    Args:
        ad: An AndroidDevice object.
        begin_time: datetime, used as the starting point to search log
    """
    keyword = "PowerManagerService: Dozing"
    ad.log.debug("Log search start time: %s" % begin_time)
    for i in range(0,3):
        result = ad.search_logcat(keyword, begin_time)
        if result:
            break
        ad.log.debug("Power manager is not dozing... retry in 1 second")
        time.sleep(1)
    else:
        ad.log.warn("Power manager didn't enter dozing")

def enter_deep_doze_mode(ad, lasting_time_in_seconds: int):
    """Puts the device into deep doze mode.

    Args:
        ad: The device under test.
        lasting_time_in_seconds: How long does the doze mode last.
    """
    target_time = datetime.now() + timedelta(seconds=lasting_time_in_seconds)

    try:
      ad.log.info("Enter deep doze mode for %d seconds" % lasting_time_in_seconds)
      device_doze.enter_doze_mode(ad, device_doze.DozeType.DEEP)
      while datetime.now() < target_time:
        time.sleep(1)
    finally:
      ad.log.info("Leave deep doze mode")
      device_doze.leave_doze_mode(ad, device_doze.DozeType.DEEP)


def check_location_report_interval(ad, location_reported_time_src, total_seconds, tolerance):
    """Validate the interval between two location reported time
    Normally the interval should be around 1 second but occasionally it may up to nearly 2 seconds
    So we set up a tolerance - 99% of reported interval should be less than 1.3 seconds

    We validate the interval backward, because the wrong interval mostly happened at the end
    Args:
        ad: An AndroidDevice object.
        location_reported_time_src: A list of reported time(in string) from GPS tool
        total_seconds: (int) how many seconds has the GPS been enabled
        tolerance: (float) set how many ratio of error should be accepted
                   if we want to set tolerance to be 1% then pass 0.01 as tolerance value
    """
    ad.log.info("Checking location report frequency")
    error_count = 0
    error_tolerance = max(1, int(total_seconds * tolerance))
    expected_longest_interval = 1.3
    location_reported_time = list(map(lambda x: datetime.strptime(x, "%Y/%m/%d %H:%M:%S.%f"),
                                      location_reported_time_src))
    location_reported_time = sorted(location_reported_time)
    last_gps_report_time = location_reported_time[-1]
    ad.log.debug("Location report time: %s" % location_reported_time)

    for reported_time in reversed(location_reported_time):
        time_diff = last_gps_report_time - reported_time
        if time_diff.total_seconds() > expected_longest_interval:
            error_count += 1
        last_gps_report_time = reported_time

    if error_count > error_tolerance:
        fail_message = (f"Interval longer than {expected_longest_interval}s "
                        f"exceed tolerance count: {error_tolerance}, error count: {error_count}")
        ad.log.error(fail_message)


@contextmanager
def set_screen_status(ad, off=True):
    """Set screen on / off

    A context manager function, can be used with "with" statement.
    example:
        with set_screen_status(ad, off=True):
            do anything you want during screen is off
    Once the function end, it will turn on the screen
    Args:
        ad: AndroidDevice object
        off: (bool) True -> turn off screen / False -> leave screen as it is
    """
    try:
        if off:
            ad.droid.goToSleepNow()
        yield ad
    finally:
        ad.droid.wakeUpNow()
        ensure_device_screen_is_on(ad)


@contextmanager
def full_gnss_measurement(ad):
    """Context manager function to enable full gnss measurement"""
    try:
        ad.adb.shell("settings put global development_settings_enabled 1")
        ad.adb.shell("settings put global enable_gnss_raw_meas_full_tracking 1")
        yield ad
    finally:
        ad.adb.shell("settings put global enable_gnss_raw_meas_full_tracking 0")


def ensure_device_screen_is_on(ad):
    """Make sure the screen is on

    Will try 3 times, each with 1 second interval

    Raise:
        GnssTestUtilsError: if screen can't be turn on after 3 tries
    """
    for _ in range(3):
        # when NotificationShade appears in focus window, it indicates the screen is still off
        if "NotificationShade" not in check_current_focus_app(ad):
            break
        time.sleep(1)
    else:
        raise GnssTestUtilsError("Device screen is not on after 3 tries")


def start_qxdm_and_tcpdump_log(ad, enable):
    """Start QXDM and adb tcpdump if collect_logs is True.
    Args:
        ad: AndroidDevice object
        enable: (bool) True -> start collecting
                       False -> not start collecting
    """
    if enable:
        start_pixel_logger(ad)
        tlutils.start_adb_tcpdump(ad)


def set_screen_always_on(ad):
    """Ensure the sceen will not turn off and display the correct app screen
    for wearable, we also disable the charing screen,
    otherwise the charing screen will keep popping up and block the GPS tool
    """
    if is_device_wearable(ad):
        ad.adb.shell("settings put global stay_on_while_plugged_in 7")
        ad.adb.shell("setprop persist.enable_charging_experience false")
    else:
        ad.adb.shell("settings put system screen_off_timeout 1800000")


def validate_adr_rate(ad, pass_criteria):
    """Check the ADR rate

    Args:
        ad: AndroidDevice object
        pass_criteria: (float) the passing ratio, 1 = 100%, 0.5 = 50%
    """
    adr_statistic = GnssMeasurement(ad).get_adr_static()

    ad.log.info("ADR threshold: {0:.1%}".format(pass_criteria))
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX + "ADR_valid_rate {0:.1%}".format(adr_statistic.valid_rate))
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX +
                "ADR_usable_rate {0:.1%}".format(adr_statistic.usable_rate))
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX + "ADR_total_count %s" % adr_statistic.total_count)
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX + "ADR_valid_count %s" % adr_statistic.valid_count)
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX + "ADR_reset_count %s" % adr_statistic.reset_count)
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX +
                "ADR_cycle_slip_count %s" % adr_statistic.cycle_slip_count)
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX +
                "ADR_half_cycle_reported_count %s" % adr_statistic.half_cycle_reported_count)
    ad.log.info(UPLOAD_TO_SPONGE_PREFIX +
                "ADR_half_cycle_resolved_count %s" % adr_statistic.half_cycle_resolved_count)

    asserts.assert_true(
        (pass_criteria < adr_statistic.valid_rate) and (pass_criteria < adr_statistic.usable_rate),
        f"ADR valid rate: {adr_statistic.valid_rate:.1%}, "
        f"ADR usable rate: {adr_statistic.usable_rate:.1%} "
        f"Lower than expected: {pass_criteria:.1%}"
    )


def pair_to_wearable(watch, phone):
    """Pair watch to phone.

    Args:
        watch: A wearable project.
        phone: A pixel phone.
    Raise:
        TestFailure: If pairing process could not success after 3 tries.
    """
    for _ in range(3):
        process_pair(watch, phone)
        if is_bluetooth_connected(watch, phone):
            watch.log.info("Pairing successfully.")
            return True
    raise signals.TestFailure("Pairing is not successfully.")


def disable_battery_defend(ad):
    """Disable battery defend config to prevent battery defend message pop up
    after connecting to the same charger for 4 days in a row.

    Args:
        ad: A wearable project.
    """
    for _ in range(5):
        remount_device(ad)
        ad.adb.shell("setprop vendor.battery.defender.disable 1")
        # To simulate cable unplug and the status will be recover after device reboot.
        ad.adb.shell("cmd battery unplug")
        # Sleep 3 seconds for waiting adb commend changes config and simulates cable unplug.
        time.sleep(3)
        config_setting = ad.adb.shell("getprop vendor.battery.defender.state")
        if config_setting == "DISABLED":
            ad.log.info("Disable Battery Defend setting successfully.")
            break


def restart_hal_service(ad):
    """Restart HAL service by killing the pid.

    Gets the pid by ps command and pass the pid to kill command. Then we get the pid of HAL service
    again to see if the pid changes(pid should be different after HAL restart). If not, we will
    retry up to 4 times before raising Test Failure.

    Args:
        ad: AndroidDevice object
    """
    ad.log.info("Restart HAL service")
    hal_process_name = "'android.hardware.gnss@[[:digit:]]\{1,2\}\.[[:digit:]]\{1,2\}-service'"
    hal_pid = get_process_pid(ad, hal_process_name)
    ad.log.info("HAL pid: %s" % hal_pid)

    # Retry kill process if the PID is the same as original one
    for _ in range(4):
        ad.log.info("Kill HAL service")
        ad.adb.shell(f"kill -9 {hal_pid}")

        # Waits for the HAL service to restart up to 4 seconds.
        for _ in range(4):
            new_hal_pid = get_process_pid(ad, hal_process_name)
            ad.log.info("New HAL pid: %s" % new_hal_pid)
            if new_hal_pid:
                if hal_pid != new_hal_pid:
                    return
                break
            time.sleep(1)
    else:
        raise signals.TestFailure("HAL service can't be killed")


def run_ttff(ad, mode, criteria, test_cycle, base_lat_long, collect_logs=False):
    """Verify TTFF functionality with mobile data.

    Args:
        mode: "cs", "ws" or "hs"
        criteria: Criteria for the test.

    Returns:
        ttff_data: A dict of all TTFF data.
    """
    start_qxdm_and_tcpdump_log(ad, collect_logs)
    return run_ttff_via_gtw_gpstool(ad, mode, criteria, test_cycle, base_lat_long)


def re_register_measurement_callback(dut):
    """Send command to unregister then register measurement callback.

    Args:
        dut: The device under test.
    """
    dut.log.info("Reregister measurement callback")
    dut.adb.shell("am broadcast -a com.android.gpstool.stop_meas_action")
    time.sleep(1)
    dut.adb.shell("am broadcast -a com.android.gpstool.start_meas_action")
    time.sleep(1)


def check_power_save_mode_status(ad, full_power, begin_time, brcm_error_allowlist):
    """Checks the power save mode status.

    For Broadcom:
        Gets NEMA sentences from pixel logger and retrieve the status [F, S, D].
        F,S => not in full power mode
        D => in full power mode
    For Qualcomm:
        Gets the HardwareClockDiscontinuityCount from logcat. In full power mode, the
        HardwareClockDiscontinuityCount should not be increased.

    Args:
        ad: The device under test.
        full_power: The device is in full power mode or not.
        begin_time: It is used to get the correct logcat information for qualcomm.
        brcm_error_allowlist: It is used to ignore certain error in pixel logger.
    """
    if check_chipset_vendor_by_qualcomm(ad):
        _check_qualcomm_power_save_mode(ad, full_power, begin_time)
    else:
        _check_broadcom_power_save_mode(ad, full_power, brcm_error_allowlist)


def _check_qualcomm_power_save_mode(ad, full_power, begin_time):
    dpo_results = _get_dpo_info_from_logcat(ad, begin_time)
    first_dpo_count = int(dpo_results[0]["log_message"].split()[-1])
    final_dpo_count = int(dpo_results[-1]["log_message"].split()[-1])
    dpo_count_diff = final_dpo_count - first_dpo_count
    ad.log.debug("The DPO count diff is {diff}".format(diff=dpo_count_diff))
    if full_power:
        asserts.assert_equal(dpo_count_diff, 0, msg="DPO count diff should be 0")
    else:
        asserts.assert_true(dpo_count_diff > 0, msg="DPO count diff should be more than 0")


def _check_broadcom_power_save_mode(ad, full_power, brcm_error_allowlist):
    power_save_log, _ = _get_power_mode_log_from_pixel_logger(
        ad, brcm_error_allowlist, stop_pixel_logger=False)
    power_status = re.compile(r',P,(\w),').search(power_save_log[-2]).group(1)
    ad.log.debug("The power status is {status}".format(status=power_status))
    if full_power:
        asserts.assert_true(power_status == "D", msg="Should be in full power mode")
    else:
        asserts.assert_true(power_status in ["F", "S"], msg="Should not be in full power mode")

@contextmanager
def run_gnss_tracking(ad, criteria, meas_flag):
    """A context manager to enable gnss tracking and stops at the end.

    Args:
        ad: The device under test.
        criteria: The criteria for First Fixed.
        meas_flag: A flag to turn on measurement log or not.
    """
    process_gnss_by_gtw_gpstool(ad, criteria=criteria, meas_flag=meas_flag)
    try:
        yield
    finally:
        start_gnss_by_gtw_gpstool(ad, state=False)

def log_current_epoch_time(ad, sponge_key):
    """Logs current epoch timestamp in second.

    Args:
        sponge_key: The key name of the sponge property.
    """
    current_epoch_time = get_current_epoch_time() // 1000
    ad.log.info(f"TestResult {sponge_key} {current_epoch_time}")


def validate_diff_of_gps_clock_elapsed_realtime(ad, start_time):
    """Validates the diff of gps clock and elapsed realtime should be stable.

    Args:
        ad: The device under test.
        start_time: When should the validation start. For BRCM devices, the PPS feature takes some
            time after first fixed to start working. Therefore we should ignore some data.
    """
    last_gps_elapsed_realtime_diff = 0
    variation_diff = {}

    for clock in GnssMeasurement(ad).get_gnss_clock_info():
        if clock.event_time < start_time:
            continue

        if not bool(last_gps_elapsed_realtime_diff):
            last_gps_elapsed_realtime_diff = clock.gps_elapsed_realtime_diff
            continue

        current_gps_elapsed_realtime_diff = clock.gps_elapsed_realtime_diff
        variation_diff[clock.event_time] = abs(
            current_gps_elapsed_realtime_diff - last_gps_elapsed_realtime_diff)
        last_gps_elapsed_realtime_diff = current_gps_elapsed_realtime_diff

    over_criteria_data = [
        (event_time, diff) for (event_time, diff) in variation_diff.items() if (
            diff > _GPS_ELAPSED_REALTIME_DIFF_TOLERANCE)
    ]

    asserts.assert_true(
        [] == over_criteria_data,
        msg=f"Following data are over criteria: {over_criteria_data}",
    )
