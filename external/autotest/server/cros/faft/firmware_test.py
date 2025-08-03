# Lint as: python2, python3
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import print_function

import ctypes
import logging
import os
import pprint
import re
import time
import uuid
from xml.parsers import expat

import six
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error, global_config
from autotest_lib.client.common_lib.cros import dev_server, retry, tpm_utils
from autotest_lib.server import test
from autotest_lib.server.cros import vboot_constants as vboot
from autotest_lib.server.cros.faft import telemetry
from autotest_lib.server.cros.faft.rpc_proxy import RPCProxy
from autotest_lib.server.cros.faft.utils import (menu_mode_switcher,
                                                 menu_navigator, mode_switcher)
from autotest_lib.server.cros.faft.utils.config import Config as FAFTConfig
from autotest_lib.server.cros.faft.utils.faft_checkers import FAFTCheckers
from autotest_lib.server.cros.power import utils as PowerUtils
from autotest_lib.server.cros.servo import (chrome_base_ec, chrome_cr50,
                                            chrome_ec, chrome_ti50, servo)
from autotest_lib.site_utils import test_runner_utils

# Experimentally tuned time in minutes to wait for partition device nodes on a
# USB stick to be ready after plugging in the stick.
PARTITION_TABLE_READINESS_TIMEOUT = 0.1  # minutes
# Experimentally tuned time in seconds to wait for the first retry of reading
# the sysfs node of a USB stick's partition device node.
PARTITION_TABLE_READINESS_FIRST_RETRY_DELAY = 1  # seconds

ConnectionError = mode_switcher.ConnectionError


