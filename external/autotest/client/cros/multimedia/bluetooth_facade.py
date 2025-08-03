# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Facade to access the bluetooth-related functionality."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import base64
import binascii
import collections
from datetime import datetime, timedelta
import glob
# AU tests use ToT client code, but ToT -3 client version.
try:
    from gi.repository import GLib, GObject
except ImportError:
    import gobject as GObject
import json
import logging
import logging.handlers
import os

# TODO(b/215715213) - Wait until ebuild runs as python3 to remove this try
try:
    import pydbus
except Exception as e:
    import platform
    logging.error('Unable to import pydbus at version=%s: %s',
                  platform.python_version(), e)
    pydbus = {}

import re
import subprocess
import functools
import time
import threading
import traceback

import common
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib.cros.bluetooth import bluetooth_socket
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.udev_helpers import UdevadmInfo, UdevadmTrigger
from autotest_lib.client.cros.audio import (audio_test_data as
                                            audio_test_data_module)
from autotest_lib.client.cros.audio import check_quality
from autotest_lib.client.cros.audio import cras_utils
from autotest_lib.client.cros.audio.sox_utils import (
        convert_format, convert_raw_file, get_file_length,
        trim_silence_from_wav_file)
from autotest_lib.client.cros.bluetooth import advertisement
from autotest_lib.client.cros.bluetooth import adv_monitor_helper
from autotest_lib.client.cros.bluetooth import output_recorder
from autotest_lib.client.cros.bluetooth import logger_helper
from autotest_lib.client.cros.bluetooth.floss.adapter_client import (
        FlossAdapterClient, BluetoothCallbacks, BluetoothConnectionCallbacks,
        BondState, SspVariant, Transport)
from autotest_lib.client.cros.bluetooth.floss.manager_client import FlossManagerClient
from autotest_lib.client.cros.bluetooth.floss.utils import GLIB_THREAD_NAME
from autotest_lib.client.cros.power import sys_power
import six
from six.moves import map
from six.moves import range

CheckQualityArgsClass = collections.namedtuple(
        'args_type', ['filename', 'rate', 'channel', 'bit_width'])


def _dbus_byte_array_to_b64_string(dbus_byte_array):
    """Base64 encodes a dbus byte array for use with the xml rpc proxy.

    Input is encoded to bytes using base64 encoding. Then the base64 bytes is
    decoded as string.
    """
    return base64.standard_b64encode(bytearray(dbus_byte_array)).decode()


def _b64_string_to_dbus_byte_array(b64_string):
    """Base64 decodes a dbus byte array for use with the xml rpc proxy."""
    dbus_array = []
    bytes = bytearray(base64.standard_b64decode(b64_string))
    for byte in bytes:
        dbus_array.append(byte)
    return dbus_array


def dbus_safe(default_return_value, return_error=False):
    """Catch all DBus exceptions and return a default value instead.

    Wrap a function with a try block that catches DBus exceptions and
    returns the error with the specified return status. The exception is logged
    to aid in debugging.

    If |return_error| is set, the call will return a tuple with
    (default_return_value, str(error)).

    @param default_return_value: What value to return in case of errors.
    @param return_error: Whether to return the error string as well.

    @return Either the return value from the method call if successful or
            the |default_return_value| or a tuple(default_return_value,
            str(error))
    """

    def decorator(wrapped_function):
        """Call a function and catch DBus errors.

        @param wrapped_function function to call in dbus safe context.
        @return function return value or default_return_value on failure.

        """

        @functools.wraps(wrapped_function)
        def wrapper(*args, **kwargs):
            """Pass args and kwargs to a dbus safe function.

            @param args formal python arguments.
            @param kwargs keyword python arguments.
            @return function return value or default_return_value on failure.

            """
            logging.debug('%s()', wrapped_function.__name__)
            try:
                return wrapped_function(*args, **kwargs)
            except GLib.Error as e:
                logging.debug('Exception while performing operation %s: %s',
                              wrapped_function.__name__, e)

                if return_error:
                    return (default_return_value, str(e))
                else:
                    return default_return_value
            except Exception as e:
                logging.debug('Exception in %s: %s', wrapped_function.__name__,
                              e)
                logging.debug(traceback.format_exc())
                raise

        return wrapper

    return decorator


def raw_dbus_call_sync(bus,
                       proxy,
                       iface,
                       method,
                       variant_in_args,
                       variant_out_type,
                       timeout_ms=None):
    """Makes a raw D-Bus call and returns the unpacked result.

    @param bus: System bus object.
    @param proxy: Proxy object.
    @param iface: D-Bus interface that exposes this method.
    @param method: Name of method to call.
    @param variant_in_args: A Glib.Variant that corresponds to the method's
                            inputs.
    @param variant_out_type: A Glib.VariantType that describes the output. This
                             is the type that will be unpacked from the result.
    @param timeout_ms: Timeout in milliseconds for this method call.

    @returns: Unpacked result from the method call.
    """
    if timeout_ms is None:
        timeout_ms = GLib.MAXINT

    return bus.con.call_sync(proxy._bus_name, proxy._path, iface, method,
                             variant_in_args, variant_out_type, 0, timeout_ms,
                             None).unpack()


def unpack_if_variant(value):
    """If given value is GLib.Variant, unpack it to the actual type."""
    if isinstance(value, GLib.Variant):
        return value.unpack()

    return value


class UpstartClient:
    """Upstart D-Bus client that allows actions on upstart targets."""

    UPSTART_MANAGER_SERVICE = 'com.ubuntu.Upstart'
    UPSTART_MANAGER_PATH = '/com/ubuntu/Upstart'
    UPSTART_MANAGER_IFACE = 'com.ubuntu.Upstart0_6'
    UPSTART_JOB_IFACE = 'com.ubuntu.Upstart0_6.Job'

    UPSTART_ERROR_UNKNOWNINSTANCE = (
            'com.ubuntu.Upstart0_6.Error.UnknownInstance')
    UPSTART_ERROR_ALREADYSTARTED = (
            'com.ubuntu.Upstart0_6.Error.AlreadyStarted')

    @classmethod
    def _get_job(cls, job_name):
        """Get job by name."""
        bus = pydbus.SystemBus()
        obj = bus.get(cls.UPSTART_MANAGER_SERVICE, cls.UPSTART_MANAGER_PATH)
        job_path = obj[cls.UPSTART_MANAGER_IFACE].GetJobByName(job_name)

        return bus.get(cls.UPSTART_MANAGER_SERVICE,
                       job_path)[cls.UPSTART_JOB_IFACE]

    @staticmethod
    def _convert_instance_args(source):
        """Convert instance args dict to array."""
        return ['{}={}'.format(k, v) for k, v in source.items()]

    @classmethod
    def start(cls, job_name, instance_args = {}):
        """Starts a job.

        @param job_name: Name of upstart job to start.
        @param instance_args: Instance arguments. Will be converted to array of
                              "key=value".

        @return True if job start was sent successfully.
        """
        try:
            job = cls._get_job(job_name)
            converted_args = cls._convert_instance_args(instance_args)
            job.Start(converted_args, True)
        except TypeError as t:
            # Can occur if cls._get_job fails
            logging.error('Error starting {}: {}'.format(job_name, t))
            return False
        except GLib.Error as e:
            # An already started error is ok. All other dbus errors should
            # return False.
            if cls.UPSTART_ERROR_ALREADYSTARTED not in str(e):
                logging.error('Error starting {}: {}'.format(job_name, e))
                return False

        return True

    @classmethod
    def stop(cls, job_name, instance_args = {}):
        """Stops a job.

        @param job_name: Name of upstart job to stop.
        @param instance_args: Instance arguments. Will be converted to
                              array of "key=value".

        @return True if job stop was sent successfully.
        """
        try:
            job = cls._get_job(job_name)
            converted_args = cls._convert_instance_args(instance_args)
            job.Stop(converted_args, True)
        except TypeError as t:
            # Can occur if cls._get_job fails
            logging.error('Error stopping {}: {}'.format(job_name, t))
            return False
        except GLib.Error as e:
            # If the job was already stopped, we will see an UnknownInstance
            # exception. All other failure reasons should be treated as
            # a failure to stop.
            if cls.UPSTART_ERROR_UNKNOWNINSTANCE not in str(e):
                logging.error('Error starting {}: {}'.format(job_name, e))
                return False

        return True


