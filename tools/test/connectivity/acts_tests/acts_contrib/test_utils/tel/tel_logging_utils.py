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

from datetime import datetime
import os
import re
import shutil
import time

from acts import utils
from acts.libs.proc import job
from acts.controllers.android_device import DEFAULT_QXDM_LOG_PATH
from acts.controllers.android_device import DEFAULT_SDM_LOG_PATH
from acts.libs.utils.multithread import run_multithread_func
from acts.utils import get_current_epoch_time
from acts.utils import start_standing_subprocess


_LS_MASK_NAME = "Lassen default + TCP"

_LS_ENABLE_LOG_SHELL = f'\
am broadcast -n com.android.pixellogger/.receiver.AlwaysOnLoggingReceiver \
-a com.android.pixellogger.service.logging.LoggingService.ACTION_CONFIGURE_ALWAYS_ON_LOGGING \
-e intent_key_enable "true" -e intent_key_config "{_LS_MASK_NAME}" \
--ei intent_key_max_log_size_mb 100 --ei intent_key_max_number_of_files 100'
_LS_DISABLE_LOG_SHELL = '\
am broadcast -n com.android.pixellogger/.receiver.AlwaysOnLoggingReceiver \
-a com.android.pixellogger.service.logging.LoggingService.ACTION_CONFIGURE_ALWAYS_ON_LOGGING \
-e intent_key_enable "false"'
_LS_GET_LOG_STATUS_SHELL = 'getprop vendor.sys.modem.logging.status'
_LS_START_LS_TIMEOUT_SECS = 30
_LS_STOP_LS_TIMEOUT_SECS = 30

def check_if_tensor_platform(ad):
    """Check if current platform belongs to the Tensor platform

    Args:
        ad: Android object

    Returns:
        True if current platform belongs to the Tensor platform. Otherwise False.
    """
    result = ad.adb.getprop("ro.boot.hardware.platform")
    if re.search('^gs', result, re.I):
        return True
    return False


def start_pixellogger_always_on_logging(ad):
    """Start always-on logging of Pixellogger for both Qualcomm and Tensor
    platform.

    Args:
        ad: Android object

    Returns:
        True if the property is set correctly. Otherwise False.
    """
    setattr(ad, 'enable_always_on_modem_logger', True)
    if check_if_tensor_platform(ad):
        key = "persist.vendor.sys.modem.logging.enable"
    else:
        key = "persist.vendor.sys.modem.diag.mdlog"

    if ad.adb.getprop(key) == "false":
        ad.adb.shell("setprop persist.vendor.sys.modem.logging.enable true")
        time.sleep(5)
        if ad.adb.getprop(key) == "true":
            return True
        else:
            return False
    else:
        return True


def start_dsp_logger(ad, p21 = False, retry = 3):
    """Start DSP logging for P21/P22 devices.

    Args:
        ad: Android object.
        p21: True if p21 device, False otherwise.
        retry: times of retry to enable DSP logger.

    Returns:
        True if DSP logger is enabled correctly. Otherwise False.
    """
    registry_name = "!LTEL1.HAL.DSP\\ clkgating\\ Enb/Dis" if p21 else "NASU.LCPU.LOG.SWITCH"
    nv_value = "00" if p21 else "02"

    def _is_dsp_enabled(ad):
        return nv_value in ad.adb.shell('am instrument -w -e request '
            f'at+googgetnv=\\"{registry_name}\\" -e response wait '
            'com.google.mdstest/com.google.mdstest.instrument.'
            'ModemATCommandInstrumentation')

    for _ in range(retry):
        if not _is_dsp_enabled(ad):
            ad.adb.shell('am instrument -w -e request at+googsetnv=\\"'
                f'{registry_name}\\",0,\\"{nv_value}\\" -e response wait '
                'com.google.mdstest/com.google.mdstest.instrument.'
                'ModemATCommandInstrumentation')
            time.sleep(3)
        else:
            ad.log.info("DSP logger is enabled, reboot to start.")
            ad.reboot()
            return True
    ad.log.warning("DSP logger enable failed")
    return False


def is_sdm_logger_running(ad):
    """Queries the status of SDM logger.

    Returns:
      True if the SDM logger is runninng.
    """
    return "true" in ad.adb.shell(_LS_GET_LOG_STATUS_SHELL, ignore_status=True)


