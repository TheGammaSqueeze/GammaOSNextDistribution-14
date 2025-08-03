# Lint as: python2, python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import functools
import logging
import math
import sys
import time

import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import hosts
from autotest_lib.client.common_lib import utils
from autotest_lib.server.cros.servo import servo
from autotest_lib.server.hosts import cros_constants
from autotest_lib.server.hosts import repair_utils
from autotest_lib.server.hosts import servo_constants
from autotest_lib.server.cros.servo.topology import servo_topology
from autotest_lib.site_utils.admin_audit import servo_updater
import six

try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock

from autotest_lib.utils.frozen_chromite.lib import timeout_util

def ignore_exception_for_non_cros_host(func):
    """
    Decorator to ignore ControlUnavailableError if servo host is not cros host.
    When using test_that command on a workstation, this enables usage of
    additional servo devices such as servo micro and Sweetberry. This shall not
    change any lab behavior.
    """
    @functools.wraps(func)
    def wrapper(self, host):
        """
        Wrapper around func.
        """
        try:
            func(self, host)
        except servo.ControlUnavailableError as e:
            if host.is_cros_host():
                raise
            logging.warning("Servo host is not cros host, ignore %s: %s",
                            type(e).__name__, e)
    return wrapper


class _UpdateVerifier(hosts.Verifier):
    """
    Verifier to trigger a servo host update, if necessary.

    The verifier works only for servo_v3.
    The operation doesn't wait for the update to complete and is
    considered a success whether or not the servo is currently
    up-to-date.
    """

    @timeout_util.TimeoutDecorator(cros_constants.LONG_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            if (
                    not host.get_dut_host_info()
                    or not host.get_dut_host_info().servo_cros_stable_version):
                logging.info('Servo stable version missed.'
                             ' Skip update check action.')
                return
            # We have seen cases that invalid GPT headers/entries block
            # v3s from been update, so always try to repair here.
            # See crbug.com/994396, crbug.com/1057302.
            host.run('cgpt repair /dev/mmcblk0', ignore_status=True)
            host.update_image()
        # We don't want failure from update block DUT repair action.
        # See crbug.com/1029950.
        except Exception as e:
            six.reraise(hosts.AutoservNonCriticalVerifyError,
                        hosts.AutoservNonCriticalVerifyError(e),
                        sys.exc_info()[2])

    def _is_applicable(self, host):
        # Run only for servo_v3 host.
        if host.is_labstation() or host.is_containerized_servod():
            return False
        # Only run if the host is in the physical lab.
        return host.is_in_lab()

    @property
    def description(self):
        return 'Servo_v3 host software is up-to-date'


class _StartServodVerifier(hosts.Verifier):
    """First start of servod on the host.

    Single running action to start servod in the first time.
    This verifier created to fit current flow and will be revisited later.
    Action never fails!
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if not hasattr(self, 'started'):
            logging.info('Starting servod!')
            host.restart_servod(quick_startup=True)
        # caching the value to prevent restart service when trigger verifier.
        self.started = True

    @property
    def description(self):
        return 'Initial servod start'


class _RootServoPresentVerifier(hosts.Verifier):
    """Verifier that first servo is present."""

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        device = None
        topology = host.get_topology()
        topology.read(host.get_dut_host_info())
        try:
            device = topology.get_root_servo()
        except Exception as e:
            if host.is_containerized_servod():
                host.restart_servod()
                logging.debug('Restarting servod container (Not critical) %s',
                              e)
            else:
                host.request_reboot()
                logging.info(
                        'Reboot labstation requested, it will be handled'
                        ' by labstation AdminRepair task.'
                        ' Unable to detect root servo info from topology.')
                logging.debug('(Not critical) %s', e)
        if device:
            logging.info('Root servo is present')
            return
        device = topology.get_root_servo_from_cache()
        if device:
            logging.debug('Found device: %s', device)
            if device.get_serial_number() != host.servo_serial:
                self.serial_mismatch = True
                raise hosts.AutoservVerifyError('Serial mismatch detected')
            logging.info('Root servo is present')
            return
        # Leaving error in case we got empty device.
        raise hosts.AutoservVerifyError('Root servo not found!')

    def _is_applicable(self, host):
        if host.is_containerized_servod():
            logging.info('Servod is running within a container.')
            return True
        if not host.is_labstation():
            logging.info('Not supported for servo_v3.')
            return False
        # Only run if the host is in the physical lab.
        return host.is_in_lab()

    @property
    def description(self):
        return 'Root servo is present'


class _RootServoV3PresentVerifier(hosts.Verifier):
    """Verifier that first servo is present."""

    RETRY_COUNT = 3

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        for a in range(self.RETRY_COUNT):
            logging.debug('Attempt: %s find servo board on servo_v3.', a + 1)
            present = host.is_servo_board_present_on_servo_v3()
            if present == False:
                raise hosts.AutoservVerifyError('Servo board not found!')
            elif present == True:
                logging.debug('Servo board is present')
                return
        raise hosts.AutoservVerifyError('Fail to find servo board!')

    def _is_applicable(self, host):
        if host.is_containerized_servod():
            logging.info('Servod is running within a container.')
            return False
        # Do not run for servos under labstations.
        if host.is_labstation():
            logging.info('Servod is running on labstation.')
            return False
        # Only run if the host is in the physical lab.
        return host.is_in_lab()

    @property
    def description(self):
        return 'Servo board on servo_v3 is present'


class _ServoFwVerifier(hosts.Verifier):
    """Verifier to check is a servo fw is up-to-date."""

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            if servo_updater.any_servo_needs_firmware_update(host):
                raise hosts.AutoservVerifyError(
                        'Some servo requires firmware update')
        except servo_updater.ServoFwVersionMissedError as e:
            # Do not fail as it will trigger re-flash fw on the servo
            logging.info(
                    'Issue with detect new version of firmware for servo.'
                    ' Please file a bug agains Fleet Automation team (go/fleet-bug)'
            )

    def _is_applicable(self, host):
        if host.is_containerized_servod():
            logging.info('Servod is running within a container.')
            return True
        # Run only for servos under labstations.
        if not host.is_labstation():
            logging.info('Not supported for servo_v3.')
            return False
        # Only run if the host is in the physical lab.
        return host.is_in_lab()

    @property
    def description(self):
        return 'Servo fw is up-to-date'


class _ConfigVerifier(hosts.Verifier):
    """
    Base verifier for the servo config file verifiers.
    """

    CONFIG_FILE = '/var/lib/servod/config'
    ATTR = ''

    @staticmethod
    def _get_config_val(host, config_file, attr):
        """
        Get the `attr` for `host` from `config_file`.

        @param host         Host to be checked for `config_file`.
        @param config_file  Path to the config file to be tested.
        @param attr         Attribute to get from config file.

        @return The attr val as set in the config file, or `None` if
                the file was absent.
        """
        getboard = ('CONFIG=%s ; [ -f $CONFIG ] && '
                    '. $CONFIG && echo $%s' % (config_file, attr))
        attr_val = host.run(getboard, ignore_status=True).stdout
        return attr_val.strip('\n') if attr_val else None

    @staticmethod
    def _validate_attr(host, val, expected_val, attr, config_file):
        """
        Check that the attr setting is valid for the host.

        This presupposes that a valid config file was found.  Raise an
        execption if:
          * There was no attr setting from the file (i.e. the setting
            is an empty string), or
          * The attr setting is valid, the attr is known,
            and the setting doesn't match the DUT.

        @param host         Host to be checked for `config_file`.
        @param val          Value to be tested.
        @param expected_val Expected value.
        @param attr         Attribute we're validating.
        @param config_file  Path to the config file to be tested.
        """
        if not val:
            raise hosts.AutoservVerifyError(
                    'config file %s exists, but %s '
                    'is not set' % (attr, config_file))
        if expected_val is not None and val != expected_val:
            raise hosts.AutoservVerifyError(
                    '%s is %s; it should be %s' % (attr, val, expected_val))


    def _get_config(self, host):
        """
        Return the config file to check.

        @param host     Host object.

        @return The config file to check.
        """
        return '%s_%d' % (self.CONFIG_FILE, host.servo_port)

    @property
    def description(self):
        return 'servo %s setting is correct' % self.ATTR


class _SerialConfigVerifier(_ConfigVerifier):
    """
    Verifier for the servo SERIAL configuration.
    """

    ATTR = 'SERIAL'

    @timeout_util.TimeoutDecorator(cros_constants.SHORT_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        """
        Test whether the `host` has a `SERIAL` setting configured.

        This tests the config file names used by the `servod` upstart
        job for a valid setting of the `SERIAL` variable.  The following
        conditions raise errors:
          * The SERIAL setting doesn't match the DUT's entry in the AFE
            database.
          * There is no config file.
        """
        if not host.is_cros_host():
            return
        # Not all servo hosts will have a servo serial so don't verify if it's
        # not set.
        if host.servo_serial is None:
            return
        config = self._get_config(host)
        serialval = self._get_config_val(host, config, self.ATTR)
        if serialval is None:
            raise hosts.AutoservVerifyError(
                    'Servo serial is unconfigured; should be %s'
                    % host.servo_serial
            )

        self._validate_attr(host, serialval, host.servo_serial, self.ATTR,
                            config)



class _BoardConfigVerifier(_ConfigVerifier):
    """
    Verifier for the servo BOARD configuration.
    """

    ATTR = 'BOARD'

    @timeout_util.TimeoutDecorator(cros_constants.SHORT_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        """
        Test whether the `host` has a `BOARD` setting configured.

        This tests the config file names used by the `servod` upstart
        job for a valid setting of the `BOARD` variable.  The following
        conditions raise errors:
          * A config file exists, but the content contains no setting
            for BOARD.
          * The BOARD setting doesn't match the DUT's entry in the AFE
            database.
          * There is no config file.
        """
        if not host.is_cros_host():
            return
        config = self._get_config(host)
        boardval = self._get_config_val(host, config, self.ATTR)
        if boardval is None:
            msg = 'Servo board is unconfigured'
            if host.servo_board is not None:
                msg += '; should be %s' % host.servo_board
            raise hosts.AutoservVerifyError(msg)

        self._validate_attr(host, boardval, host.servo_board, self.ATTR,
                            config)


class _ServodJobVerifier(hosts.Verifier):
    """
    Verifier to check that the `servod` upstart job is running.
    """

    @timeout_util.TimeoutDecorator(cros_constants.SHORT_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if not host.is_cros_host():
            return
        status_cmd = 'status servod PORT=%d' % host.servo_port
        job_status = host.run(status_cmd, ignore_status=True).stdout
        if 'start/running' not in job_status:
            raise hosts.AutoservVerifyError(
                    'servod not running on %s port %d' %
                    (host.hostname, host.servo_port))

    @property
    def description(self):
        return 'servod upstart job is running'


class _ServodEchoVerifier(hosts.Verifier):
    """
    Verifier to check that the `servod` upstart job is responsible.
    """

    SERVOD_INITIALIZED = 'servodtool instance wait-for-active -p %d --timeout 60'
    SERVOD_RESPONSIVE = 'dut-control -p %d serialname'

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        self._verify_servod_initialized(host)
        self._verify_servod_responsive(host)

    def _verify_servod_initialized(self, host):
        # Verify that servod initialized.
        cmd = self.SERVOD_INITIALIZED % host.servo_port
        res = host.run(cmd, ignore_status=True, timeout=120)
        if res.exit_status != 0:
            raise hosts.AutoservVerifyError(
                    'Servod instance is not initialized')
        logging.debug("Presented instance: %s", res.stdout.strip())

    def _verify_servod_responsive(self, host):
        # Verify if servod started and process is responsible.
        cmd = self.SERVOD_RESPONSIVE % host.servo_port
        res = host.run(cmd, ignore_status=True, timeout=120)
        if res.exit_status != 0:
            raise hosts.AutoservVerifyError(
                    'Servod is not responsive for dut-control commands')
        logging.info('Servod responsive: %s', res.stdout)

    @property
    def description(self):
        return 'Servod is running and responsive to dut-control run.'


class _DiskSpaceVerifier(hosts.Verifier):
    """
    Verifier to make sure there is enough disk space left on servohost.
    """

    @timeout_util.TimeoutDecorator(cros_constants.SHORT_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # Check available space of stateful is greater than threshold, in Gib.
        host.check_diskspace('/mnt/stateful_partition', 0.5)

    @property
    def description(self):
        return 'servohost has enough disk space.'

    def _is_applicable(self, host):
        if host.is_containerized_servod():
            logging.info('Servod is running within a container.')
            return False
        return True


class _ServodConnectionVerifier(hosts.Verifier):
    """
    Verifier to check that we can connect to servod server.

    If this verifier failed, it most likely servod was crashed or in a
    crashing loop. For servo_v4 it's usually caused by not able to detect
    CCD or servo_micro.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        host.initialize_servo()

    @property
    def description(self):
        return 'servod service is taking calls'


class _ServodControlVerifier(hosts.Verifier):
    """
    Verifier to check basic servo control functionality.

    This tests the connection to the target servod service with a simple
    method call.  As a side-effect, all servo signals are initialized to
    default values.

    N.B. Initializing servo signals is necessary because the power
    button and lid switch verifiers both test against expected initial
    values.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            host.initialize_dut_for_servo()
        except Exception as e:
            six.reraise(hosts.AutoservNonCriticalVerifyError,
                        hosts.AutoservNonCriticalVerifyError(e),
                        sys.exc_info()[2])

    @property
    def description(self):
        return 'Basic servod control is working'


class _Cr50ConsoleVerifier(hosts.Verifier):
    """Verifier to check if cr50 console is present and working.

    Validating based by running commands and expect they will not fail.
    If any command fail then console is not working as expected.
    """

    COMMAND_TO_CHECK_CONSOLE = (
            'gsc_ccd_level',
            'cr50_testlab',
            'cr50_ccd_state_flags',
    )

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            for command in self.COMMAND_TO_CHECK_CONSOLE:
                if host.get_servo().has_control(command):
                    # Response of command is not important.
                    host.get_servo().get(command)
        except Exception as e:
            six.reraise(hosts.AutoservNonCriticalVerifyError,
                        hosts.AutoservNonCriticalVerifyError(e),
                        sys.exc_info()[2])

    def _is_applicable(self, host):
        # Only when DUT is running through ccd or c2d2.
        # TODO(coconutruben): replace with ccd API when available in servo.py
        return host.get_servo() and host.get_servo().main_device_uses_gsc_drv()

    @property
    def description(self):
        return 'CR50 console is working'


class _CCDTestlabVerifier(hosts.Verifier):
    """
    Verifier to check that ccd testlab is enabled.

    All DUT connected by ccd has to supported cr50 with enabled testlab
    to allow manipulation by servo. The flag testlab is sticky and will
    stay enabled if was set up. The testlab can be enabled when ccd is
    open. (go/ccd-setup)
    """
    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if not host.get_servo().has_control('cr50_testlab'):
            raise hosts.AutoservVerifyError(
                    'gsc has to be supported when use servo with '
                    'ccd_*/type-c connection')

        status = host.get_servo().get('cr50_testlab')
        # check by 'on' to fail when get unexpected value
        if status == 'on':
            # If servo uses cr50 to control the dut, open ccd so repair actions
            # that reset the dut will work (cr50_reboot, cold_reset, warm_reset)
            if host.get_servo().main_device_uses_gsc_drv():
                host.get_servo().set_nocheck('cr50_testlab', 'open')
            # ccd testlab enabled
            return
        raise hosts.AutoservNonCriticalVerifyError(
            'The ccd testlab is disabled; DUT requires manual work '
            'to enable it (go/ccd-setup).')

    def _is_applicable(self, host):
        # Only when DUT is running through ccd.
        # TODO(coconutruben): replace with ccd API when available in servo.py
        return host.get_servo() and host.get_servo().main_device_is_ccd()

    @property
    def description(self):
        return 'ccd testlab enabled'

class _CCDPowerDeliveryVerifier(hosts.Verifier):
    """Verifier to check and reset servo_v4_role for servos that support
    power delivery feature(a.k.a power pass through).

    There are currently two position of servo_v4_role, src and snk:
    src --  servo in power delivery mode and passes power to the DUT.
    snk --  servo in normal mode and not passes power to DUT.
    We want to ensure that servo_v4_role is set to src.
    """
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if host.get_servo():
            self._printControl(host.get_servo(), 'ppdut5_mv')
            self._printControl(host.get_servo(), 'ppchg5_mv')
        if host.get_servo().get('servo_pd_role') == 'snk':
            raise hosts.AutoservNonCriticalVerifyError(
                    'Power delivery not in src role.')

    def _printControl(self, servo, control):
        if servo.has_control(control):
            logging.info("%s: %s", control, servo.get(control))

    def _is_applicable(self, host):
        return (host.is_in_lab() and
                host.get_servo().supports_built_in_pd_control())

    @property
    def description(self):
        return 'ensure applicable servo is in "src" mode for power delivery'


class _BaseDUTConnectionVerifier(hosts.Verifier):
    """Verifier to check connection between DUT and servo."""

    # Bus voltage on ppdut5. Value can be:
    # - less than 500 - DUT is likely not connected
    # - between 500 and 4000 - unexpected value
    # - more than 4000 - DUT is likely connected
    MAX_PPDUT5_MV_WHEN_NOT_CONNECTED = 500
    MIN_PPDUT5_MV_WHEN_CONNECTED = 4000

    def _is_usb_hub_connected(self, host):
        """Checking bus voltage on ppdut5.

        Supported only on servo_v4 boards.
        If voltage value is lower than 500 then device is not connected.
        When value higher 4000 means the device is connected. If value
        between 500 and 4000 is not expected and will be marked as connected
        and collected information which DUT has this exception.

        @returns: bool
        """
        logging.debug('Started check by ppdut5_mv:on')
        try:
            val = host.get_servo().get('ppdut5_mv')
            logging.info('ppdut5_mv=%s', val)
            if val < self.MAX_PPDUT5_MV_WHEN_NOT_CONNECTED:
                # servo is not connected to the DUT
                return False
            if val < self.MIN_PPDUT5_MV_WHEN_CONNECTED:
                # is unexpected value.
                # collecting metrics to look case by case
                # TODO(otabek) for analysis b:163845694
                data = host._get_host_metrics_data()
                metrics.Counter('chromeos/autotest/repair/ppdut5_mv_case'
                                ).increment(fields=data)
            # else:
            # servo is physical connected to the DUT
        except Exception as e:
            logging.debug('(Not critical) %s', e)
        return True

    def _is_ribbon_cable_connected(self, host):
        """Check if ribbon cable is connected to the DUT.

        The servo_micro/flex - can be checked by `cold_reset` signal.
        When `cold_reset` is `on` it commonly indicates that the DUT
        is disconnected. To avoid mistake of real signal we try
        switch it off and if is cannot then servo is not connected.

        @returns: bool
        """
        logging.debug('Started check by cold_reset:on')
        try:
            val = host.get_servo().get('cold_reset')
            logging.info('cold_reset=%s', val)
            if val == 'on':
                # If cold_reset has is on can be right signal
                # or caused by missing connection between servo_micro and DUT.
                # if we can switch it to the off then it was signal.
                host.get_servo().set('cold_reset', 'off')
        except error.TestFail:
            logging.debug('Ribbon cable is not connected to the DUT.')
            return False
        except Exception as e:
            logging.debug('(Not critical) %s', e)
        return True

    def _is_dut_power_on(self, host):
        # DUT is running in normal state.
        # if EC not supported by board then we expect error
        try:
            return host.get_servo().get('ec_system_powerstate') == 'S0'
        except Exception as e:
            logging.debug('(Not critical) %s', e)
        return False

    def _is_servo_v4_type_a(self, host):
        return host.is_labstation() and host.get_servo().is_servo_v4_type_a()

    def _is_servo_v4_type_c(self, host):
        return host.is_labstation() and host.get_servo().is_servo_v4_type_c()

    def _is_servo_v3(self, host):
        return not host.is_labstation()


class _DUTConnectionVerifier(_BaseDUTConnectionVerifier):
    """Verifier to check connection Servo to the DUT.

    Servo_v4 type-a connected to the DUT by:
        1) servo_micro - checked by `cold_reset`.
    Servo_v4 type-c connected to the DUT by:
        1) ccd - checked by ppdut5_mv.
    Servo_v3 connected to the DUT by:
        1) legacy servo header - can be checked by `cold_reset`.
    """

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if self._is_servo_v4_type_a(host):
            if not self._is_ribbon_cable_connected(host):
                raise hosts.AutoservVerifyError(
                        'Servo_micro is likely not connected to the DUT.')
        elif self._is_servo_v4_type_c(host):
            if (host.get_servo().supports_built_in_pd_control()
                        and not self._is_usb_hub_connected(host)):
                raise hosts.AutoservVerifyError(
                        'Servo_v4 is likely not connected to the DUT.')
        elif self._is_servo_v3(host):
            if not self._is_ribbon_cable_connected(host):
                raise hosts.AutoservVerifyError(
                        'Servo_v3 is likely not connected to the DUT.')

    @property
    def description(self):
        return 'Ensure the Servo connected to the DUT.'


class _ServoHubConnectionVerifier(_BaseDUTConnectionVerifier):
    """Verifier to check connection ServoHub to DUT.

    Servo_v4 type-a connected to the DUT by:
        1) USB hub - checked by ppdut5_mv.
    """

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if self._is_servo_v4_type_a(host):
            if (self._is_dut_power_on(host)
                        and not self._is_usb_hub_connected(host)):
                raise hosts.AutoservVerifyError(
                        'Servo USB hub is likely not connected to the DUT.')

    def _is_applicable(self, host):
        if host.is_ec_supported():
            return True
        logging.info('Host does not support EC.')
        return False

    @property
    def description(self):
        return 'Ensure the Servo HUB connected to the DUT.'


class _BaseCr50SBUVerifier(_BaseDUTConnectionVerifier):
    """Check servod issue related to SBU voltage."""

    # Min SBU voltage to detect usb-device
    SBU_THRESHOLD = 2500.0
    # How many times collect SBU voltage to calc AVG value.
    _TOTAL_CHECK_SBU_VOLTAGE = 10

    def _is_applicable(self, host):
        if host.is_localhost():
            logging.info('Target servo is not in a lab,'
                         ' action is not applicable.')
            return False
        if not self._is_servo_v4_type_c(host):
            logging.info('Check support only servo-v4 (type-c),'
                         ' action is not applicable.')
            return False
        return True

    def _is_sbu_voltage_issue(self, host):
        """Check if servo does not detected by SBU voltage issue."""
        command = 'dut_sbu_voltage_float_fault'
        if host.get_servo().has_control(command):
            if host.get_servo().get(command) == 'on':
                return True
        return False

    def _get_max_sbu_value(self, host):
        """Get average voltage on SBU lines."""
        servo = host.get_servo()
        if not servo.has_control('servo_dut_sbu1_mv'):
            return -1
        s1 = 0
        s2 = 0
        for i in range(self._TOTAL_CHECK_SBU_VOLTAGE):
            try:
                sbu1 = int(servo.get('servo_dut_sbu1_mv'))
                sbu2 = int(servo.get('servo_dut_sbu2_mv'))
                logging.debug('Attempt:%2d, sbu1 %4d sbu2 %4d', i, sbu1, sbu2)
                s1 += sbu1
                s2 += sbu2
            except error.TestFail as e:
                # This is a nice to have but if reading this fails, it
                # shouldn't interfere with the test.
                logging.exception(e)
        logging.debug('Total:  sbu1 %4d sbu2 %4d', s1, s2)
        # Use float to get values with changes
        s1 = s1 / float(self._TOTAL_CHECK_SBU_VOLTAGE)
        s2 = s2 / float(self._TOTAL_CHECK_SBU_VOLTAGE)
        logging.debug('Avg: sbu1 %7.2f sbu2 %7.2f', s1, s2)
        max_sbu = max(s1, s2)
        logging.info('Max sbu: %7.2f', max_sbu)
        return max_sbu


class _Cr50OffVerifier(_BaseCr50SBUVerifier):
    """Check if CR50 is in deep sleep and fail to detected.

    If SBU voltage is higher threshold but still cannot be detected
    as usb device then probably CR50 is in deep sleep.
    Threshold is 2500 mV on any SBU lines.
    """

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if self._is_sbu_voltage_issue(host):
            if self._get_max_sbu_value(host) > self.SBU_THRESHOLD:
                raise hosts.AutoservVerifyError(
                        'CR50 voltage detected but usb device not enumerated')

    @property
    def description(self):
        return 'CR50 voltage detected but not enumerated.'


class _Cr50LowSBUVerifier(_BaseCr50SBUVerifier):
    """Check if servod fail to detect CR50 due low voltage.

    CR50 cannot be enumerated as SBU voltage line lower then
    threshold.
    Threshold is 2500 mV on any SBU lines.
    """

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if self._is_sbu_voltage_issue(host):
            v = self._get_max_sbu_value(host)
            if v > 1 and v <= self.SBU_THRESHOLD:
                raise hosts.AutoservVerifyError(
                        'Cr50 is not detected due to SBU voltages'
                        ' being below %dmV' % self.SBU_THRESHOLD)

    @property
    def description(self):
        return 'Cr50 not detected as both SBU voltages are below threshold.'


class _TopologyVerifier(hosts.Verifier):
    """Verifier that all servo component is presented."""

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        topology = host.get_topology()
        topology.read(host.get_dut_host_info())
        try:
            # Linux takes 1 second to detect and enumerate USB device since
            # 2010 year. We take 10 seconds to be sure as old standard was
            # 5 seconds.
            time.sleep(10)
            topology.validate(raise_error=True,
                              dual_set=host.is_dual_setup(),
                              compare=True)
        except servo_topology.ServoTopologyError as e:
            six.reraise(hosts.AutoservVerifyError,
                        hosts.AutoservVerifyError(e),
                        sys.exc_info()[2])

    def _is_applicable(self, host):
        if host.is_localhost():
            logging.info('Target servo is not in a lab,'
                         ' action is not applicable.')
            return False
        if not host.is_servo_topology_supported():
            logging.info('Target servo-topology is not supported,'
                         ' action is not applicable.')
            return False
        return True

    @property
    def description(self):
        return 'Ensure all Servo component present.'


class _PowerButtonVerifier(hosts.Verifier):
    """
    Verifier to check the `pwr_button` signal.

    Tests that the `pwr_button` signal shows the power button has been
    released.  When `pwr_button` is stuck at `press`, it commonly
    indicates that the ribbon cable is disconnected.
    """
    # TODO (crbug.com/646593) - Remove list below once servo has been updated
    # with a fake pwr_button signal.
    _BOARDS_WO_PWR_BUTTON = ['arkham', 'gale', 'mistral', 'storm', 'whirlwind']

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.SHORT_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if host.servo_board in self._BOARDS_WO_PWR_BUTTON:
            return
        try:
            button = host.get_servo().get('pwr_button')
        except Exception as e:
            six.reraise(hosts.AutoservNonCriticalVerifyError,
                        hosts.AutoservNonCriticalVerifyError(e),
                        sys.exc_info()[2])

        if button != 'release':
            raise hosts.AutoservNonCriticalVerifyError(
                'Check ribbon cable: \'pwr_button\' is stuck')

    def _is_applicable(self, host):
        return (host.get_servo() and host.get_servo().main_device_is_flex())

    @property
    def description(self):
        return 'pwr_button control is normal'


class _BatteryVerifier(hosts.Verifier):
    """Collect battery info for analysis."""

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            servo = host.get_servo()
            charging = False
            if servo.has_control('battery_is_charging'):
                charging = servo.get('battery_is_charging')
            level = -1
            if servo.has_control('battery_charge_percent'):
                level = servo.get('battery_charge_percent')
            design_mah = servo.get('battery_full_design_mah')
            charge_mah = servo.get('battery_full_charge_mah')
            logging.info('Charging: %s', charging)
            logging.info('Percentage: %s', level)
            logging.info('Full charge max: %s', charge_mah)
            logging.info('Full design max: %s', design_mah)
            # based on analysis of ratio we can find out what is
            # the level when we can say that battery is dead
            ratio = int(math.floor(charge_mah / design_mah * 100.0))
            logging.info('Ratio: %s', ratio)
            data = {
                    'board': host.servo_board or 'unknown',
                    'model': host.servo_model or 'unknown',
                    'ratio': ratio
            }
            metrics.Counter('chromeos/autotest/battery/ratio').increment(
                    fields=data)
        except Exception as e:
            # Keeping it with info level because we do not expect it.
            logging.info('(Not critical) %s', e)

    def _is_applicable(self, host):
        if not host.is_ec_supported():
            logging.info('The board not support EC')
            return False
        dut_info = host.get_dut_host_info()
        if dut_info:
            host_info = host.get_dut_host_info()
            if host_info.get_label_value('power') != 'battery':
                logging.info('The board does not have battery')
                return False
        servo = host.get_servo()
        if (not servo.has_control('battery_full_design_mah')
                    or not servo.has_control('battery_full_charge_mah')):
            logging.info('The board is not supported battery controls...')
            return False
        return True

    @property
    def description(self):
        return 'Logs battery levels'


class _LidVerifier(hosts.Verifier):
    """
    Verifier to check the `lid_open` signal.
    """

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.SHORT_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            lid_open = host.get_servo().get('lid_open')
        except Exception as e:
            six.reraise(hosts.AutoservNonCriticalVerifyError,
                        hosts.AutoservNonCriticalVerifyError(e),
                        sys.exc_info()[2])

        if lid_open != 'yes' and lid_open != 'not_applicable':
            raise hosts.AutoservNonCriticalVerifyError(
                'Check lid switch: lid_open is %s' % lid_open)

    @property
    def description(self):
        return 'lid_open control is normal'


class ECConsoleVerifier(hosts.Verifier):
    """
    Verifier response from the EC console.
    """

    COMMAND_TO_CHECK_CONSOLE = (
            'ec_system_powerstate',
            'ec_board',
    )

    @ignore_exception_for_non_cros_host
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if not host.is_ec_supported():
            logging.info('The board does not support EC')
            return

        for command in self.COMMAND_TO_CHECK_CONSOLE:
            if host.get_servo().has_control(command):
                try:
                    # Response of command is not important.
                    r = host.get_servo().get(command)
                    logging.debug('Result %s:%s', command, r)
                    # Exiting as we confirmed that console is working.
                    return
                except Exception as e:
                    logging.error('Fail to read %s control. Error: %s',
                                  command, e)
        # If we reached this point then no command succeeded.
        raise hosts.AutoservNonCriticalVerifyError(
                'EC console is not responding; '
                'may be caused of broken EC firmware')

    @property
    def description(self):
        return 'Check EC console'


class ServodDutControllerMissingVerifier(hosts.Verifier):
    """Verifier to check whether the servod dut controller is missing or not.

    When servod is initializing, it checks if DUT controller is
    missing. If yes,then it sets 'dut_controller_missing_fault' to
    'on', otherwise, to 'off'. Missing controller means servo
    component connected to the DUT is missing, or is not responsive.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        logging.debug('ServodDutControllerMissingVerifier: Starting verifier.')
        if host.get_servo().get('dut_controller_missing_fault') == 'on':
            logging.debug('ServodDutControllerMissingVerifier: DUT Controller missing fault is on.')
            raise hosts.AutoservVerifyError('Servod is missing dut controller')
        else:
            logging.debug('ServodDutControllerMissingVerifier: DUT Controller missing fault is not on.')

    def _is_applicable(self, host):
        if host.is_containerized_servod():
            logging.debug('ServodDutControllerMissingVerifier: Detected containerized servod.')
            logging.info('Servod is running within a container')
            return True
        if not host.is_labstation():
            logging.debug('ServodDutControllerMissingVerifier: Detected non-labstation.')
            logging.info('Not supported for servo_v3.')
            return False
        return host.is_in_lab()

    @property
    def description(self):
        return 'ensure servod does not have missing dut controller'