class BluetoothBaseFacadeLocal(object):
    """Base facade shared by Bluez and Floss daemons. This takes care of any
    functionality that is common across the two daemons.
    """

    # Both bluez and floss share the same lib dir for configuration and cache
    BLUETOOTH_LIBDIR = '/var/lib/bluetooth'

    SYSLOG_LEVELS = [
            'EMERG', 'ALERT', 'CRIT', 'ERR', 'WARNING', 'NOTICE', 'INFO',
            'DEBUG'
    ]

    # How long to wait for hid device
    HID_TIMEOUT = 15
    HID_CHECK_SECS = 2

    # Due to problems transferring a date object, we convert to stringtime first
    # This is the standard format that we will use.
    OUT_DATE_FORMAT = '%Y-%m-%d %H:%M:%S.%f'

    # Upstart job name for the Floss Manager daemon
    MANAGER_JOB = "btmanagerd"
    # File path for btmanagerd
    BTMANGERD_FILE_PATH = '/usr/bin/btmanagerd'
    # How long we wait for the manager daemon to come up after we start it
    DAEMON_TIMEOUT_SEC = 5

    # Upstart job name for ChromeOS Audio daemon
    CRAS_JOB = "cras"

    CHIPSET_TO_VIDPID = {
            'MVL-8897': [(('0x02df', '0x912d'), 'SDIO')],
            'MVL-8997': [(('0x1b4b', '0x2b42'), 'USB')],
            'QCA-6174A-5-USB': [(('0x168c', '0x003e'), 'USB')],
            'QCA-6174A-3-UART': [(('0x0271', '0x050a'), 'UART')],
            'QCA-WCN6856': [(('0x17cb', '0x1103'), 'USB')],
            'Intel-AX200': [(('0x8086', '0x2723'), 'USB')],  # CcP2
            'Intel-AX201': [
                    (('0x8086', '0x02f0'), 'USB'),
                    (('0x8086', '0x4df0'), 'USB'),
                    (('0x8086', '0xa0f0'), 'USB'),
            ],  # HrP2
            'Intel-AC9260': [(('0x8086', '0x2526'), 'USB')],  # ThP2
            'Intel-AC9560': [
                    (('0x8086', '0x31dc'), 'USB'),  # JfP2
                    (('0x8086', '0x9df0'), 'USB')
            ],
            'Intel-AC7260': [
                    (('0x8086', '0x08b1'), 'USB'),  # WP2
                    (('0x8086', '0x08b2'), 'USB')
            ],
            'Intel-AC7265': [
                    (('0x8086', '0x095a'), 'USB'),  # StP2
                    (('0x8086', '0x095b'), 'USB')
            ],
            'Realtek-RTL8822C-USB': [(('0x10ec', '0xc822'), 'USB')],
            'Realtek-RTL8822C-UART': [(('0x10ec', '0xc822'), 'UART')],
            'Realtek-RTL8852A-USB': [(('0x10ec', '0x8852'), 'USB')],
            'Mediatek-MTK7921-USB': [(('0x14c3', '0x7961'), 'USB')],
            'Mediatek-MTK7921-SDIO': [(('0x037a', '0x7901'), 'SDIO')]

            # The following doesn't expose vid:pid
            # 'WCN3991-UART'
    }

    def __init__(self):
        # Initialize a messages object to record general logging.
        self.messages = logger_helper.LogManager()

        # Set up cras test client for audio tests
        self._cras_test_client = cras_utils.CrasTestClient()

    def configure_floss(self, enabled):
        """Start and configure the Floss manager daemon.

        In order to manage whether we use bluez or floss, we need to start the
        Floss manager daemon and then set floss enabled. This exists in the base
        implementation because bluez tests will need to start the manager to
        disable Floss.

        @param enabled: Whether to enable Floss

        @return Whether Floss was configured successfully.
        """
        # Start manager daemon or exit early
        if not UpstartClient.start(self.MANAGER_JOB):
            return False

        # Since we've just started the manager daemon, we also need to recreate
        # the client.
        self.manager_client = FlossManagerClient(self.bus)

        # Wait for the manager daemon to come up
        try:
            utils.poll_for_condition(
                    condition=(lambda: self.manager_client.has_proxy()),
                    desc='Wait for manager daemon to come up',
                    sleep_interval=0.5,
                    timeout=self.DAEMON_TIMEOUT_SEC)
        except Exception as e:
            logging.error('timeout: error starting manager daemon: %s', e)

        # We need to observe callbacks for proper operation.
        if not self.manager_client.register_callbacks():
            logging.error('manager_client: Failed to register callbacks')
            return False

        # Floss may not yet be enabled so make sure to enable it here.
        if self.manager_client.get_floss_enabled() != enabled:
            self.manager_client.set_floss_enabled(enabled)
            default_adapter = self.manager_client.get_default_adapter()
            try:
                utils.poll_for_condition(
                        condition=(lambda: self.manager_client.
                                   get_adapter_enabled(default_adapter
                                                       ) == enabled),
                        desc='Wait for set floss enabled to complete',
                        sleep_interval=0.5,
                        timeout=self.DAEMON_TIMEOUT_SEC)
            except Exception as e:
                logging.error('timeout: error waiting for set_floss_enabled')

        # Also configure cras to enable/disable floss
        self.configure_cras_floss(enabled)

        return True

    def configure_cras_floss(self, enabled):
        """Configure whether CRAS has floss enabled."""
        cras_utils.set_floss_enabled(enabled)

    def _restart_cras(self, enable_floss=False):
        """Restarts CRAS and sets whether Floss is enabled."""
        UpstartClient.stop(self.CRAS_JOB)
        started = UpstartClient.start(self.CRAS_JOB)

        def _set_floss():
            try:
                self.configure_cras_floss(enable_floss)
                return True
            except:
                return False

        try:
            if started:
                utils.poll_for_condition(
                        condition=_set_floss,
                        desc='Wait for CRAS to come up and configure floss',
                        sleep_interval=1,
                        timeout=self.DAEMON_TIMEOUT_SEC)
        except Exception as e:
            logging.error('timeout: error waiting to set floss on cras')
            return False

        # Did we successfully start the cras daemon?
        return started

    def log_message(self, msg):
        """ log a message to /var/log/messages."""
        try:
            cmd = ['logger', msg]
            subprocess.call(cmd)
        except Exception as e:
            logging.error("log_message %s failed with %s", cmd, str(e))

    def messages_start(self):
        """Start messages monitoring.

        @returns: True if logging started successfully, else False
        """

        try:
            self.messages.StartRecording()
            return True

        except Exception as e:
            logging.error('Failed to start log recording with error: %s', e)

        return False

    def messages_stop(self):
        """Stop messages monitoring.

        @returns: True if logs were successfully gathered since logging started,
                else False
        """
        try:
            self.messages.StopRecording()
            return True

        except Exception as e:
            logging.error('Failed to stop log recording with error: %s', e)

        return False

    def messages_find(self, pattern_str):
        """Find if a pattern string exists in messages output.

        @param pattern_str: the pattern string to find.

        @returns: True on success. False otherwise.

        """
        return self.messages.LogContains(pattern_str)

    def clean_bluetooth_kernel_log(self, log_level):
        """Remove Bluetooth kernel logs in /var/log/messages with loglevel
           equal to or greater than |log_level|

        @param log_level: int in range [0..7]
        """
        reg_exp = '[^ ]+ ({LEVEL}) kernel: \[.*\] Bluetooth: .*'.format(
                LEVEL='|'.join(self.SYSLOG_LEVELS[log_level:]))

        logging.debug('Set kernel filter to level %d', log_level)

        self.messages.FilterOut(reg_exp)

    def _encode_base64_json(self, data):
        """Base64 encode and json encode the data.
        Required to handle non-ascii data

        @param data: data to be base64 and JSON encoded

        @return: base64 and JSON encoded data

        """
        logging.debug('_encode_base64_json raw data is %s', data)
        b64_encoded = utils.base64_recursive_encode(data)
        logging.debug('base64 encoded data is %s', b64_encoded)
        json_encoded = json.dumps(b64_encoded)
        logging.debug('JSON encoded data is %s', json_encoded)
        return json_encoded

    def is_wrt_supported(self):
        """Check if Bluetooth adapter support WRT logs

        WRT is supported on Intel adapters other than (StP2 and WP2)

        @returns : True if adapter is Intel made.
        """
        # Dict of Intel Adapters that support WRT and vid:pid
        vid_pid_dict = {
                'HrP2': '8086:02f0',
                'ThP2': '8086:2526',
                'JfP2': '8086:31dc',
                'JfP2-2': '8086:9df0'
        }  # On Sarien/Arcada

        def _get_lspci_vid_pid(output):
            """ parse output of lspci -knn and get the vid:pid

            output is of the form '01:00.0 Network controller [0280]:
            \Intel Corporation Device [8086:2526] (rev 29)\n'

            @returns : 'vid:pid' or None
            """
            try:
                for i in output.split(b'\n'):
                    if 'Network controller' in i.decode('utf-8'):
                        logging.debug('Got line %s', i)
                        if 'Intel Corporation' in i.decode('utf-8'):
                            return i.split(b'[')[2].split(b']')[0]
                return None
            except Exception as e:
                logging.debug('Exception in _get_lspci_vidpid %s', str(e))
                return None

        try:
            cmd = ['lspci', '-knn']
            output = subprocess.check_output(cmd, encoding='UTF-8')
            vid_pid = _get_lspci_vid_pid(output)
            logging.debug("got vid_pid %s", vid_pid)
            if vid_pid is not None:
                if vid_pid in list(vid_pid_dict.values()):
                    return True
        except Exception as e:
            logging.error('is_intel_adapter  failed with %s', cmd, str(e))
            return False

    def enable_wrt_logs(self):
        """ Enable WRT logs for Intel Bluetooth adapters.

            This is applicable only to Intel adapters.
            Execute a series of custom hciconfig commands to
            setup WRT log collection

            Precondition :
                1) Check if the DUT has Intel controller other than StP2
                2) Make sure the controller is powered on
        """
        fw_trace_cmd = (
                'hcitool cmd 3f 7c 01 10 00 00 00 FE 81 02 80 04 00 00'
                ' 00 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00'
                ' 00 00 00 00 00 00 00')
        ddc_read_cmd = 'hcitool cmd 3f 8c 28 01'
        ddc_write_cmd_prefix = 'hcitool cmd 3f 8b 03 28 01'
        hw_trace_cmd = (
                'hcitool cmd 3f 6f 01 08 00 00 00 00 00 00 00 00 01 00'
                ' 00 03 01 03 03 03 10 03 6A 0A 6A 0A 6A 0A 6A 0A 00 00'
                ' 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00'
                ' 00 00 00 00 00 00')
        multi_comm_trace_str = ('000000F600000000005002000000003F3F3F3'
                                'F3F003F000000000000000001000000000000000000'
                                '000000000000000000000000000000000000000000'
                                '00000000000000000000000000000000000000000'
                                '00000000000000000')
        multi_comm_trace_file = ('/sys/kernel/debug/ieee80211'
                                 '/phy0/iwlwifi/iwlmvm/send_hcmd')

        def _execute_cmd(cmd_str, msg=''):
            """Wrapper around subprocess.check_output.

            @params cmd: Command to be executed as a string
            @params msg: Optional description of the command

            @returns: (True, output) if execution succeeded
                  (False, None) if execution failed

            """
            try:
                logging.info('Executing %s cmd', msg)
                cmd = cmd_str.split(' ')
                logging.debug('command is "%s"', cmd)
                output = subprocess.check_output(cmd, enconding='UTF-8')
                logging.info('%s cmd successfully executed', msg)
                logging.debug('output is %s', output)
                return (True, output)
            except Exception as e:
                logging.error('Exception %s while executing %s command',
                              str(e), msg)
                return (False, None)

        def _get_ddc_write_cmd(ddc_read_result, ddc_write_cmd_prefix):
            """ Create ddc_write_cmd from read command

           This function performs the following
           1) Take the output of ddc_read_cmd which is in following form
              '< HCI Command: ogf 0x3f, ocf 0x008c, plen 1\n
               01 \n>
               HCI Event: 0x0e plen 6\n  01 8C FC 12 00 18 \n'
           2) Take the last value of the output
              01 8C FC 12 00 ===>> 18 <====
           3) Bitwise or with 0x40
              0x18 | 0x40 = 0x58
           4) Add it to the end of the ddc_write_cmd
              'hcitool 01 8C FC 00 28 01 ===> 58 <===='

           """
            last_line = [
                    i for i in ddc_read_result.strip().split(b'\n') if i != ''
            ][-1]
            last_byte = [i for i in last_line.split(b' ') if i != ''][-1]
            processed_byte = hex(int(last_byte, 16) | 0x40).split('0x')[1]
            cmd = ddc_write_cmd_prefix + ' ' + processed_byte
            logging.debug('ddc_write_cmd is %s', cmd)
            return cmd

        try:
            logging.info('Enabling WRT logs')
            status, _ = _execute_cmd(fw_trace_cmd, 'FW trace cmd')
            if not status:
                logging.info('FW trace command execution failed')
                return False

            status, ddc_read_result = _execute_cmd(ddc_read_cmd, 'DDC Read')
            if not status:
                logging.info('DDC Read command  execution failed')
                return False

            ddc_write_cmd = _get_ddc_write_cmd(ddc_read_result,
                                               ddc_write_cmd_prefix)
            logging.debug('DDC Write command  is %s', ddc_write_cmd)
            status, _ = _execute_cmd(ddc_write_cmd, 'DDC Write')
            if not status:
                logging.info('DDC Write commanad execution failed')
                return False

            status, hw_trace_result = _execute_cmd(hw_trace_cmd, 'HW trace')
            if not status:
                logging.info('HW Trace command  execution failed')
                return False

            logging.debug('Executing the multi_comm_trace cmd %s to file %s',
                          multi_comm_trace_str, multi_comm_trace_file)
            with open(multi_comm_trace_file, 'w') as f:
                f.write(multi_comm_trace_str + '\n')
                f.flush()

            logging.info('WRT Logs enabled')
            return True
        except Exception as e:
            logging.error('Exception %s while enabling WRT logs', str(e))
            return False

    def collect_wrt_logs(self):
        """Collect the WRT logs for Intel Bluetooth adapters

           This is applicable only to Intel adapters.
           Execute following command to collect WRT log. The logs are
           copied to /var/spool/crash/

           'echo 1 > sudo tee /sys/kernel/debug/ieee80211/phy0'
                           '/iwlwifi/iwlmvm/fw_dbg_collect'
           This is to be called only after enable_wrt_logs is called


           Precondition:
                 1) enable_wrt_logs has been called
        """

        def _collect_logs():
            """Execute command to collect wrt logs."""
            try:
                with open(
                        '/sys/kernel/debug/ieee80211/phy0/iwlwifi/'
                        'iwlmvm/fw_dbg_collect', 'w') as f:
                    f.write('1')
                    f.flush()
                # There is some flakiness in log collection. This sleep
                # is due to the flakiness
                time.sleep(10)
                return True
            except Exception as e:
                logging.error('Exception %s in _collect logs ', str(e))
                return False

        def _get_num_log_files():
            """Return number of WRT log files."""
            try:
                return len(glob.glob('/var/spool/crash/devcoredump_iwlwifi*'))
            except Exception as e:
                logging.debug('Exception %s raised in _get_num_log_files',
                              str(e))
                return 0

        try:
            logging.info('Collecting WRT logs')
            #
            # The command to trigger the logs does seems to work always.
            # As a workaround for this flakiness, execute it multiple times
            # until a new log is created
            #
            num_logs_present = _get_num_log_files()
            logging.debug('%s logs present', num_logs_present)
            for i in range(10):
                time.sleep(1)
                logging.debug('Executing command to collect WRT logs ')
                if _collect_logs():
                    logging.debug('Command to collect WRT logs executed')
                else:
                    logging.debug('Command to collect WRT logs failed')
                    continue

                if _get_num_log_files() > num_logs_present:
                    logging.info('Successfully collected WRT logs ')
                    return True
                else:
                    logging.debug('Log file not written. Trying again')

            logging.info('Unable to collect WRT logs')
            return False
        except Exception as e:
            logging.error('Exception %s while collecting WRT logs', str(e))
            return False

    def _get_wake_enabled_path(self):
        # Walk up the parents from hci0 sysfs path and find the first one with
        # a power/wakeup property. Return that path (including power/wakeup).

        # Resolve hci path to get full device path (i.e. w/ usb or uart)
        search_at = os.path.realpath('/sys/class/bluetooth/hci0')

        # Exit early if path doesn't exist
        if not os.path.exists(search_at):
            return None

        # Walk up parents and try to find one with 'power/wakeup'
        for _ in range(search_at.count('/') - 1):
            search_at = os.path.normpath(os.path.join(search_at, '..'))
            try:
                path = os.path.join(search_at, 'power', 'wakeup')
                with open(path, 'r') as f:
                    return path
            except IOError:
                # No power wakeup at the given location so keep going
                continue

        return None

    def _is_wake_enabled(self):
        search_at = self._get_wake_enabled_path()

        if search_at is not None:
            try:
                with open(search_at, 'r') as f:
                    value = f.read()
                    logging.info('Power/wakeup found at {}: {}'.format(
                            search_at, value))
                    return 'enabled' in value
            except IOError:
                # Path was not readable
                return False

        logging.debug('No power/wakeup path found')
        return False

    def _set_wake_enabled(self, value):
        path = self._get_wake_enabled_path()
        if path is not None:
            try:
                with open(path, 'w') as f:
                    f.write('enabled' if value else 'disabled')
                    return True
            except IOError:
                # Path was not writeable
                return False

        return False

    def is_wake_enabled(self):
        """Checks whether the bluetooth adapter has wake enabled.

        This will walk through all parents of the hci0 sysfs path and try to
        find one with a 'power/wakeup' entry and returns whether its value is
        'enabled'.

        @return True if 'power/wakeup' of an hci0 parent is 'enabled'
        """
        enabled = self._is_wake_enabled()
        return enabled

    def set_wake_enabled(self, value):
        """Sets wake enabled to the value if path exists.

        This will walk through all parents of the hci0 sysfs path and write the
        value to the first one it finds.

        @param value: Sets power/wakeup to "enabled" if value is true, else
                   "disabled"

        @return True if it wrote value to a power/wakeup, False otherwise
        """
        return self._set_wake_enabled(value)

    def wait_for_hid_device(self, device_address, timeout, sleep_interval):
        """Waits for hid device with given device address.

        @param device_address: Peripheral address
        @param timeout: maximum number of seconds to wait
        @param sleep_interval: time to sleep between polls

        @return True if hid device found, False otherwise
        """

        def _match_hid_to_device(hidpath, device_address):
            """Check if given hid syspath is for the given device address """
            # If the syspath has a uniq property that matches the peripheral
            # device's address, then it has matched
            props = UdevadmInfo.GetProperties(hidpath)
            if (props.get(b'uniq', b'').lower().decode() == device_address):
                logging.info('Found hid device for address {} at {}'.format(
                        device_address, hidpath))
                return True
            else:
                logging.info('Path {} is not right device.'.format(hidpath))

            return False

        def _hid_is_created(device_address):
            existing_inputs = UdevadmTrigger(
                    subsystem_match=['input']).DryRun()
            for entry in existing_inputs:
                entry = entry.decode()
                bt_hid = any([t in entry for t in ['uhid', 'hci']])
                logging.info('udevadm trigger entry is {}: {}'.format(
                        bt_hid, entry))

                if (bt_hid and _match_hid_to_device(entry,
                                                    device_address.lower())):
                    return True

            return False

        if timeout is None:
            timeout = self.HID_TIMEOUT
        if sleep_interval is None:
            sleep_interval = self.HID_CHECK_SECS

        method_name = 'wait_for_hid_device'
        try:
            utils.poll_for_condition(
                    condition=(lambda: _hid_is_created(device_address)),
                    timeout=timeout,
                    sleep_interval=sleep_interval,
                    desc=('Waiting for HID device to be created from %s' %
                          device_address))
            return True
        except utils.TimeoutError as e:
            logging.error('%s: %s', method_name, e)
        except Exception as e:
            logging.error('%s: unexpected error: %s', method_name, e)

        return False

    def _powerd_last_resume_details(self, before=5, after=0):
        """ Look at powerd logs for last suspend/resume attempt.

        Note that logs are in reverse order (chronologically). Keep that in mind
        for the 'before' and 'after' parameters.

        @param before: Number of context lines before search item to show.
        @param after: Number of context lines after search item to show.

        @return Most recent lines containing suspend resume details or ''.
        """
        event_file = '/var/log/power_manager/powerd.LATEST'

        # Each powerd_suspend wakeup has a log "powerd_suspend returned 0",
        # with the return code of the suspend. We search for the last
        # occurrence in the log, and then find the collocated event_count log,
        # indicating the wakeup cause. -B option for grep will actually grab the
        # *next* 5 logs in time, since we are piping the powerd file backwards
        # with tac command
        resume_indicator = 'powerd_suspend returned'
        cmd = 'tac {} | grep -A {} -B {} -m1 "{}"'.format(
                event_file, after, before, resume_indicator)

        try:
            return utils.run(cmd).stdout
        except error.CmdError:
            logging.error('Could not locate recent suspend')

        return ''

    def bt_caused_last_resume(self):
        """Checks if last resume from suspend was caused by bluetooth

        @return: True if BT wake path was cause of resume, False otherwise
        """

        # When the resume cause is printed to powerd log, it omits the
        # /power/wakeup portion of wake path
        bt_wake_path = self._get_wake_enabled_path()

        # If bluetooth does not have a valid wake path, it could not have caused
        # the resume
        if not bt_wake_path:
            return False

        bt_wake_path = bt_wake_path.replace('/power/wakeup', '')

        last_resume_details = self._powerd_last_resume_details().rstrip(
                '\n ').split('\n')
        logging.debug('/var/log/power_manager/powerd.LATEST: 5 lines after '
                      'powerd_suspend returns:')
        for l in last_resume_details[::-1]:
            logging.debug(l)
        # If BT caused wake, there will be a line describing the bt wake
        # path's event_count before and after the resume
        for line in last_resume_details:
            if 'event_count' in line:
                logging.info('Checking wake event: {}'.format(line))
                if bt_wake_path in line:
                    logging.debug('BT event woke the DUT')
                    return True

        return False

    def find_last_suspend_via_powerd_logs(self):
        """ Finds the last suspend attempt via powerd logs.

        Finds the last suspend attempt using powerd logs by searching backwards
        through the logs to find the latest entries with 'powerd_suspend'. If we
        can't find a suspend attempt, we return None.

        @return: Tuple (suspend start time, suspend end time, suspend result) or
                None if we can't find a suspend attempt
        """
        # Logs look like this (ignore newline):
        # 2021-02-11T18:53:43.561880Z INFO powerd:
        #       [daemon.cc(724)] powerd_suspend returned 0
        # ... stuff in between ...
        # 2021-02-11T18:53:13.277695Z INFO powerd:
        #       [suspender.cc(574)] Starting suspend

        # Date format for strptime and strftime
        date_format = '%Y-%m-%dT%H:%M:%S.%fZ'
        date_group_re = ('(?P<date>[0-9]+-[0-9]+-[0-9]+T'
                         '[0-9]+:[0-9]+:[0-9]+[.][0-9]+Z)\s')

        finish_suspend_re = re.compile(
                '^{date_regex}'
                '.*daemon.*powerd_suspend returned '
                '(?P<exitcode>[0-9]+)'.format(date_regex=date_group_re))
        start_suspend_re = re.compile(
                '^{date_regex}.*suspender.*'
                'Starting suspend'.format(date_regex=date_group_re))

        now = datetime.now()
        last_resume_details = self._powerd_last_resume_details(before=0,
                                                               after=8)
        if last_resume_details:
            start_time, end_time, ret = None, None, None
            try:
                for line in last_resume_details.split('\n'):
                    logging.debug('Last suspend search: %s', line)
                    m = finish_suspend_re.match(line)
                    if m:
                        logging.debug('Found suspend end: date(%s) ret(%s)',
                                      m.group('date'), m.group('exitcode'))
                        end_time = datetime.strptime(
                                m.group('date'),
                                date_format).replace(year=now.year)
                        ret = int(m.group('exitcode'))

                    m = start_suspend_re.match(line)
                    if m:
                        logging.debug('Found suspend start: date(%s)',
                                      m.group('date'))
                        start_time = datetime.strptime(
                                m.group('date'),
                                date_format).replace(year=now.year)
                        break

                if all([x is not None for x in [start_time, end_time, ret]]):
                    # Return dates in string format due to inconsistency between
                    # python2/3 usage on host and dut
                    return (start_time.strftime(self.OUT_DATE_FORMAT),
                            end_time.strftime(self.OUT_DATE_FORMAT), ret)
                else:
                    logging.error(
                            'Failed to parse details from last suspend. %s %s %s',
                            str(start_time), str(end_time), str(ret))
            except Exception as e:
                logging.error('Failed to parse last suspend: %s', str(e))
        else:
            logging.error('No powerd_suspend attempt found')

        return None

    def do_suspend(self, seconds, expect_bt_wake):
        """Suspend DUT using the power manager.

        @param seconds: The number of seconds to suspend the device.
        @param expect_bt_wake: Whether we expect bluetooth to wake us from
            suspend. If true, we expect this resume will occur early

        @throws: SuspendFailure on resume with unexpected timing or wake source.
            The raised exception will be handled as a non-zero retcode over the
            RPC, signalling for the test to fail.
        """
        early_wake = False
        try:
            sys_power.do_suspend(seconds)

        except sys_power.SpuriousWakeupError:
            logging.info('Early resume detected...')
            early_wake = True

        # Handle error conditions based on test expectations, whether resume
        # was early, and cause of the resume
        bt_caused_wake = self.bt_caused_last_resume()
        logging.info('Cause for resume: {}'.format(
                'BT' if bt_caused_wake else 'Not BT'))

        if not expect_bt_wake and bt_caused_wake:
            raise sys_power.SuspendFailure('BT woke us unexpectedly')

        # TODO(b/160803597) - Uncomment when BT wake reason is correctly
        # captured in powerd log.
        #
        # if expect_bt_wake and not bt_caused_wake:
        #   raise sys_power.SuspendFailure('BT should have woken us')
        #
        # if bt_caused_wake and not early_wake:
        #   raise sys_power.SuspendFailure('BT wake did not come early')

        return True

    def get_wlan_vid_pid(self):
        """ Return vendor id and product id of the wlan chip on BT/WiFi module

        @returns: (vid,pid) on success; (None,None) on failure
        """
        vid = None
        pid = None
        path_template = '/sys/class/net/%s/device/'
        for dev_name in ['wlan0', 'mlan0']:
            if os.path.exists(path_template % dev_name):
                path_v = path_template % dev_name + 'vendor'
                path_d = path_template % dev_name + 'device'
                logging.debug('Paths are %s %s', path_v, path_d)
                try:
                    vid = open(path_v).read().strip('\n')
                    pid = open(path_d).read().strip('\n')
                    break
                except Exception as e:
                    logging.error('Exception %s while reading vid/pid', str(e))
        logging.debug('returning vid:%s pid:%s', vid, pid)
        return (vid, pid)

    def get_bt_transport(self):
        """ Return transport (UART/USB/SDIO) used by BT module

        @returns: USB/UART/SDIO on success; None on failure
        """
        try:
            transport_str = os.path.realpath(
                    '/sys/class/bluetooth/hci0/device/driver/module')
            logging.debug('transport is %s', transport_str)
            transport = transport_str.split('/')[-1]
            if transport == 'btusb':
                return 'USB'
            elif transport == 'hci_uart':
                return 'UART'
            elif transport in ['btmrvl_sdio', 'btmtksdio']:
                return 'SDIO'
            else:
                return None
        except Exception as e:
            logging.error('Exception %s in get_bt_transport', str(e))
            return None

    def get_bt_module_name(self):
        """ Return bluetooth module name for non-USB devices

        @returns '' on failure. On success return chipset name, if found in
                 dict.Otherwise it returns the raw string read.
        """
        # map the string read from device to chipset name
        chipset_string_dict = {
                'qcom,wcn3991-bt\x00': 'WCN3991',
                'qcom,wcn6750-bt\x00': 'WCN6750',
        }

        hci_device = '/sys/class/bluetooth/hci0'
        real_path = os.path.realpath(hci_device)

        logging.debug('real path is %s', real_path)
        if 'usb' in real_path:
            return ''

        device_path = os.path.join(real_path, 'device', 'of_node',
                                   'compatible')
        try:
            chipset_string = open(device_path).read()
            logging.debug('read string %s from %s', chipset_string,
                          device_path)
        except Exception as e:
            logging.error('Exception %s while reading from file', str(e),
                          device_path)
            return ''

        if chipset_string in chipset_string_dict:
            return chipset_string_dict[chipset_string]
        else:
            logging.debug("Chipset not known. Returning %s", chipset_string)
            return chipset_string

    def get_chipset_name(self):
        """ Get the name of BT/WiFi chipset on this host

        @returns chipset name if successful else ''
        """
        (vid, pid) = self.get_wlan_vid_pid()
        logging.debug('Bluetooth module vid pid is %s %s', vid, pid)
        transport = self.get_bt_transport()
        logging.debug('Bluetooth transport is %s', transport)
        if vid is None or pid is None:
            # Controllers that aren't WLAN+BT combo chips does not expose
            # Vendor ID/Product ID. Use alternate method.
            # This will return one of ['WCN3991', ''] or a string containing
            # the name of chipset read from DUT
            return self.get_bt_module_name()
        for name, l in self.CHIPSET_TO_VIDPID.items():
            if ((vid, pid), transport) in l:
                return name
        return ''

    def get_bt_usb_device_strs(self):
        """ Return the usb endpoints for the bluetooth device, if they exist

        We wish to be able to identify usb disconnect events that affect our
        bluetooth operation. To do so, we must first identify the usb endpoint
        that is associated with our bluetooth device.

        @returns: Relevant usb endpoints for the bluetooth device,
                  i.e. ['1-1','1-1.2'] if they exist,
                  [] otherwise
        """

        hci_device = '/sys/class/bluetooth/hci0'
        real_path = os.path.realpath(hci_device)

        # real_path for a usb bluetooth controller will look something like:
        # ../../devices/pci0000:00/0000:00:14.0/usb1/1-4/1-4:1.0/bluetooth/hci0
        if 'usb' not in real_path:
            return []

        logging.debug('Searching for usb path: {}'.format(real_path))

        # Grab all numbered entries between 'usb' and 'bluetooth' descriptors
        m = re.search(r'usb(.*)bluetooth', real_path)

        if not m:
            logging.error(
                    'Unable to extract usb dev from {}'.format(real_path))
            return []

        # Return the path as a list of individual usb descriptors
        return m.group(1).split('/')

    def get_bt_usb_disconnect_str(self):
        """ Return the expected log error on USB disconnect

        Locate the descriptor that will be used from the list of all usb
        descriptors associated with our bluetooth chip, and format into the
        expected string error for USB disconnect

        @returns: string representing expected usb disconnect log entry if usb
                  device could be identified, None otherwise
        """
        disconnect_log_template = 'usb {}: USB disconnect'
        descriptors = self.get_bt_usb_device_strs()

        # The usb disconnect log message seems to use the most detailed
        # descriptor that does not use the ':1.0' entry
        for d in sorted(descriptors, key=len, reverse=True):
            if ':' not in d:
                return disconnect_log_template.format(d)

        return None

    def get_device_utc_time(self):
        """ Get the current device time in UTC. """
        return datetime.utcnow().strftime(self.OUT_DATE_FORMAT)

    def create_audio_record_directory(self, audio_record_dir):
        """Create the audio recording directory.

        @param audio_record_dir: the audio recording directory

        @returns: True on success. False otherwise.
        """
        try:
            if not os.path.exists(audio_record_dir):
                os.makedirs(audio_record_dir)
            return True
        except Exception as e:
            logging.error('Failed to create %s on the DUT: %s',
                          audio_record_dir, e)
            return False

    def start_capturing_audio_subprocess(self, audio_data, recording_device):
        """Start capturing audio in a subprocess.

        @param audio_data: the audio test data
        @param recording_device: which device recorded the audio,
                possible values are 'recorded_by_dut' or 'recorded_by_peer'

        @returns: True on success. False otherwise.
        """
        audio_data = json.loads(audio_data)
        return self._cras_test_client.start_capturing_subprocess(
                audio_data[recording_device],
                sample_format=audio_data['format'],
                channels=audio_data['channels'],
                rate=audio_data['rate'],
                duration=audio_data['duration'])

    def stop_capturing_audio_subprocess(self):
        """Stop capturing audio.

        @returns: True on success. False otherwise.
        """
        return self._cras_test_client.stop_capturing_subprocess()

    def _generate_playback_file(self, audio_data):
        """Generate the playback file if it does not exist yet.

        Some audio test files may be large. Generate them on the fly
        to save the storage of the source tree.

        @param audio_data: the audio test data
        """
        if not os.path.exists(audio_data['file']):
            data_format = dict(file_type='raw',
                               sample_format='S16_LE',
                               channel=audio_data['channels'],
                               rate=audio_data['rate'])

            # Make the audio file a bit longer to handle any delay
            # issue in capturing.
            duration = audio_data['duration'] + 3
            audio_test_data_module.GenerateAudioTestData(
                    data_format=data_format,
                    path=audio_data['file'],
                    duration_secs=duration,
                    frequencies=audio_data['frequencies'])
            logging.debug("Raw file generated: %s", audio_data['file'])

    def start_playing_audio_subprocess(self, audio_data, pin_device=None):
        """Start playing audio in a subprocess.

        @param audio_data: the audio test data.
        @param pin_device: the device id to play audio.

        @returns: True on success. False otherwise.
        """
        audio_data = json.loads(audio_data)
        self._generate_playback_file(audio_data)
        try:
            return self._cras_test_client.start_playing_subprocess(
                    audio_data['file'],
                    pin_device=pin_device,
                    channels=audio_data['channels'],
                    rate=audio_data['rate'],
                    duration=audio_data['duration'])
        except Exception as e:
            logging.error("start_playing_subprocess() failed: %s", str(e))
            return False

    def stop_playing_audio_subprocess(self):
        """Stop playing audio in the subprocess.

        @returns: True on success. False otherwise.
        """
        return self._cras_test_client.stop_playing_subprocess()

    def play_audio(self, audio_data):
        """Play audio.

        It blocks until it has completed playing back the audio.

        @param audio_data: the audio test data

        @returns: True on success. False otherwise.
        """
        audio_data = json.loads(audio_data)
        self._generate_playback_file(audio_data)
        return self._cras_test_client.play(audio_data['file'],
                                           channels=audio_data['channels'],
                                           rate=audio_data['rate'],
                                           duration=audio_data['duration'])

    def check_audio_frames_legitimacy(self, audio_test_data, recording_device,
                                      recorded_file):
        """Get the number of frames in the recorded audio file.

        @param audio_test_data: the audio test data
        @param recording_device: which device recorded the audio,
                possible values are 'recorded_by_dut' or 'recorded_by_peer'
        @param recorded_file: the recorded file name

        @returns: True if audio frames are legitimate.
        """
        if bool(recorded_file):
            recorded_filename = recorded_file
        else:
            audio_test_data = json.loads(audio_test_data)
            recorded_filename = audio_test_data[recording_device]

        if recorded_filename.endswith('.raw'):
            # Make sure that the recorded file does not contain all zeros.
            filesize = os.path.getsize(recorded_filename)
            cmd_str = 'cmp -s -n %d %s /dev/zero' % (filesize,
                                                     recorded_filename)
            try:
                result = subprocess.call(cmd_str.split())
                return result != 0
            except Exception as e:
                logging.error("Failed: %s (%s)", cmd_str, str(e))
                return False
        else:
            # The recorded wav file should not be empty.
            wav_file = check_quality.WaveFile(recorded_filename)
            return wav_file.get_number_frames() > 0

    def convert_audio_sample_rate(self, input_file, out_file, test_data,
                                  new_rate):
        """Convert audio file to new sample rate.

        @param input_file: Path to file to upsample.
        @param out_file: Path to create upsampled file.
        @param test_data: Dictionary with information about file.
        @param new_rate: New rate to upsample file to.

        @returns: True if upsampling succeeded, False otherwise.
        """
        test_data = json.loads(test_data)
        logging.debug('Resampling file {} to new rate {}'.format(
                input_file, new_rate))

        convert_format(input_file,
                       test_data['channels'],
                       test_data['bit_width'],
                       test_data['rate'],
                       out_file,
                       test_data['channels'],
                       test_data['bit_width'],
                       new_rate,
                       1.0,
                       use_src_header=True,
                       use_dst_header=True)

        return os.path.isfile(out_file)

    def trim_wav_file(self,
                      in_file,
                      out_file,
                      new_duration,
                      test_data,
                      tolerance=0.1):
        """Trim long file to desired length.

        Trims audio file to length by cutting out silence from beginning and
        end.

        @param in_file: Path to audio file to be trimmed.
        @param out_file: Path to trimmed audio file to create.
        @param new_duration: A float representing the desired duration of
                the resulting trimmed file.
        @param test_data: Dictionary containing information about the test file.
        @param tolerance: (optional) A float representing the allowable
                difference between trimmed file length and desired duration

        @returns: True if file was trimmed successfully, False otherwise.
        """
        test_data = json.loads(test_data)
        trim_silence_from_wav_file(in_file, out_file, new_duration)
        measured_length = get_file_length(out_file, test_data['channels'],
                                          test_data['bit_width'],
                                          test_data['rate'])
        return abs(measured_length - new_duration) <= tolerance

    def unzip_audio_test_data(self, tar_path, data_dir):
        """Unzip audio test data files.

        @param tar_path: Path to audio test data tarball on DUT.
        @oaram data_dir: Path to directory where to extract test data directory.

        @returns: True if audio test data folder exists, False otherwise.
        """
        logging.debug('Downloading audio test data on DUT')
        # creates path to dir to extract test data to by taking name of the
        # tarball without the extension eg. <dir>/file.ext to data_dir/file/
        audio_test_dir = os.path.join(
                data_dir,
                os.path.split(tar_path)[1].split('.', 1)[0])

        unzip_cmd = 'tar -xf {0} -C {1}'.format(tar_path, data_dir)

        unzip_proc = subprocess.Popen(unzip_cmd.split(),
                                      stdout=subprocess.PIPE,
                                      stderr=subprocess.PIPE)
        _, stderr = unzip_proc.communicate()

        if stderr:
            logging.error('Error occurred in unzipping audio data: {}'.format(
                    str(stderr)))
            return False

        return unzip_proc.returncode == 0 and os.path.isdir(audio_test_dir)

    def convert_raw_to_wav(self, input_file, output_file, test_data):
        """Convert raw audio file to wav file.

        @oaram input_file: the location of the raw file
        @param output_file: the location to place the resulting wav file
        @param test_data: the data for the file being converted

        @returns: True if conversion was successful otherwise false
        """
        test_data = json.loads(test_data)
        convert_raw_file(input_file, test_data['channels'],
                         test_data['bit_width'], test_data['rate'],
                         output_file)

        return os.path.isfile(output_file)

    def get_primary_frequencies(self, audio_test_data, recording_device,
                                recorded_file):
        """Get primary frequencies of the audio test file.

        @param audio_test_data: the audio test data
        @param recording_device: which device recorded the audio,
                possible values are 'recorded_by_dut' or 'recorded_by_peer'
        @param recorded_file: the recorded file name

        @returns: a list of primary frequencies of channels in the audio file
        """
        audio_test_data = json.loads(audio_test_data)

        if bool(recorded_file):
            recorded_filename = recorded_file
        else:
            recorded_filename = audio_test_data[recording_device]

        args = CheckQualityArgsClass(filename=recorded_filename,
                                     rate=audio_test_data['rate'],
                                     channel=audio_test_data['channels'],
                                     bit_width=16)
        raw_data, rate = check_quality.read_audio_file(args)
        checker = check_quality.QualityChecker(raw_data, rate)
        # The highest frequency recorded would be near 24 Khz
        # as the max sample rate is 48000 in our tests.
        # So let's set ignore_high_freq to be 48000.
        checker.do_spectral_analysis(ignore_high_freq=48000,
                                     check_quality=False,
                                     quality_params=None)
        spectra = checker._spectrals
        primary_freq = [
                float(spectra[i][0][0]) if spectra[i] else 0
                for i in range(len(spectra))
        ]
        primary_freq.sort()
        return primary_freq

    def enable_wbs(self, value):
        """Enable or disable wideband speech (wbs) per the value.

        @param value: True to enable wbs.

        @returns: True if the operation succeeds.
        """
        return self._cras_test_client.enable_wbs(value)

    def set_player_playback_status(self, status):
        """Set playback status for the registered media player.

        @param status: playback status in string.

        """
        return self._cras_test_client.set_player_playback_status(status)

    def set_player_position(self, position):
        """Set media position for the registered media player.

        @param position: position in micro seconds.

        """
        return self._cras_test_client.set_player_position(position)

    def set_player_metadata(self, metadata):
        """Set metadata for the registered media player.

        @param metadata: dictionary of media metadata.

        """
        return self._cras_test_client.set_player_metadata(metadata)

    def set_player_length(self, length):
        """Set media length for the registered media player.

        Media length is a part of metadata information. However, without
        specify its type to int64. dbus-python will guess the variant type to
        be int32 by default. Separate it from the metadata function to help
        prepare the data differently.

        @param length: length in micro seconds.

        """
        return self._cras_test_client.set_player_length(length)

    def select_input_device(self, device_name):
        """Select the audio input device.

        @param device_name: the name of the Bluetooth peer device

        @returns: True if the operation succeeds.
        """
        return self._cras_test_client.select_input_device(device_name)

    @dbus_safe(None)
    def select_output_node(self, node_type):
        """Select the audio output node.

        @param node_type: the node type of the Bluetooth peer device

        @returns: True if the operation succeeds.
        """
        return cras_utils.set_single_selected_output_node(node_type)

    @dbus_safe(None)
    def get_selected_output_device_type(self):
        """Get the selected audio output node type.

        @returns: the node type of the selected output device.
        """
        # Note: should convert the dbus.String to the regular string.
        return str(cras_utils.get_selected_output_device_type())

    @dbus_safe(None)
    def get_device_id_from_node_type(self, node_type, is_input):
        """Gets device id from node type.

        @param node_type: a node type defined in CRAS_NODE_TYPES.
        @param is_input: True if the node is input. False otherwise.

        @returns: a string for device id.
        """
        return cras_utils.get_device_id_from_node_type(node_type, is_input)

    def get_audio_thread_summary(self):
        """Dumps audio thread info.

        @returns: a list of cras audio information.
        """
        return cras_utils.get_audio_thread_summary()

    def is_btmanagerd_present(self):
        """ Check if /usr/bin/btmanagerd file is present

        @returns: True if /usr/bin/btmanagerd is present and False if not
        """
        return os.path.exists(self.BTMANGERD_FILE_PATH)