def start_sdm_logger(ad):
    """Start SDM logger."""
    if not getattr(ad, "sdm_log", True): return

    # Delete existing SDM logs which were created 15 mins prior
    ad.sdm_log_path = DEFAULT_SDM_LOG_PATH
    file_count = ad.adb.shell(
        f"find {ad.sdm_log_path} -type f -iname sbuff_[0-9]*.sdm* | wc -l")
    if int(file_count) > 3:
        seconds = 15 * 60
        # Remove sdm logs modified more than specified seconds ago
        ad.adb.shell(
            f"find {ad.sdm_log_path} -type f -iname sbuff_[0-9]*.sdm* "
            f"-not -mtime -{seconds}s -delete")

    if not is_sdm_logger_running(ad):
        ad.log.debug("starting sdm logger...")
        ad.adb.shell(_LS_ENABLE_LOG_SHELL, ignore_status=True)

        timeout = time.monotonic() + _LS_START_LS_TIMEOUT_SECS
        while time.monotonic() < timeout:
            time.sleep(1)
            if is_sdm_logger_running(ad):
                ad.log.info('SDM logger has started')
                break
        else:
            raise RuntimeError(
                'Timed out while waiting for SDM logger to start.')


def stop_sdm_logger(ad):
    """Stop SDM logger."""
    if is_sdm_logger_running(ad):
        ad.log.info("Stopping SDM logger...")
        ad.adb.shell(_LS_DISABLE_LOG_SHELL, ignore_status=True)

        timeout = time.monotonic() + _LS_STOP_LS_TIMEOUT_SECS
        while time.monotonic() < timeout:
            time.sleep(1)
            if not is_sdm_logger_running(ad):
                ad.log.info('SDM logger has stoped')
                break
        else:
            raise RuntimeError(
                'Timed out while waiting for SDM logger to stop.')


def start_sdm_loggers(log, ads):
    tasks = [(start_sdm_logger, [ad]) for ad in ads
             if getattr(ad, "sdm_log", True)]
    if tasks: run_multithread_func(log, tasks)


def stop_sdm_loggers(log, ads):
    tasks = [(stop_sdm_logger, [ad]) for ad in ads]
    run_multithread_func(log, tasks)


def find_qxdm_log_mask(ad, mask="default.cfg"):
    """Find QXDM logger mask."""
    if "/" not in mask:
        # Call nexuslogger to generate log mask
        start_nexuslogger(ad)
        # Find the log mask path
        for path in (DEFAULT_QXDM_LOG_PATH, "/data/diag_logs",
                     "/vendor/etc/mdlog/", "/vendor/etc/modem/"):
            out = ad.adb.shell(
                "find %s -type f -iname %s" % (path, mask), ignore_status=True)
            if out and "No such" not in out and "Permission denied" not in out:
                if path.startswith("/vendor/"):
                    setattr(ad, "qxdm_log_path", DEFAULT_QXDM_LOG_PATH)
                else:
                    setattr(ad, "qxdm_log_path", path)
                return out.split("\n")[0]
        for mask_file in ("/vendor/etc/mdlog/", "/vendor/etc/modem/"):
            if mask in ad.adb.shell("ls %s" % mask_file, ignore_status=True):
                setattr(ad, "qxdm_log_path", DEFAULT_QXDM_LOG_PATH)
                return "%s/%s" % (mask_file, mask)
    else:
        out = ad.adb.shell("ls %s" % mask, ignore_status=True)
        if out and "No such" not in out:
            qxdm_log_path, cfg_name = os.path.split(mask)
            setattr(ad, "qxdm_log_path", qxdm_log_path)
            return mask
    ad.log.warning("Could NOT find QXDM logger mask path for %s", mask)


def set_qxdm_logger_command(ad, mask=None):
    """Set QXDM logger always on.

    Args:
        ad: android device object.

    """
    ## Neet to check if log mask will be generated without starting nexus logger
    masks = []
    mask_path = None
    if mask:
        masks = [mask]
    masks.extend(["QC_Default.cfg", "default.cfg"])
    for mask in masks:
        mask_path = find_qxdm_log_mask(ad, mask)
        if mask_path: break
    if not mask_path:
        ad.log.error("Cannot find QXDM mask %s", mask)
        ad.qxdm_logger_command = None
        return False
    else:
        ad.log.info("Use QXDM log mask %s", mask_path)
        ad.log.debug("qxdm_log_path = %s", ad.qxdm_log_path)
        output_path = os.path.join(ad.qxdm_log_path, "logs")
        ad.qxdm_logger_command = ("diag_mdlog -f %s -o %s -s 90 -c" %
                                  (mask_path, output_path))
        return True


def stop_qxdm_logger(ad):
    """Stop QXDM logger."""
    for cmd in ("diag_mdlog -k", "killall diag_mdlog"):
        output = ad.adb.shell("ps -ef | grep mdlog") or ""
        if "diag_mdlog" not in output:
            break
        ad.log.debug("Kill the existing qxdm process")
        ad.adb.shell(cmd, ignore_status=True)
        time.sleep(5)