class _ConnectionVerifier(repair_utils.SshVerifier):
    """
    Ensure the servo host container is up.
    """

    def verify(self, host):
        if host.is_containerized_servod():
            # We need start servod container first before check it-is present
            host.start_containerized_servod()
        return super(_ConnectionVerifier, self).verify(host)

    @property
    def description(self):
        return 'Check the connection to the machine or container running servod.'


class _RestartServod(hosts.RepairAction):
    """Restart `servod` with the proper BOARD setting."""

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        if host.is_containerized_servod():
            logging.debug('Restarting servod container')
        elif not host.is_cros_host():
            raise hosts.AutoservRepairError(
                    'Can\'t restart servod: not running '
                    'embedded ChromeOS.',
                    'servo_not_applicable_to_non_cros_host')
        host.restart_servod()

    @property
    def description(self):
        return 'Start servod with the proper config settings.'


class _ServoRebootRepair(repair_utils.RebootRepair):
    """Try repair servo by reboot servohost.

    This is the same as the standard `RebootRepair`, for servo_v3 it will
    reboot the beaglebone board immediately while for labstation it will
    request a reboot by touch a flag file on its labstation, then
    labstation reboot will be handled by labstation AdminRepair task as
    labstation host multiple servos and need do an synchronized reboot.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        super(_ServoRebootRepair, self).repair(host)
        # restart servod for v3 after reboot.
        host.restart_servod()

    def _is_applicable(self, host):
        if host.is_localhost() or not host.is_cros_host():
            logging.info('Target servo is not in a lab, the reboot repair'
                         ' action is not applicable.')
            return False

        if host.is_labstation():
            host.request_reboot()
            logging.info('Reboot labstation requested, it will be handled'
                         ' by labstation AdminRepair task.')
            return False
        return True

    @property
    def description(self):
        return 'Reboot the servo host.'


class _ToggleCCLineRepair(hosts.RepairAction):
    """Try repair servod by toggle cc.

    When cr50 is not enumerated we can try to recover it by toggle cc line.
    """
    # Timeout for shut down configuration channel.
    CC_OFF_TIMEOUT = 10
    # Timeout for initialize configuration channel.
    CC_ON_TIMEOUT = 30

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        logging.info('Turn off configuration channel and wait 10 seconds.')
        servo_uart_cmd = 'servo_v4_uart_cmd'
        if not host.get_servo().has_control(servo_uart_cmd):
            servo_uart_cmd = 'servo_v4p1_uart_cmd'
        host.get_servo().set_nocheck(servo_uart_cmd, 'cc off')
        # wait till command will be effected
        time.sleep(self.CC_OFF_TIMEOUT)

        logging.info('Turn on configuration channel and wait 30 seconds.')
        # alternative option to turn line on is by `cc srcdts`
        host.get_servo().set_nocheck('servo_pd_role', 'src')
        host.get_servo().set_nocheck('servo_dts_mode', 'on')
        # wait till command will be effected
        time.sleep(self.CC_ON_TIMEOUT)
        host.restart_servod()

    def _is_applicable(self, host):
        if host.is_localhost():
            logging.debug('Not supported for localhost.')
            return False
        if not host.servo_serial:
            logging.debug('Servod does not have serial.')
            return False
        if not host.servo_recovery:
            logging.debug('Servod is not running in recovery mode.')
            return False
        if not (host.is_labstation() or host.is_containerized_servod()):
            logging.debug('Not supported for servo_v3.')
            return False
        if not host.get_servo():
            logging.debug('Servo is not initialized.')
            return False
        return self._is_type_c(host)

    def _is_type_c(self, host):
        if host.get_dut_host_info():
            servo_type = host.get_dut_host_info().get_label_value(
                    servo_constants.SERVO_TYPE_LABEL_PREFIX)
            return 'ccd' in servo_type
        return False

    @property
    def description(self):
        return 'Toggle cc lines'


class _FakedisconnectRepair(hosts.RepairAction):
    """Try repair servod by mimic reconnection of servo.

    When cr50 is not enumerated as we can try to recover it by reconnect to DUT.
    """
    # Delay to disconnect.
    DISC_DELAY_MS = 100
    # Timeout to wait to restore the connection.
    DISC_TIMEOUT_MS = 2000
    # Timeout to wait to execute the command and apply effect.
    EXEC_TIMEOUT = (DISC_DELAY_MS + DISC_TIMEOUT_MS) / 1000 + 2

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        disc_cmd = ('fakedisconnect %d %d' %
                    (self.DISC_DELAY_MS, self.DISC_TIMEOUT_MS))
        # cannot use 'set' as control is not returned executed commands
        servo_uart_cmd = 'servo_v4_uart_cmd'
        if not host.get_servo().has_control(servo_uart_cmd):
            servo_uart_cmd = 'servo_v4p1_uart_cmd'
        host.get_servo().set_nocheck(servo_uart_cmd, disc_cmd)
        logging.debug('Waiting %ss for affect of action', self.EXEC_TIMEOUT)
        time.sleep(self.EXEC_TIMEOUT)
        host.restart_servod()

    def _is_applicable(self, host):
        if host.is_localhost():
            logging.debug('Not supported for localhost.')
            return False
        if not host.servo_serial:
            logging.debug('Servod does not have serial.')
            return False
        if not host.servo_recovery:
            logging.debug('Servod is not running in recovery mode.')
            return False
        if not (host.is_labstation() or host.is_containerized_servod()):
            logging.debug('Not supported for servo_v3.')
            return False
        if not host.get_servo():
            logging.debug('Servo is not initialized.')
            return False
        return self._is_type_c(host)

    def _is_type_c(self, host):
        if host.get_dut_host_info():
            servo_type = host.get_dut_host_info().get_label_value(
                    servo_constants.SERVO_TYPE_LABEL_PREFIX)
            return 'ccd' in servo_type
        return False

    @property
    def description(self):
        return 'Fake reconnect to DUT'


class _PowerDeliveryRepair(hosts.RepairAction):
    """Repair to check servo_v4_role for servos that support
    power delivery feature(a.k.a power pass through).

    There are currently two position of servo_v4_role, src and snk:
    src --  servo in power delivery mode and passes power to the DUT.
    snk --  servo in normal mode and not passes power to DUT.
    """
    # How many time retry to set PD in correct mode and verify that is stay.
    # Set 5 as each attempt has 10 attempts inside 'set' method.
    _SET_ATTEMPT_COUNT = 5

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        host.get_servo().set_nocheck('servo_pd_role', 'snk')
        time.sleep(1)
        for x in range(self._SET_ATTEMPT_COUNT):
            logging.debug('Try set servo_v4_role to src.'
                          ' Attempt: %s', x + 1)
            try:
                host.get_servo().set('servo_pd_role', 'src')
                # Waiting a few seconds as it can be change to snk if PD
                # on servo has issue.
                time.sleep(5)
            except BaseException as e:
                logging.debug('Setting PD with retries failed %s', e)
            if host.get_servo().get('servo_pd_role') == 'src':
                break
        if host.get_servo().get('servo_pd_role') == 'snk':
            raise hosts.AutoservNonCriticalVerifyError(
                    'Cannot switch power delivery to the src role')
        # Restart servod to re-initialize servos.
        # In some cases if device did not receive power can block detection
        # of servo components.
        host.restart_servod()

    def _is_type_c(self, host):
        return (host.is_in_lab() and host.get_servo()
                and host.get_servo().supports_built_in_pd_control())

    @property
    def description(self):
        return 'Recover power delivery on servo'


class _ECRebootRepair(hosts.RepairAction):
    """
    Reboot EC on DUT from servo.
    """

    def _is_applicable(self, host):
        return (not host.is_localhost()) and host.is_ec_supported()

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        host.get_servo().ec_reboot()

    @property
    def description(self):
        return 'Reboot EC'


class _DutRebootRepair(hosts.RepairAction):
    """
    Reboot DUT to recover some servo controls depending on EC console.

    Some servo controls, like lid_open, requires communicating with DUT through
    EC UART console. Failure of this kinds of controls can be recovered by
    rebooting the DUT.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        host.get_servo().get_power_state_controller().reset()
        # Get the lid_open value which requires EC console.
        lid_open = host.get_servo().get('lid_open')
        if lid_open != 'yes' and lid_open != 'not_applicable':
            raise hosts.AutoservVerifyError(
                    'Still fail to contact EC console after rebooting DUT')

    @property
    def description(self):
        return 'Reset the DUT via servo'