class FirmwareTest(test.test):
    """
    Base class that sets up helper objects/functions for firmware tests.

    It launches the FAFTClient on DUT, such that the test can access its
    firmware functions and interfaces. It also provides some methods to
    handle the reboot mechanism, in order to ensure FAFTClient is still
    connected after reboot.
    @type servo: servo.Servo
    @type _client: autotest_lib.server.hosts.ssh_host.SSHHost |
                   autotest_lib.server.hosts.cros_host.CrosHost

    TODO: add documentaion as the FAFT rework progresses.
    """
    version = 1

    # Set this to True in test classes that need to boot from the USB stick.
    # When True, initialize() will raise TestWarn if USB stick is marked bad.
    NEEDS_SERVO_USB = False

    # Mapping of partition number of kernel and rootfs.
    KERNEL_MAP = {'a':'2', 'b':'4', '2':'2', '4':'4', '3':'2', '5':'4'}
    ROOTFS_MAP = {'a':'3', 'b':'5', '2':'3', '4':'5', '3':'3', '5':'5'}
    OTHER_KERNEL_MAP = {'a':'4', 'b':'2', '2':'4', '4':'2', '3':'4', '5':'2'}
    OTHER_ROOTFS_MAP = {'a':'5', 'b':'3', '2':'5', '4':'3', '3':'5', '5':'3'}

    # Mapping of kernel type and name.
    KERNEL_TYPE_NAME_MAP = {'KERN': 'kernel', 'MINIOS': 'minios'}

    CHROMEOS_MAGIC = "CHROMEOS"
    CORRUPTED_MAGIC = "CORRUPTD"

    # System power states
    POWER_STATE_S0 = 'S0'
    POWER_STATE_S0IX = 'S0ix'
    POWER_STATE_S3 = 'S3'
    POWER_STATE_S5 = 'S5'
    POWER_STATE_G3 = 'G3'
    POWER_STATE_SUSPEND = '|'.join([POWER_STATE_S0IX, POWER_STATE_S3])

    # Delay for waiting client to return before EC suspend
    EC_SUSPEND_DELAY = 5

    # Delay between EC suspend and wake
    WAKE_DELAY = 10

    # Delay between closing and opening lid
    LID_DELAY = 1

    # Delay for establishing state after changing PD settings
    PD_RESYNC_DELAY = 2

    # Delay to wait for servo USB to work after power role swap:
    # tPSSourceOff (920ms) + tPSSourceOn (480ms) + buffer
    POWER_ROLE_SWAP_DELAY = 2

    # The default number of power state check retries (each try takes 3 secs)
    DEFAULT_PWR_RETRIES = 5

    # FWMP space constants
    FWMP_CLEARED_EXIT_STATUS = 1
    FWMP_CLEARED_ERROR_MSG = ('CRYPTOHOME_ERROR_FIRMWARE_MANAGEMENT_PARAMETERS'
                              '_INVALID')

    _ROOTFS_PARTITION_NUMBER = 3

    # Class level variable, keep track the states of one time setup.
    # This variable is preserved across tests which inherit this class.
    _global_setup_done = {
        'gbb_flags': False,
        'reimage': False,
        'usb_check': False,
    }

    # CCD password used by tests.
    CCD_PASSWORD = 'Password'

    RESPONSE_TIMEOUT = 180

    @classmethod
    def check_setup_done(cls, label):
        """Check if the given setup is done.

        @param label: The label of the setup.
        """
        return cls._global_setup_done[label]

    @classmethod
    def mark_setup_done(cls, label):
        """Mark the given setup done.

        @param label: The label of the setup.
        """
        cls._global_setup_done[label] = True

    @classmethod
    def unmark_setup_done(cls, label):
        """Mark the given setup not done.

        @param label: The label of the setup.
        """
        cls._global_setup_done[label] = False

    def initialize(self, host, cmdline_args, ec_wp=None):
        """Initialize the FirmwareTest.

        This method interacts with the Servo, FAFT RPC client, FAFT Config,
        Mode Switcher, EC consoles, write-protection, GBB flags, and a lockfile.

        @type host: autotest_lib.server.hosts.CrosHost
        """
        self.run_id = str(uuid.uuid4())
        self._client = host
        self.servo = host.servo
        if self.servo is None:
            raise error.TestError('FirmwareTest failed to set up servo')

        self.lockfile = '/usr/local/tmp/faft/lock'
        self._backup_gbb_flags = None
        self._backup_firmware_identity = dict()
        self._backup_kernel_sha = dict()
        for kernel_type in self.KERNEL_TYPE_NAME_MAP:
            self._backup_kernel_sha[kernel_type] = dict()
        self._backup_cgpt_attr = dict()
        self._backup_dev_mode = None
        self._restore_power_mode = None
        self._uart_file_dict = {}

        logging.info('FirmwareTest initialize begin (id=%s)', self.run_id)

        # Parse arguments from command line
        args = {}
        self.power_control = host.POWER_CONTROL_RPM
        for arg in cmdline_args:
            match = re.search("^(\w+)=(.+)", arg)
            if match:
                args[match.group(1)] = match.group(2)

        self._no_fw_rollback_check = False
        if 'no_fw_rollback_check' in args:
            if 'true' in args['no_fw_rollback_check'].lower():
                self._no_fw_rollback_check = True

        self._no_ec_sync = False
        if 'no_ec_sync' in args:
            if 'true' in args['no_ec_sync'].lower():
                self._no_ec_sync = True

        self._use_sync_script = global_config.global_config.get_config_value(
                'CROS', 'enable_fs_sync_script', type=bool, default=False)

        self.servo.initialize_dut()
        self.faft_client = RPCProxy(host)
        self.faft_config = FAFTConfig(
                self.faft_client.system.get_platform_name(),
                self.faft_client.system.get_model_name())
        self.checkers = FAFTCheckers(self)

        # Mapping of kernel type and kernel servicer class
        self.kernel_servicer = {
                'KERN': self.faft_client.kernel,
                'MINIOS': self.faft_client.minios,
        }

        if self.faft_config.chrome_ec:
            self.ec = chrome_ec.ChromeEC(self.servo)
        self.menu_navigator = menu_navigator.create_menu_navigator(self)
        self.switcher = mode_switcher.create_mode_switcher(
                self, self.menu_navigator)
        # This will be None for menuless UI
        self.menu_switcher = menu_mode_switcher.create_menu_mode_switcher(
                self, self.menu_navigator)
        # Check for presence of a USBPD console
        if self.faft_config.chrome_usbpd:
            self.usbpd = chrome_ec.ChromeUSBPD(self.servo)
        elif self.faft_config.chrome_ec:
            # If no separate USBPD console, then PD exists on EC console
            self.usbpd = self.ec
        # Get pdtester console
        self.pdtester = host.pdtester
        self.pdtester_host = host._pdtester_host
        gsc = None
        if self.servo.has_control('ti50_version') or \
            self.servo.has_control('ti50_version', 'ccd_gsc'):
            gsc = chrome_ti50.ChromeTi50(self.servo, self.faft_config)
        elif self.servo.has_control('cr50_version'):
            gsc = chrome_cr50.ChromeCr50(self.servo, self.faft_config)
        if gsc:
            try:
                # Check that the gsc console works before declaring the
                # connection exists and enabling uart capture.
                gsc.get_version()
                self.cr50 = gsc
            except servo.ControlUnavailableError:
                logging.warning('gsc console not supported.')
            except Exception as e:
                logging.warning('Ignored unknown gsc version error: %s',
                                str(e))

        if 'power_control' in args:
            self.power_control = args['power_control']
            if self.power_control not in host.POWER_CONTROL_VALID_ARGS:
                raise error.TestError('Valid values for --args=power_control '
                                      'are %s. But you entered wrong argument '
                                      'as "%s".'
                                      % (host.POWER_CONTROL_VALID_ARGS,
                                         self.power_control))

        if self.NEEDS_SERVO_USB and not host.is_servo_usb_usable():
            usb_state = host.get_servo_usb_state()
            raise error.TestWarn(
                    "Servo USB disk unusable (%s); canceling test." %
                    usb_state)

        if not self.faft_client.system.dev_tpm_present():
            raise error.TestError('/dev/tpm0 does not exist on the client')

        # Initialize servo role to src
        self.servo.set_servo_v4_role('src')

        # Create the BaseEC object. None if not available.
        self.base_ec = chrome_base_ec.create_base_ec(self.servo)

        self._record_uart_capture()
        self._record_system_info()
        self.faft_client.system.set_dev_default_boot()
        self.fw_vboot2 = self.faft_client.system.get_fw_vboot2()
        logging.info('vboot version: %d', 2 if self.fw_vboot2 else 1)
        if self.fw_vboot2:
            self.faft_client.system.set_fw_try_next('A')
            if self.faft_client.system.get_crossystem_value(
                    'mainfw_act') == 'B':
                logging.info('mainfw_act is B. rebooting to set it A')
                # TODO(crbug.com/1018322): remove try/catch once that bug is
                # marked as fixed and verified. In that case the overlay for
                # the board itself will map warm_reset to cold_reset.
                try:
                    self.switcher.mode_aware_reboot()
                except ConnectionError as e:
                    if 'DUT is still up unexpectedly' in str(e):
                        # In this case, try doing a cold_reset instead
                        self.switcher.mode_aware_reboot(reboot_type='cold')
                    else:
                        raise

        # Check flashrom before first use, to avoid xmlrpclib.Fault.
        if not self.faft_client.bios.is_available():
            raise error.TestError(
                    "flashrom is broken; check 'flashrom -p host'"
                    "and rpc server log.")

        self._setup_gbb_flags()
        self.faft_client.updater.stop_daemon()
        self._create_faft_lockfile()
        self._setup_ec_write_protect(ec_wp)
        # See chromium:239034 regarding needing this sync.
        self.blocking_sync()
        logging.info('FirmwareTest initialize done (id=%s)', self.run_id)

    def stage_build_to_usbkey(self):
        """Downloads host's build to the USB key attached to servo.

        @return: True if build is verified to be on USB key, False otherwise.
        """
        info = self._client.host_info_store.get()
        if info.build and info.build != test_runner_utils.NO_BUILD:
            current_build = self._client._servo_host.validate_image_usbkey()
            if current_build != info.build:
                logging.debug('Current build on USB: %s differs from test'
                              ' build: %s, proceed with download.',
                              current_build, info.build)
                try:
                    self._client.stage_build_to_usb(info.build)
                    return True
                except (error.AutotestError,
                        dev_server.DevServerException) as e:
                    logging.warning(
                            'Stage build to USB failed, tests that require'
                            ' test image on Servo USB may fail: {}'.format(e))
                    return False
            else:
                logging.debug('Current build on USB: %s is same as test'
                              ' build, skip download.', current_build)
                return True
        else:
            logging.warning('Failed to get build label from the DUT, will use'
                            ' existing image in Servo USB.')
            return False

    def run_once(self, *args, **dargs):
        """Delegates testing to a test method.

        test_name is either the 1st positional argument or a named argument.

        test_name will be mapped to a test method as follows:
        test_name                     method
        --------------                -----------
        <TestClass>                   test
        <TestClass>.<Case>            test_<Case>
        <TestClass>.<Case>.<SubCase>  test_<Case>_<SubCase>

        Any arguments not consumed by FirmwareTest are passed to the test method.

        @param test_name: Should be set to NAME in the control file.

        @raise TestError: If test_name wasn't found in args, does not start
                          with test class, or if the method is not found.
        """
        self_name = type(self).__name__

        # Parse and remove test name from args.
        if 'test_name' in dargs:
            test_name = dargs.pop('test_name')
        elif len(args) >= 1:
            test_name = args[0]
            args = args[1:]
        else:
            raise error.TestError('"%s" class must define run_once, or the'
                                  ' control file must specify "test_name".' %
                                  self_name)

        # Check that test_name starts with the test class name.
        name_parts = test_name.split('.')

        test_class = name_parts.pop(0)
        if test_class != self_name:
            raise error.TestError('Class "%s" does not match that found in test'
                                  ' name "%s"' % (self_name, test_class))

        # Construct and call the test method.
        method_name = '_'.join(['test'] + name_parts)
        if not hasattr(self, method_name):
            raise error.TestError('Method "%s" for testing "%s" not found in'
                                  ' "%s"' % (method_name, test_name, self_name))

        logging.info('Starting test: "%s"', test_name)
        utils.cherry_pick_call(getattr(self, method_name), *args, **dargs)

    def cleanup(self):
        """Autotest cleanup function."""
        # Unset state checker in case it's set by subclass
        logging.info('FirmwareTest cleaning up (id=%s)', self.run_id)

        # Capture UART before doing anything else, so we can guarantee we get
        # some uart results.
        try:
            self._record_uart_capture()
        except:
            logging.warning('Failed initial uart capture during cleanup')

        try:
            self.faft_client.system.is_available()
        except:
            # Remote is not responding. Revive DUT so that subsequent tests
            # don't fail.
            self._restore_routine_from_timeout()

        if hasattr(self, 'switcher'):
            self.switcher.restore_mode()

        self._restore_ec_write_protect()
        self._restore_servo_v4_role()

        if hasattr(self, 'faft_client'):
            self._restore_gbb_flags()
            self.faft_client.updater.start_daemon()
            self.faft_client.updater.cleanup()
            self._remove_faft_lockfile()
            self.faft_client.quit()
            self.faft_client.collect_logfiles(self.resultsdir)

        # Capture any new uart output, then discard log messages again.
        self._cleanup_uart_capture()

        super(FirmwareTest, self).cleanup()
        logging.info('FirmwareTest cleanup done (id=%s)', self.run_id)

    def _record_system_info(self):
        """Record some critical system info to the attr keyval.

        This info is used by generate_test_report later.
        """
        system_info = {
            'hwid': self.faft_client.system.get_crossystem_value('hwid'),
            'ec_version': self.faft_client.ec.get_version(),
            'ro_fwid': self.faft_client.system.get_crossystem_value('ro_fwid'),
            'rw_fwid': self.faft_client.system.get_crossystem_value('fwid'),
            'servo_host_os_version' : self.servo.get_os_version(),
            'servod_version': self.servo.get_servod_version(),
            'os_version': self._client.get_release_builder_path(),
            'servo_type': self.servo.get_servo_version()
        }

        # Record the servo v4 and servo micro versions when possible
        system_info.update(self.servo.get_servo_fw_versions())

        if hasattr(self, 'cr50'):
            system_info['cr50_version'] = self.cr50.get_full_version()

        logging.info('System info:\n%s', pprint.pformat(system_info))
        self.write_attr_keyval(system_info)

    def invalidate_firmware_setup(self):
        """Invalidate all firmware related setup state.

        This method is called when the firmware is re-flashed. It resets all
        firmware related setup states so that the next test setup properly
        again.
        """
        self.unmark_setup_done('gbb_flags')

    def _retrieve_recovery_reason_from_trap(self):
        """Try to retrieve the recovery reason from a trapped recovery screen.

        @return: The recovery_reason, 0 if any error.
        """
        recovery_reason = 0
        logging.info('Try to retrieve recovery reason...')
        if self.servo.get_usbkey_state() == 'dut':
            self.switcher.bypass_rec_mode()
        else:
            self.servo.switch_usbkey('dut')

        try:
            self.switcher.wait_for_client()
            lines = self.faft_client.system.run_shell_command_get_output(
                        'crossystem recovery_reason')
            recovery_reason = int(lines[0])
            logging.info('Got the recovery reason %d.', recovery_reason)
        except ConnectionError:
            logging.error('Failed to get the recovery reason due to connection '
                          'error.')
        return recovery_reason

    def _reset_client(self):
        """Reset client to a workable state.

        This method is called when the client is not responsive. It may be
        caused by the following cases:
          - halt on a firmware screen without timeout, e.g. REC_INSERT screen;
          - corrupted firmware;
          - corrutped OS image.
        """
        # DUT may halt on a firmware screen. Try cold reboot.
        logging.info('Try cold reboot...')
        self.switcher.mode_aware_reboot(reboot_type='cold',
                                        sync_before_boot=False,
                                        wait_for_dut_up=False)
        self.switcher.wait_for_client_offline()
        self.switcher.bypass_dev_mode()
        try:
            self.switcher.wait_for_client()
            return
        except ConnectionError:
            logging.warning("Cold reboot didn't help, still connection error.")

        # DUT may be broken by a corrupted firmware. Restore firmware.
        # We assume the recovery boot still works fine. Since the recovery
        # code is in RO region and all FAFT tests don't change the RO region
        # except GBB.
        if self.is_firmware_saved():
            self._ensure_client_in_recovery()
            logging.info('Try restoring the original firmware...')
            try:
                if self.restore_firmware():
                    return
            except ConnectionError:
                logging.warning("Restoring firmware didn't help, still "
                                "connection error.")

        # Perhaps it's kernel that's broken. Let's try restoring it.
        for kernel_type, kernel_name in self.KERNEL_TYPE_NAME_MAP.items():
            if self.is_kernel_saved(kernel_type):
                self._ensure_client_in_recovery()
                logging.info('Try restoring the original %s...', kernel_name)
                try:
                    if self.restore_kernel(kernel_type=kernel_type):
                        return
                except ConnectionError:
                    logging.warning(
                            "Restoring %s didn't help, still "
                            "connection error.", kernel_name)

        # DUT may be broken by a corrupted OS image. Restore OS image.
        self._ensure_client_in_recovery()
        logging.info('Try restoring the OS image...')
        self.faft_client.system.run_shell_command('chromeos-install --yes')
        self.switcher.mode_aware_reboot(wait_for_dut_up=False)
        self.switcher.wait_for_client_offline()
        self.switcher.bypass_dev_mode()
        try:
            self.switcher.wait_for_client()
            logging.info('Successfully restored OS image.')
            return
        except ConnectionError:
            logging.warning("Restoring OS image didn't help, still connection "
                            "error.")

    def _ensure_client_in_recovery(self):
        """Ensure client in recovery boot; reboot into it if necessary.

        @raise TestError: if failed to boot the USB image.
        """
        logging.info('Try booting into USB image...')
        self.switcher.reboot_to_mode(to_mode='rec', sync_before_boot=False,
                                     wait_for_dut_up=False)
        self.servo.switch_usbkey('host')
        self.switcher.bypass_rec_mode()
        try:
            self.switcher.wait_for_client()
        except ConnectionError:
            raise error.TestError('Failed to boot the USB image.')

    def _restore_routine_from_timeout(self):
        """A routine to try to restore the system from a timeout error.

        This method is called when FAFT failed to connect DUT after reboot.

        @raise TestFail: This exception is already raised, with a decription
                         why it failed.
        """
        # DUT is disconnected. Capture the UART output for debug.
        self._record_uart_capture()

        # Replug the Ethernet to identify if it is a network flaky.
        self.servo.eth_power_reset()

        recovery_reason = self._retrieve_recovery_reason_from_trap()

        # Reset client to a workable state.
        self._reset_client()

        # Raise the proper TestFail exception.
        if recovery_reason:
            raise error.TestFail('Trapped in the recovery screen (reason: %d) '
                                 'and timed out' % recovery_reason)
        else:
            raise error.TestFail('Timed out waiting for DUT reboot')

    def assert_test_image_in_usb_disk(self, usb_dev=None):
        """Assert an USB disk plugged-in on servo and a test image inside.

        @param usb_dev: A string of USB stick path on the host, like '/dev/sdc'.
                        If None, it is detected automatically.
        @raise TestError: if USB disk not detected or not a test image.
        """
        if self.check_setup_done('usb_check'):
            return
        if usb_dev:
            assert self.servo.get_usbkey_state() == 'host'
        else:
            self.servo.switch_usbkey('host')
            usb_dev = self.servo.probe_host_usb_dev()
            if not usb_dev:
                raise error.TestError(
                    'An USB disk should be plugged in the servo board. %s' %
                    telemetry.collect_usb_state(self.servo))

        rootfs = '%s%s' % (usb_dev, self._ROOTFS_PARTITION_NUMBER)
        logging.info('usb dev is %s', usb_dev)
        tmpd = self.servo.system_output('mktemp -d -t usbcheck.XXXX')
        # After the USB key is muxed from the DUT to the servo host, there
        # appears to be a delay between when servod can confirm that a sysfs
        # entry exists for the disk (as done by probe_host_usb_dev) and when
        # sysfs entries get populated for the disk's partitions.
        @retry.retry(error.AutoservRunError,
                     timeout_min=PARTITION_TABLE_READINESS_TIMEOUT,
                     delay_sec=PARTITION_TABLE_READINESS_FIRST_RETRY_DELAY)
        def confirm_rootfs_partition_device_node_readable():
            """Repeatedly poll for the RootFS partition sysfs node."""
            self.servo.system('ls {}'.format(rootfs))

        try:
            confirm_rootfs_partition_device_node_readable()
        except error.AutoservRunError as e:
            usb_info = telemetry.collect_usb_state(self.servo)
            raise error.TestError(
                    ('Could not ls the device node for the RootFS on the USB '
                     'device. %s: %s\nMore telemetry: %s') %
                    (type(e).__name__, e, usb_info))
        try:
            self.servo.system('mount -o ro %s %s' % (rootfs, tmpd))
        except error.AutoservRunError as e:
            usb_info = telemetry.collect_usb_state(self.servo)
            raise error.TestError(
                ('Could not mount the partition on USB device. %s: %s\n'
                 'More telemetry: %s') % (type(e).__name__, e, usb_info))

        try:
            usb_lsb = self.servo.system_output('cat %s' %
                os.path.join(tmpd, 'etc/lsb-release'))
            logging.debug('Dumping lsb-release on USB stick:\n%s', usb_lsb)
            dut_lsb = '\n'.join(self.faft_client.system.
                run_shell_command_get_output('cat /etc/lsb-release'))
            logging.debug('Dumping lsb-release on DUT:\n%s', dut_lsb)
            if not re.search(r'RELEASE_TRACK=.*test', usb_lsb):
                raise error.TestError('USB stick in servo is no test image')
            usb_board = re.search(r'BOARD=(.*)', usb_lsb).group(1)
            dut_board = re.search(r'BOARD=(.*)', dut_lsb).group(1)
            if usb_board != dut_board:
                raise error.TestError('USB stick in servo contains a %s '
                    'image, but DUT is a %s' % (usb_board, dut_board))
        finally:
            for cmd in ('umount -l %s' % tmpd, 'sync %s' % usb_dev,
                        'rm -rf %s' % tmpd):
                self.servo.system(cmd)

        self.mark_setup_done('usb_check')

    def setup_pdtester(self, flip_cc=False, dts_mode=False, pd_faft=True,
                       min_batt_level=None):
        """Setup the PDTester to a given state.

        @param flip_cc: True to flip CC polarity; False to not flip it.
        @param dts_mode: True to config PDTester to DTS mode; False to not.
        @param pd_faft: True to config PD FAFT setup.
        @param min_batt_level: An int for minimum battery level, or None for
                               skip.
        @raise TestError: If Servo v4 not setup properly.
        """

        # PD FAFT is only tested with a combination of servo_v4 or servo_v4p1
        # with servo micro or C2D2.
        pd_setup = []
        for first in self.pdtester.FIRST_PD_SETUP_ELEMENT:
            for second in self.pdtester.SECOND_PD_SETUP_ELEMENT:
                pd_setup.append(first + '_with_' + second)

        if pd_faft and self.pdtester.servo_type not in pd_setup:
            raise error.TestError(', '.join(pd_setup) +
                                  ' is a mandatory setup '
                                  'for PD FAFT. Got %s.' %
                                  self.pdtester.servo_type)

        # Ensure the battery is enough for testing, this should be done before
        # all the following setup.
        if (min_batt_level is not None) and self._client.has_battery():
            logging.info('Start charging if batt level < %d', min_batt_level)
            PowerUtils.put_host_battery_in_range(self._client, min_batt_level,
                                                 100, 600)

        # Servo v4 by default has dts_mode enabled. Enabling dts_mode affects
        # the behaviors of what PD FAFT tests. So we want it disabled.
        pd_tester_device = self.pdtester.servo_type.split('_with_')[0]
        if pd_tester_device in self.pdtester.FIRST_PD_SETUP_ELEMENT:
            self.servo.set_dts_mode('on' if dts_mode else 'off')
        else:
            logging.warning('Configuring DTS mode only supported on %s',
                            pd_tester_device)

        self.pdtester.set('usbc_polarity', 'cc2' if flip_cc else 'cc1')
        # Make it sourcing max voltage.
        self.pdtester.charge(self.pdtester.USBC_MAX_VOLTAGE)

        time.sleep(self.PD_RESYNC_DELAY)

        # Servo v4 requires an external charger to source power. Make sure
        # this setup is correct.
        if pd_tester_device in self.pdtester.FIRST_PD_SETUP_ELEMENT:
            role = self.pdtester.get('servo_pd_role')
            if role != 'src':
                raise error.TestError(
                        '%s is not sourcing power! Make sure the servo '
                        '"DUT POWER" port is connected to a working charger. '
                        'servo_pd_role:%s' % (pd_tester_device, role))

    def setup_usbkey(self, usbkey, host=None, used_for_recovery=None):
        """Setup the USB disk for the test.

        It checks the setup of USB disk and a valid ChromeOS test image inside.
        It also muxes the USB disk to either the host or DUT by request.

        @param usbkey: True if the USB disk is required for the test, False if
                       not required.
        @param host: Optional, True to mux the USB disk to host, False to mux it
                    to DUT, default to do nothing.
        @param used_for_recovery: Optional, True if the USB disk is used for
                                  recovery boot; False if the USB disk is not
                                  used for recovery boot, like Ctrl-U USB boot.
        """
        if usbkey:
            self.stage_build_to_usbkey()
            self.assert_test_image_in_usb_disk()
        elif host is None:
            # USB disk is not required for the test. Better to mux it to host.
            host = True

        if host is True:
            self.servo.switch_usbkey('host')
        elif host is False:
            self.servo.switch_usbkey('dut')

        if used_for_recovery is None:
            # Default value is True if usbkey == True.
            # As the common usecase of USB disk is for recovery boot. Tests
            # can define it explicitly if not.
            used_for_recovery = usbkey

        if used_for_recovery:
            # In recovery boot, the locked EC RO doesn't support PD for most
            # of the CrOS devices. The default servo v4 power role is a SRC.
            # The DUT becomes a SNK. Lack of PD makes CrOS unable to do the
            # data role swap from UFP to DFP; as a result, DUT can't see the
            # USB disk and the Ethernet dongle on servo v4.
            #
            # This is a workaround to set servo v4 as a SNK, for every FAFT
            # test which boots into the USB disk in the recovery mode.
            #
            # TODO(waihong): Add a check to see if the battery level is too
            # low and sleep for a while for charging.
            self.set_servo_v4_role_to_snk()

            # Force reconnection; otherwise, the next RPC call will timeout
            logging.info('Waiting for reconnection after power role swap...')
            time.sleep(self.POWER_ROLE_SWAP_DELAY)
            self.faft_client.disconnect()
            self.faft_client.connect()

    def set_servo_v4_role_to_snk(self, pd_comm=False):
        """Set the servo v4 role to SNK.

        @param pd_comm: a bool. Enable PD communication if True, else otherwise
        """
        self._needed_restore_servo_v4_role = True
        self.servo.set_servo_v4_role('snk')
        if pd_comm:
            self.servo.set_servo_v4_pd_comm('on')

    def _restore_servo_v4_role(self):
        """Restore the servo v4 role to default SRC."""
        if not hasattr(self, '_needed_restore_servo_v4_role'):
            return
        if self._needed_restore_servo_v4_role:
            self.servo.set_servo_v4_role('src')

    def set_dut_low_power_idle_delay(self, delay):
        """Set EC low power idle delay

        @param delay: Delay in seconds
        """
        if not self.ec.has_command('dsleep'):
            logging.info("Can't set low power idle delay.")
            return
        self._previous_ec_low_power_delay = int(
                self.ec.send_command_get_output("dsleep",
                ["timeout:\s+(\d+)\ssec"])[0][1])
        self.ec.send_command("dsleep " + str(delay))

    def restore_dut_low_power_idle_delay(self):
        """Restore EC low power idle delay"""
        if getattr(self, '_previous_ec_low_power_delay', None):
            self.ec.send_command("dsleep " + str(
                    self._previous_ec_low_power_delay))

    def get_usbdisk_path_on_dut(self):
        """Get the path of the USB disk device plugged-in the servo on DUT.

        Returns:
          A string representing USB disk path, like '/dev/sdb', or None if
          no USB disk is found.
        """
        cmd = 'ls -d /dev/s*[a-z]'
        original_value = self.servo.get_usbkey_state()

        # Make the dut unable to see the USB disk.
        self.servo.switch_usbkey('off')
        time.sleep(self.faft_config.usb_unplug)
        no_usb_set = set(
            self.faft_client.system.run_shell_command_get_output(cmd))

        # Make the dut able to see the USB disk.
        self.servo.switch_usbkey('dut')
        time.sleep(self.faft_config.usb_plug)
        has_usb_set = set(
            self.faft_client.system.run_shell_command_get_output(cmd))

        # Back to its original value.
        if original_value != self.servo.get_usbkey_state():
            self.servo.switch_usbkey(original_value)

        diff_set = has_usb_set - no_usb_set
        if len(diff_set) == 1:
            return diff_set.pop()
        else:
            return None

    def _create_faft_lockfile(self):
        """Creates the FAFT lockfile."""
        logging.info('Creating FAFT lockfile...')
        command = 'touch %s' % (self.lockfile)
        self.faft_client.system.run_shell_command(command)

    def _remove_faft_lockfile(self):
        """Removes the FAFT lockfile."""
        logging.info('Removing FAFT lockfile...')
        command = 'rm -f %s' % (self.lockfile)
        self.faft_client.system.run_shell_command(command)

    def clear_set_gbb_flags(self, clear_mask, set_mask, reboot=True):
        """Clear and set the GBB flags in the current flashrom.

        @param clear_mask: A mask of flags to be cleared.
        @param set_mask: A mask of flags to be set.
        @param reboot: If true, then this method will reboot the DUT if certain
                       flags are modified.
        """
        gbb_flags = self.faft_client.bios.get_gbb_flags()
        new_flags = gbb_flags & ctypes.c_uint32(~clear_mask).value | set_mask
        if new_flags == gbb_flags:
            logging.info(
                    'Current GBB flags (0x%x) match desired clear mask '
                    '(0x%x) and desired set mask (0x%x)', gbb_flags,
                    clear_mask, set_mask)
            return

        logging.info('Changing GBB flags from 0x%x to 0x%x.', gbb_flags,
                     new_flags)
        if self._backup_gbb_flags is None:
            self._backup_gbb_flags = gbb_flags
        self.faft_client.bios.set_gbb_flags(new_flags)

        # If changing FORCE_DEV_SWITCH_ON or DISABLE_EC_SOFTWARE_SYNC flag,
        # reboot to get a clear state
        if reboot and bool((gbb_flags ^ new_flags)
                           & (vboot.GBB_FLAG_FORCE_DEV_SWITCH_ON
                              | vboot.GBB_FLAG_DISABLE_EC_SOFTWARE_SYNC)):
            self.switcher.mode_aware_reboot()


    def _check_capability(self, target, required_cap, suppress_warning):
        """Check if current platform has required capabilities for the target.

        @param required_cap: A list containing required capabilities.
        @param suppress_warning: True to suppress any warning messages.
        @return: True if requirements are met. Otherwise, False.
        """
        if not required_cap:
            return True

        if target not in ['ec', 'cr50']:
            raise error.TestError('Invalid capability target %r' % target)

        for cap in required_cap:
            if cap not in getattr(self.faft_config, target + '_capability'):
                if not suppress_warning:
                    logging.warning(
                            'Requires %s capability "%s" to run this '
                            'test.', target, cap)
                return False

        return True


    def check_ec_capability(self, required_cap=None, suppress_warning=False):
        """Check if current platform has required EC capabilities.

        @param required_cap: A list containing required EC capabilities. Pass in
                             None to only check for presence of Chrome EC.
        @param suppress_warning: True to suppress any warning messages.
        @return: True if requirements are met. Otherwise, False.
        """
        if not self.faft_config.chrome_ec:
            if not suppress_warning:
                logging.warning('Requires Chrome EC to run this test.')
            return False
        return self._check_capability('ec', required_cap, suppress_warning)


    def check_cr50_capability(self, required_cap=None, suppress_warning=False):
        """Check if current platform has required Cr50 capabilities.

        @param required_cap: A list containing required Cr50 capabilities. Pass
                             in None to only check for presence of cr50 uart.
        @param suppress_warning: True to suppress any warning messages.
        @return: True if requirements are met. Otherwise, False.
        """
        if not hasattr(self, 'cr50'):
            if not suppress_warning:
                logging.warning('Requires Chrome Cr50 to run this test.')
            return False
        return self._check_capability('cr50', required_cap, suppress_warning)


    def check_root_part_on_non_recovery(self, part):
        """Check the partition number of root device and on normal/dev boot.

        @param part: A string of partition number, e.g.'3'.
        @return: True if the root device matched and on normal/dev boot;
                 otherwise, False.
        """
        return self.checkers.root_part_checker(part) and \
                self.checkers.crossystem_checker({
                    'mainfw_type': ('normal', 'developer'),
                })

    def _join_part(self, dev, part):
        """Return a concatenated string of device and partition number.

        @param dev: A string of device, e.g.'/dev/sda'.
        @param part: A string of partition number, e.g.'3'.
        @return: A concatenated string of device and partition number,
                 e.g.'/dev/sda3'.

        >>> seq = FirmwareTest()
        >>> seq._join_part('/dev/sda', '3')
        '/dev/sda3'
        >>> seq._join_part('/dev/mmcblk0', '2')
        '/dev/mmcblk0p2'
        """
        if 'mmcblk' in dev:
            return dev + 'p' + part
        elif 'nvme' in dev:
            return dev + 'p' + part
        else:
            return dev + part

    def copy_kernel_and_rootfs(self, from_part, to_part):
        """Copy kernel and rootfs from from_part to to_part.

        @param from_part: A string of partition number to be copied from.
        @param to_part: A string of partition number to be copied to.
        """
        root_dev = self.faft_client.system.get_root_dev()
        logging.info('Copying kernel from %s to %s. Please wait...',
                     from_part, to_part)
        self.faft_client.system.run_shell_command('dd if=%s of=%s bs=4M' %
                (self._join_part(root_dev, self.KERNEL_MAP[from_part]),
                 self._join_part(root_dev, self.KERNEL_MAP[to_part])))
        logging.info('Copying rootfs from %s to %s. Please wait...',
                     from_part, to_part)
        self.faft_client.system.run_shell_command('dd if=%s of=%s bs=4M' %
                (self._join_part(root_dev, self.ROOTFS_MAP[from_part]),
                 self._join_part(root_dev, self.ROOTFS_MAP[to_part])))

    def ensure_kernel_boot(self, part):
        """Ensure the request kernel boot.

        If not, it duplicates the current kernel to the requested kernel
        and sets the requested higher priority to ensure it boot.

        @param part: A string of kernel partition number or 'a'/'b'.
        """
        if not self.checkers.root_part_checker(part):
            if self.faft_client.kernel.diff_a_b():
                self.copy_kernel_and_rootfs(
                        from_part=self.OTHER_KERNEL_MAP[part],
                        to_part=part)
            self.reset_and_prioritize_kernel(part)
            self.switcher.mode_aware_reboot()

    def ensure_dev_internal_boot(self, original_dev_boot_usb):
        """Ensure internal device boot in developer mode.

        If not internal device boot, it will try to reboot the device and
        bypass dev mode to boot into internal device.

        @param original_dev_boot_usb: Original dev_boot_usb value.
        """
        self.faft_client.system.set_dev_default_boot()
        if self.faft_client.system.is_removable_device_boot():
            logging.info('Reboot into internal disk...')
            self.faft_client.system.set_dev_boot_usb(original_dev_boot_usb)
            self.switcher.mode_aware_reboot()
        self.check_state((self.checkers.dev_boot_usb_checker, False,
                          'Device not booted from internal disk properly.'))

    def set_hardware_write_protect(self, enable):
        """Set hardware write protect pin.

        @param enable: True if asserting write protect pin. Otherwise, False.
        """
        self.servo.set('fw_wp_state', 'force_on' if enable else 'force_off')

    def set_ap_write_protect_and_reboot(self, enable):
        """Set AP write protect status and reboot to take effect.

        @param enable: True if asserting write protect. Otherwise, False.
        """
        self.set_hardware_write_protect(enable)
        if hasattr(self, 'ec'):
            self.sync_and_ec_reboot()
            self.switcher.wait_for_client()

    def run_chromeos_firmwareupdate(self, mode, append=None, options=(),
            ignore_status=False):
        """Use RPC to get the command to run, but do the actual run via ssh.

        Running the command via SSH improves the reliability in cases where the
        USB network connection gets interrupted.  SSH will still return the
        output, and won't hang like RPC would.
        """
        update_cmd = self.faft_client.updater.get_firmwareupdate_command(
                mode, append, options)
        try:
            result = self._client.run(
                    update_cmd, timeout=300, ignore_status=ignore_status)
            if result.exit_status == 255:
                self.faft_client.disconnect()
            return result
        except error.AutoservRunError as e:
            if e.result_obj.exit_status == 255:
                self.faft_client.disconnect()
            if ignore_status:
                return e.result_obj
            raise

    def set_ec_write_protect_and_reboot(self, enable):
        """Set EC write protect status and reboot to take effect.

        The write protect state is only activated if both hardware write
        protect pin is asserted and software write protect flag is set.
        This method asserts/deasserts hardware write protect pin first, and
        set corresponding EC software write protect flag.

        If the device uses non-Chrome EC, set the software write protect via
        flashrom.

        If the device uses Chrome EC, a reboot is required for write protect
        to take effect. Since the software write protect flag cannot be unset
        if hardware write protect pin is asserted, we need to deasserted the
        pin first if we are deactivating write protect. Similarly, a reboot
        is required before we can modify the software flag.

        @param enable: True if activating EC write protect. Otherwise, False.
        """
        self.set_hardware_write_protect(enable)
        if self.faft_config.chrome_ec:
            self.set_chrome_ec_write_protect_and_reboot(enable)
        else:
            self.faft_client.ec.set_write_protect(enable)
            self.switcher.mode_aware_reboot()

    def set_chrome_ec_write_protect_and_reboot(self, enable):
        """Set Chrome EC write protect status and reboot to take effect.

        @param enable: True if activating EC write protect. Otherwise, False.
        """
        if enable:
            # Set write protect flag and reboot to take effect.
            self.ec.set_flash_write_protect(enable)
            self.sync_and_ec_reboot(
                    flags='hard',
                    extra_sleep=self.faft_config.ec_boot_to_wp_en)
        else:
            # Reboot after deasserting hardware write protect pin to deactivate
            # write protect. And then remove software write protect flag.
            # Some ITE ECs can only clear their WP status on a power-on reset,
            # no software-initiated reset will do.
            self.sync_and_ec_reboot(flags='cold')
            self.ec.set_flash_write_protect(enable)

    def _setup_ec_write_protect(self, ec_wp):
        """Setup for EC write-protection.

        It makes sure the EC in the requested write-protection state. If not, it
        flips the state. Flipping the write-protection requires DUT reboot.

        @param ec_wp: True to request EC write-protected; False to request EC
                      not write-protected; None to do nothing.
        """
        if ec_wp is None:
            return
        self._old_wpsw_cur = self.checkers.crossystem_checker(
                                    {'wpsw_cur': '1'}, suppress_logging=True)
        if ec_wp != self._old_wpsw_cur:
            if not self.faft_config.ap_access_ec_flash:
                raise error.TestNAError(
                        "Cannot change EC write-protect for this device")

            logging.info('The test required EC is %swrite-protected. Reboot '
                         'and flip the state.', '' if ec_wp else 'not ')
            self.switcher.mode_aware_reboot(
                    'custom',
                     lambda:self.set_ec_write_protect_and_reboot(ec_wp))
        wpsw_cur = '1' if ec_wp else '0'
        self.check_state((self.checkers.crossystem_checker, {
                               'wpsw_cur': wpsw_cur}))

    def _restore_ec_write_protect(self):
        """Restore the original EC write-protection."""
        if (not hasattr(self, '_old_wpsw_cur')) or (self._old_wpsw_cur is
                                                    None):
            return
        if not self.checkers.crossystem_checker({'wpsw_cur': '1' if
                       self._old_wpsw_cur else '0'}, suppress_logging=True):
            logging.info('Restore original EC write protection and reboot.')
            self.switcher.mode_aware_reboot(
                    'custom',
                    lambda:self.set_ec_write_protect_and_reboot(
                            self._old_wpsw_cur))
        self.check_state((self.checkers.crossystem_checker, {
                          'wpsw_cur': '1' if self._old_wpsw_cur else '0'}))

    def _record_uart_capture(self):
        """Record the CPU/EC/PD UART output stream to files."""
        self.servo.record_uart_capture(self.resultsdir)

    def _cleanup_uart_capture(self):
        """Cleanup the CPU/EC/PD UART capture."""
        self.servo.close(self.resultsdir)

    def set_ap_off_power_mode(self, power_mode):
        """
        Set the DUT power mode to suspend (S0ix/S3) or shutdown (G3/S5).
        The DUT must be in S0 when calling this method.

        @param power_mode: a string for the expected power mode, either
                           'suspend' or 'shutdown'.
        """
        if power_mode == 'suspend':
            target_power_state = self.POWER_STATE_SUSPEND
        elif power_mode == 'shutdown':
            target_power_state = self.POWER_STATE_G3
        else:
            raise error.TestError('%s is not a valid ap-off power mode.' %
                                  power_mode)

        if self.get_power_state() != self.POWER_STATE_S0:
            raise error.TestError('The DUT is not in S0.')

        self._restore_power_mode = True

        if target_power_state == self.POWER_STATE_G3:
            self.run_shutdown_cmd()
            time.sleep(self.faft_config.shutdown)
        elif target_power_state == self.POWER_STATE_SUSPEND:
            self.suspend()

        if self.wait_power_state(target_power_state, self.DEFAULT_PWR_RETRIES):
            logging.info('System entered %s state.', target_power_state)
        else:
            self._restore_power_mode = False
            raise error.TestFail('System fail to enter %s state. '
                                 'Current state: %s' %
                                 (target_power_state, self.get_power_state()))

    def restore_ap_on_power_mode(self):
        """
        Wake up the DUT to S0. If the DUT was not set to suspend or
        shutdown mode by set_ap_off_power_mode(), raise an error.
        """
        if self.get_power_state() != self.POWER_STATE_S0:
            logging.info('Wake up the DUT to S0.')
            self.servo.power_normal_press()
            # If the DUT is ping-able, it must be in S0.
            self.switcher.wait_for_client()
            if self._restore_power_mode != True:
                raise error.TestFail('The DUT was not set to suspend/shutdown '
                        'mode by set_ap_off_power_mode().')
            self._restore_power_mode = False

    def get_power_state(self):
        """
        Return the current power state of the AP (via EC 'powerinfo' command)

        @return the name of the power state, or None if a problem occurred
        """
        if not hasattr(self, 'ec'):
            # Don't fail when EC not present or not fully initialized
            return None

        pattern = r'power state (\w+) = (\w+),'

        try:
            match = self.ec.send_command_get_output("powerinfo", [pattern],
                                                    retries=3)
        except (error.TestFail, expat.ExpatError) as err:
            logging.warning("powerinfo command encountered an error: %s", err)
            return None
        if not match:
            logging.warning("powerinfo output did not match pattern: %r",
                            pattern)
            return None
        (line, state_num, state_name) = match[0]
        logging.debug("power state info %r", match)
        return state_name

    def _check_power_state(self, expected_power_state, actual_power_state):
        """
        Check for correct power state of the AP (via EC 'powerinfo' command)

        @param expected_power_state: full-string regex of power state you are
        expecting
        @param actual_power_state: the power state returned from get_power_state
        @return: the line and the match, if the output matched.
        @raise error.TestFail: if output didn't match after the delay.
        """
        if not isinstance(expected_power_state, six.string_types):
            raise error.TestError('%s is not a string while it should be.' %
                                  expected_power_state)
        if not isinstance(actual_power_state, six.string_types):
            raise error.TestError('%s is not a string while it should be.' %
                                  actual_power_state)
        if re.match('^' + expected_power_state + '$', actual_power_state):
            return True
        return False

    def wait_power_state(self, power_state, retries, retry_delay=3):
        """
        Wait for certain power state.

        @param power_state: full-string regex of power state you are expecting
        @param retries: retries.  This is necessary if AP is powering down
        and transitioning through different states.
        @param retry_delay: delay between retries in seconds
        """
        logging.info('Checking power state "%s" maximum %d times.',
                     power_state, retries)

        last_power_state = ''
        while retries > 0:
            logging.debug("try count: %d", retries)
            start_time = time.time()
            try:
                retries = retries - 1
                actual_power_state = self.get_power_state()
                if last_power_state != actual_power_state:
                    logging.info("power state: %s", actual_power_state)
                if actual_power_state is None:
                    continue
                if self._check_power_state(power_state, actual_power_state):
                    return True
                last_power_state = actual_power_state
            except (error.TestFail, expat.ExpatError):
                pass
            delay_time = retry_delay - time.time() + start_time
            if delay_time > 0:
                time.sleep(delay_time)
        return False

    def run_shutdown_cmd(self, wait_for_offline=True):
        """Shut down the DUT by running '/sbin/shutdown -P now'."""
        self.faft_client.disconnect()
        # Shut down in the background after sleeping so the call gets a reply.
        try:
            self._client.run_background('sleep 0.5; /sbin/shutdown -P now')
        except error.AutoservRunError as e:
            # From the ssh man page, error code 255 indicates ssh errors.
            if e.result_obj.exit_status == 255:
                logging.warning("Ignoring error from ssh: %s", e)
            else:
                raise
        if wait_for_offline:
            self.switcher.wait_for_client_offline()
        self._client.close_main_ssh()

    def suspend(self):
        """Suspends the DUT."""
        cmd = 'sleep %d; powerd_dbus_suspend' % self.EC_SUSPEND_DELAY
        block = False
        self.faft_client.system.run_shell_command(cmd, block)
        time.sleep(self.EC_SUSPEND_DELAY)

    def _setup_gbb_flags(self):
        """Setup the GBB flags for FAFT test."""
        if self.check_setup_done('gbb_flags'):
            return

        logging.info('Setting proper GBB flags for test.')
        # Ensure that GBB flags are set to 0x140.
        flags_to_set = (vboot.GBB_FLAG_RUNNING_FAFT |
                        vboot.GBB_FLAG_ENTER_TRIGGERS_TONORM)
        # And if the "no_ec_sync" argument is set, then disable EC software
        # sync.
        if self._no_ec_sync:
            logging.info(
                    'User selected to disable EC software sync')
            flags_to_set |= vboot.GBB_FLAG_DISABLE_EC_SOFTWARE_SYNC

        # And if the "no_fw_rollback_check" argument is set, then disable fw
        # rollback check.
        if self._no_fw_rollback_check:
            logging.info(
                    'User selected to disable FW rollback check')
            flags_to_set |= vboot.GBB_FLAG_DISABLE_FW_ROLLBACK_CHECK

        self.clear_set_gbb_flags(0xffffffff, flags_to_set)
        self.mark_setup_done('gbb_flags')

    def _restore_gbb_flags(self):
        """Restore GBB flags to their original state."""
        if self._backup_gbb_flags is None:
            return
        # Setting up and restoring the GBB flags take a lot of time. For
        # speed-up purpose, don't restore it.
        logging.info('***')
        logging.info('*** Please manually restore the original GBB flags to: '
                     '0x%x ***', self._backup_gbb_flags)
        logging.info('***')
        self.unmark_setup_done('gbb_flags')

    def setup_tried_fwb(self, tried_fwb):
        """Setup for fw B tried state.

        It makes sure the system in the requested fw B tried state. If not, it
        tries to do so.

        @param tried_fwb: True if requested in tried_fwb=1;
                          False if tried_fwb=0.
        """
        if tried_fwb:
            if not self.checkers.crossystem_checker({'tried_fwb': '1'}):
                logging.info(
                    'Firmware is not booted with tried_fwb. Reboot into it.')
                self.faft_client.system.set_try_fw_b()
        else:
            if not self.checkers.crossystem_checker({'tried_fwb': '0'}):
                logging.info(
                    'Firmware is booted with tried_fwb. Reboot to clear.')

    def power_on(self):
        """Switch DUT AC power on."""
        self._client.power_on(self.power_control)

    def power_off(self):
        """Switch DUT AC power off."""
        self._client.power_off(self.power_control)

    def power_cycle(self):
        """Power cycle DUT AC power."""
        self._client.power_cycle(self.power_control)

    def setup_rw_boot(self, section='a'):
        """Make sure firmware is in RW-boot mode.

        If the given firmware section is in RO-boot mode, turn off the RO-boot
        flag and reboot DUT into RW-boot mode.

        @param section: A firmware section, either 'a' or 'b'.
        """
        flags = self.faft_client.bios.get_preamble_flags(section)
        if flags & vboot.PREAMBLE_USE_RO_NORMAL:
            flags = flags ^ vboot.PREAMBLE_USE_RO_NORMAL
            self.faft_client.bios.set_preamble_flags(section, flags)
            self.switcher.mode_aware_reboot()

    def setup_kernel(self, part):
        """Setup for kernel test.

        It makes sure both kernel A and B bootable and the current boot is
        the requested kernel part.

        @param part: A string of kernel partition number or 'a'/'b'.
        """
        self.ensure_kernel_boot(part)
        logging.info('Checking the integrity of kernel B and rootfs B...')
        if (self.faft_client.kernel.diff_a_b() or
                not self.faft_client.rootfs.verify_rootfs('B')):
            logging.info('Copying kernel and rootfs from A to B...')
            self.copy_kernel_and_rootfs(from_part=part,
                                        to_part=self.OTHER_KERNEL_MAP[part])
        self.reset_and_prioritize_kernel(part)

    def reset_and_prioritize_kernel(self, part):
        """Make the requested partition highest priority.

        This function also reset kerenl A and B to bootable.

        @param part: A string of partition number to be prioritized.
        """
        root_dev = self.faft_client.system.get_root_dev()
        # Reset kernel A and B to bootable.
        self.faft_client.system.run_shell_command(
            'cgpt add -i%s -P1 -S1 -T0 %s' % (self.KERNEL_MAP['a'], root_dev))
        self.faft_client.system.run_shell_command(
            'cgpt add -i%s -P1 -S1 -T0 %s' % (self.KERNEL_MAP['b'], root_dev))
        # Set kernel part highest priority.
        self.faft_client.system.run_shell_command('cgpt prioritize -i%s %s' %
                (self.KERNEL_MAP[part], root_dev))

    def do_blocking_sync(self, device):
        """Run a blocking sync command."""
        logging.debug("Blocking sync for %s", device)

        if 'mmcblk' in device:
            # For mmc devices, use `mmc status get` command to send an
            # empty command to wait for the disk to be available again.
            self.faft_client.system.run_shell_command('mmc status get %s' %
                                                      device)
        elif 'nvme' in device:
            # For NVMe devices, use `nvme flush` command to commit data
            # and metadata to non-volatile media.

            # Get a list of NVMe namespaces, and flush them individually.
            # The output is assumed to be in the following format:
            # [ 0]:0x1
            # [ 1]:0x2
            list_ns_cmd = "nvme list-ns %s" % device
            available_ns = self.faft_client.system.run_shell_command_get_output(
                list_ns_cmd)

            if not available_ns:
                raise error.TestError(
                    "Listing namespaces failed (empty output): %s"
                    % list_ns_cmd)

            for ns in available_ns:
                ns = ns.split(':')[-1]
                flush_cmd = 'nvme flush %s -n %s' % (device, ns)
                flush_rc = self.faft_client.system.run_shell_command_get_status(
                    flush_cmd)
                if flush_rc != 0:
                    raise error.TestError(
                        "Flushing namespace %s failed (rc=%s): %s"
                        % (ns, flush_rc, flush_cmd))
        else:
            # For other devices, hdparm sends TUR to check if
            # a device is ready for transfer operation.
            self.faft_client.system.run_shell_command('hdparm -f %s' % device)

    def blocking_sync(self, freeze_for_reset=False):
        """Sync root device and internal device, via script if possible.

        The actual calls end up logged by the run() call, since they're printed
        to stdout/stderr in the script.

        @param freeze_for_reset: if True, prepare for reset by blocking writes
                                 (only if enable_fs_sync_fsfreeze=True)
        """

        if self._use_sync_script:
            if freeze_for_reset:
                self.faft_client.quit()
            try:
                return self._client.blocking_sync(freeze_for_reset)
            except (AttributeError, ImportError, error.AutoservRunError) as e:
                logging.warning(
                        'Falling back to old sync method due to error: %s', e)

        # The double calls to sync fakes a blocking call
        # since the first call returns before the flush
        # is complete, but the second will wait for the
        # first to finish.
        self.faft_client.system.run_shell_command('sync')
        self.faft_client.system.run_shell_command('sync')

        # sync only sends SYNCHRONIZE_CACHE but doesn't check the status.
        # This function will perform a device-specific sync command.
        root_dev = self.faft_client.system.get_root_dev()
        self.do_blocking_sync(root_dev)

        # Also sync the internal device if booted from removable media.
        if self.faft_client.system.is_removable_device_boot():
            internal_dev = self.faft_client.system.get_internal_device()
            self.do_blocking_sync(internal_dev)

    def sync_and_ec_reboot(self, flags='', extra_sleep=0):
        """Request the client sync and do a EC triggered reboot.

        @param flags: Optional, a space-separated string of flags passed to EC
                      reboot command, including:
                          default: EC soft reboot;
                          'hard': EC hard reboot.
                          'cold': Cold reboot via servo.
        @param extra_sleep: Optional, int or float for extra wait time for EC
                            reboot in seconds.
        """
        self.blocking_sync(freeze_for_reset=True)
        if flags == 'cold':
            self.servo.get_power_state_controller().reset()
        else:
            self.ec.reboot(flags)
        time.sleep(self.faft_config.ec_boot_to_console + extra_sleep)
        self.check_lid_and_power_on()

    def reboot_and_reset_tpm(self):
        """Reboot into recovery mode, reset TPM, then reboot back to disk."""
        self.switcher.reboot_to_mode(to_mode='rec')
        self.faft_client.system.run_shell_command('chromeos-tpm-recovery')
        self.switcher.mode_aware_reboot()

    def full_power_off_and_on(self):
        """Shutdown the device by pressing power button and power on again."""
        boot_id = self.get_bootid()
        self.faft_client.disconnect()

        # Press power button to trigger ChromeOS normal shutdown process.
        # We use a customized delay since the normal-press 1.2s is not enough.
        self.servo.power_key(self.faft_config.hold_pwr_button_poweroff)
        # device can take 44-51 seconds to restart,
        # add buffer from the default timeout of 60 seconds.
        self.switcher.wait_for_client_offline(timeout=100, orig_boot_id=boot_id)
        time.sleep(self.faft_config.shutdown)
        if self.faft_config.chrome_ec:
            self.check_shutdown_power_state(self.POWER_STATE_G3,
                                            orig_boot_id=boot_id)
        # Short press power button to boot DUT again.
        self.servo.power_key(self.faft_config.hold_pwr_button_poweron)

    def check_shutdown_power_state(self, power_state,
                                   pwr_retries=DEFAULT_PWR_RETRIES,
                                   orig_boot_id=None):
        """Check whether the device shut down and entered the given power state.

        If orig_boot_id is specified, it will check whether the DUT responds to
        ssh requests, then use orig_boot_id to check if it rebooted.

        @param power_state: EC power state has to be checked. Either S5 or G3.
        @param pwr_retries: Times to check if the DUT in expected power state.
        @param orig_boot_id: Old boot_id, to check for unexpected reboots.
        @raise TestFail: If device failed to enter into requested power state.
        """
        if not self.wait_power_state(power_state, pwr_retries):
            current_state = self.get_power_state()
            if current_state == self.POWER_STATE_S0 and self._client.wait_up():
                # DUT is unexpectedly up, so check whether it rebooted instead.
                new_boot_id = self.get_bootid()
                logging.debug('orig_boot_id=%s, new_boot_id=%s',
                              orig_boot_id, new_boot_id)
                if orig_boot_id is None or new_boot_id is None:
                    # Can't say anything more specific without values to compare
                    raise error.TestFail(
                            "Expected state %s, but the system is unexpectedly"
                            " still up.  Current state: %s"
                            % (power_state, current_state))
                if new_boot_id == orig_boot_id:
                    raise error.TestFail(
                            "Expected state %s, but the system didn't shut"
                            " down.  Current state: %s"
                            % (power_state, current_state))
                else:
                    raise error.TestFail(
                            "Expected state %s, but the system rebooted instead"
                            " of shutting down.  Current state: %s"
                            % (power_state, current_state))

            if current_state is None:
                current_state = '(unknown)'

            if current_state == power_state:
                raise error.TestFail(
                        "Expected state %s, but the system didn't reach it"
                        " until after the limit of %s tries."
                        % (power_state, pwr_retries))

            raise error.TestFail('System not shutdown properly and EC fails'
                                 ' to enter into %s state.  Current state: %s'
                                 % (power_state, current_state))
        logging.info('System entered into %s state..', power_state)

    def check_lid_and_power_on(self):
        """
        On devices with EC software sync, system powers on after EC reboots if
        lid is open. Otherwise, the EC shuts down CPU after about 3 seconds.
        This method checks lid switch state and presses power button if
        necessary.
        """
        if self.servo.get("lid_open") == "no":
            time.sleep(self.faft_config.software_sync)
            self.servo.power_short_press()

    def stop_powerd(self):
        """Stop the powerd daemon on the AP.

        This will cause the AP to ignore power button presses sent by the EC.
        """
        powerd_running = self.faft_client.system.run_shell_command_check_output(
                'status powerd', 'start/running')
        if powerd_running:
            logging.debug('Stopping powerd')
            self.faft_client.system.run_shell_command("stop powerd")

    def _modify_usb_kernel(self, usb_dev, from_magic, to_magic):
        """Modify the kernel header magic in USB stick.

        The kernel header magic is the first 8-byte of kernel partition.
        We modify it to make it fail on kernel verification check.

        @param usb_dev: A string of USB stick path on the host, like '/dev/sdc'.
        @param from_magic: A string of magic which we change it from.
        @param to_magic: A string of magic which we change it to.
        @raise TestError: if failed to change magic.
        """
        assert len(from_magic) == 8
        assert len(to_magic) == 8
        # USB image only contains one kernel.
        kernel_part = self._join_part(usb_dev, self.KERNEL_MAP['a'])
        read_cmd = "sudo dd if=%s bs=8 count=1 2>/dev/null" % kernel_part
        current_magic = self.servo.system_output(read_cmd)
        if current_magic == to_magic:
            logging.info("The kernel magic is already %s.", current_magic)
            return
        if current_magic != from_magic:
            raise error.TestError("Invalid kernel image on USB: wrong magic.")

        logging.info('Modify the kernel magic in USB, from %s to %s.',
                     from_magic, to_magic)
        write_cmd = ("echo -n '%s' | sudo dd of=%s oflag=sync conv=notrunc "
                     " 2>/dev/null" % (to_magic, kernel_part))
        self.servo.system(write_cmd)

        if self.servo.system_output(read_cmd) != to_magic:
            raise error.TestError("Failed to write new magic.")

    def corrupt_usb_kernel(self, usb_dev):
        """Corrupt USB kernel by modifying its magic from CHROMEOS to CORRUPTD.

        @param usb_dev: A string of USB stick path on the host, like '/dev/sdc'.
        """
        self._modify_usb_kernel(usb_dev, self.CHROMEOS_MAGIC,
                                self.CORRUPTED_MAGIC)

    def restore_usb_kernel(self, usb_dev):
        """Restore USB kernel by modifying its magic from CORRUPTD to CHROMEOS.

        @param usb_dev: A string of USB stick path on the host, like '/dev/sdc'.
        """
        self._modify_usb_kernel(usb_dev, self.CORRUPTED_MAGIC,
                                self.CHROMEOS_MAGIC)

    def _call_action(self, action_tuple, check_status=False):
        """Call the action function with/without arguments.

        @param action_tuple: A function, or a tuple (function, args, error_msg),
                             in which, args and error_msg are optional. args is
                             either a value or a tuple if multiple arguments.
                             This can also be a list containing multiple
                             function or tuple. In this case, these actions are
                             called in sequence.
        @param check_status: Check the return value of action function. If not
                             succeed, raises a TestFail exception.
        @return: The result value of the action function.
        @raise TestError: An error when the action function is not callable.
        @raise TestFail: When check_status=True, action function not succeed.
        """
        if isinstance(action_tuple, list):
            return all([self._call_action(action, check_status=check_status)
                        for action in action_tuple])

        action = action_tuple
        args = ()
        error_msg = 'Not succeed'
        if isinstance(action_tuple, tuple):
            action = action_tuple[0]
            if len(action_tuple) >= 2:
                args = action_tuple[1]
                if not isinstance(args, tuple):
                    args = (args,)
            if len(action_tuple) >= 3:
                error_msg = action_tuple[2]

        if action is None:
            return

        if not callable(action):
            raise error.TestError('action is not callable!')

        info_msg = 'calling %s' % action.__name__
        if args:
            info_msg += ' with args %s' % str(args)
        logging.info(info_msg)
        ret = action(*args)

        if check_status and not ret:
            raise error.TestFail('%s: %s returning %s' %
                                 (error_msg, info_msg, str(ret)))
        return ret

    def run_shutdown_process(self, shutdown_action, pre_power_action=None,
                             run_power_action=True, post_power_action=None,
                             shutdown_timeout=None):
        """Run shutdown_action(), which makes DUT shutdown, and power it on.

        @param shutdown_action: function which makes DUT shutdown, like
                                pressing power key.
        @param pre_power_action: function which is called before next power on.
        @param run_power_action: power_key press by default, set to None to skip.
        @param post_power_action: function which is called after next power on.
        @param shutdown_timeout: a timeout to confirm DUT shutdown.
        @raise TestFail: if the shutdown_action() failed to turn DUT off.
        """
        self._call_action(shutdown_action)
        logging.info('Wait to ensure DUT shut down...')
        try:
            if shutdown_timeout is None:
                shutdown_timeout = self.faft_config.shutdown_timeout
            self.switcher.wait_for_client(timeout=shutdown_timeout)
            raise error.TestFail(
                    'Should shut the device down after calling %s.' %
                    shutdown_action.__name__)
        except ConnectionError:
            if self.faft_config.chrome_ec:
                self.check_shutdown_power_state(self.POWER_STATE_G3)
            logging.info(
                'DUT is surely shutdown. We are going to power it on again...')

        if pre_power_action:
            self._call_action(pre_power_action)
        if run_power_action:
            self.servo.power_key(self.faft_config.hold_pwr_button_poweron)
        if post_power_action:
            self._call_action(post_power_action)

    def get_bootid(self, retry=3):
        """
        Return the bootid.
        """
        boot_id = None
        while retry:
            try:
                boot_id = self._client.get_boot_id()
                break
            except error.AutoservRunError:
                retry -= 1
                if retry:
                    logging.debug('Retry to get boot_id...')
                else:
                    logging.warning('Failed to get boot_id.')
        logging.debug('boot_id: %s', boot_id)
        return boot_id

    def check_state(self, func):
        """
        Wrapper around _call_action with check_status set to True. This is a
        helper function to be used by tests and is currently implemented by
        calling _call_action with check_status=True.

        TODO: This function's arguments need to be made more stringent. And
        its functionality should be moved over to check functions directly in
        the future.

        @param func: A function, or a tuple (function, args, error_msg),
                             in which, args and error_msg are optional. args is
                             either a value or a tuple if multiple arguments.
                             This can also be a list containing multiple
                             function or tuple. In this case, these actions are
                             called in sequence.
        @return: The result value of the action function.
        @raise TestFail: If the function does notsucceed.
        """
        self._call_action(func, check_status=True)

    def get_current_firmware_identity(self):
        """Get current firmware sha and fwids of body and vblock.

        @return: Current firmware checksums and fwids, as a dict
        """

        current_checksums = {
            'VBOOTA': self.faft_client.bios.get_sig_sha('a'),
            'FVMAINA': self.faft_client.bios.get_body_sha('a'),
            'VBOOTB': self.faft_client.bios.get_sig_sha('b'),
            'FVMAINB': self.faft_client.bios.get_body_sha('b'),
        }
        if not all(current_checksums.values()):
            raise error.TestError(
                    'Failed to get firmware sha: %s', current_checksums)

        current_fwids = {
            'RO_FRID': self.faft_client.bios.get_section_fwid('ro'),
            'RW_FWID_A': self.faft_client.bios.get_section_fwid('a'),
            'RW_FWID_B': self.faft_client.bios.get_section_fwid('b'),
        }
        if not all(current_fwids.values()):
            raise error.TestError(
                    'Failed to get firmware fwid(s): %s', current_fwids)

        identifying_info = dict(current_fwids)
        identifying_info.update(current_checksums)
        return identifying_info

    def is_firmware_changed(self):
        """Check if the current firmware changed, by comparing its SHA and fwid.

        @return: True if it is changed, otherwise False.
        """
        # Device may not be rebooted after test.
        self.faft_client.bios.reload()

        current_info = self.get_current_firmware_identity()
        prev_info = self._backup_firmware_identity

        if current_info == prev_info:
            return False
        else:
            changed = set()
            for section in set(current_info.keys()) | set(prev_info.keys()):
                if current_info.get(section) != prev_info.get(section):
                    changed.add(section)

            logging.info('Firmware changed: %s', ', '.join(sorted(changed)))
            return True

    def backup_firmware(self, suffix='.original'):
        """Backup firmware to file, and then send it to host.

        @param suffix: a string appended to backup file name
        """
        remote_temp_dir = self.faft_client.system.create_temp_dir()
        remote_bios_path = os.path.join(remote_temp_dir, 'bios')
        self.faft_client.bios.dump_whole(remote_bios_path)
        self._client.get_file(remote_bios_path,
                              os.path.join(self.resultsdir, 'bios' + suffix))

        if self.faft_config.chrome_ec:
            remote_ec_path = os.path.join(remote_temp_dir, 'ec')
            self.faft_client.ec.dump_whole(remote_ec_path)
            self._client.get_file(remote_ec_path,
                              os.path.join(self.resultsdir, 'ec' + suffix))

        self._client.run('rm -rf %s' % remote_temp_dir)
        logging.info('Backup firmware stored in %s with suffix %s',
            self.resultsdir, suffix)

        self._backup_firmware_identity = self.get_current_firmware_identity()

    def is_firmware_saved(self):
        """Check if a firmware saved (called backup_firmware before).

        @return: True if the firmware is backed up; otherwise False.
        """
        return bool(self._backup_firmware_identity)

    def restore_firmware(self, suffix='.original', restore_ec=True,
                         reboot_ec=False):
        """Restore firmware from host in resultsdir.

        @param suffix: a string appended to backup file name
        @param restore_ec: True to restore the ec firmware; False not to do.
        @param reboot_ec: True to reboot EC after restore (if it was restored)
        @return: True if firmware needed to be restored
        """
        if not self.is_firmware_changed():
            return False

        # Backup current corrupted firmware.
        self.backup_firmware(suffix='.corrupt')

        # Restore firmware.
        remote_temp_dir = self.faft_client.system.create_temp_dir()

        bios_local = os.path.join(self.resultsdir, 'bios%s' % suffix)
        bios_remote = os.path.join(remote_temp_dir, 'bios%s' % suffix)
        self._client.send_file(bios_local, bios_remote)
        self.faft_client.bios.write_whole(bios_remote)

        if self.faft_config.chrome_ec and restore_ec:
            ec_local = os.path.join(self.resultsdir, 'ec%s' % suffix)
            ec_remote = os.path.join(remote_temp_dir, 'ec%s' % suffix)
            self._client.send_file(ec_local, ec_remote)
            ec_cmd = self.faft_client.ec.get_write_cmd(ec_remote)
            try:
                self._client.run(ec_cmd, timeout=300)
            except error.AutoservSSHTimeout:
                logging.warning("DUT connection died during EC restore")
                self.faft_client.disconnect()

            except error.GenericHostRunError:
                logging.warning("DUT command failed during EC restore")
                logging.debug("Full exception:", exc_info=True)
            if reboot_ec:
                self.switcher.mode_aware_reboot(
                        'custom', lambda: self.sync_and_ec_reboot('hard'))
            else:
                self.switcher.mode_aware_reboot()
        else:
            self.switcher.mode_aware_reboot()
        logging.info('Successfully restored firmware.')
        return True

    def setup_firmwareupdate_shellball(self, shellball=None):
        """Setup a shellball to use in firmware update test.

        Check if there is a given shellball, and it is a shell script. Then,
        send it to the remote host. Otherwise, use the
        /usr/sbin/chromeos-firmwareupdate in the image and replace its inside
        BIOS and EC images with the active firmware images.

        @param shellball: path of a shellball or default to None.
        """
        if shellball:
            # Determine the firmware file is a shellball or a raw binary.
            is_shellball = (utils.system_output("file %s" % shellball).find(
                    "shell script") != -1)
            if is_shellball:
                logging.info('Device will update firmware with shellball %s',
                             shellball)
                temp_path = self.faft_client.updater.get_temp_path()
                working_shellball = os.path.join(temp_path,
                                                 'chromeos-firmwareupdate')
                self._client.send_file(shellball, working_shellball)
                self.faft_client.updater.extract_shellball()
            else:
                raise error.TestFail(
                    'The given shellball is not a shell script.')
        else:
            logging.info('No shellball given, use the original shellball and '
                         'replace its BIOS and EC images.')
            work_path = self.faft_client.updater.get_work_path()
            bios_in_work_path = os.path.join(
                work_path, self.faft_client.updater.get_bios_relative_path())
            ec_in_work_path = os.path.join(
                work_path, self.faft_client.updater.get_ec_relative_path())
            logging.info('Writing current BIOS to: %s', bios_in_work_path)
            self.faft_client.bios.dump_whole(bios_in_work_path)
            if self.faft_config.chrome_ec:
                logging.info('Writing current EC to: %s', ec_in_work_path)
                self.faft_client.ec.dump_firmware(ec_in_work_path)
            self.faft_client.updater.repack_shellball()

    def is_kernel_changed(self, kernel_type='KERN'):
        """Check if the current kernel is changed, by comparing its SHA1 hash.

        @param kernel_type: The type name of kernel ('KERN' or 'MINIOS').
        @return: True if it is changed; otherwise, False.
        """
        changed = False
        for p in ('A', 'B'):
            backup_sha = self._backup_kernel_sha[kernel_type].get(p, None)
            current_sha = self.kernel_servicer[kernel_type].get_sha(p)
            if backup_sha != current_sha:
                changed = True
                logging.info('Kernel %s-%s is changed', kernel_type, p)
        return changed

    def backup_kernel(self, suffix='.original', kernel_type='KERN'):
        """Backup kernel to files, and the send them to host.

        @param kernel_type: The type name of kernel ('KERN' or 'MINIOS').
        @param suffix: a string appended to backup file name.
        """
        kernel_name = self.KERNEL_TYPE_NAME_MAP[kernel_type]
        servicer = self.kernel_servicer[kernel_type]
        remote_temp_dir = self.faft_client.system.create_temp_dir()
        for p in ('A', 'B'):
            remote_path = os.path.join(remote_temp_dir,
                                       '%s_%s' % (kernel_name, p))
            servicer.dump(p, remote_path)
            self._client.get_file(
                    remote_path,
                    os.path.join(self.resultsdir,
                                 '%s_%s%s' % (kernel_name, p, suffix)))
            self._backup_kernel_sha[kernel_type][p] = servicer.get_sha(p)
        logging.info('Backup %s stored in %s with suffix %s', kernel_name,
                     self.resultsdir, suffix)

    def is_kernel_saved(self, kernel_type='KERN'):
        """Check if kernel images are saved (backup_kernel called before).

        @param kernel_type: The type name of kernel ('KERN' or 'MINIOS').
        @return: True if the kernel is saved; otherwise, False.
        """
        return len(self._backup_kernel_sha[kernel_type]) != 0

    def restore_kernel(self, suffix='.original', kernel_type='KERN'):
        """Restore kernel from host in resultsdir.

        @param kernel_type: The type name of kernel ('KERN' or 'MINIOS').
        @param suffix: a string appended to backup file name.
        @return: True if kernel needed to be restored
        """
        if not self.is_kernel_changed(kernel_type):
            return False

        # Backup current corrupted kernel.
        self.backup_kernel(suffix='.corrupt', kernel_type=kernel_type)

        # Restore kernel.
        kernel_name = self.KERNEL_TYPE_NAME_MAP[kernel_type]
        servicer = self.kernel_servicer[kernel_type]
        remote_temp_dir = self.faft_client.system.create_temp_dir()
        for p in ('A', 'B'):
            remote_path = os.path.join(remote_temp_dir,
                                       '%s_%s' % (kernel_name, p))
            servicer.dump(p, remote_path)
            self._client.send_file(
                    os.path.join(self.resultsdir,
                                 '%s_%s%s' % (kernel_name, p, suffix)),
                    remote_path)
            servicer.write(p, remote_path)

        self.switcher.mode_aware_reboot()
        logging.info('Successfully restored %s.', kernel_type)
        return True

    def backup_cgpt_attributes(self):
        """Backup CGPT partition table attributes."""
        self._backup_cgpt_attr = self.faft_client.cgpt.get_attributes()

    def restore_cgpt_attributes(self):
        """Restore CGPT partition table attributes."""
        current_table = self.faft_client.cgpt.get_attributes()
        if current_table == self._backup_cgpt_attr:
            return
        logging.info('CGPT table is changed. Original: %r. Current: %r.',
                     self._backup_cgpt_attr,
                     current_table)
        self.faft_client.cgpt.set_attributes(
                self._backup_cgpt_attr['A'], self._backup_cgpt_attr['B'])

        self.switcher.mode_aware_reboot()
        logging.info('Successfully restored CGPT table.')

    def try_fwb(self, count=0):
        """set to try booting FWB count # times

        Wrapper to set fwb_tries for vboot1 and fw_try_count,fw_try_next for
        vboot2

        @param count: an integer specifying value to program into
                      fwb_tries(vb1)/fw_try_next(vb2)
        """
        if self.fw_vboot2:
            self.faft_client.system.set_fw_try_next('B', count)
        else:
            # vboot1: we need to boot into fwb at least once
            if not count:
                count = count + 1
            self.faft_client.system.set_try_fw_b(count)

    def identify_shellball(self, include_ec=None):
        """Get the FWIDs of all targets and sections in the shellball

        @param include_ec: if True, get EC fwids.
                           If None (default), assume True if board has an EC
        @return: the dict of versions in the shellball
        """
        fwids = dict()
        fwids['bios'] = self.faft_client.updater.get_image_fwids('bios')

        if include_ec is None:
            if self.faft_config.platform.lower() == 'samus':
                include_ec = False  # no ec.bin in shellball
            else:
                include_ec = self.faft_config.chrome_ec

        if include_ec:
            fwids['ec'] = self.faft_client.updater.get_image_fwids('ec')
        return fwids

    def modify_shellball(self, append, modify_ro=True, modify_ec=False):
        """Modify the FWIDs of targets and sections in the shellball

        @return: the full path of the shellball
        """

        if modify_ro:
            self.faft_client.updater.modify_image_fwids(
                    'bios', ['ro', 'a', 'b'])
        else:
            self.faft_client.updater.modify_image_fwids(
                    'bios', ['a', 'b'])

        if modify_ec:
            if modify_ro:
                self.faft_client.updater.modify_image_fwids(
                        'ec', ['ro', 'rw'])
            else:
                self.faft_client.updater.modify_image_fwids(
                        'ec', ['rw'])

        modded_shellball = self.faft_client.updater.repack_shellball(append)

        return modded_shellball

    @staticmethod
    def check_fwids_written(before_fwids, image_fwids, after_fwids,
                            expected_written):
        """Check the dicts of fwids for correctness after an update is applied.

        The targets checked come from the keys of expected_written.
        The sections checked come from the inner dicts of the fwids parameters.

        The fwids should be keyed by target (flash type), then by section:
        {'bios': {'ro': '<fwid>', 'a': '<fwid>', 'b': '<fwid>'},
         'ec': {'ro': '<fwid>', 'rw': '<fwid>'}

        For expected_written, the dict should be keyed by flash type only:
        {'bios': ['ro'], 'ec': ['ro', 'rw']}
        To expect the contents completely unchanged, give only the keys:
        {'bios': [], 'ec': []} or {'bios': None, 'ec': None}

        @param before_fwids: dict of versions from before the update
        @param image_fwids: dict of versions in the update
        @param after_fwids: dict of actual versions after the update
        @param expected_written: dict indicating which ones should have changed
        @return: list of error lines for mismatches

        @type before_fwids: dict
        @type image_fwids: dict | None
        @type after_fwids: dict
        @type expected_written: dict
        @rtype: list
        """
        errors = []

        if image_fwids is None:
            image_fwids = {}

        for target in sorted(expected_written.keys()):
            # target is BIOS or EC

            before_missing = (target not in before_fwids)
            after_missing = (target not in after_fwids)
            if before_missing or after_missing:
                if before_missing:
                    errors.append("...no before_fwids[%s]" % target)
                if after_missing:
                    errors.append("...no after_fwids[%s]" % target)
                continue

            written_sections = expected_written.get(target) or list()
            written_sections = set(written_sections)

            before_sections = set(before_fwids.get(target) or dict())
            image_sections = set(image_fwids.get(target) or dict())
            after_sections = set(after_fwids.get(target) or dict())

            for section in before_sections | image_sections | after_sections:
                # section is RO, RW, A, or B

                before_fwid = before_fwids[target][section]
                image_fwid = image_fwids.get(target, {}).get(section, None)
                actual_fwid = after_fwids[target][section]

                if section in written_sections:
                    expected_fwid = image_fwid
                    expected_desc = 'rewritten fwid (%s)' % expected_fwid
                    if image_fwid == before_fwid:
                        expected_desc = ('rewritten (no changes) fwid (%s)' %
                                         expected_fwid)
                else:
                    expected_fwid = before_fwid
                    expected_desc = 'original fwid (%s)' % expected_fwid

                if actual_fwid == expected_fwid:
                    actual_desc = 'correct value'

                elif actual_fwid == image_fwid:
                    actual_desc = 'rewritten fwid (%s)' % actual_fwid
                    if image_fwid == before_fwid:
                        # The flash could have been rewritten with the same fwid
                        actual_desc = 'possibly written fwid (%s)' % actual_fwid

                elif actual_fwid == before_fwid:
                    actual_desc = 'original fwid (%s)' % actual_fwid

                else:
                    actual_desc = 'unknown fwid (%s)' % actual_fwid

                msg = ("...FWID (%s %s): expected %s, got %s" %
                       (target.upper(), section.upper(),
                        expected_desc, actual_desc))

                if actual_fwid != expected_fwid:
                    errors.append(msg)
        return errors


    def fwmp_is_cleared(self):
        """Return True if the FWMP has been created"""
        res = self.host.run('cryptohome '
                            '--action=get_firmware_management_parameters',
                            ignore_status=True)
        if res.exit_status and res.exit_status != self.FWMP_CLEARED_EXIT_STATUS:
            raise error.TestError('Could not run cryptohome command %r' % res)
        return (self.FWMP_CLEARED_ERROR_MSG in res.stdout
                or tpm_utils.FwmpIsAllZero(res.stdout))


    def _tpm_is_owned(self):
        """Returns True if the tpm is owned"""
        result = self.host.run('tpm_manager_client status --nonsensitive',
                               ignore_status=True)
        logging.debug(result)
        return result.exit_status == 0 and 'is_owned: true' in result.stdout

    def clear_fwmp(self):
        """Clear the FWMP"""
        if self.fwmp_is_cleared():
            return
        tpm_utils.ClearTPMOwnerRequest(self.host, wait_for_ready=True)
        self.host.run('tpm_manager_client take_ownership')
        if not utils.wait_for_value(self._tpm_is_owned, expected_value=True):
            raise error.TestError('Unable to own tpm while clearing fwmp.')
        self.host.run('cryptohome '
                      '--action=remove_firmware_management_parameters')

    def wait_for(self, cfg_field, action_msg=None, extra_time=0):
        """Waits for time specified in a config.

        @ivar cfg_field: The name of the config field that specifies the
                            time to wait.
        @ivar action_msg: Optional log message describing the action that
                            will occur after the wait.
        @ivar extra_time: Additional time to be added to time from config.
        """
        wait_time = self.faft_config.__getattr__(cfg_field) + extra_time
        if extra_time:
            wait_src = "%s + %s" % (cfg_field, extra_time)
        else:
            wait_src = cfg_field

        units = 'second' if wait_time==1 else 'seconds'
        start_msg = "Waiting %s(%s) %s" % (wait_time, wait_src, units)
        if action_msg:
            start_msg += ", before '%s'" % action_msg
        start_msg += "."

        logging.info(start_msg)
        time.sleep(wait_time)
        logging.info("Done waiting.")

    def _try_to_bring_dut_up(self):
        """Try to quickly get the dut in a pingable state"""
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')
        logging.info('Bringing DUT up')

        self.servo.set_nocheck('cold_reset', 'off')
        self.servo.set_nocheck('warm_reset', 'off')

        time.sleep(self.cr50.SHORT_WAIT)
        if not self.cr50.ap_is_on():
            logging.info('Pressing power button to turn on AP')
            self.servo.power_short_press()

        end_time = time.time() + self.RESPONSE_TIMEOUT
        while not self.host.ping_wait_up(
                self.faft_config.delay_reboot_to_ping * 2):
            if time.time() > end_time:
                logging.warning(
                        'DUT is unresponsive after trying to bring it up')
                return
            self.servo.get_power_state_controller().reset()
            logging.info('DUT did not respond. Resetting it.')

    def _check_open_and_press_power_button(self):
        """Check stdout and press the power button if prompted.

        Returns:
            True if the process is still running.
        """
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        logging.info(self._get_ccd_open_output())
        self.servo.power_short_press()
        logging.info('long int power button press')
        # power button press cr50 erases nvmem and resets the dut before setting
        # the state to open. Wait a bit so we don't check the ccd state in the
        # middle of this reset process. Power button requests happen once a
        # minute, so waiting 10 seconds isn't a big deal.
        time.sleep(10)
        return (self.cr50.OPEN == self.cr50.get_ccd_level() or
                self._ccd_open_job.sp.poll() is not None)

    def _get_ccd_open_output(self):
        """Read the new output."""
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        self._ccd_open_job.process_output()
        output = self._ccd_open_stdout.getvalue()
        self._ccd_open_stdout.seek(self._ccd_open_last_len)
        self._ccd_open_last_len = len(output)
        return self._ccd_open_stdout.read().strip()

    def _close_ccd_open_job(self):
        """Terminate the process and check the results."""
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        exit_status = utils.nuke_subprocess(self._ccd_open_job.sp)
        stdout = self._ccd_open_stdout.getvalue().strip()
        delattr(self, '_ccd_open_job')
        if stdout:
            logging.info('stdout of ccd open:\n%s', stdout)
        if exit_status:
            logging.info('exit status: %d', exit_status)
        if 'Error' in stdout:
            raise error.TestFail('ccd open Error %s' %
                                 stdout.split('Error')[-1])
        if self.cr50.OPEN != self.cr50.get_ccd_level():
            raise error.TestFail('unable to open cr50: %s' % stdout)
        else:
            logging.info('Opened Cr50')

    def ccd_open_from_ap(self):
        """Start the open process and press the power button."""
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        # Opening CCD requires power button presses. If those presses would
        # power off the AP and prevent CCD open from completing, ignore them.
        if self.faft_config.ec_forwards_short_pp_press:
            self.stop_powerd()

        # Make sure the test waits long enough to avoid ccd rate limiting.
        time.sleep(self.cr50.CCD_PASSWORD_RATE_LIMIT)

        self._ccd_open_last_len = 0

        self._ccd_open_stdout = six.StringIO()

        ccd_open_cmd = utils.sh_escape('gsctool -a -o')
        full_ssh_cmd = '%s "%s"' % (self.host.ssh_command(options='-tt'),
                                    ccd_open_cmd)
        # Start running the Cr50 Open process in the background.
        self._ccd_open_job = utils.BgJob(full_ssh_cmd,
                                         nickname='ccd_open',
                                         stdout_tee=self._ccd_open_stdout,
                                         stderr_tee=utils.TEE_TO_LOGS)
        if self._ccd_open_job == None:
            raise error.TestFail('could not start ccd open')

        try:
            # Wait for the first gsctool power button prompt before starting the
            # open process.
            logging.info(self._get_ccd_open_output())
            # Cr50 starts out by requesting 5 quick presses then 4 longer
            # power button presses. Run the quick presses without looking at the
            # command output, because getting the output can take some time. For
            # the presses that require a 1 minute wait check the output between
            # presses, so we can catch errors
            #
            # run quick presses for 30 seconds. It may take a couple of seconds
            # for open to start. 10 seconds should be enough. 30 is just used
            # because it will definitely be enough, and this process takes 300
            # seconds, so doing quick presses for 30 seconds won't matter.
            end_time = time.time() + 30
            while time.time() < end_time:
                self.servo.power_short_press()
                logging.info('short int power button press')
                time.sleep(self.PP_SHORT_INTERVAL)
            # Poll the output and press the power button for the longer presses.
            utils.wait_for_value(self._check_open_and_press_power_button,
                                 expected_value=True,
                                 timeout_sec=self.cr50.PP_LONG)
        except Exception as e:
            logging.info(e)
            raise
        finally:
            self._close_ccd_open_job()
            self._try_to_bring_dut_up()
        logging.info(self.cr50.get_ccd_info())

    def enter_mode_after_checking_cr50_state(self, mode):
        """Reboot to mode if cr50 doesn't already match the state"""
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        # If the device is already in the correct mode, don't do anything
        if (mode == 'dev') == self.cr50.in_dev_mode():
            logging.info('already in %r mode', mode)
            return

        self.switcher.reboot_to_mode(to_mode=mode)

        if (mode == 'dev') != self.cr50.in_dev_mode():
            raise error.TestError('Unable to enter %r mode' % mode)

    def fast_ccd_open(self, enable_testlab=False, reset_ccd=True,
                      dev_mode=False):
        """Try to use ccd testlab open. If that fails, do regular ap open.

        Args:
            enable_testlab: If True, enable testlab mode after cr50 is open.
            reset_ccd: If True, reset ccd after open.
            dev_mode: True if the device should be in dev mode after ccd is
                      is opened.
        """
        if not hasattr(self, 'cr50'):
            raise error.TestNAError('Test can only be run on devices with '
                                    'access to the Cr50 console')

        if self.servo.main_device_is_ccd() and not self.cr50.testlab_is_on():
            error_txt = 'because the main servo device is CCD.'
            if enable_testlab:
                raise error.TestNAError('Cannot enable testlab: %s' % error_txt)
            elif reset_ccd:
                raise error.TestNAError('CCD reset not allowed: %s' % error_txt)

        if not self.faft_config.has_powerbutton:
            logging.warning('No power button', exc_info=True)
            enable_testlab = False

        # Try to use testlab open first, so we don't have to wait for the
        # physical presence check.
        self.cr50.send_command('ccd testlab open')
        if self.cr50.OPEN != self.cr50.get_ccd_level():
            if self.servo.has_control('chassis_open'):
                self.servo.set('chassis_open', 'yes')
            pw = '' if self.cr50.password_is_reset() else self.CCD_PASSWORD
            # Use the console to open cr50 without entering dev mode if
            # possible. Ittakes longer and relies on more systems to enter dev
            # mode and ssh into the AP. Skip the steps that aren't required.
            if not (pw or self.cr50.get_cap(
                            'OpenNoDevMode')[self.cr50.CAP_IS_ACCESSIBLE]):
                self.enter_mode_after_checking_cr50_state('dev')

            if pw or self.cr50.get_cap(
                            'OpenFromUSB')[self.cr50.CAP_IS_ACCESSIBLE]:
                self.cr50.set_ccd_level(self.cr50.OPEN, pw)
            else:
                self.ccd_open_from_ap()

            if self.servo.has_control('chassis_open'):
                self.servo.set('chassis_open', 'no')

            if enable_testlab:
                self.cr50.set_ccd_testlab('on')

        if reset_ccd:
            self.cr50.ccd_reset()

        # In default, the device should be in normal mode. After opening cr50,
        # the TPM should be cleared and the device should automatically reset to
        # normal mode. However, some tests might want the device in 'dev' mode.
        self.enter_mode_after_checking_cr50_state('dev' if dev_mode else
                                                 'normal')