def start_qxdm_logger(ad, begin_time=None):
    """Start QXDM logger."""
    if not getattr(ad, "qxdm_log", True): return
    # Delete existing QXDM logs 5 minutes earlier than the begin_time
    current_time = get_current_epoch_time()
    if getattr(ad, "qxdm_log_path", None):
        seconds = None
        file_count = ad.adb.shell(
            "find %s -type f -iname *.qmdl | wc -l" % ad.qxdm_log_path)
        if int(file_count) > 3:
            if begin_time:
                # if begin_time specified, delete old qxdm logs modified
                # 10 minutes before begin time
                seconds = int((current_time - begin_time) / 1000.0) + 10 * 60
            else:
                # if begin_time is not specified, delete old qxdm logs modified
                # 15 minutes before current time
                seconds = 15 * 60
        if seconds:
            # Remove qxdm logs modified more than specified seconds ago
            ad.adb.shell(
                "find %s -type f -iname *.qmdl -not -mtime -%ss -delete" %
                (ad.qxdm_log_path, seconds))
            ad.adb.shell(
                "find %s -type f -iname *.xml -not -mtime -%ss -delete" %
                (ad.qxdm_log_path, seconds))
    if getattr(ad, "qxdm_logger_command", None):
        output = ad.adb.shell("ps -ef | grep mdlog") or ""
        if ad.qxdm_logger_command not in output:
            ad.log.debug("QXDM logging command %s is not running",
                         ad.qxdm_logger_command)
            if "diag_mdlog" in output:
                # Kill the existing non-matching diag_mdlog process
                # Only one diag_mdlog process can be run
                stop_qxdm_logger(ad)
            ad.log.info("Start QXDM logger")
            ad.adb.shell_nb(ad.qxdm_logger_command)
            time.sleep(10)
        else:
            run_time = check_qxdm_logger_run_time(ad)
            if run_time < 600:
                # the last diag_mdlog started within 10 minutes ago
                # no need to restart
                return True
            if ad.search_logcat(
                    "Diag_Lib: diag: In delete_log",
                    begin_time=current_time -
                    run_time) or not ad.get_file_names(
                        ad.qxdm_log_path,
                        begin_time=current_time - 600000,
                        match_string="*.qmdl"):
                # diag_mdlog starts deleting files or no qmdl logs were
                # modified in the past 10 minutes
                ad.log.debug("Quit existing diag_mdlog and start a new one")
                stop_qxdm_logger(ad)
                ad.adb.shell_nb(ad.qxdm_logger_command)
                time.sleep(10)
        return True


def disable_qxdm_logger(ad):
    for prop in ("persist.sys.modem.diag.mdlog",
                 "persist.vendor.sys.modem.diag.mdlog",
                 "vendor.sys.modem.diag.mdlog_on"):
        if ad.adb.getprop(prop):
            ad.adb.shell("setprop %s false" % prop, ignore_status=True)
    for apk in ("com.android.nexuslogger", "com.android.pixellogger"):
        if ad.is_apk_installed(apk) and ad.is_apk_running(apk):
            ad.force_stop_apk(apk)
    stop_qxdm_logger(ad)
    return True


def check_qxdm_logger_run_time(ad):
    output = ad.adb.shell("ps -eo etime,cmd | grep diag_mdlog")
    result = re.search(r"(\d+):(\d+):(\d+) diag_mdlog", output)
    if result:
        return int(result.group(1)) * 60 * 60 + int(
            result.group(2)) * 60 + int(result.group(3))
    else:
        result = re.search(r"(\d+):(\d+) diag_mdlog", output)
        if result:
            return int(result.group(1)) * 60 + int(result.group(2))
        else:
            return 0


def start_qxdm_loggers(log, ads, begin_time=None):
    tasks = [(start_qxdm_logger, [ad, begin_time]) for ad in ads
             if getattr(ad, "qxdm_log", True)]
    if tasks: run_multithread_func(log, tasks)


def stop_qxdm_loggers(log, ads):
    tasks = [(stop_qxdm_logger, [ad]) for ad in ads]
    run_multithread_func(log, tasks)


def check_qxdm_logger_mask(ad, mask_file="QC_Default.cfg"):
    """Check if QXDM logger always on is set.

    Args:
        ad: android device object.

    """
    output = ad.adb.shell(
        "ls /data/vendor/radio/diag_logs/", ignore_status=True)
    if not output or "No such" in output:
        return True
    if mask_file not in ad.adb.shell(
            "cat /data/vendor/radio/diag_logs/diag.conf", ignore_status=True):
        return False
    return True