class _DiskCleanupRepair(hosts.RepairAction):
    """
    Remove old logs/metrics/crash_dumps on servohost to free up disk space.
    """
    KEEP_LOGS_MAX_DAYS = 5

    FILE_TO_REMOVE = [
            '/var/lib/metrics/uma-events', '/var/spool/crash/*',
            '/var/log/chrome/*', '/var/log/ui/*',
            '/home/chronos/BrowserMetrics/*'
    ]

    @timeout_util.TimeoutDecorator(cros_constants.SHORT_REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        if host.is_localhost():
            # we don't want to remove anything from local testing.
            return

        # Remove old servod logs.
        host.run('/usr/bin/find /var/log/servod_* -mtime +%d -print -delete'
                 % self.KEEP_LOGS_MAX_DAYS, ignore_status=True)

        # Remove pre-defined metrics and crash dumps.
        for path in self.FILE_TO_REMOVE:
            host.run('rm %s' % path, ignore_status=True)

    @property
    def description(self):
        return 'Clean up old logs/metrics on servohost to free up disk space.'


class _ServoFwUpdateRepair(hosts.RepairAction):
    """Update firmware for servos.

    We try to update servo 3 times and then try to force update it.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        try:
            servo_updater.update_servo_firmware(host,
                                                try_attempt_count=3,
                                                force_update=False,
                                                try_force_update=True)
        except servo_updater.ServoUpdaterError as er:
            # Catch servo_updater issue to cache it.
            self.servo_updater_issue_detected = True
            raise hosts.AutoservVerifyError('ServoUpdater issue detected')

    def _is_applicable(self, host):
        # Run only for servo_v4 and servo_v4p1.
        return host.is_labstation() or host.is_containerized_servod()

    @property
    def description(self):
        return 'Update servo-fw if required.'


class _ServoMicroFlashRepair(hosts.RepairAction):
    """
    Remove old logs/metrics/crash_dumps on servohost to free up disk space.
    """
    _TARGET_SERVO = 'servo_micro'

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        if not host.is_cros_host():
            raise hosts.AutoservRepairError(
                    'Can\'t restart servod: not running '
                    'embedded ChromeOS.',
                    'servo_not_applicable_to_non_cros_host')
        servo = host.get_servo()
        if not servo or self._TARGET_SERVO not in servo.get_servo_type():
            logging.info("Servo-micro is not present on set-up")
            return

        try:
            servo_updater.update_servo_firmware(host,
                                                boards=(self._TARGET_SERVO, ),
                                                force_update=True,
                                                ignore_version=True)
        except Exception as e:
            logging.debug("(Not critical) Servo device update error: %s", e)
            raise hosts.AutoservVerifyError(
                    'Still fail to contact EC console after rebooting DUT')
        # Update time when we reflashed the fw on the device
        dhp = host.get_dut_health_profile()
        dhp.refresh_servo_miro_fw_update_run_time()
        host.restart_servod()

    def is_time_to_try(self, dhp):
        """Verify that it is time when we can try to re-flash fw on servo_micro.

        Re-flashing limited to once per 2 weeks to avoid over-flashing
        the servo device.
        """
        today_time = int(time.time())
        last_check = dhp.get_servo_micro_fw_update_time_epoch()
        can_run = today_time > (last_check + (14 * 24 * 60 * 60))
        if not can_run:
            logging.info("The servo_micro fw updated in las 2 weeks ago.")
        return can_run

    def _is_applicable(self, host):
        return (not host.is_localhost() and host.get_dut_health_profile()
                and self.is_time_to_try(host.get_dut_health_profile()))

    @property
    def description(self):
        return 'Re-flash servo_micro firmware.'


def _servo_verifier_actions():
    """
    Return a verifiers for a `ServoHost`.
    """
    return (
            (_ConnectionVerifier, 'connection', []),
            (_RootServoPresentVerifier, 'servo_root_present', ['connection']),
            (_RootServoV3PresentVerifier, 'servo_v3_root_present',
             ['connection']),
            (_ServoFwVerifier, 'servo_fw', ['servo_root_present']),
            (_StartServodVerifier, 'start_servod',
             ['servo_fw', 'servo_v3_root_present']),
            (_DiskSpaceVerifier, 'servo_disk_space', ['connection']),
            (_UpdateVerifier, 'servo_update', ['servo_v3_root_present']),
            (_BoardConfigVerifier, 'servo_config_board', ['connection']),
            (_SerialConfigVerifier, 'servo_config_serial', ['connection']),
            (_ServodJobVerifier, 'servod_started', [
                    'start_servod', 'servo_config_board',
                    'servo_config_serial', 'servo_disk_space'
            ]),
            (_ServodEchoVerifier, 'servod_echo', ['servod_started']),
            (_TopologyVerifier, 'servo_topology', ['servod_echo']),
            (_ServodConnectionVerifier, 'servod_connection', ['servod_echo']),
            (_Cr50LowSBUVerifier, 'servo_cr50_low_sbu', ['servod_connection']),
            (ServodDutControllerMissingVerifier,
             'servod_dut_controller_missing', ['servod_connection']),
            (_Cr50OffVerifier, 'servo_cr50_off', ['servod_connection']),
            (_ServodControlVerifier, 'servod_control', ['servod_connection']),
            (_DUTConnectionVerifier, 'servo_dut_connected',
             ['servod_connection']),
            (_ServoHubConnectionVerifier, 'servo_hub_connected',
             ['servo_dut_connected']),
            (_PowerButtonVerifier, 'servo_pwr_button', ['servo_hub_connected'
                                                        ]),
            (_BatteryVerifier, 'servo_battery', ['servo_hub_connected']),
            (_LidVerifier, 'servo_lid_open', ['servo_hub_connected']),
            (ECConsoleVerifier, 'servo_ec_console', ['servo_dut_connected']),
            (_Cr50ConsoleVerifier, 'servo_cr50_console',
             ['servo_dut_connected']),
            (_CCDTestlabVerifier, 'servo_ccd_testlab', ['servo_cr50_console']),
            (_CCDPowerDeliveryVerifier, 'servo_power_delivery',
             ['servod_connection']),
    )


def _servo_repair_actions():
    """
    Return a `RepairStrategy` for a `ServoHost`.
    """
    config = ['servo_config_board', 'servo_config_serial', 'start_servod']
    base_triggers = [
            'servod_started', 'servo_topology', 'servod_connection',
            'servod_echo', 'servod_control', 'servo_dut_connected',
            'servo_hub_connected', 'servo_pwr_button', 'servo_cr50_console',
            'servo_cr50_low_sbu', 'servo_cr50_off', 'servo_power_delivery',
            'servod_dut_controller_missing'
    ]
    dut_triggers = [
            'servod_control', 'servo_lid_open', 'servo_ec_console',
            'servo_topology', 'servo_dut_connected', 'servo_hub_connected',
            'servo_cr50_low_sbu', 'servo_cr50_off', 'servo_cr50_console',
            'servo_power_delivery', 'servod_dut_controller_missing'
    ]
    reboot_triggers = [
            'servo_topology', 'servo_root_present', 'servo_disk_space',
            'servo_power_delivery'
    ]
    return (
            (_ServoFwUpdateRepair, 'servo_fw_update', ['connection'],
             ['servo_fw']),
            (_DiskCleanupRepair, 'servo_disk_cleanup', ['connection'],
             ['servo_disk_space']),
            (_ServoMicroFlashRepair, 'servo_micro_flash',
             ['connection', 'servo_topology'], ['servo_dut_connected']),
            (_RestartServod, 'servod_restart', ['connection', 'servo_fw'],
             config + base_triggers),
            (_ServoRebootRepair, 'servo_reboot', ['connection'],
             reboot_triggers),
            (_PowerDeliveryRepair, 'servo_pd_recover', ['servod_connection'],
             base_triggers),
            (_FakedisconnectRepair, 'servo_fakedisconnect',
             ['servod_connection'], base_triggers),
            (_ToggleCCLineRepair, 'servo_cc', ['servod_connection'],
             base_triggers),
            (_DutRebootRepair, 'servo_dut_reboot', ['servod_connection'],
             dut_triggers),
            (_ECRebootRepair, 'servo_ec_reboot', ['servod_connection'],
             dut_triggers),
    )


def create_servo_repair_strategy():
    """
    Return a `RepairStrategy` for a `ServoHost`.
    """
    return hosts.RepairStrategy(_servo_verifier_actions(),
                                _servo_repair_actions(), 'servo')