class BluezPairingAgent:
    """The agent handling the authentication process of bluetooth pairing.

    BluezPairingAgent overrides RequestPinCode method to return a given pin code.
    User can use this agent to pair bluetooth device which has a known
    pin code.

    TODO (josephsih): more pairing modes other than pin code would be
    supported later.

    """

    def __init__(self, bus, path, pin):
        """Constructor.

        @param bus: system bus object.
        @param path: Object path to register.
        @param pin: Pin to respond with for |RequestPinCode|.
        """
        self._pin = pin
        self.path = path
        self.obj = bus.register_object(path, self, None)

    # D-Bus service definition (required by pydbus).
    dbus = """
        <node>
            <interface name="org.bluez.Agent1">
                <method name="RequestPinCode">
                    <arg type="o" name="device_path" direction="in" />
                    <arg type="s" name="response" direction="out" />
                </method>
                <method name="AuthorizeService">
                    <arg type="o" name="device_path" direction="in" />
                    <arg type="s" name="uuid" direction="in" />
                    <arg type="b" name="response" direction="out" />
                </method>
            </interface>
        </node>
        """

    def unregister(self):
        """Unregisters self from bus."""
        self.obj.unregister()

    def RequestPinCode(self, device_path):
        """Requests pin code for a device.

        Returns the known pin code for the request.

        @param device_path: The object path of the device.

        @returns: The known pin code.

        """
        logging.info('RequestPinCode for %s; return %s', device_path,
                     self._pin)
        return self._pin

    def AuthorizeService(self, device_path, uuid):
        """Authorize given service for device.

        @param device_path: The object path of the device.
        @param uuid: The service that needs to be authorized.

        @returns: True (we authorize everything since this is a test)
        """
        return True