def start_nexuslogger(ad):
    """Start Nexus/Pixel Logger Apk."""
    qxdm_logger_apk = None
    for apk, activity in (("com.android.nexuslogger", ".MainActivity"),
                          ("com.android.pixellogger",
                           ".ui.main.MainActivity")):
        if ad.is_apk_installed(apk):
            qxdm_logger_apk = apk
            break
    if not qxdm_logger_apk: return
    if ad.is_apk_running(qxdm_logger_apk):
        if "granted=true" in ad.adb.shell(
                "dumpsys package %s | grep READ_EXTERN" % qxdm_logger_apk):
            return True
        else:
            ad.log.info("Kill %s" % qxdm_logger_apk)
            ad.force_stop_apk(qxdm_logger_apk)
            time.sleep(5)
    ad.adb.shell("pm grant %s android.permission.READ_EXTERNAL_STORAGE" % (qxdm_logger_apk))
    time.sleep(2)
    for i in range(3):
        ad.unlock_screen()
        ad.log.info("Start %s Attempt %d" % (qxdm_logger_apk, i + 1))
        ad.adb.shell("am start -n %s/%s" % (qxdm_logger_apk, activity))
        time.sleep(5)
        if ad.is_apk_running(qxdm_logger_apk):
            ad.send_keycode("HOME")
            return True
    return False


def start_tcpdumps(ads,
                   test_name="",
                   begin_time=None,
                   interface="any",
                   mask="all"):
    for ad in ads:
        try:
            start_adb_tcpdump(
                ad,
                test_name=test_name,
                begin_time=begin_time,
                interface=interface,
                mask=mask)
        except Exception as e:
            ad.log.warning("Fail to start tcpdump due to %s", e)


def start_adb_tcpdump(ad,
                      test_name="",
                      begin_time=None,
                      interface="any",
                      mask="all"):
    """Start tcpdump on any iface

    Args:
        ad: android device object.
        test_name: tcpdump file name will have this

    """
    out = ad.adb.shell("ls -l /data/local/tmp/tcpdump/", ignore_status=True)
    if "No such file" in out or not out:
        ad.adb.shell("mkdir /data/local/tmp/tcpdump")
    else:
        ad.adb.shell(
            "find /data/local/tmp/tcpdump -type f -not -mtime -1800s -delete",
            ignore_status=True)
        ad.adb.shell(
            "find /data/local/tmp/tcpdump -type f -size +5G -delete",
            ignore_status=True)

    if not begin_time:
        begin_time = get_current_epoch_time()

    out = ad.adb.shell(
        'ifconfig | grep -v -E "r_|-rmnet" | grep -E "lan|data"',
        ignore_status=True,
        timeout=180)
    intfs = re.findall(r"(\S+).*", out)
    if interface and interface not in ("any", "all"):
        if interface not in intfs: return
        intfs = [interface]

    out = ad.adb.shell("ps -ef | grep tcpdump")
    cmds = []
    for intf in intfs:
        if intf in out:
            ad.log.info("tcpdump on interface %s is already running", intf)
            continue
        else:
            log_file_name = "/data/local/tmp/tcpdump/tcpdump_%s_%s_%s_%s.pcap" \
                            % (ad.serial, intf, test_name, begin_time)
            if mask == "ims":
                cmds.append(
                    "adb -s %s shell tcpdump -i %s -s0 -n -p udp port 500 or "
                    "udp port 4500 -w %s" % (ad.serial, intf, log_file_name))
            else:
                cmds.append("adb -s %s shell tcpdump -i %s -s0 -w %s" %
                            (ad.serial, intf, log_file_name))
    if "Qualcomm" not in str(ad.adb.shell("getprop gsm.version.ril-impl")):
        log_file_name = ("/data/local/tmp/tcpdump/tcpdump_%s_any_%s_%s.pcap"
                         % (ad.serial, test_name, begin_time))
        cmds.append("adb -s %s shell nohup tcpdump -i any -s0 -w %s" %
                    (ad.serial, log_file_name))
    for cmd in cmds:
        ad.log.info(cmd)
        try:
            start_standing_subprocess(cmd, 10)
        except Exception as e:
            ad.log.error(e)
    if cmds:
        time.sleep(5)


def stop_tcpdumps(ads):
    for ad in ads:
        stop_adb_tcpdump(ad)


def stop_adb_tcpdump(ad, interface="any"):
    """Stops tcpdump on any iface
       Pulls the tcpdump file in the tcpdump dir

    Args:
        ad: android device object.

    """
    if interface == "any":
        try:
            ad.adb.shell("killall -9 tcpdump", ignore_status=True)
        except Exception as e:
            ad.log.error("Killing tcpdump with exception %s", e)
    else:
        out = ad.adb.shell("ps -ef | grep tcpdump | grep %s" % interface)
        if "tcpdump -i" in out:
            pids = re.findall(r"\S+\s+(\d+).*tcpdump -i", out)
            for pid in pids:
                ad.adb.shell("kill -9 %s" % pid)
    ad.adb.shell(
        "find /data/local/tmp/tcpdump -type f -not -mtime -1800s -delete",
        ignore_status=True)


def get_tcpdump_log(ad, test_name="", begin_time=None):
    """Stops tcpdump on any iface
       Pulls the tcpdump file in the tcpdump dir
       Zips all tcpdump files

    Args:
        ad: android device object.
        test_name: test case name
        begin_time: test begin time
    """
    logs = ad.get_file_names("/data/local/tmp/tcpdump", begin_time=begin_time)
    if logs:
        ad.log.info("Pulling tcpdumps %s", logs)
        log_path = os.path.join(
            ad.device_log_path, "TCPDUMP_%s_%s" % (ad.model, ad.serial))
        os.makedirs(log_path, exist_ok=True)
        ad.pull_files(logs, log_path)
        shutil.make_archive(log_path, "zip", log_path)
        shutil.rmtree(log_path)
    return True


def wait_for_log(ad, pattern, begin_time=None, end_time=None, max_wait_time=120):
    """Wait for logcat logs matching given pattern. This function searches in
    logcat for strings matching given pattern by using search_logcat per second
    until max_wait_time reaches.

    Args:
        ad: android device object
        pattern: pattern to be searched in grep format
        begin_time: only the lines in logcat with time stamps later than
            begin_time will be searched.
        end_time: only the lines in logcat with time stamps earlier than
            end_time will be searched.
        max_wait_time: timeout of this function

    Returns:
        All matched lines will be returned. If no line matches the given pattern
        None will be returned.
    """
    start_time = datetime.now()
    while True:
        ad.log.info(
            '====== Searching logcat for "%s" ====== ', pattern)
        res = ad.search_logcat(
            pattern, begin_time=begin_time, end_time=end_time)
        if res:
            return res
        time.sleep(1)
        stop_time = datetime.now()
        passed_time = (stop_time - start_time).total_seconds()
        if passed_time > max_wait_time:
            return


def extract_test_log(log, src_file, dst_file, test_tag):
    os.makedirs(os.path.dirname(dst_file), exist_ok=True)
    cmd = "grep -n '%s' %s" % (test_tag, src_file)
    result = job.run(cmd, ignore_status=True)
    if not result.stdout or result.exit_status == 1:
        log.warning("Command %s returns %s", cmd, result)
        return
    line_nums = re.findall(r"(\d+).*", result.stdout)
    if line_nums:
        begin_line = int(line_nums[0])
        end_line = int(line_nums[-1])
        if end_line - begin_line <= 5:
            result = job.run("wc -l < %s" % src_file)
            if result.stdout:
                end_line = int(result.stdout)
        log.info("Extract %s from line %s to line %s to %s", src_file,
                 begin_line, end_line, dst_file)
        job.run("awk 'NR >= %s && NR <= %s' %s > %s" % (begin_line, end_line,
                                                        src_file, dst_file))


def log_screen_shot(ad, test_name=""):
    file_name = "/sdcard/Pictures/screencap"
    if test_name:
        file_name = "%s_%s" % (file_name, test_name)
    file_name = "%s_%s.png" % (file_name, utils.get_current_epoch_time())
    try:
        ad.adb.shell("screencap -p %s" % file_name)
    except:
        ad.log.error("Fail to log screen shot to %s", file_name)


def get_screen_shot_log(ad, test_name="", begin_time=None):
    logs = ad.get_file_names("/sdcard/Pictures", begin_time=begin_time)
    if logs:
        ad.log.info("Pulling %s", logs)
        log_path = os.path.join(ad.device_log_path, "Screenshot_%s" % ad.serial)
        os.makedirs(log_path, exist_ok=True)
        ad.pull_files(logs, log_path)
    ad.adb.shell("rm -rf /sdcard/Pictures/screencap_*", ignore_status=True)


def get_screen_shot_logs(ads, test_name="", begin_time=None):
    for ad in ads:
        get_screen_shot_log(ad, test_name=test_name, begin_time=begin_time)