class BluezFacadeLocal(BluetoothBaseFacadeLocal):
    """Exposes DUT methods called remotely during Bluetooth autotests for the
    Bluez daemon.

    All instance methods of this object without a preceding '_' are exposed via
    an XML-RPC server. This is not a stateless handler object, which means that
    if you store state inside the delegate, that state will remain around for
    future calls.
    """

    BLUETOOTHD_JOB = 'bluetoothd'

    DBUS_ERROR_SERVICEUNKNOWN = 'org.freedesktop.DBus.Error.ServiceUnknown'

    BLUEZ_SERVICE_NAME = 'org.bluez'
    BLUEZ_MANAGER_PATH = '/'
    BLUEZ_DEBUG_LOG_PATH = '/org/chromium/Bluetooth'
    BLUEZ_DEBUG_LOG_IFACE = 'org.chromium.Bluetooth.Debug'
    BLUEZ_MANAGER_IFACE = 'org.freedesktop.DBus.ObjectManager'
    BLUEZ_ADAPTER_IFACE = 'org.bluez.Adapter1'
    BLUEZ_ADMIN_POLICY_SET_IFACE = 'org.bluez.AdminPolicySet1'
    BLUEZ_ADMIN_POLICY_STATUS_IFACE = 'org.bluez.AdminPolicyStatus1'
    BLUEZ_BATTERY_IFACE = 'org.bluez.Battery1'
    BLUEZ_DEVICE_IFACE = 'org.bluez.Device1'
    BLUEZ_GATT_SERV_IFACE = 'org.bluez.GattService1'
    BLUEZ_GATT_CHAR_IFACE = 'org.bluez.GattCharacteristic1'
    BLUEZ_GATT_DESC_IFACE = 'org.bluez.GattDescriptor1'
    BLUEZ_LE_ADVERTISING_MANAGER_IFACE = 'org.bluez.LEAdvertisingManager1'
    BLUEZ_ADV_MONITOR_MANAGER_IFACE = 'org.bluez.AdvertisementMonitorManager1'
    BLUEZ_AGENT_MANAGER_PATH = '/org/bluez'
    BLUEZ_AGENT_MANAGER_IFACE = 'org.bluez.AgentManager1'
    BLUEZ_PROFILE_MANAGER_PATH = '/org/bluez'
    BLUEZ_PROFILE_MANAGER_IFACE = 'org.bluez.ProfileManager1'
    BLUEZ_ERROR_ALREADY_EXISTS = 'org.bluez.Error.AlreadyExists'
    BLUEZ_PLUGIN_DEVICE_IFACE = 'org.chromium.BluetoothDevice'
    DBUS_PROP_IFACE = 'org.freedesktop.DBus.Properties'
    AGENT_PATH = '/test/agent'

    BTMON_STOP_DELAY_SECS = 3

    # Timeout for how long we'll wait for BlueZ and the Adapter to show up
    # after reset.
    ADAPTER_TIMEOUT = 30

    # How long we should wait for property update signal before we cancel it.
    PROPERTY_UPDATE_TIMEOUT_MILLI_SECS = 5000

    # How often we should check for property update exit.
    PROPERTY_UPDATE_CHECK_MILLI_SECS = 500

    def __init__(self):
        # Init the BaseFacade first
        super(BluezFacadeLocal, self).__init__()

        # Open the Bluetooth Raw socket to the kernel which provides us direct,
        # raw, access to the HCI controller.
        self._raw = bluetooth_socket.BluetoothRawSocket()

        # Open the Bluetooth Control socket to the kernel which provides us
        # raw management access to the Bluetooth Host Subsystem. Read the list
        # of adapter indexes to determine whether or not this device has a
        # Bluetooth Adapter or not.
        self._control = bluetooth_socket.BluetoothControlSocket()
        self._has_adapter = len(self._control.read_index_list()) > 0

        # Create an Advertisement Monitor App Manager instance.
        # This needs to be created before making any dbus connections as
        # AdvMonitorAppMgr internally forks a new helper process and due to
        # a limitation of python, it is not possible to fork a new process
        # once any dbus connections are established.
        self.advmon_appmgr = adv_monitor_helper.AdvMonitorAppMgr()

        # Set up the connection to the D-Bus System Bus, get the object for
        # the Bluetooth Userspace Daemon (BlueZ) and that daemon's object for
        # the Bluetooth Adapter, and the advertising manager.
        self.bus = pydbus.SystemBus()
        self._update_bluez()
        self._update_adapter()
        self._update_advertising()
        self._update_adv_monitor_manager()

        # The agent to handle pin code request, which will be
        # created when user calls pair_legacy_device method.
        self._pairing_agent = None
        # The default capability of the agent.
        self._capability = 'KeyboardDisplay'

        # Initialize a btmon object to record bluetoothd's activity.
        self.btmon = output_recorder.OutputRecorder(
                ['btmon', '-c', 'never'],
                stop_delay_secs=self.BTMON_STOP_DELAY_SECS)

        self.advertisements = []
        self.advmon_interleave_logger = logger_helper.InterleaveLogger()
        self._chrc_property = None
        self._timeout_id = 0
        self._signal_watch = None
        self._dbus_mainloop = GObject.MainLoop()

    @dbus_safe(False)
    def set_debug_log_levels(self, bluez_vb, kernel_vb):
        """Enable or disable the debug logs of bluetooth

        @param bluez_vb: verbosity of bluez debug log, either 0 or 1
        @param kernel_vb: verbosity of kernel debug log, either 0 or 1

        """
        debug_object = self.bus.get(self.BLUEZ_SERVICE_NAME,
                                    self.BLUEZ_DEBUG_LOG_PATH)

        # Make a raw synchronous call using GLib (pydbus doesn't correctly
        # serialize '(yy)'.
        raw_dbus_call_sync(self.bus, debug_object, self.BLUEZ_DEBUG_LOG_IFACE,
                           'SetLevels',
                           GLib.Variant('(yy)', (bluez_vb, kernel_vb)),
                           GLib.VariantType.new('()'))
        return

    @dbus_safe(False)
    def set_quality_debug_log(self, enable):
        """Enable or disable bluez quality debug log in the DUT
        @param enable: True to enable all of the debug log,
                       False to disable all of the debug log.
        """
        bluez_debug = self.bus.get(
                self.BLUEZ_SERVICE_NAME, self.BLUEZ_DEBUG_LOG_PATH)[
                        self.BLUEZ_DEBUG_LOG_IFACE]
        bluez_debug.SetQualityDebug(enable)

    @dbus_safe(False)
    def start_bluetoothd(self):
        """start bluetoothd.

        This includes powering up the adapter.

        @returns: True if bluetoothd is started correctly.
                  False otherwise.

        """
        # Always start bluez tests with Floss disabled
        self.configure_floss(enabled=False)

        # Start the daemon and exit if that fails.
        if not UpstartClient.start(self.BLUETOOTHD_JOB):
            return False

        logging.debug('waiting for bluez start')
        try:
            utils.poll_for_condition(condition=self._update_bluez,
                                     desc='Bluetooth Daemon has started.',
                                     timeout=self.ADAPTER_TIMEOUT)
        except Exception as e:
            logging.error('timeout: error starting bluetoothd: %s', e)
            return False

        # Waiting for the self._adapter object.
        # This does not mean that the adapter is powered on.
        logging.debug('waiting for bluez to obtain adapter information')
        try:
            utils.poll_for_condition(
                    condition=self._update_adapter,
                    desc='Bluetooth Daemon has adapter information.',
                    timeout=self.ADAPTER_TIMEOUT)
        except Exception as e:
            logging.error('timeout: error starting adapter: %s', e)
            return False

        # Waiting for the self._advertising interface object.
        logging.debug('waiting for bluez to obtain interface manager.')
        try:
            utils.poll_for_condition(
                    condition=self._update_advertising,
                    desc='Bluetooth Daemon has advertising interface.',
                    timeout=self.ADAPTER_TIMEOUT)
        except utils.TimeoutError:
            logging.error('timeout: error getting advertising interface')
            return False

        # Register the pairing agent so we can authorize connections
        logging.debug('registering default pairing agent')
        self._setup_pairing_agent(0)

        return True

    @dbus_safe(False)
    def stop_bluetoothd(self):
        """stop bluetoothd.

        @returns: True if bluetoothd is stopped correctly.
                  False otherwise.

        """

        def bluez_stopped():
            """Checks the bluetooth daemon status.

            @returns: True if bluez is stopped. False otherwise.

            """
            return not self._update_bluez()

        # Stop the daemon and exit if that fails.
        if not UpstartClient.stop(self.BLUETOOTHD_JOB):
            return False

        logging.debug('waiting for bluez stop')
        try:
            utils.poll_for_condition(condition=bluez_stopped,
                                     desc='Bluetooth Daemon has stopped.',
                                     timeout=self.ADAPTER_TIMEOUT)
            bluetoothd_stopped = True
        except Exception as e:
            logging.error('timeout: error stopping bluetoothd: %s', e)
            bluetoothd_stopped = False

        return bluetoothd_stopped

    def restart_cras(self):
        """Restarts the cras daemon."""
        return self._restart_cras()

    def is_bluetoothd_running(self):
        """Is bluetoothd running?

        @returns: True if bluetoothd is running

        """
        return bool(self._get_dbus_proxy_for_bluetoothd())

    def is_bluetoothd_proxy_valid(self):
        """Checks whether the proxy object for bluetoothd is ok.

        The dbus proxy object (self._bluez) can become unusable if bluetoothd
        crashes or restarts for any reason. This method checks whether this has
        happened by attempting to use the object proxy. If bluetoothd has
        restarted (or is not available), then the session will no longer be
        valid and this will result in a dbus exception (GLib.Error).

        Returns:
            True if the bluez proxy is still usable. False otherwise.
        """

        try:
            return self.is_bluetoothd_running() and bool(
                    self._objmgr_proxy) and bool(
                            self._objmgr_proxy.GetManagedObjects())
        except GLib.Error:
            return False

    def _update_bluez(self):
        """Store a D-Bus proxy for the Bluetooth daemon in self._bluez.

        This may be called in a loop until it returns True to wait for the
        daemon to be ready after it has been started.

        @return True on success, False otherwise.

        """
        self._bluez = self._get_dbus_proxy_for_bluetoothd()
        return bool(self._bluez)

    @property
    def _objmgr_proxy(self):
        """Returns proxy object to object manager if bluez is valid."""
        if self._bluez:
            return self._bluez[self.BLUEZ_MANAGER_IFACE]

        return None

    @dbus_safe(False)
    def _get_dbus_proxy_for_bluetoothd(self):
        """Get the D-Bus proxy for the Bluetooth daemon.

        @return True on success, False otherwise.

        """
        bluez = None
        try:
            bluez = self.bus.get(self.BLUEZ_SERVICE_NAME,
                                 self.BLUEZ_MANAGER_PATH)
            logging.debug('bluetoothd is running')
        except GLib.Error as e:
            # When bluetoothd is not running, the exception looks like
            #     org.freedesktop.DBus.Error.ServiceUnknown: The name org.bluez
            #     was not provided by any .service files
            if self.DBUS_ERROR_SERVICEUNKNOWN in str(e):
                logging.debug('bluetoothd is not running')
            else:
                logging.error('Error getting dbus proxy for Bluez: %s', e)
        return bluez

    def _update_adapter(self):
        """Store a D-Bus proxy for the local adapter in self._adapter.

        This may be called in a loop until it returns True to wait for the
        daemon to be ready, and have obtained the adapter information itself,
        after it has been started.

        Since not all devices will have adapters, this will also return True
        in the case where we have obtained an empty adapter index list from the
        kernel.

        Note that this method does not power on the adapter.

        @return True on success, including if there is no local adapter,
            False otherwise.

        """
        self._adapter = None
        self._adapter_path = None

        # Re-check kernel to make sure adapter is available
        self._has_adapter = len(self._control.read_index_list()) > 0

        if self._bluez is None:
            logging.warning('Bluez not found!')
            return False
        if not self._has_adapter:
            logging.debug('Device has no adapter; returning')
            return True
        (self._adapter, self._adapter_path) = self._get_adapter()
        return bool(self._adapter)

    def _update_advertising(self):
        """Store a D-Bus proxy for the local advertising interface manager.

        This may be called repeatedly in a loop until True is returned;
        otherwise we wait for bluetoothd to start. After bluetoothd starts, we
        check the existence of a local adapter and proceed to get the
        advertisement interface manager.

        Since not all devices will have adapters, this will also return True
        in the case where there is no adapter.

        @return True on success, including if there is no local adapter,
                False otherwise.

        """
        self._advertising = None
        if self._bluez is None:
            logging.warning('Bluez not found!')
            return False
        if not self._has_adapter:
            logging.debug('Device has no adapter; returning')
            return True
        self._advertising = self._advertising_proxy
        return bool(self._advertising)

    def _update_adv_monitor_manager(self):
        """Store a D-Bus proxy for the local advertisement monitor manager.

        This may be called repeatedly in a loop until True is returned;
        otherwise we wait for bluetoothd to start. After bluetoothd starts, we
        check the existence of a local adapter and proceed to get the
        advertisement monitor manager interface.

        Since not all devices will have adapters, this will also return True
        in the case where there is no adapter.

        @return True on success, including if there is no local adapter,
                False otherwise.

        """
        self._adv_monitor_manager = None
        if self._bluez is None:
            logging.warning('Bluez not found!')
            return False
        if not self._has_adapter:
            logging.debug('Device has no adapter; returning without '
                          'advertisement monitor manager')
            return True
        self._adv_monitor_manager = self._get_adv_monitor_manager()
        return bool(self._adv_monitor_manager)

    @dbus_safe(False)
    def _get_adapter(self):
        """Get the D-Bus proxy for the local adapter.

        @return Tuple of (adapter, object_path) on success else (None, None).

        """
        objects = self._objmgr_proxy.GetManagedObjects()
        for path, ifaces in six.iteritems(objects):
            logging.debug('%s -> %r', path, list(ifaces.keys()))
            if self.BLUEZ_ADAPTER_IFACE in ifaces:
                logging.debug('using adapter %s', path)
                adapter = self.bus.get(self.BLUEZ_SERVICE_NAME, path)
                return (adapter, path)
        else:
            logging.warning('No adapter found in interface!')
            return (None, None)

    @property
    def _adapter_proxy(self):
        """Returns proxy object to adapter interface if adapter is valid."""
        if self._adapter:
            return self._adapter[self.BLUEZ_ADAPTER_IFACE]

        return None

    @property
    def _property_proxy(self):
        """Returns proxy object to adapter properties if adapter is valid."""
        if self._adapter:
            return self._adapter[self.DBUS_PROP_IFACE]

        return None

    @property
    def _advertising_proxy(self):
        """Returns proxy object to advertising interface if adapter is valid."""
        if self._adapter:
            return self._adapter[self.BLUEZ_LE_ADVERTISING_MANAGER_IFACE]

        return None

    @dbus_safe(False)
    def _get_adv_monitor_manager(self):
        """Get the D-Bus proxy for the local advertisement monitor manager.

        @return the advertisement monitor manager interface object.

        """
        return self._adapter[self.BLUEZ_ADV_MONITOR_MANAGER_IFACE]

    @dbus_safe(False)
    def reset_on(self):
        """Reset the adapter and settings and power up the adapter.

        @return True on success, False otherwise.

        """
        return self._reset(set_power=True)

    @dbus_safe(False)
    def reset_off(self):
        """Reset the adapter and settings, leave the adapter powered off.

        @return True on success, False otherwise.

        """
        return self._reset(set_power=False)

    def has_adapter(self):
        """Return if an adapter is present.

        This will only return True if we have determined both that there is
        a Bluetooth adapter on this device (kernel adapter index list is not
        empty) and that the Bluetooth daemon has exported an object for it.

        @return True if an adapter is present, False if not.

        """
        return self._has_adapter and self._adapter is not None

    def _reset(self, set_power=False):
        """Remove remote devices and set adapter to set_power state.

        Do not restart bluetoothd as this may incur a side effect.
        The unhappy chrome may disable the adapter randomly.

        @param set_power: adapter power state to set (True or False).

        @return True on success, False otherwise.

        """
        logging.debug('_reset')

        if not self._adapter:
            logging.warning('Adapter not found!')
            return False

        objects = self._objmgr_proxy.GetManagedObjects()

        devices = []
        for path, ifaces in six.iteritems(objects):
            if self.BLUEZ_DEVICE_IFACE in ifaces:
                devices.append(objects[path][self.BLUEZ_DEVICE_IFACE])

        # Turn on the adapter in order to remove all remote devices.
        if not self.is_powered_on():
            if not self.set_powered(True):
                logging.warning('Unable to power on the adapter')
                return False

        for device in devices:
            logging.debug('removing %s', device.get('Address'))
            self.remove_device_object(device.get('Address'))

        # Toggle power to the adapter.
        if not self.set_powered(False):
            logging.warning('Unable to power off adapter')
            return False
        if set_power and not self.set_powered(True):
            logging.warning('Unable to power on adapter')
            return False

        return True

    @dbus_safe(False)
    def is_discoverable(self):
        """Returns whether the adapter is discoverable."""
        return bool(self._get_adapter_properties().get('Discoverable') == 1)

    @dbus_safe(False)
    def set_powered(self, powered):
        """Set the adapter power state.

        @param powered: adapter power state to set (True or False).

        @return True on success, False otherwise.

        """
        if not self._adapter:
            if not powered:
                # Return success if we are trying to power off an adapter that's
                # missing or gone away, since the expected result has happened.
                return True
            else:
                logging.warning('Adapter not found!')
                return False

        logging.debug('_set_powered %r', powered)
        self._property_proxy.Set(self.BLUEZ_ADAPTER_IFACE, 'Powered',
                                 GLib.Variant('b', powered))

        return True

    @dbus_safe(False)
    def set_discoverable(self, discoverable):
        """Set the adapter discoverable state.

        @param discoverable: adapter discoverable state to set (True or False).

        @return True on success, False otherwise.

        """
        if not discoverable and not self._adapter:
            # Return success if we are trying to make an adapter that's
            # missing or gone away, undiscoverable, since the expected result
            # has happened.
            return True
        self._property_proxy.Set(self.BLUEZ_ADAPTER_IFACE, 'Discoverable',
                                 GLib.Variant('b', discoverable))
        return True

    @dbus_safe(False)
    def get_discoverable_timeout(self):
        """Get the adapter discoverable_timeout.

        @return True on success, False otherwise.

        """
        return int(
                self._property_proxy.Get(self.BLUEZ_ADAPTER_IFACE,
                                         'DiscoverableTimeout'))

    @dbus_safe(False)
    def set_discoverable_timeout(self, discoverable_timeout):
        """Set the adapter discoverable_timeout property.

        @param discoverable_timeout: adapter discoverable_timeout value
               in seconds to set (Integer).

        @return True on success, False otherwise.

        """
        self._property_proxy.Set(self.BLUEZ_ADAPTER_IFACE,
                                 'DiscoverableTimeout',
                                 GLib.Variant('u', discoverable_timeout))
        return True

    @dbus_safe(False)
    def get_pairable_timeout(self):
        """Get the adapter pairable_timeout.

        @return True on success, False otherwise.

        """
        return int(
                self._property_proxy.Get(self.BLUEZ_ADAPTER_IFACE,
                                         'PairableTimeout'))

    @dbus_safe(False)
    def set_pairable_timeout(self, pairable_timeout):
        """Set the adapter pairable_timeout property.

        @param pairable_timeout: adapter pairable_timeout value
               in seconds to set (Integer).

        @return True on success, False otherwise.

        """
        self._property_proxy.Set(self.BLUEZ_ADAPTER_IFACE, 'PairableTimeout',
                                 GLib.Variant('u', pairable_timeout))
        return True

    @dbus_safe(False)
    def get_pairable(self):
        """Gets the adapter pairable state.

        @return Pairable property value.
        """
        return bool(
                self._property_proxy.Get(self.BLUEZ_ADAPTER_IFACE, 'Pairable'))

    @dbus_safe(False)
    def set_pairable(self, pairable):
        """Set the adapter pairable state.

        @param pairable: adapter pairable state to set (True or False).

        @return True on success, False otherwise.

        """
        self._property_proxy.Set(self.BLUEZ_ADAPTER_IFACE, 'Pairable',
                                 GLib.Variant('b', pairable))
        return True

    @dbus_safe(False)
    def set_adapter_alias(self, alias):
        """Set the adapter alias.

        @param alias: adapter alias to set with type String

        @return True on success, False otherwise.
        """
        self._property_proxy.Set(self.BLUEZ_ADAPTER_IFACE, 'Alias',
                                 GLib.Variant('s', alias))
        return True

    def _get_adapter_properties(self):
        """Read the adapter properties from the Bluetooth Daemon.

        @return the properties as a JSON-encoded dictionary on success,
            the value False otherwise.

        """

        @dbus_safe({})
        def get_props():
            """Get props from dbus."""
            objects = self._objmgr_proxy.GetManagedObjects()
            return objects[self._adapter_path][self.BLUEZ_ADAPTER_IFACE]

        if self._bluez and self._adapter:
            props = get_props().copy()
        else:
            props = {}
        logging.debug('get_adapter_properties')
        for i in props.items():
            logging.debug(i)
        return props

    def get_adapter_properties(self):
        return json.dumps(self._get_adapter_properties())

    def is_powered_on(self):
        """Checks whether the adapter is currently powered."""
        return bool(self._get_adapter_properties().get('Powered'))

    def get_address(self):
        """Gets the current bluez adapter address."""
        return str(self._get_adapter_properties()['Address'])

    def get_bluez_version(self):
        """Get the BlueZ version.

        Returns:
            Bluez version like 'BlueZ 5.39'.
        """
        return str(self._get_adapter_properties()['Name'])

    def get_bluetooth_class(self):
        """Get the bluetooth class of the adapter.

        Example for Chromebook: 4718852

        Returns:
            Class of device for the adapter.
        """
        return str(self._get_adapter_properties()['Class'])

    def read_version(self):
        """Read the version of the management interface from the Kernel.

        @return the information as a JSON-encoded tuple of:
          ( version, revision )

        """
        #TODO(howardchung): resolve 'cannot allocate memory' error when
        #                   BluetoothControlSocket idle too long(about 3 secs)
        #                   (b:137603211)
        _control = bluetooth_socket.BluetoothControlSocket()
        return json.dumps(_control.read_version())

    def read_supported_commands(self):
        """Read the set of supported commands from the Kernel.

        @return the information as a JSON-encoded tuple of:
          ( commands, events )

        """
        #TODO(howardchung): resolve 'cannot allocate memory' error when
        #                   BluetoothControlSocket idle too long(about 3 secs)
        #                   (b:137603211)
        _control = bluetooth_socket.BluetoothControlSocket()
        return json.dumps(_control.read_supported_commands())

    def read_index_list(self):
        """Read the list of currently known controllers from the Kernel.

        @return the information as a JSON-encoded array of controller indexes.

        """
        #TODO(howardchung): resolve 'cannot allocate memory' error when
        #                   BluetoothControlSocket idle too long(about 3 secs)
        #                   (b:137603211)
        _control = bluetooth_socket.BluetoothControlSocket()
        return json.dumps(_control.read_index_list())

    def read_info(self):
        """Read the adapter information from the Kernel.

        @return the information as a JSON-encoded tuple of:
          ( address, bluetooth_version, manufacturer_id,
            supported_settings, current_settings, class_of_device,
            name, short_name )

        """
        #TODO(howardchung): resolve 'cannot allocate memory' error when
        #                   BluetoothControlSocket idle too long(about 3 secs)
        #                   (b:137603211)
        _control = bluetooth_socket.BluetoothControlSocket()
        return json.dumps(_control.read_info(0))

    def add_device(self, address, address_type, action):
        """Add a device to the Kernel action list.

        @param address: Address of the device to add.
        @param address_type: Type of device in @address.
        @param action: Action to take.

        @return on success, a JSON-encoded typle of:
          ( address, address_type ), None on failure.

        """
        #TODO(howardchung): resolve 'cannot allocate memory' error when
        #                   BluetoothControlSocket idle too long(about 3 secs)
        #                   (b:137603211)
        _control = bluetooth_socket.BluetoothControlSocket()
        return json.dumps(_control.add_device(0, address, address_type,
                                              action))

    def remove_device(self, address, address_type):
        """Remove a device from the Kernel action list.

        @param address: Address of the device to remove.
        @param address_type: Type of device in @address.

        @return on success, a JSON-encoded typle of:
          ( address, address_type ), None on failure.

        """
        #TODO(howardchung): resolve 'cannot allocate memory' error when
        #                   BluetoothControlSocket idle too long(about 3 secs)
        #                   (b:137603211)
        _control = bluetooth_socket.BluetoothControlSocket()
        return json.dumps(_control.remove_device(0, address, address_type))

    @dbus_safe(False)
    def _get_devices(self):
        """Read information about remote devices known to the adapter.

        @return the properties of each device in a list

        """
        objects = self._objmgr_proxy.GetManagedObjects()
        devices = []
        for path, ifaces in six.iteritems(objects):
            if self.BLUEZ_DEVICE_IFACE in ifaces:
                devices.append(objects[path][self.BLUEZ_DEVICE_IFACE])
        return devices

    def _encode_json(self, data):
        """Encodes input data as JSON object.

        Note that for bytes elements in the input data, they are decoded as
        unicode string.

        @param data: data to be JSON encoded

        @return: JSON encoded data
        """
        logging.debug('_encode_json raw data is %s', data)
        str_data = utils.bytes_to_str_recursive(data)
        json_encoded = json.dumps(str_data)
        logging.debug('JSON encoded data is %s', json_encoded)
        return json_encoded

    def get_devices(self):
        """Read information about remote devices known to the adapter.

        @return the properties of each device as a JSON-encoded array of
            dictionaries on success, the value False otherwise.

        """
        devices = self._get_devices()
        # Note that bluetooth facade now runs in Python 3.
        # Refer to crrev.com/c/3268347.
        return self._encode_json(devices)

    def get_num_connected_devices(self):
        """ Return number of remote devices currently connected to the DUT.

        @returns: The number of devices known to bluez with the Connected
            property active
        """
        num_connected_devices = 0
        for dev in self._get_devices():
            if dev and dev.get('Connected', False):
                num_connected_devices += 1

        return num_connected_devices

    @dbus_safe(None)
    def get_device_property(self, address, prop_name):
        """Read a property of BT device by directly querying device dbus object

        @param address: Address of the device to query
        @param prop_name: Property to be queried

        @return Base 64 JSON repr of property if device is found and has
                property, otherwise None on failure. JSON is a recursive
                converter, automatically converting dbus types to python natives
                and base64 allows us to pass special characters over xmlrpc.
                Decode is done in bluetooth_device.py
        """

        prop_val = None

        # Grab dbus object, _find_device will catch any thrown dbus error
        device_obj = self._find_device(address)

        if device_obj:
            # Query dbus object for property
            prop_val = unpack_if_variant(device_obj[self.DBUS_PROP_IFACE].Get(
                    self.BLUEZ_DEVICE_IFACE, prop_name))

        return self._encode_json(prop_val)

    @dbus_safe(None)
    def get_battery_property(self, address, prop_name):
        """Read a property from Battery1 interface.

        @param address: Address of the device to query
        @param prop_name: Property to be queried

        @return The battery percentage value, or None if does not exist.
        """

        prop_val = None

        # Grab dbus object, _find_battery will catch any thrown dbus error
        battery_obj = self._find_battery(address)

        if battery_obj:
            # Query dbus object for property
            prop_val = unpack_if_variant(battery_obj[self.DBUS_PROP_IFACE].Get(
                    self.BLUEZ_BATTERY_IFACE, prop_name))

        return prop_val

    @dbus_safe(False)
    def set_discovery_filter(self, filter):
        """Set the discovery filter.

        @param filter: The discovery filter to set.

        @return True on success, False otherwise.

        """
        if not self._adapter:
            return False

        converted_filter = {}
        for key in filter:
            converted_filter[key] = GLib.Variant('s', filter[key])

        self._adapter_proxy.SetDiscoveryFilter(converted_filter)
        return True

    @dbus_safe(False, return_error=True)
    def start_discovery(self):
        """Start discovery of remote devices.

        Obtain the discovered device information using get_devices(), called
        stop_discovery() when done.

        @return True on success, False otherwise.

        """
        if not self._adapter:
            return (False, "Adapter Not Found")
        self._adapter_proxy.StartDiscovery()
        return (True, None)

    @dbus_safe(False, return_error=True)
    def stop_discovery(self):
        """Stop discovery of remote devices.

        @return True on success, False otherwise.

        """
        if not self._adapter:
            return (False, "Adapter Not Found")
        self._adapter_proxy.StopDiscovery()
        return (True, None)

    def is_discovering(self):
        """Check if adapter is discovering."""
        return self._get_adapter_properties().get('Discovering', 0) == 1

    def get_dev_info(self):
        """Read raw HCI device information.

        @return JSON-encoded tuple of:
                (index, name, address, flags, device_type, bus_type,
                       features, pkt_type, link_policy, link_mode,
                       acl_mtu, acl_pkts, sco_mtu, sco_pkts,
                       err_rx, err_tx, cmd_tx, evt_rx, acl_tx, acl_rx,
                       sco_tx, sco_rx, byte_rx, byte_tx) on success,
                None on failure.

        """
        return json.dumps(self._raw.get_dev_info(0))

    @dbus_safe(None, return_error=True)
    def get_supported_capabilities(self):
        """ Get supported capabilities of the adapter

        @returns (capabilities, None) on Success. (None, <error>) on failure
        """
        value = self._adapter_proxy.GetSupportedCapabilities()
        return (json.dumps(value), None)

    @dbus_safe(False)
    def register_profile(self, path, uuid, options):
        """Register new profile (service).

        @param path: Path to the profile object.
        @param uuid: Service Class ID of the service as string.
        @param options: Dictionary of options for the new service, compliant
                        with BlueZ D-Bus Profile API standard.

        @return True on success, False otherwise.

        """
        converted_options = {}
        if 'ServiceRecord' in options:
            converted_options['ServiceRecord'] = GLib.Variant(
                    's', options['ServiceRecord'])

        profile_manager = self.bus.get(
                self.BLUEZ_SERVICE_NAME, self.BLUEZ_PROFILE_MANAGER_PATH)[
                        self.BLUEZ_PROFILE_MANAGER_IFACE]
        profile_manager.RegisterProfile(path, uuid, converted_options)
        return True

    def has_device(self, address):
        """Checks if the device with a given address exists.

        @param address: Address of the device.

        @returns: True if there is an interface object with that address.
                  False if the device is not found.

        @raises: Exception if a D-Bus error is encountered.

        """
        result = self._find_device(address)
        logging.debug('has_device result: %s', str(result))

        # The result being False indicates that there is a D-Bus error.
        if result is False:
            raise Exception('dbus.Interface error')

        # Return True if the result is not None, e.g. a D-Bus interface object;
        # False otherwise.
        return bool(result)

    @dbus_safe(False)
    def _find_device(self, address):
        """Finds the device with a given address.

        Find the device with a given address and returns the
        device interface.

        @param address: Address of the device.

        @returns: An 'org.bluez.Device1' interface to the device.
                  None if device can not be found.
        """
        path = self._get_device_path(address)
        if path:
            return self.bus.get(self.BLUEZ_SERVICE_NAME, path)
        logging.info('Device not found')
        return None

    @dbus_safe(None)
    def _find_battery(self, address):
        """Finds the battery with a given address.

        Find the battery with a given address and returns the
        battery interface.

        @param address: Address of the device.

        @returns: An 'org.bluez.Battery1' interface to the device.
                  None if device can not be found.
        """
        path = self._get_device_path(address)
        if path:
            try:
                obj = self.bus.get(self.BLUEZ_SERVICE_NAME, path)
                if obj[self.BLUEZ_BATTERY_IFACE] is not None:
                    return obj
            except:
                pass
        logging.info('Battery not found')
        return None

    @dbus_safe(False)
    def _get_device_path(self, address):
        """Gets the path for a device with a given address.

        Find the device with a given address and returns the
        the path for the device.

        @param address: Address of the device.

        @returns: The path to the address of the device, or None if device is
            not found in the object tree.

        """

        # Create device path, i.e. '/org/bluez/hci0/dev_AA_BB_CC_DD_EE_FF' based
        # on path assignment scheme used in bluez
        address_up = address.replace(':', '_')
        device_path = '{}/dev_{}'.format(self._adapter_path, address_up)

        # Verify the Address property agrees to confirm we have the device
        try:
            device = self.bus.get(self.BLUEZ_SERVICE_NAME, device_path)
            found_addr = device[self.DBUS_PROP_IFACE].Get(
                    self.BLUEZ_DEVICE_IFACE, 'Address')

            if found_addr == address:
                logging.info('Device found at {}'.format(device_path))
                return device_path

        except KeyError as ke:
            logging.debug('Couldn\'t reach device: %s: %s', address, ke)
        except GLib.Error as e:
            log_msg = 'Couldn\'t reach device: {}'.format(str(e))
            logging.debug(log_msg)

        logging.debug('No device found at {}'.format(device_path))
        return None

    @dbus_safe(False)
    def _setup_pairing_agent(self, pin):
        """Initializes and resiters a BluezPairingAgent to handle authentication.

        @param pin: The pin code this agent will answer.

        """
        if self._pairing_agent:
            logging.info(
                    'Removing the old agent before initializing a new one')
            self._pairing_agent.unregister()
            self._pairing_agent = None

        # Create and register pairing agent
        self._pairing_agent = BluezPairingAgent(self.bus, self.AGENT_PATH, pin)

        agent_manager = self.bus.get(
                self.BLUEZ_SERVICE_NAME,
                self.BLUEZ_AGENT_MANAGER_PATH)[self.BLUEZ_AGENT_MANAGER_IFACE]
        try:
            # Make sure agent is accessible on bus
            #agent_obj = self.bus.get(self.BLUEZ_SERVICE_NAME, self.AGENT_PATH)
            agent_manager.RegisterAgent(self.AGENT_PATH, str(self._capability))
        except GLib.Error as e:
            if self.BLUEZ_ERROR_ALREADY_EXISTS in str(e):
                logging.info('Unregistering old agent and registering the new')
                agent_manager.UnregisterAgent(self.AGENT_PATH)
                agent_manager.RegisterAgent(self.AGENT_PATH,
                                            str(self._capability))
            else:
                logging.error('Error setting up pin agent: %s', e)
                raise
        except Exception as e:
            logging.debug('Setup pairing agent: %s', str(e))
            raise
        logging.info('Agent registered: %s', self.AGENT_PATH)

    @dbus_safe(False)
    def _is_paired(self, device):
        """Checks if a device is paired.

        @param device: An 'org.bluez.Device1' interface to the device.

        @returns: True if device is paired. False otherwise.

        """
        props = device[self.DBUS_PROP_IFACE]
        paired = props.Get(self.BLUEZ_DEVICE_IFACE, 'Paired')
        return bool(paired)

    @dbus_safe(False)
    def device_is_paired(self, address):
        """Checks if a device is paired.

        @param address: address of the device.

        @returns: True if device is paired. False otherwise.

        """
        device = self._find_device(address)
        if not device:
            logging.error('Device not found')
            return False
        return self._is_paired(device)

    @dbus_safe(False)
    def _is_connected(self, device):
        """Checks if a device is connected.

        @param device: An 'org.bluez.Device1' interface to the device.

        @returns: True if device is connected. False otherwise.

        """
        props = device[self.DBUS_PROP_IFACE]
        connected = props.Get(self.BLUEZ_DEVICE_IFACE, 'Connected')
        logging.info('Got connected = %r', connected)
        return bool(connected)

    @dbus_safe(False)
    def _set_trusted_by_device(self, device, trusted=True):
        """Set the device trusted by device object.

        @param device: the device object to set trusted.
        @param trusted: True or False indicating whether to set trusted or not.

        @returns: True if successful. False otherwise.

        """
        try:
            properties = device[self.DBUS_PROP_IFACE]
            properties.Set(self.BLUEZ_DEVICE_IFACE, 'Trusted',
                           GLib.Variant('b', trusted))
            return True
        except Exception as e:
            logging.error('_set_trusted_by_device: %s', e)
        except:
            logging.error('_set_trusted_by_device: unexpected error')
        return False

    @dbus_safe(False)
    def _set_trusted_by_path(self, device_path, trusted=True):
        """Set the device trusted by the device path.

        @param device_path: the object path of the device.
        @param trusted: True or False indicating whether to set trusted or not.

        @returns: True if successful. False otherwise.

        """
        try:
            device = self.bus.get(self.BLUEZ_SERVICE_NAME, device_path)
            return self._set_trusted_by_device(device, trusted)
        except Exception as e:
            logging.error('_set_trusted_by_path: %s', e)
        except:
            logging.error('_set_trusted_by_path: unexpected error')
        return False

    @dbus_safe(False)
    def set_trusted(self, address, trusted=True):
        """Set the device trusted by address.

        @param address: The bluetooth address of the device.
        @param trusted: True or False indicating whether to set trusted or not.

        @returns: True if successful. False otherwise.

        """
        try:
            device = self._find_device(address)
            return self._set_trusted_by_device(device, trusted)
        except Exception as e:
            logging.error('set_trusted: %s', e)
        except:
            logging.error('set_trusted: unexpected error')
        return False

    @dbus_safe(False)
    def pair_legacy_device(self, address, pin, trusted, timeout=60):
        """Pairs a device with a given pin code.

        Registers a agent who handles pin code request and
        pairs a device with known pin code. After pairing, this function will
        automatically connect to the device as well (prevents timing issues
        between pairing and connect and reduces overall test execution time).

        @param address: Address of the device to pair.
        @param pin: The pin code of the device to pair.
        @param trusted: indicating whether to set the device trusted.
        @param timeout: The timeout in seconds for pairing.

        @returns: True on success. False otherwise.

        """

        def connect_reply():
            """Handler when connect succeeded."""
            logging.info('Device connected: %s', device_path)

        def connect_error(error):
            """Handler when connect failed.

            @param error: one of the errors defined in org.bluez.Error
            representing the error in connect.
            """
            logging.error('Connect device failed: %s', error)

        def pair_reply():
            """Handler when pairing succeeded."""
            logging.info('Device paired: %s', device_path)
            if trusted:
                self._set_trusted_by_path(device_path, trusted=True)
                logging.info('Device trusted: %s', device_path)

            # On finishing pairing, also connect
            self.dbus_method_with_handlers(device.Connect,
                                           connect_reply,
                                           connect_error,
                                           timeout=timeout * 1000)

        def pair_error(error):
            """Handler when pairing failed.

            @param error: one of errors defined in org.bluez.Error representing
                          the error in pairing.

            """
            if 'org.freedesktop.DBus.Error.NoReply' in str(error):
                logging.error('Timed out after %d ms. Cancelling pairing.',
                              timeout)
                device.CancelPairing()
            else:
                logging.error('Pairing device failed: %s', error)

        device = self._find_device(address)
        if not device:
            logging.error('Device not found')
            return False

        device_path = self._get_device_path(address)
        logging.info('Device %s is found.', device_path)

        self._setup_pairing_agent(pin)

        try:
            if not self._is_paired(device):
                logging.info('Device is not paired. Pair and Connect.')
                self.dbus_method_with_handlers(device.Pair,
                                               pair_reply,
                                               pair_error,
                                               timeout=timeout * 1000)
            elif not self._is_connected(device):
                logging.info('Device is already paired. Connect.')
                self.dbus_method_with_handlers(device.Connect,
                                               connect_reply,
                                               connect_error,
                                               tiemout=timeout * 1000)
        except Exception as e:
            logging.error('Exception %s in pair_legacy_device', e)
            return False

        return self._is_paired(device) and self._is_connected(device)

    @dbus_safe(False)
    def remove_device_object(self, address):
        """Removes a device object and the pairing information.

        Calls RemoveDevice method to remove remote device
        object and the pairing information.

        @param address: Address of the device to unpair.

        @returns: True on success. False otherwise.

        """
        device = self._find_device(address)
        if not device:
            logging.error('Device not found')
            return False
        self._adapter_proxy.RemoveDevice(self._get_device_path(address))
        return True

    @dbus_safe(False)
    def connect_device(self, address):
        """Connects a device.

        Connects a device if it is not connected.

        @param address: Address of the device to connect.

        @returns: True on success. False otherwise.

        """
        device = self._find_device(address)
        if not device:
            logging.error('Device not found')
            return False
        if self._is_connected(device):
            logging.info('Device is already connected')
            return True
        device.Connect()
        return self._is_connected(device)

    @dbus_safe(False)
    def device_is_connected(self, address):
        """Checks if a device is connected.

        @param address: Address of the device to connect.

        @returns: True if device is connected. False otherwise.

        """
        device = self._find_device(address)
        if not device:
            logging.error('Device not found')
            return False
        return self._is_connected(device)

    @dbus_safe(False)
    def disconnect_device(self, address):
        """Disconnects a device.

        Disconnects a device if it is connected.

        @param address: Address of the device to disconnect.

        @returns: True on success. False otherwise.

        """
        device = self._find_device(address)
        if not device:
            logging.error('Device not found')
            return False
        if not self._is_connected(device):
            logging.info('Device is not connected')
            return True
        device.Disconnect()
        return not self._is_connected(device)

    @dbus_safe(False)
    def _device_services_resolved(self, device):
        """Checks if services are resolved.

        @param device: An 'org.bluez.Device1' interface to the device.

        @returns: True if device is connected. False otherwise.

        """
        logging.info('device for services resolved: %s', device)
        props = device[self.DBUS_PROP_IFACE]
        resolved = props.Get(self.BLUEZ_DEVICE_IFACE, 'ServicesResolved')
        logging.info('Services resolved = %r', resolved)
        return bool(resolved)

    @dbus_safe(False)
    def device_services_resolved(self, address):
        """Checks if service discovery is complete on a device.

        Checks whether service discovery has been completed..

        @param address: Address of the remote device.

        @returns: True on success. False otherwise.

        """
        device = self._find_device(address)
        if not device:
            logging.error('Device not found')
            return False

        if not self._is_connected(device):
            logging.info('Device is not connected')
            return False

        return self._device_services_resolved(device)

    def btmon_start(self):
        """Start btmon monitoring."""
        self.btmon.start()

    def btmon_stop(self):
        """Stop btmon monitoring."""
        self.btmon.stop()

    def btmon_get(self, search_str, start_str):
        """Get btmon output contents.

        @param search_str: only lines with search_str would be kept.
        @param start_str: all lines before the occurrence of start_str would be
                filtered.

        @returns: the recorded btmon output.

        """
        return self.btmon.get_contents(search_str=search_str,
                                       start_str=start_str)

    def btmon_find(self, pattern_str):
        """Find if a pattern string exists in btmon output.

        @param pattern_str: the pattern string to find.

        @returns: True on success. False otherwise.

        """
        return self.btmon.find(pattern_str)

    def dbus_method_with_handlers(self, dbus_method, reply_handler,
                                  error_handler, *args, **kwargs):
        """Run an async dbus method.

        @param dbus_method: the dbus async method to invoke.
        @param reply_handler: the reply handler for the dbus method.
        @param error_handler: the error handler for the dbus method.
        @param *args: additional arguments for the dbus method.
        @param **kwargs: additional keyword arguments for the dbus method.

        @returns: an empty string '' on success;
                  None if there is no _advertising interface manager; and
                  an error string if the dbus method fails or exception occurs

        """

        def successful_cb():
            """Called when the dbus_method completed successfully."""
            reply_handler()
            self.dbus_cb_msg = ''

        def error_cb(error):
            """Called when the dbus_method failed."""
            error_handler(error)
            self.dbus_cb_msg = str(error)

        # Successful dbus calls will have a non-throwing result and error
        # results will throw GLib.Error.
        try:
            _ = dbus_method(*args, **kwargs)
            successful_cb()
        except GLib.Error as e:
            error_cb(e)
        except Exception as e:
            logging.error('Exception %s in dbus_method_with_handlers ', e)
            return str(e)

        return self.dbus_cb_msg

    def advmon_check_manager_interface_exist(self):
        """Check if AdvertisementMonitorManager1 interface is available.

        @returns: True if Manager interface is available, False otherwise.

        """
        objects = self._objmgr_proxy.GetManagedObjects()
        for _, ifaces in six.iteritems(objects):
            if self.BLUEZ_ADV_MONITOR_MANAGER_IFACE in ifaces:
                return True

        return False

    def advmon_read_supported_types(self):
        """Read the Advertisement Monitor supported monitor types.

        Reads the value of 'SupportedMonitorTypes' property of the
        AdvertisementMonitorManager1 interface on the adapter.

        @returns: the list of the supported monitor types.

        """
        return unpack_if_variant(
                self._property_proxy.Get(self.BLUEZ_ADV_MONITOR_MANAGER_IFACE,
                                         'SupportedMonitorTypes'))

    def advmon_read_supported_features(self):
        """Read the Advertisement Monitor supported features.

        Reads the value of 'SupportedFeatures' property of the
        AdvertisementMonitorManager1 interface on the adapter.

        @returns: the list of the supported features.

        """
        return unpack_if_variant(
                self._property_proxy.Get(self.BLUEZ_ADV_MONITOR_MANAGER_IFACE,
                                         'SupportedFeatures'))

    def advmon_create_app(self):
        """Create an advertisement monitor app.

        @returns: app id, once the app is created.

        """
        return self.advmon_appmgr.create_app()

    def advmon_exit_app(self, app_id):
        """Exit an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.advmon_appmgr.exit_app(app_id)

    def advmon_kill_app(self, app_id):
        """Kill an advertisement monitor app by sending SIGKILL.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.advmon_appmgr.kill_app(app_id)

    def advmon_register_app(self, app_id):
        """Register an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.advmon_appmgr.register_app(app_id)

    def advmon_unregister_app(self, app_id):
        """Unregister an advertisement monitor app.

        @param app_id: the app id.

        @returns: True on success, False otherwise.

        """
        return self.advmon_appmgr.unregister_app(app_id)

    def advmon_add_monitor(self, app_id, monitor_data):
        """Create an Advertisement Monitor object.

        @param app_id: the app id.
        @param monitor_data: the list containing monitor type, RSSI filter
                             values and patterns.

        @returns: monitor id, once the monitor is created, None otherwise.

        """
        return self.advmon_appmgr.add_monitor(app_id, monitor_data)

    def advmon_remove_monitor(self, app_id, monitor_id):
        """Remove the Advertisement Monitor object.

        @param app_id: the app id.
        @param monitor_id: the monitor id.

        @returns: True on success, False otherwise.

        """
        return self.advmon_appmgr.remove_monitor(app_id, monitor_id)

    def advmon_get_event_count(self, app_id, monitor_id, event):
        """Read the count of a particular event on the given monitor.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: count of the specific event or dict of counts of all events.

        """
        return self.advmon_appmgr.get_event_count(app_id, monitor_id, event)

    def advmon_reset_event_count(self, app_id, monitor_id, event):
        """Reset the count of a particular event on the given monitor.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param event: name of the specific event or 'All' for all events.

        @returns: True on success, False otherwise.

        """
        return self.advmon_appmgr.reset_event_count(app_id, monitor_id, event)

    def advmon_set_target_devices(self, app_id, monitor_id, devices):
        """Set the target devices to the given monitor.

        DeviceFound and DeviceLost will only be counted if it is triggered by a
        target device.

        @param app_id: the app id.
        @param monitor_id: the monitor id.
        @param devices: a list of devices in MAC address

        @returns: True on success, False otherwise.

        """
        paths = []
        for addr in devices:
            paths.append('{}/dev_{}'.format(self._adapter_path,
                                            addr.replace(':', '_')))

        return self.advmon_appmgr.set_target_devices(app_id, monitor_id, paths)

    def advmon_interleave_scan_logger_start(self):
        """ Start interleave logger recording
        """
        self.advmon_interleave_logger.StartRecording()

    def advmon_interleave_scan_logger_stop(self):
        """ Stop interleave logger recording

        @returns: True if logs were successfully collected,
                  False otherwise.

        """
        return self.advmon_interleave_logger.StopRecording()

    def advmon_interleave_scan_logger_get_records(self):
        """ Get records in previous log collections

        @returns: a list of records, where each item is a record of
                  interleave |state| and the |time| the state starts.
                  |state| could be {'no filter', 'allowlist'}
                  |time| is system time in sec

        """
        return self.advmon_interleave_logger.records

    def advmon_interleave_scan_logger_get_cancel_events(self):
        """ Get cancel events in previous log collections

        @returns: a list of cancel |time| when a interleave cancel event log
                  was found.
                  |time| is system time in sec

        """
        return self.advmon_interleave_logger.cancel_events

    def register_advertisement(self, advertisement_data):
        """Register an advertisement.

        Note that rpc supports only conformable types. Hence, a
        dict about the advertisement is passed as a parameter such
        that the advertisement object could be constructed on the host.

        @param advertisement_data: a dict of the advertisement to register.

        @returns: True on success. False otherwise.

        """
        adv = advertisement.Advertisement(self.bus, advertisement_data)
        self.advertisements.append(adv)
        return self.dbus_method_with_handlers(
                self._advertising.RegisterAdvertisement,
                # reply handler
                lambda: logging.info('register_advertisement: succeeded.'),
                # error handler
                lambda error: logging.error(
                        'register_advertisement: failed: %s', str(error)),
                # other arguments
                adv.get_path(),
                {})

    def unregister_advertisement(self, advertisement_data):
        """Unregister an advertisement.

        Note that to unregister an advertisement, it is required to use
        the same self._advertising interface manager. This is because
        bluez only allows the same sender to invoke UnregisterAdvertisement
        method. Hence, watch out that the bluetoothd is not restarted or
        self.start_bluetoothd() is not executed between the time span that
        an advertisement is registered and unregistered.

        @param advertisement_data: a dict of the advertisements to unregister.

        @returns: True on success. False otherwise.

        """
        path = advertisement_data.get('Path')
        for index, adv in enumerate(self.advertisements):
            if adv.get_path() == path:
                break
        else:
            logging.error('Fail to find the advertisement under the path: %s',
                          path)
            return False

        result = self.dbus_method_with_handlers(
                self._advertising.UnregisterAdvertisement,
                # reply handler
                lambda: logging.info('unregister_advertisement: succeeded.'),
                # error handler
                lambda error: logging.error(
                        'unregister_advertisement: failed: %s', str(error)),
                # other arguments
                adv.get_path())

        # Call unregister() so that the same path could be reused.
        adv.unregister()
        del self.advertisements[index]

        return result

    def set_advertising_intervals(self, min_adv_interval_ms,
                                  max_adv_interval_ms):
        """Set advertising intervals.

        @param min_adv_interval_ms: the min advertising interval in ms.
        @param max_adv_interval_ms: the max advertising interval in ms.

        @returns: True on success. False otherwise.

        """
        return self.dbus_method_with_handlers(
                self._advertising.SetAdvertisingIntervals,
                # reply handler
                lambda: logging.info('set_advertising_intervals: succeeded.'),
                # error handler
                lambda error: logging.error(
                        'set_advertising_intervals: failed: %s', str(error)),
                # other arguments
                min_adv_interval_ms,
                max_adv_interval_ms)

    def get_advertisement_property(self, adv_path, prop_name):
        """Grab property of an advertisement registered on the DUT

        The service on the DUT registers a dbus object and holds it. During the
        test, some properties on the object may change, so this allows the test
        access to the properties at run-time.

        @param adv_path: string path of the dbus object
        @param prop_name: string name of the property required

        @returns: the value of the property in standard (non-dbus) type if the
                    property exists, else None
        """
        for adv in self.advertisements:
            if str(adv.get_path()) == adv_path:
                adv_props = adv.GetAll('org.bluez.LEAdvertisement1')
                return unpack_if_variant(adv_props.get(prop_name, None))

        return None

    def get_advertising_manager_property(self, prop_name):
        """Grab property of the bluez advertising manager

        This allows us to understand the DUT's advertising capabilities, for
        instance the maximum number of advertising instances supported, so that
        we can test these capabilities.

        @param adv_path: string path of the dbus object
        @param prop_name: string name of the property required

        @returns: the value of the property in standard (non-dbus) type if the
                    property exists, else None
        """

        return unpack_if_variant(
                self._property_proxy.Get(
                        self.BLUEZ_LE_ADVERTISING_MANAGER_IFACE, prop_name))

    def reset_advertising(self):
        """Reset advertising.

        This includes un-registering all advertisements, reset advertising
        intervals, and disable advertising.

        @returns: True on success. False otherwise.

        """
        # It is required to execute unregister() to unregister the
        # object-path handler of each advertisement. In this way, we could
        # register an advertisement with the same path repeatedly.
        for adv in self.advertisements:
            adv.unregister()
        del self.advertisements[:]

        return self.dbus_method_with_handlers(
                self._advertising.ResetAdvertising,
                # reply handler
                lambda: logging.info('reset_advertising: succeeded.'),
                # error handler
                lambda error: logging.error('reset_advertising: failed: %s',
                                            str(error)))

    def get_gatt_attributes_map(self, address):
        """Return a JSON formatted string of the GATT attributes of a device,
        keyed by UUID
        @param address: a string of the MAC address of the device

        @return: JSON formated string, stored the nested structure of the
        attributes. Each attribute has 'path' and
        ['characteristics' | 'descriptors'], which store their object path and
        children respectively.

        """
        attribute_map = dict()

        device_object_path = self._get_device_path(address)
        objects = self._objmgr_proxy.GetManagedObjects()
        service_map = self._get_service_map(device_object_path, objects)

        servs = dict()
        attribute_map['services'] = servs

        for uuid, path in service_map.items():

            servs[uuid] = dict()
            serv = servs[uuid]

            serv['path'] = path
            serv['characteristics'] = dict()
            chrcs = serv['characteristics']

            chrcs_map = self._get_characteristic_map(path, objects)
            for uuid, path in chrcs_map.items():
                chrcs[uuid] = dict()
                chrc = chrcs[uuid]

                chrc['path'] = path
                chrc['descriptors'] = dict()
                descs = chrc['descriptors']

                descs_map = self._get_descriptor_map(path, objects)

                for uuid, path in descs_map.items():
                    descs[uuid] = dict()
                    desc = descs[uuid]

                    desc['path'] = path

        return json.dumps(attribute_map)

    def _get_gatt_interface(self, uuid, object_path, interface):
        """Get dbus interface by uuid
        @param uuid: a string of uuid
        @param object_path: a string of the object path of the service

        @return: a dbus interface
        """

        return self.bus.get(self.BLUEZ_SERVICE_NAME, object_path)[interface]

    def get_gatt_service_property(self, object_path, property_name):
        """Get property from a service attribute
        @param object_path: a string of the object path of the service
        @param property_name: a string of a property, ex: 'Value', 'UUID'

        @return: the property if success,
                 none otherwise

        """
        return self.get_gatt_attribute_property(object_path,
                                                self.BLUEZ_GATT_SERV_IFACE,
                                                property_name)

    def get_gatt_characteristic_property(self, object_path, property_name):
        """Get property from a characteristic attribute
        @param object_path: a string of the object path of the characteristic
        @param property_name: a string of a property, ex: 'Value', 'UUID'

        @return: the property if success,
                 none otherwise

        """
        return self.get_gatt_attribute_property(object_path,
                                                self.BLUEZ_GATT_CHAR_IFACE,
                                                property_name)

    def get_gatt_descriptor_property(self, object_path, property_name):
        """Get property from descriptor attribute
        @param object_path: a string of the object path of the descriptor
        @param property_name: a string of a property, ex: 'Value', 'UUID'

        @return: the property if success,
                 none otherwise

        """
        return self.get_gatt_attribute_property(object_path,
                                                self.BLUEZ_GATT_DESC_IFACE,
                                                property_name)

    @dbus_safe(None)
    def get_gatt_attribute_property(self, object_path, interface,
                                    property_name):
        """Get property from attribute
        @param object_path: a string of the bject path
        @param property_name: a string of a property, ex: 'Value', 'UUID'

        @return: the property if success,
                 none otherwise

        """
        gatt_object = self.bus.get(self.BLUEZ_SERVICE_NAME, object_path)
        prop = self._get_dbus_object_property(gatt_object, interface,
                                              property_name)
        logging.info(prop)
        if isinstance(prop, bytearray):
            return _dbus_byte_array_to_b64_string(prop)
        if isinstance(prop, bool):
            return bool(prop)
        if isinstance(prop, list):
            return list(map(str, prop))
        return prop

    @dbus_safe(None)
    def gatt_characteristic_read_value(self, uuid, object_path):
        """Perform method ReadValue on a characteristic attribute
        @param uuid: a string of uuid
        @param object_path: a string of the object path of the characteristic

        @return: base64 string of dbus bytearray
        """

        dbus_interface = self._get_gatt_interface(uuid, object_path,
                                                  self.BLUEZ_GATT_CHAR_IFACE)
        value = dbus_interface.ReadValue({})
        return _dbus_byte_array_to_b64_string(value)

    @dbus_safe(None)
    def gatt_descriptor_read_value(self, uuid, object_path):
        """Perform method ReadValue on a descriptor attribute
        @param uuid: a string of uuid
        @param object_path: a string of the object path of the descriptor

        @return: base64 string of dbus bytearray
        """

        dbus_interface = self._get_gatt_interface(uuid, object_path,
                                                  self.BLUEZ_GATT_DESC_IFACE)
        value = dbus_interface.ReadValue({})
        return _dbus_byte_array_to_b64_string(value)

    @dbus_safe(False)
    def _get_attribute_map(self, object_path, dbus_interface, objects):
        """Gets a map of object paths under an object path.

        Walks the object tree, and returns a map of UUIDs to object paths for
        all resolved gatt object.

        @param object_path: The object path of the attribute to retrieve
            gatt  UUIDs and paths from.
        @param objects: The managed objects.

        @returns: A dictionary of object paths, keyed by UUID.

        """
        attr_map = {}

        if object_path:
            for path, ifaces in six.iteritems(objects):
                if (dbus_interface in ifaces and path.startswith(object_path)):
                    uuid = ifaces[dbus_interface]['UUID'].lower()
                    attr_map[uuid] = path

        else:
            logging.warning('object_path %s is not valid', object_path)

        return attr_map

    def _get_service_map(self, device_path, objects):
        """Gets a map of service paths for a device.

        @param device_path: the object path of the device.
        @param objects: The managed objects.
        """
        return self._get_attribute_map(device_path, self.BLUEZ_GATT_SERV_IFACE,
                                       objects)

    def _get_characteristic_map(self, serv_path, objects):
        """Gets a map of characteristic paths for a service.

        @param serv_path: the object path of the service.
        @param objects: The managed objects.
        """
        return self._get_attribute_map(serv_path, self.BLUEZ_GATT_CHAR_IFACE,
                                       objects)

    def _get_descriptor_map(self, chrc_path, objects):
        """Gets a map of descriptor paths for a characteristic.

        @param chrc_path: the object path of the characteristic.
        @param objects: The managed objects.
        """
        return self._get_attribute_map(chrc_path, self.BLUEZ_GATT_DESC_IFACE,
                                       objects)

    @dbus_safe(None)
    def _get_dbus_object_property(self, dbus_object, dbus_interface,
                                  dbus_property):
        """Get the property in an object.

        @param dbus_object: a dbus object
        @param dbus_interface: a dbus interface where the property exists
        @param dbus_property: a dbus property of the dbus object, as a string

        @return: dbus type object if it success, e.g. dbus.Boolean, dbus.String,
                 none otherwise

        """
        return dbus_object[self.DBUS_PROP_IFACE].Get(dbus_interface,
                                                     dbus_property)

    @dbus_safe(False)
    def get_characteristic_map(self, address):
        """Gets a map of characteristic paths for a device.

        Walks the object tree, and returns a map of uuids to object paths for
        all resolved gatt characteristics.

        @param address: The MAC address of the device to retrieve
            gatt characteristic uuids and paths from.

        @returns: A dictionary of characteristic paths, keyed by uuid.

        """
        device_path = self._get_device_path(address)
        char_map = {}

        if device_path:
            objects = self._objmgr_proxy.GetManagedObjects()

            for path, ifaces in six.iteritems(objects):
                if (self.BLUEZ_GATT_CHAR_IFACE in ifaces
                            and path.startswith(device_path)):
                    uuid = ifaces[self.BLUEZ_GATT_CHAR_IFACE]['UUID'].lower()
                    char_map[uuid] = path
        else:
            logging.warning('Device %s not in object tree.', address)

        return char_map

    @dbus_safe(None)
    def _get_char_object(self, uuid, address):
        """Gets a characteristic object.

        Gets a characteristic object for a given UUID and address.

        @param uuid: The UUID of the characteristic, as a string.
        @param address: The MAC address of the remote device.

        @returns: A dbus interface for the characteristic if the uuid/address
                      is in the object tree.
                  None if the address/uuid is not found in the object tree.

        """
        path = self.get_characteristic_map(address).get(uuid)
        if not path:
            logging.error("path not found: %s %s", uuid, address)
            return None
        return self.bus.get(self.BLUEZ_SERVICE_NAME,
                            path)[self.BLUEZ_GATT_CHAR_IFACE]

    @dbus_safe(None)
    def read_characteristic(self, uuid, address):
        """Reads the value of a gatt characteristic.

        Reads the current value of a gatt characteristic. Base64 endcoding is
        used for compatibility with the XML RPC interface.

        @param uuid: The uuid of the characteristic to read, as a string.
        @param address: The MAC address of the remote device.

        @returns: A b64 encoded version of a byte array containing the value
                      if the uuid/address is in the object tree.
                  None if the uuid/address was not found in the object tree, or
                      if a DBus exception was raised by the read operation.

        """
        char_obj = self._get_char_object(uuid, address)
        if char_obj is None:
            return None
        value = char_obj.ReadValue({})
        return _dbus_byte_array_to_b64_string(value)

    @dbus_safe(None)
    def write_characteristic(self, uuid, address, value):
        """Performs a write operation on a gatt characteristic.

        Writes to a GATT characteristic on a remote device. Base64 endcoding is
        used for compatibility with the XML RPC interface.

        @param uuid: The uuid of the characteristic to write to, as a string.
        @param address: The MAC address of the remote device, as a string.
        @param value: A byte array containing the data to write.

        @returns: True if the write operation does not raise an exception.
                  None if the uuid/address was not found in the object tree, or
                      if a DBus exception was raised by the write operation.

        """
        char_obj = self._get_char_object(uuid, address)
        if char_obj is None:
            return None
        dbus_value = _b64_string_to_dbus_byte_array(value)
        char_obj.WriteValue(dbus_value, {})
        return True

    @dbus_safe(None)
    def exchange_messages(self, tx_object_path, rx_object_path, value):
        """Performs a write operation on a gatt characteristic and wait for
        the response on another characteristic.

        @param tx_object_path: the object path of the characteristic to write.
        @param rx_object_path: the object path of the characteristic to read.
        @param value: A byte array containing the data to write.

        @returns: The value of the characteristic to read from.
                  None if the uuid/address was not found in the object tree, or
                      if a DBus exception was raised by the write operation.

        """
        tx_obj = self._get_gatt_characteristic_object(tx_object_path)

        if tx_obj is None:
            return None

        self._chrc_property = ''.encode('utf-8')

        value = str(value)
        proxy = self.bus.get(self.BLUEZ_SERVICE_NAME, rx_object_path)[self.DBUS_PROP_IFACE]
        self._signal_watch = proxy.PropertiesChanged.connect(self._property_changed)

        # Start timeout source
        self._timeout_start = time.time()
        self._timeout_early = False
        self._timeout_id = GObject.timeout_add(
                self.PROPERTY_UPDATE_CHECK_MILLI_SECS,
                self._property_wait_timeout)

        write_value = _b64_string_to_dbus_byte_array(value)
        tx_obj.WriteValue(write_value, {})

        self._dbus_mainloop.run()

        return _dbus_byte_array_to_b64_string(self._chrc_property)

    def _property_changed(self, *args, **kwargs):
        """Handler for properties changed signal."""
        # We don't cancel the timeout here due to a problem with the GLib
        # mainloop. See |_property_wait_timeout| for a full explanation.
        self._timeout_early = True
        self._signal_watch.disconnect()
        changed_prop = args

        logging.info(changed_prop)
        prop_dict = changed_prop[1]
        self._chrc_property = prop_dict['Value']
        if self._dbus_mainloop.is_running():
            self._dbus_mainloop.quit()

    def _property_wait_timeout(self):
        """Timeout handler when waiting for properties update signal."""
        # Sometimes, GLib.Mainloop doesn't exit after |mainloop.quit()| is
        # called. This seems to occur only if a timeout source was active and
        # was removed before it had a chance to run. To mitigate this, we don't
        # cancel the timeout but mark an early completion instead.
        # See b/222364364#comment3 for more information.
        if not self._timeout_early and int(
                (time.time() - self._timeout_start) *
                1000) <= self.PROPERTY_UPDATE_TIMEOUT_MILLI_SECS:
            # Returning True means this will be called again.
            return True

        self._signal_watch.disconnect()
        if self._dbus_mainloop.is_running():
            logging.warning("quit main loop due to timeout")
            self._dbus_mainloop.quit()
        # Return false so that this method will not be called again.
        return False

    @dbus_safe(False)
    def _get_gatt_characteristic_object(self, object_path):
        return self.bus.get(self.BLUEZ_SERVICE_NAME,
                            object_path)[self.BLUEZ_GATT_CHAR_IFACE]

    @dbus_safe(False)
    def start_notify(self, object_path, cccd_value):
        """Starts the notification session on the gatt characteristic.

        @param object_path: the object path of the characteristic.
        @param cccd_value: Possible CCCD values include
               0x00 - inferred from the remote characteristic's properties
               0x01 - notification
               0x02 - indication

        @returns: True if the operation succeeds.
                  False if the characteristic is not found, or
                      if a DBus exception was raised by the operation.

        """
        char_obj = self._get_gatt_characteristic_object(object_path)
        if char_obj is None:
            logging.error("characteristic not found: %s %s", object_path)
            return False

        try:
            char_obj.StartNotify(cccd_value)
            return True
        except Exception as e:
            logging.error('start_notify: %s', e)
        except:
            logging.error('start_notify: unexpected error')
        return False

    @dbus_safe(False)
    def stop_notify(self, object_path):
        """Stops the notification session on the gatt characteristic.

        @param object_path: the object path of the characteristic.

        @returns: True if the operation succeeds.
                  False if the characteristic is not found, or
                      if a DBus exception was raised by the operation.

        """
        char_obj = self._get_gatt_characteristic_object(object_path)
        if char_obj is None:
            logging.error("characteristic not found: %s %s", object_path)
            return False

        try:
            char_obj.StopNotify()
            return True
        except Exception as e:
            logging.error('stop_notify: %s', e)
        except:
            logging.error('stop_notify: unexpected error')
        return False

    @dbus_safe(False)
    def is_notifying(self, object_path):
        """Is the GATT characteristic in a notifying session?

        @param object_path: the object path of the characteristic.

        @return True if it is in a notification session. False otherwise.

        """

        return self.get_gatt_characteristic_property(object_path, 'Notifying')

    @dbus_safe(False)
    def is_characteristic_path_resolved(self, uuid, address):
        """Checks whether a characteristic is in the object tree.

        Checks whether a characteristic is curently found in the object tree.

        @param uuid: The uuid of the characteristic to search for.
        @param address: The MAC address of the device on which to search for
            the characteristic.

        @returns: True if the characteristic is found.
                  False if the characteristic path is not found.

        """
        return bool(self.get_characteristic_map(address).get(uuid))

    @dbus_safe(False)
    def get_connection_info(self, address):
        """Get device connection info.

        @param address: The MAC address of the device.

        @returns: On success, a JSON-encoded tuple of:
                      ( RSSI, transmit_power, max_transmit_power )
                  None otherwise.

        """
        plugin_device = self._get_plugin_device_interface(address)
        if plugin_device is None:
            return None

        try:
            connection_info = plugin_device.GetConnInfo()
            return json.dumps(connection_info)
        except Exception as e:
            logging.error('get_connection_info: %s', e)
        except:
            logging.error('get_connection_info: unexpected error')
        return None

    def has_connection_info(self, address):
        """Checks whether the address has connection info.

        @param address: The MAC address of the device.
        @returns True if connection info can be found.
        """
        return self.get_connection_info(address) is not None

    @dbus_safe(False)
    def set_le_connection_parameters(self, address, parameters):
        """Set the LE connection parameters.

        @param address: The MAC address of the device.
        @param parameters: The LE connection parameters to set.

        @return: True on success. False otherwise.

        """
        plugin_device = self._get_plugin_device_interface(address)
        if plugin_device is None:
            return False

        return not self.dbus_method_with_handlers(
                plugin_device.SetLEConnectionParameters,
                # reply handler
                lambda: logging.info('set_le_connection_parameters: succeeded.'
                                     ),
                # error handler
                lambda error: logging.
                error('set_le_connection_parameters: failed: %s', str(error)),
                # other arguments
                parameters)

    @dbus_safe(False)
    def _get_plugin_device_interface(self, address):
        """Get the BlueZ Chromium device plugin interface.

        This interface can be used to issue dbus requests such as
        GetConnInfo and SetLEConnectionParameters.

        @param address: The MAC address of the device.

        @return: On success, the BlueZ Chromium device plugin interface
                 None otherwise.

        """
        path = self._get_device_path(address)
        if path is None:
            return None

        return self.bus.get(self.BLUEZ_SERVICE_NAME,
                            path)[self.BLUEZ_PLUGIN_DEVICE_IFACE]

    @dbus_safe(False)
    def policy_get_service_allow_list(self):
        """Get the service allow list for enterprise policy.

        @returns: array of strings representing the allowed service UUIDs.
        """
        uuids = unpack_if_variant(
                self._property_proxy.Get(self.BLUEZ_ADMIN_POLICY_STATUS_IFACE,
                                         'ServiceAllowList'))
        logging.debug('ServiceAllowList: %s', uuids)
        return uuids

    @dbus_safe(False, return_error=True)
    def policy_set_service_allow_list(self, uuids):
        """Set the service allow list for enterprise policy.

        @param uuids: a string representing the uuids; e.g., "1234,0xabcd" or ""

        @returns: (True, '') on success, (False, '<error>') on failure.
        """
        dbus_array = []
        if bool(uuids.strip()):
            for uuid in uuids.split(','):
                dbus_array.append(uuid.strip())

        logging.debug('policy_set_service_allow_list: %s', dbus_array)
        self._adapter[self.BLUEZ_ADMIN_POLICY_SET_IFACE].SetServiceAllowList(
                dbus_array)
        return (True, '')

    @dbus_safe(False, return_error=True)
    def policy_get_device_affected(self, device_address):
        """Check if the device is affected by enterprise policy.

        @param device_address: address of the device
                               e.g. '6C:29:95:1A:D4:6F'

        @returns: True if the device is affected by the enterprise policy.
                  False if not. None if the device is not found.
        """
        device = self._find_device(device_address)
        if not device:
            logging.debug('Failed to find device %s', device_address)
            return None

        affected = unpack_if_variant(device[self.DBUS_PROP_IFACE].Get(
                self.BLUEZ_ADMIN_POLICY_STATUS_IFACE, 'AffectedByPolicy'))
        logging.debug('policy_get_device_affected(%s): %s', device_address,
                      affected)
        return affected

    def cleanup(self):
        """Cleanup before exiting the client xmlrpc process."""

        self.advmon_appmgr.destroy()

    def get_sysconfig(self):
        """Helper function to get default controller parameters

        @returns: dict of type to values, both are in string form,
                  None if the operation read-sysconfig failed.
        """
        tlv_re = re.compile('Type: (0x[0-9A-Fa-f]{4})\s+'
                            'Length: ([0-9A-Fa-f]{2})\s+'
                            'Value: ([0-9A-Fa-f]+)')

        cmd = 'btmgmt read-sysconfig'
        # btmgmt needs stdin, otherwise it won't output anything.
        # Please refer to
        # third_party/bluez/current/src/shared/shell.c:bt_shell_printf
        # for more information
        output = subprocess.check_output(cmd.split(),
                                         stdin=subprocess.PIPE,
                                         encoding='UTF-8')

        if output is None:
            logging.warning('Unable to retrieve output of %s', cmd)
            return None

        sysconfig = dict()

        for line in output.splitlines():
            try:
                m = tlv_re.match(line)
                t, l, v = m.groups()
                sysconfig[int(t, 16)] = v
            except Exception as e:
                logging.warning('Unexpected error %s at "%s"', str(e), line)

        logging.debug("default controller parameters: %s", sysconfig)
        return sysconfig

    def _le_hex_to_int(self, le_hex):
        """Convert a little-endian hex-string to an unsigned integer.
        For example, _le_hex_to_int('0x0102') returns the same value as
        int('0201', 16)
        """
        if le_hex is None:
            return None

        ba = bytearray.fromhex(le_hex)
        ba.reverse()
        return int(binascii.hexlify(ba), 16)

    def get_advmon_interleave_durations(self):
        """Get durations of allowlist scan and no filter scan

        @returns: a dict of {'allowlist': allowlist_duration,
                             'no filter': no_filter_duration},
                  or None if something went wrong
        """

        sysconfig = self.get_sysconfig()

        if sysconfig is None:
            return None

        AllowlistScanDuration = self._le_hex_to_int(sysconfig.get(
                0x001d, None))
        NoFilterScanDuration = self._le_hex_to_int(sysconfig.get(0x001e, None))

        return {
                'allowlist': AllowlistScanDuration,
                'no filter': NoFilterScanDuration
        }


class FlossFacadeLocal(BluetoothBaseFacadeLocal):
    """Exposes DUT methods called remotely during Bluetooth autotests for the
    Floss daemon.

    All instance methods of this object without a preceding '_' are exposed via
    an XML-RPC server. This is not a stateless handler object, which means that
    if you store state inside the delegate, that state will remain around for
    future calls.
    """

    # Default to this adapter during init. We will initialize to the correct
    # default adapter after the manager client is initialized.
    DEFAULT_ADAPTER = 0

    # How long we wait for the adapter to come up after we start it
    ADAPTER_DAEMON_TIMEOUT_SEC = 20

    # Floss stops discovery after ~12s after starting. To improve discovery
    # chances in tests, we need to keep restarting discovery. This timeout
    # tracks how long an overall discovery session should be.
    DISCOVERY_TIMEOUT_SEC = 60

    class DiscoveryObserver(BluetoothCallbacks):
        """ Discovery observer that restarts discovery until a timeout.

        By default, the Floss stack stops discovery after ~12s. This can be an
        insufficient amount of time to discover a device, especially classic
        devices. To mimic Bluez, we have this observer restart discovery each
        time it is stopped up until a given timeout.
        """

        def __init__(self, adapter_client, timeout_secs):
            """Constructor.

            @param adapter_client: Already initialized client instance.
            @param timeout_secs: How long to continue refreshing discovery.
            """
            self.adapter_client = adapter_client
            self.deadline = datetime.now() + timedelta(seconds=timeout_secs)
            self.adapter_client.register_callback_observer(
                    'DiscoveryObserver', self)
            self.discovering = None

        def __del__(self):
            if self.adapter_client:
                self.cleanup()

        def cleanup(self):
            """Clean up after this observer."""
            self.adapter_client.unregister_callback_observer(
                    'DiscoveryObserver', self)
            self.adapter_client = None

        def on_discovering_changed(self, discovering):
            """Discovering has changed."""

            logging.info('Discovering changed to %s', discovering)

            prev = self.discovering
            self.discovering = discovering

            # No-op if this is the same notification sent multiple times
            if prev == discovering:
                pass
            # If discovering ended, check if the observer has timed out yet. If
            # not, re-start the discovery.
            if not discovering and datetime.now() < self.deadline:
                self.adapter_client.start_discovery(
                        method_callback=self.start_discovery_rsp)

        def start_discovery_rsp(self, err, result):
            """Result to |adapter_client.start_discovery|."""
            # Log any errors that may have occurred
            if err:
                logging.error('Error on start_discovery: %s', err)
            elif result:
                logging.error('Error on start_discovery: Status=%s', result)

    def __init__(self):
        # Init the BaseFacade first
        super(FlossFacadeLocal, self).__init__()

        # Start mainloop thread in background. This will also initialize a few
        # other variables (self.bus, self.mainloop, self.event_context) that may
        # be necessary for proper operation.
        self.mainloop_quit = threading.Event()
        self.mainloop_ready = threading.Event()
        self.thread = threading.Thread(
                name=GLIB_THREAD_NAME,
                target=FlossFacadeLocal.mainloop_thread,
                args=(self, ))
        self.thread.start()

        # Wait for mainloop to be ready
        if not self.mainloop_ready.wait(timeout=5):
            raise Exception('Unable to initialize GLib mainloop')

        # Always initialize the manager client since there is a single instance.
        self.manager_client = FlossManagerClient(self.bus)
        self.adapter_client = FlossAdapterClient(self.bus,
                                                 self.DEFAULT_ADAPTER)

        self.is_clean = False

        # Discovery needs to last longer than the default 12s. Keep an observer
        # that re-enables discovery up to some timeout.
        self.discovery_observer = None

        # Cache some mock properties for testing. These may be properties that
        # are required in bluez but don't carry over well into Floss.
        self.mock_properties = {}

    def __del__(self):
        if not self.is_clean:
            self.cleanup()

    def cleanup(self):
        """Clean up the mainloop thread."""
        self.mainloop_quit.set()
        self.mainloop.quit()
        self.is_clean = True

    @staticmethod
    def mainloop_thread(self):
        """Runs GLib mainloop until we signal that we should quit."""

        # Set up mainloop. All subsequent buses and connections will use this
        # mainloop. We also use a separate main context to avoid multithreading
        # issues.
        #self.event_context = GLib.MainContext()
        #self.mainloop = GLib.MainLoop(context=self.event_context)
        GLib.threads_init()
        self.mainloop = GLib.MainLoop()

        # Set up bus connection
        self.bus = pydbus.SystemBus()

        # Set thread ready
        self.mainloop_ready.set()

        while not self.mainloop_quit.is_set():
            self.mainloop.run()

    def get_floss_enabled(self):
        """Is Floss enabled right now?

        Returns:
            True if Floss is enabled, False if Bluez is enabled.
        """
        return self.manager_client.get_floss_enabled()

    def set_floss_enabled(self, enabled):
        """Enable or disable Floss."""
        self.manager_client.set_floss_enabled(enabled)

    def start_bluetoothd(self):
        """Starts Floss. This includes enabling the adapter.

        Returns:
            True if default adapter is enabled successfully. False otherwise.
        """
        # Start manager and enable Floss
        if not self.configure_floss(enabled=True):
            return False

        # Restarts the default adapter
        if not self.reset_on():
            return False

        # If we need to wait for any other interfaces, add below here:
        # ------------------------------------------------------------

        return True

    def stop_bluetoothd(self):
        """Stops Floss. This includes disabling btmanagerd.

        Returns:
            True if adapter daemon and manager daemon are both off.
        """
        # First power off the adapter
        if not self.reset_off():
            logging.warn('Failed to stop btadapterd')
            return False

        if not UpstartClient.stop(self.MANAGER_JOB):
            logging.warn('Failed to stop btmanagerd')
            return False

        def _daemon_stopped():
            return all([
                    not self.manager_client.has_proxy(),
                    not self.adapter_client.has_proxy(),
            ])

        try:
            utils.poll_for_condition(condition=_daemon_stopped,
                                     desc='Bluetooth daemons have stopped',
                                     timeout=self.DAEMON_TIMEOUT_SEC)
            daemon_stopped = True
        except Exception as e:
            logging.error('timeout: error stopping floss daemons: %s', e)
            daemon_stopped = False

        return daemon_stopped

    def restart_cras(self):
        """Restarts the cras daemon."""
        self._restart_cras(enable_floss=True)

    def is_bluetoothd_proxy_valid(self):
        """Checks whether the proxy objects for Floss are ok."""
        return all([
                self.manager_client.has_proxy(),
                self.adapter_client.has_proxy()
        ])

    def is_bluetoothd_running(self):
        """Checks whether Floss daemon is running."""
        # This api doesn't enforce that the adapter is powered so we only check
        # that the manager proxy is up.
        return self.manager_client.has_proxy()

    def has_adapter(self):
        """Checks whether an adapter exists."""
        return len(self.manager_client.get_available_adapters()) > 0

    def set_debug_log_levels(self, bluez_vb, kernel_vb):
        """Enables verbose logging."""
        # TODO(abps) - This will be necessary for Floss but may not need to
        #              touch the kernel. This needs to be implemented at the
        #              daemon level still.
        return False

    def start_discovery(self):
        """Start discovery of remote devices."""
        if not self.adapter_client.has_proxy():
            return (False, 'Adapter not found')

        if self.discovery_observer:
            self.discovery_observer.cleanup()

        self.discovery_observer = self.DiscoveryObserver(
                self.adapter_client, self.DISCOVERY_TIMEOUT_SEC)
        return (self.adapter_client.start_discovery(), '')

    def stop_discovery(self):
        """Stop discovery of remote deviecs."""
        if not self.adapter_client.has_proxy():
            return (False, 'Adapter not found')

        if self.discovery_observer:
            self.discovery_observer.cleanup()
            self.discovery_observer = None

        return (self.adapter_client.stop_discovery(), '')

    def is_discovering(self):
        """Check if adapter is discovering."""
        return self.adapter_client.is_discovering()

    def is_powered_on(self):
        """Gets whether the default adapter is enabled."""
        default_adapter = self.manager_client.get_default_adapter()
        return self.manager_client.get_adapter_enabled(default_adapter)

    def set_powered(self, powered):
        """Sets the default adapter's enabled state."""
        default_adapter = self.manager_client.get_default_adapter()

        if powered and not self.manager_client.has_default_adapter():
            logging.warning('set_powered: Default adapter not available.')
            return False

        if powered:
            self.manager_client.start(default_adapter)
        else:
            self.manager_client.stop(default_adapter)

        return True

    def reset_on(self):
        """Reset the default adapter into an ON state."""
        return self.do_reset(True)

    def reset_off(self):
        """Reset the default adapter into an OFF state."""
        return self.do_reset(False)

    def do_reset(self, power_on):
        """Resets the default adapter."""
        # Start manager and enable Floss if not already up
        if not self.configure_floss(enabled=True):
            return False

        default_adapter = self.manager_client.get_default_adapter()

        def _is_adapter_down(client):
            return lambda: not client.has_proxy()

        def _is_adapter_ready(client):
            return lambda: client.has_proxy() and client.get_address()

        self.manager_client.stop(default_adapter)
        try:
            condition = _is_adapter_down(self.adapter_client)
            utils.poll_for_condition(condition=condition,
                                     desc='Wait for adapter stop',
                                     sleep_interval=0.5,
                                     timeout=self.ADAPTER_DAEMON_TIMEOUT_SEC)
        except Exception as e:
            logging.error('timeout: error stopping adapter daemon: %s', e)
            logging.error(traceback.format_exc())
            return False

        if not power_on:
            logging.debug('do_reset: Completed with power_on=False')
            return True

        # Start the client again
        self.manager_client.start(default_adapter)
        self.adapter_client = FlossAdapterClient(self.bus, default_adapter)

        try:
            condition = _is_adapter_ready(self.adapter_client)
            utils.poll_for_condition(condition=condition,
                                     desc='Wait for adapter start',
                                     sleep_interval=0.5,
                                     timeout=self.ADAPTER_DAEMON_TIMEOUT_SEC)
        except Exception as e:
            logging.error('timeout: error starting adapter daemon: %s', e)
            logging.error(traceback.format_exc())
            return False

        # We need to observe callbacks for proper operation.
        if not self.adapter_client.register_callbacks():
            logging.error('adapter_client: Failed to register callbacks')
            return False

        logging.debug('do_reset: Completed with power_on=True')
        return True

    def policy_get_service_allow_list(self):
        """Gets the service allow list for enterprise policy."""
        # TODO(abps) - Actually implement this
        return []

    def policy_set_service_allow_list(self, uuids):
        """Sets the service allow list for enterprise policy."""
        # TODO(abps) - Actually implement this
        return (True, '')

    def get_address(self):
        """Gets the default adapter address."""
        return self.adapter_client.get_address()

    def has_device(self, address):
        """Checks if adapter knows the device."""
        return self.adapter_client.has_device(address)

    def remove_device_object(self, address):
        """Removes a known device object."""
        return self.adapter_client.forget_device(address)

    def connect_device(self, address):
        """Connect a specific address."""
        return self.adapter_client.connect_all_enabled_profiles(address)

    def disconnect_device(self, address):
        """Disconnect a specific address."""
        return self.adapter_client.disconnect_all_enabled_profiles(address)

    def get_device_property(self, address, prop_name):
        """Read a property from a remote device.

        @param address: Address of the device to query
        @param prop_name: Property to be queried

        @return Base64 encoded json if property exists or None.
        """
        prop_val = None

        if self.adapter_client.has_device(address):
            prop_val = self.adapter_client.get_remote_property(
                    address, prop_name)

        return self._encode_base64_json(prop_val)

    def get_pairable(self):
        """Gets whether the default adapter is pairable.

        @return True if default adapter is pairable.
        """
        # TODO(abps) - Control pairable setting on adapter
        return self.mock_properties.get('Pairable', False)

    def set_pairable(self, pairable):
        """Sets default adapter as pairable.

        @param pairable: Control pairable property of the adapter.

        @return True on success.
        """
        # TODO(abps) - Control pairable setting on adapter
        self.mock_properties['Pairable'] = pairable
        return True

    def pair_legacy_device(self, address, pin, trusted, timeout=60):
        """Pairs a peer device.

        @param address: BT address of the peer device.
        @param pin: What pin to use for pairing.
        @param trusted: Unused by Floss.
        @param timeout: How long to wait for pairing to complete.
        """

        class PairingObserver(BluetoothCallbacks,
                              BluetoothConnectionCallbacks):
            """Observer of certain callbacks for pairing."""

            def __init__(self, adapter_client, done_event, address, pin):
                self.adapter_client = adapter_client
                self.adapter_client.register_callback_observer(
                        'PairingObserver' + address, self)

                # Event to trigger once we are paired and connected.
                self.done_event = done_event
                self.address = address
                self.pin = pin
                self.bond_state = BondState.NOT_BONDED
                self.connected = self.adapter_client.is_connected(address)

            def __del__(self):
                """Destructor"""
                if self.adapter_client:
                    self.cleanup()

            def cleanup(self):
                """Clean up after this observer."""
                self.adapter_client.unregister_callback_observer(
                        'PairingObserver' + address, self)
                self.adapter_client = None

            def on_bond_state_changed(self, status, device_address, state):
                """Handle bond state change."""
                logging.info('[%s] bond state=%d', device_address, state)

                if device_address != self.address:
                    return

                # If we have a non-zero status, bonding failed in some way.
                # Report it and unblock the main thread.
                if status != 0:
                    logging.error('[%s] failed to bond. Status=%d, State=%d',
                                  device_address, status, state)
                    self.done_event.set()
                    return

                self.bond_state = state
                logging.info('[%s] bond state=%d', device_address, state)

                # We've completed bonding. Make sure to connect
                if state == BondState.BONDED:
                    # If not connected, connect profiles and wait for connected
                    # callback. Else, unblock the main thread.
                    if not self.connected:
                        if not self.adapter_client.connect_all_enabled_profiles(
                                self.address):
                            logging.error(
                                    '[%s] failed on connect_all_enabled_profiles',
                                    self.address)
                            self.done_event.set()
                    else:
                        self.done_event.set()

            def on_ssp_request(self, remote_device, class_of_device, variant,
                               passkey):
                """Handle SSP request."""
                (remote_address, remote_name) = remote_device

                if remote_address != self.address:
                    return

                logging.info('Ssp: [%s: %s]: Class=%d, Variant=%d, Passkey=%d',
                             remote_address, remote_name, class_of_device,
                             variant, passkey)

                if variant == int(SspVariant.CONSENT):
                    self.adapter_client.set_pairing_confirmation(
                            remote_address,
                            True,
                            method_callback=self.on_set_pairing_confirmation)

                logging.info('Exited ssp request.')

            def on_set_pairing_confirmation(self, err, result):
                """Handle async method result from set pairing confirmation."""
                if err or not result:
                    logging.error(
                            'Pairing confirmation failed: err[%s], result[%s]',
                            err, result)
                    self.done_event.set()

            def on_device_connected(self, remote_device):
                """Handle device connection."""
                (remote_address, _) = remote_device

                logging.info('[%s] connected', remote_address)

                if remote_address != self.address:
                    return

                self.connected = True

                # If we're already bonded, unblock the main thread.
                if self.bond_state == BondState.BONDED:
                    self.done_event.set()

        # Start pairing process in main thread

        done_evt = threading.Event()

        # First we need an observer that watches for callbacks
        pairing_observer = PairingObserver(self.adapter_client, done_evt,
                                           address, pin)

        # Pair and connect. If either action fails, mark the done event so that
        # we fall through without blocking.
        if not self.device_is_paired(address):
            if not self.adapter_client.create_bond(address, Transport.AUTO):
                done_evt.set()
        elif not self.device_is_connected(address):
            if not self.adapter_client.connect_all_enabled_profiles(address):
                done_evt.set()

        done_evt.wait(timeout=timeout)
        if not done_evt.is_set():
            logging.error('Timed out waiting for pairing to complete.')

        is_paired = self.device_is_paired(address)
        is_connected = self.device_is_connected(address)

        # If pairing and hci connection is complete, also trigger all profile
        # connections here. This is necessary because device connection doesn't
        # always imply profile connection.
        if is_paired and is_connected:
            self.adapter_client.connect_all_enabled_profiles(address)

        logging.info('Pairing result: paired(%s) connected(%s)', is_paired,
                     is_connected)

        return is_paired and is_connected

    def device_is_connected(self, address):
        """Checks whether a device is connected.

        @param address: BT address of peer device.
        @return True if connected.
        """
        return self.adapter_client.is_connected(address)

    def has_connection_info(self, address):
        """Same as |device_is_connected| on Floss.

        Bluez has a separate ConnectionInfo tuple that is read from the kernel
        but Floss doesn't have this. We have this function simply for
        compatibility.

        @param address: BT address of peer device.
        @return True if connected.
        """
        return self.device_is_connected(address)

    def get_num_connected_devices(self):
        """ Return number of remote devices currently connected to the DUT.

        @returns: The number of devices known to bluez with the Connected
            property active
        """
        return self.adapter_client.get_connected_devices_count()

    def device_is_paired(self, address):
        """Checks if a device is paired.

        @param address: address of the device.
        @returns: True if device is paired. False otherwise.
        """
        return self.adapter_client.is_bonded(address)

    def is_discoverable(self):
        """Return whether the adapter is currently discoverable."""
        return self.adapter_client.get_property('Discoverable')

    def set_discoverable(self, discoverable, duration=60):
        """Sets the adapter as discoverable for given duration in seconds."""
        return self.adapter_client.set_property('Discoverable', discoverable,
                                                duration)

    def get_supported_capabilities(self):
        """" Get supported capabilities of the adapter."""
        return (json.dumps({}), 'Not yet implemented')
