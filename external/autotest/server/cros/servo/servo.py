# Lint as: python2, python3
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
#
# Expects to be run in an environment with sudo and no interactive password
# prompt, such as within the Chromium OS development chroot.

import ast
import logging
import os
import re
import six
import six.moves.xmlrpc_client
import six.moves.http_client
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import lsbrelease_utils
from autotest_lib.client.common_lib import seven
from autotest_lib.server import utils as server_utils
from autotest_lib.server.cros.servo import firmware_programmer
from autotest_lib.server.cros.faft.utils.config import Config as FAFTConfig


# Regex to match XMLRPC errors due to a servod control not existing.
# Servod uses both 'No control %s' and 'No control named %s' in exceptions.
NO_CONTROL_RE = re.compile(r'No control(?: named)? (?P<name>\w*\.?\w*)')

# Please see servo/drv/pty_driver.py for error messages to match.

# This common prefix can apply to all subtypes of console errors.
# The first portion is an optional qualifier of the type
# of error that occurred. Each error is or'd.
CONSOLE_COMMON_RE = (r'((Timeout waiting for response|'
                     r'Known error [\w\'\".\s]+). )?'
                     # The second portion is an optional name for the console
                     # source
                     r'(\w+\: )?')

# Regex to match XMLRPC errors due to a console being unresponsive.
NO_CONSOLE_OUTPUT_RE = re.compile(r'%sNo data was sent from the pty\.' %
                                  CONSOLE_COMMON_RE)


# Regex to match XMLRPC errors due to a console control failing, but the
# underlying Console being responsive.
CONSOLE_MISMATCH_RE = re.compile(r'%sThere was output:' % CONSOLE_COMMON_RE)


# The minimum voltage on the charger port on servo v4 that is expected. This is
# to query whether a charger is plugged into servo v4 and thus pd control
# capabilities can be used.
V4_CHG_ATTACHED_MIN_VOLTAGE_MV = 4400


class ControlUnavailableError(error.TestFail):
    """Custom error class to indicate a control is unavailable on servod."""
    pass


class ConsoleError(error.TestFail):
    """Common error class for servod console-back control failures."""
    pass


class UnresponsiveConsoleError(ConsoleError):
    """Error for: A console control fails for lack of console output."""
    pass


class ResponsiveConsoleError(ConsoleError):
    """Error for: A console control fails but console is responsive."""
    pass


class ServodBadResponse(six.moves.http_client.BadStatusLine):
    """Indicates a bad HTTP response from servod"""

    def __init__(self, when, line):
        """

        @param when: Description of the operation being performed (get/set)
        @param line: The line that came from the server, often an empty string.
        """
        super(ServodBadResponse, self).__init__(line)
        self.when = when

    def __str__(self):
        """String representation of the exception"""
        return '%s -- StatusLine=%s' % (self.when, self.line)


class ServodEmptyResponse(ServodBadResponse):
    """Indicates an empty response from servod, possibly because it exited."""
    pass


class ServodConnectionError(seven.SOCKET_ERRORS[0]):
    """Indicates socket errors seen during communication with servod"""

    def __init__(self, when, errno, strerror, filename):
        """Instance initializer

        The filename is used to add details to the exception message:
        [Errno 104] Connection reset by peer: "<Servo 'ipaddr:9999'>"

        @param when: Description of the operation being performed at the time
        @param errno: errno value, such as ECONNRESET
        @param strerror: OS-provided description ("connection reset by peer")
        @param filename: Something to report as a path, such as a socket address
        """
        # [Errno 104] [Setting ctrl:val] Connection reset by peer: <Servo...
        self.when = when
        super(ServodConnectionError, self).__init__(errno, strerror, filename)

    def __str__(self):
        """String representation of the exception"""
        msgv = [self.when]
        if self.errno is not None or self.strerror is not None:
            msgv.append('--')
        if self.errno is not None:
            msgv.append('[Errno %d]' % self.errno)
        if self.strerror is not None:
            msgv.append(self.strerror)
        return '%s: %r' % (' '.join(msgv), self.filename)


# TODO: once in python 3, inherit from AbstractContextManager
class _WrapServoErrors(object):
    """
    Wrap an operation, replacing BadStatusLine and socket.error with
    servo-specific versions, and extracting exception info from xmlrplib.Fault.

    @param servo_name: The servo object, used to add the servo name to errors.
                       See the ServodConnectionError docstring.
    @param description:  String to use when describing what was being done
    @raise ServodBadStatusLine: if exception is a httplib.BadStatusLine
    @raise ServodSocketError: if exception is a socket.error
    @raise ControlUnavailableError: if Fault matches NO_CONTROL_RE
    @raise UnresponsiveConsoleError: if Fault matches NO_CONSOLE_OUTPUT_RE
    @raise ResponsiveConsoleError: if Fault matches CONSOLE_MISMATCH_RE
    """

    def __init__(self, servo, description):
        self.servo_name = str(servo)
        self.description = description

    @staticmethod
    def _get_xmlrpclib_exception(xmlexc):
        """Get meaningful exception string from xmlrpc.

        Args:
            xmlexc: xmlrpclib.Fault object

        xmlrpclib.Fault.faultString has the following format:

        <type 'exception type'>:'actual error message'

        Parse and return the real exception from the servod side instead of the
        less meaningful one like,
           <Fault 1: "<type 'exceptions.AttributeError'>:'tca6416' object has no
           attribute 'hw_driver'">

        Returns:
            string of underlying exception raised in servod.
        """
        return re.sub('^.*>:', '', xmlexc.faultString)

    @staticmethod
    def _log_exception(exc_type, exc_val, exc_tb):
        """Log exception information"""
        if exc_val is not None:
            logging.debug(
                    'Wrapped exception:', exc_info=(exc_type, exc_val, exc_tb))

    def __enter__(self):
        """Enter the context"""
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """Exit the context, handling the exception if there was one"""
        try:
            if isinstance(exc_val, six.moves.http_client.BadStatusLine):
                self._log_exception(exc_type, exc_val, exc_tb)
                if exc_val.line in ('', "''"):
                    err = ServodEmptyResponse(self.description, exc_val.line)
                else:
                    err = ServodBadResponse(self.description, exc_val.line)
                six.reraise(err.__class__, err, exc_tb)

            if isinstance(exc_val, seven.SOCKET_ERRORS):
                self._log_exception(exc_type, exc_val, exc_tb)
                if len(exc_val.args) == 0:
                    errno = None
                    strerror = None
                elif len(exc_val.args) == 1:
                    errno = None
                    strerror = exc_val.args[0]
                else:
                    errno = exc_val.args[0]
                    strerror = exc_val.args[1]
                err = ServodConnectionError(self.description, errno, strerror,
                                            self.servo_name)
                six.reraise(err.__class__, err, exc_tb)

            if isinstance(exc_val, six.moves.xmlrpc_client.Fault):
                err_str = self._get_xmlrpclib_exception(exc_val)
                err_msg = '%s :: %s' % (self.description, err_str)
                unknown_ctrl = re.search(NO_CONTROL_RE, err_str)
                if not unknown_ctrl:
                    # Log the full text for errors, except unavailable controls.
                    self._log_exception(exc_type, exc_val, exc_tb)
                    logging.debug(err_msg)
                if unknown_ctrl:
                    # The error message for unavailable controls is huge, since
                    # it reports all known controls.  Don't log the full text.
                    unknown_ctrl_name = unknown_ctrl.group('name')
                    logging.debug('%s :: No control named %r',
                                  self.description, unknown_ctrl_name)
                    err = ControlUnavailableError(
                            'No control named %r' % unknown_ctrl_name)
                elif re.search(NO_CONSOLE_OUTPUT_RE, err_str):
                    err = UnresponsiveConsoleError(
                            'Console not printing output. %s.' %
                            self.description)
                elif re.search(CONSOLE_MISMATCH_RE, err_str):
                    err = ResponsiveConsoleError(
                            'Control failed but console alive. %s.' %
                            self.description)
                else:
                    err = error.TestFail(err_msg)
                six.reraise(err.__class__, err, exc_tb)
        finally:
            del exc_tb


def _extract_image_from_tarball(tarball, dest_dir, image_candidates, timeout):
    """Try extracting the image_candidates from the tarball.

    @param tarball: The path of the tarball.
    @param dest_path: The path of the destination.
    @param image_candidates: A tuple of the paths of image candidates.
    @param timeout: Time to wait in seconds before timing out.

    @return: The first path from the image candidates, which succeeds, or None
             if all the image candidates fail.
    """

    # Create the firmware_name subdirectory if it doesn't exist
    if not os.path.exists(dest_dir):
        os.mkdir(dest_dir)

    # Generate a list of all tarball files
    stdout = server_utils.system_output('tar tf %s' % tarball,
                                        timeout=timeout,
                                        ignore_status=True,
                                        args=image_candidates)
    tarball_files = stdout.splitlines()

    # Check if image candidates are in the list of tarball files
    for image in image_candidates:
        logging.debug("Trying to extract %s (autotest)", image)
        if image in tarball_files:
            # Extract and return the first image candidate found
            tar_cmd = 'tar xf %s -C %s %s' % (tarball, dest_dir, image)
            status = server_utils.system(tar_cmd,
                                         timeout=timeout,
                                         ignore_status=True)
            if status == 0:
                return image
    return None


class _PowerStateController(object):

    """Class to provide board-specific power operations.

    This class is responsible for "power on" and "power off"
    operations that can operate without making assumptions in
    advance about board state.  It offers an interface that
    abstracts out the different sequences required for different
    board types.

    """
    # Constants acceptable to be passed for the `rec_mode` parameter
    # to power_on().
    #
    # REC_ON:  Boot the DUT in recovery mode, i.e. boot from USB or
    #   SD card.
    # REC_OFF:  Boot in normal mode, i.e. boot from internal storage.

    REC_ON = 'rec'
    REC_OFF = 'on'
    REC_ON_FORCE_MRC = 'rec_force_mrc'

    # Delay in seconds needed between asserting and de-asserting
    # warm reset.
    _RESET_HOLD_TIME = 0.5


    def __init__(self, servo):
        """Initialize the power state control.

        @param servo Servo object providing the underlying `set` and `get`
                     methods for the target controls.

        """
        self._servo = servo
        self.supported = self._servo.has_control('power_state')
        self.last_rec_mode = self.REC_OFF
        if not self.supported:
            logging.info('Servo setup does not support power-state operations. '
                         'All power-state calls will lead to error.TestFail')

    def _check_supported(self):
        """Throw an error if dts mode control is not supported."""
        if not self.supported:
            raise error.TestFail('power_state controls not supported')

    def reset(self):
        """Force the DUT to reset.

        The DUT is guaranteed to be on at the end of this call,
        regardless of its previous state, provided that there is
        working OS software. This also guarantees that the EC has
        been restarted.

        """
        self._check_supported()
        self._servo.set_nocheck('power_state', 'reset')

    def cr50_reset(self):
        """Force the DUT to reset.

        The DUT is guaranteed to be on at the end of this call,
        regardless of its previous state, provided that there is
        working OS software. This also guarantees that the EC has
        been restarted. Works only for ccd connections.

        """
        self._check_supported()
        self._servo.set_nocheck('power_state', 'cr50_reset')

    def warm_reset(self):
        """Apply warm reset to the DUT.

        This asserts, then de-asserts the 'warm_reset' signal.
        Generally, this causes the board to restart.

        """
        # TODO: warm_reset support has added to power_state.py. Once it
        # available to labstation remove fallback method.
        self._check_supported()
        try:
            self._servo.set_nocheck('power_state', 'warm_reset')
        except error.TestFail as err:
            logging.info("Fallback to warm_reset control method")
            self._servo.set_get_all(['warm_reset:on',
                                 'sleep:%.4f' % self._RESET_HOLD_TIME,
                                 'warm_reset:off'])

    def power_off(self):
        """Force the DUT to power off.

        The DUT is guaranteed to be off at the end of this call,
        regardless of its previous state, provided that there is
        working EC and boot firmware.  There is no requirement for
        working OS software.

        """
        self._check_supported()
        self._servo.set_nocheck('power_state', 'off')

    def power_on(self, rec_mode=REC_OFF):
        """Force the DUT to power on.

        Prior to calling this function, the DUT must be powered off,
        e.g. with a call to `power_off()`.

        At power on, recovery mode is set as specified by the
        corresponding argument.  When booting with recovery mode on, it
        is the caller's responsibility to unplug/plug in a bootable
        external storage device.

        If the DUT requires a delay after powering on but before
        processing inputs such as USB stick insertion, the delay is
        handled by this method; the caller is not responsible for such
        delays.

        @param rec_mode Setting of recovery mode to be applied at
                        power on. default: REC_OFF aka 'off'

        """
        self._check_supported()
        self._servo.set_nocheck('power_state', rec_mode)
        self.last_rec_mode = rec_mode

    def retry_power_on(self):
        """Retry powering on the DUT.

        After power_on(...) the system might not come up reliably, although
        the reasons aren't known yet. This function retries turning on the
        system again, trying to bring it in the last state that power_on()
        attempted to reach.
        """
        self._check_supported()
        self._servo.set_nocheck('power_state', self.last_rec_mode)


class _Uart(object):
    """Class to capture UART streams of CPU, EC, Cr50, etc."""
    _UartToCapture = ('cpu', 'cr50', 'ec', 'servo_micro', 'servo_v4', 'usbpd',
                      'ccd_cr50.ec', 'ccd_cr50.cpu', 'ccd_cr50.cr50'
                      'ccd_gsc.ec', 'ccd_gsc.cpu', 'ccd_gsc.cr50')


    def __init__(self, servo):
        self._servo = servo
        self._streams = []
        self.logs_dir = None

    def _start_stop_capture(self, uart, start):
        """Helper function to start/stop capturing on specified UART.

        @param uart:  The UART name to start/stop capturing.
        @param start:  True to start capturing, otherwise stop.

        @returns True if the operation completes successfully.
                 False if the UART capturing is not supported or failed due to
                 an error.
        """
        logging.debug('%s capturing %s UART.', 'Start' if start else 'Stop',
                      uart)
        uart_cmd = '%s_uart_capture' % uart
        target_level = 'on' if start else 'off'
        level = None
        if self._servo.has_control(uart_cmd):
            # Do our own implementation of set() here as not_applicable
            # should also count as a valid control.
            logging.debug('Trying to set %s to %s.', uart_cmd, target_level)
            try:
                self._servo.set_nocheck(uart_cmd, target_level)
                level = self._servo.get(uart_cmd)
            except error.TestFail as e:
                # Any sort of test failure here should not stop the test. This
                # is just to capture more output. Log and move on.
                logging.warning('Failed to set %s to %s. %s. Ignoring.',
                                uart_cmd, target_level, str(e))
            if level == target_level:
                logging.debug('Managed to set %s to %s.', uart_cmd, level)
            else:
                logging.debug('Failed to set %s to %s. Got %s.', uart_cmd,
                              target_level, level)
        return level == target_level

    def start_capture(self):
        """Start capturing UART streams."""
        for uart in self._UartToCapture:
            # Always try to start the uart. Only add it to _streams if it's not
            # in the list.
            if (self._start_stop_capture(uart, True)
                        and uart not in self._streams):
                self._streams.append(uart)

    def get_logfile(self, uart):
        """Return the path to the uart logfile or none if logs_dir isn't set."""
        if not self.logs_dir:
            return None
        return os.path.join(self.logs_dir, '%s_uart.txt' % uart)

    def dump(self):
        """Dump UART streams to log files accordingly."""
        if not self.logs_dir:
            return

        for uart in self._streams:
            logfile_fullname = self.get_logfile(uart)
            stream = '%s_uart_stream' % uart
            try:
                content = self._servo.get(stream)
            except Exception as err:
                logging.warning('Failed to get UART log for %s: %s', stream, err)
                continue

            if content == 'not_applicable':
                logging.warning('%s is not applicable', stream)
                continue

            # The UART stream may contain non-printable characters, and servo
            # returns it in string representation. We use `ast.leteral_eval`
            # to revert it back.
            with open(logfile_fullname, 'a') as fd:
                try:
                    fd.write(ast.literal_eval(content))
                except ValueError:
                    logging.exception('Invalid value for %s: %r', stream,
                                      content)

    def stop_capture(self):
        """Stop capturing UART streams."""
        for uart in self._UartToCapture:
            try:
                self._start_stop_capture(uart, False)
            except Exception as err:
                logging.warning('Failed to stop UART logging for %s: %s', uart,
                             err)


class Servo(object):

    """Manages control of a Servo board.

    Servo is a board developed by hardware group to aide in the debug and
    control of various partner devices. Servo's features include the simulation
    of pressing the power button, closing the lid, and pressing Ctrl-d. This
    class manages setting up and communicating with a servo demon (servod)
    process. It provides both high-level functions for common servo tasks and
    low-level functions for directly setting and reading gpios.

    """

    # Power button press delays in seconds.
    #
    # The EC specification says that 8.0 seconds should be enough
    # for the long power press.  However, some platforms need a bit
    # more time.  Empirical testing has found these requirements:
    #   Alex: 8.2 seconds
    #   ZGB:  8.5 seconds
    # The actual value is set to the largest known necessary value.
    #
    # TODO(jrbarnette) Being generous is the right thing to do for
    # existing platforms, but if this code is to be used for
    # qualification of new hardware, we should be less generous.
    SHORT_DELAY = 0.1

    # Maximum number of times to re-read power button on release.
    GET_RETRY_MAX = 10

    # Delays to deal with DUT state transitions.
    SLEEP_DELAY = 6
    BOOT_DELAY = 10

    # Default minimum time interval between 'press' and 'release'
    # keyboard events.
    SERVO_KEY_PRESS_DELAY = 0.1

    # Time to toggle recovery switch on and off.
    REC_TOGGLE_DELAY = 0.1

    # Time to toggle development switch on and off.
    DEV_TOGGLE_DELAY = 0.1

    # Time between an usb disk plugged-in and detected in the system.
    USB_DETECTION_DELAY = 5

    # Time to wait before timing out on servo initialization.
    INIT_TIMEOUT_SECS = 10

    # Time to wait before timing out when extracting firmware images.
    #
    # This was increased from 60 seconds to support boards with very
    # large (>500MB) firmware archives taking longer than expected to
    # extract firmware on the lab host machines (b/149419503).
    EXTRACT_TIMEOUT_SECS = 900

    # The VBUS voltage threshold used to detect if VBUS is supplied
    VBUS_THRESHOLD = 3000.0

    # List of servos that connect to a debug header on the board.
    FLEX_SERVOS = ['c2d2', 'servo_micro', 'servo_v3']

    # List of servos that rely on gsc commands for some part of dut control.
    GSC_DRV_SERVOS = ['c2d2', 'ccd_gsc', 'ccd_cr50']

    CCD_PREFIX = 'ccd_'

    def __init__(self, servo_host, servo_serial=None, delay_init=False):
        """Sets up the servo communication infrastructure.

        @param servo_host: A ServoHost object representing
                           the host running servod.
        @type servo_host: autotest_lib.server.hosts.servo_host.ServoHost
        @param servo_serial: Serial number of the servo board.
        @param delay_init:  Delay cache servo_type and power_state to prevent
                            attempt to connect to the servod.
        """
        # TODO(fdeng): crbug.com/298379
        # We should move servo_host object out of servo object
        # to minimize the dependencies on the rest of Autotest.
        self._servo_host = servo_host
        self._servo_serial = servo_serial
        self._servo_type = None
        self._power_state = None
        self._programmer = None
        self._prev_log_inode = None
        self._prev_log_size = 0
        self._ccd_watchdog_disabled = False
        if not delay_init:
            self._servo_type = self.get_servo_version()
            self._power_state = _PowerStateController(self)
        self._uart = _Uart(self)

    def __str__(self):
        """Description of this object and address, for use in errors"""
        return "<%s '%s:%s'>" % (
                type(self).__name__,
                self._servo_host.hostname,
                self._servo_host.servo_port)

    @property
    def _server(self):
        with _WrapServoErrors(
                servo=self, description='get_servod_server_proxy()'):
            return self._servo_host.get_servod_server_proxy()

    @property
    def servo_serial(self):
        """Returns the serial number of the servo board."""
        return self._servo_serial

    def get_power_state_controller(self):
        """Return the power state controller for this Servo.

        The power state controller provides board-independent
        interfaces for reset, power-on, power-off operations.

        """
        if self._power_state is None:
            self._power_state = _PowerStateController(self)
        return self._power_state


    def initialize_dut(self, cold_reset=False, enable_main=True):
        """Initializes a dut for testing purposes.

        This sets various servo signals back to default values
        appropriate for the target board.  By default, if the DUT
        is already on, it stays on.  If the DUT is powered off
        before initialization, its state afterward is unspecified.

        Rationale:  Basic initialization of servo sets the lid open,
        when there is a lid.  This operation won't affect powered on
        units; however, setting the lid open may power on a unit
        that's off, depending on the board type and previous state
        of the device.

        If `cold_reset` is a true value, the DUT and its EC will be
        reset, and the DUT rebooted in normal mode.

        @param cold_reset If True, cold reset the device after
                          initialization.
        @param enable_main If True, make sure the main servo device has
                           control of the dut.

        """
        if enable_main:
            self.enable_main_servo_device()

        with _WrapServoErrors(
                servo=self, description='initialize_dut()->hwinit()'):
            self._server.hwinit()
        if self.has_control('usb_mux_oe1'):
            self.set('usb_mux_oe1', 'on')
            self.switch_usbkey('off')
        else:
            logging.warning('Servod command \'usb_mux_oe1\' is not available. '
                            'Any USB drive related servo routines will fail.')
        # Create a record of SBU voltages if this is running support servo (v4,
        # v4p1).
        # TODO(coconutruben): eventually, replace this with a metric to track
        # SBU voltages wrt servo-hw/dut-hw
        if self.has_control('servo_dut_sbu1_mv'):
            # Attempt to take a reading of sbu1 and sbu2 multiple times to
            # account for situations where the two lines exchange hi/lo roles
            # frequently.
            for i in range(10):
                try:
                    sbu1 = int(self.get('servo_dut_sbu1_mv'))
                    sbu2 = int(self.get('servo_dut_sbu2_mv'))
                    logging.info('attempt %d sbu1 %d sbu2 %d', i, sbu1, sbu2)
                except error.TestFail as e:
                    # This is a nice to have but if reading this fails, it
                    # shouldn't interfere with the test.
                    logging.exception(e)
        self._uart.start_capture()
        # Run testlab open if servo relies on ccd to control the dut.
        if self.main_device_uses_gsc_drv():
            self.set_nocheck('cr50_testlab', 'open')
        if cold_reset:
            if not self.get_power_state_controller().supported:
                logging.info('Cold-reset for DUT requested, but servo '
                             'setup does not support power_state. Skipping.')
            else:
                self.get_power_state_controller().reset()
        with _WrapServoErrors(
                servo=self, description='initialize_dut()->get_version()'):
            version = self._server.get_version()
        logging.debug('Servo initialized, version is %s', version)


    def is_localhost(self):
        """Is the servod hosted locally?

        Returns:
          True if local hosted; otherwise, False.
        """
        return self._servo_host.is_localhost()


    def get_os_version(self):
        """Returns the chromeos release version."""
        lsb_release_content = self.system_output('cat /etc/lsb-release',
                                                 ignore_status=True)
        return lsbrelease_utils.get_chromeos_release_builder_path(
                    lsb_release_content=lsb_release_content)


    def get_servod_version(self):
        """Returns the servod version."""
        # TODO: use system_output once servod --sversion prints to stdout
        try:
            result = self._servo_host.run('servod --sversion 2>&1')
        except error.AutoservRunError as e:
            if 'command execution error' in str(e):
                # Fall back to version if sversion is not supported yet.
                result = self._servo_host.run('servod --version 2>&1')
                return result.stdout.strip() or result.stderr.strip()
            # An actually unexpected error occurred, just raise.
            raise e
        sversion = result.stdout or result.stderr
        # The sversion output contains 3 lines:
        # servod v1.0.816-ff8e966 // the extended version with git hash
        # 2020-04-08 01:10:29 // the time of the latest commit
        # chromeos-ci-legacy-us-central1-b-x32-55-u8zc // builder information
        # For debugging purposes, we mainly care about the version, and the
        # timestamp.
        if type(sversion) == type(b' '):
            sversion = sversion.decode("utf-8")
        return ' '.join(sversion.split()[1:4])


    def power_long_press(self):
        """Simulate a long power button press."""
        # After a long power press, the EC may ignore the next power
        # button press (at least on Alex).  To guarantee that this
        # won't happen, we need to allow the EC one second to
        # collect itself.
        # long_press is defined as 8.5s in servod
        self.power_key('long_press')


    def power_normal_press(self):
        """Simulate a normal power button press."""
        # press is defined as 1.2s in servod
        self.power_key('press')


    def power_short_press(self):
        """Simulate a short power button press."""
        # tab is defined as 0.2s in servod
        self.power_key('tab')


    def power_key(self, press_secs='tab'):
        """Simulate a power button press.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        # TODO(b/224804060): use the power_key control for all servo types when
        # c2d2 has a defined power_key driver.
        if 'c2d2' not in self.get_servo_type():
            self.set_nocheck('power_key', press_secs)
            return
        if isinstance(press_secs, str):
            if press_secs == 'tab':
                press_secs = 0.2
            elif press_secs == 'press':
                press_secs = 1.2
            elif press_secs == 'long_press':
                press_secs = 8.5
            else:
                raise error.TestError('Invalid press %r' % press_secs)
        logging.info('Manual power button press for %ds', press_secs)
        self.set_nocheck('pwr_button', 'press')
        time.sleep(press_secs)
        self.set_nocheck('pwr_button', 'release')


    def pwr_button(self, action='press'):
        """Simulate a power button press.

        @param action: str; could be press or could be release.
        """
        self.set_nocheck('pwr_button', action)


    def lid_open(self):
        """Simulate opening the lid and raise exception if all attempts fail"""
        self.set('lid_open', 'yes')


    def lid_close(self):
        """Simulate closing the lid and raise exception if all attempts fail

        Waits 6 seconds to ensure the device is fully asleep before returning.
        """
        self.set('lid_open', 'no')
        time.sleep(Servo.SLEEP_DELAY)


    def vbus_power_get(self):
        """Get current vbus_power."""
        return self.get('vbus_power')


    def volume_up(self, timeout=300):
        """Simulate pushing the volume down button.

        @param timeout: Timeout for setting the volume.
        """
        self.set_get_all(['volume_up:yes',
                          'sleep:%.4f' % self.SERVO_KEY_PRESS_DELAY,
                          'volume_up:no'])
        # we need to wait for commands to take effect before moving on
        time_left = float(timeout)
        while time_left > 0.0:
            value = self.get('volume_up')
            if value == 'no':
                return
            time.sleep(self.SHORT_DELAY)
            time_left = time_left - self.SHORT_DELAY
        raise error.TestFail("Failed setting volume_up to no")

    def volume_down(self, timeout=300):
        """Simulate pushing the volume down button.

        @param timeout: Timeout for setting the volume.
        """
        self.set_get_all(['volume_down:yes',
                          'sleep:%.4f' % self.SERVO_KEY_PRESS_DELAY,
                          'volume_down:no'])
        # we need to wait for commands to take effect before moving on
        time_left = float(timeout)
        while time_left > 0.0:
            value = self.get('volume_down')
            if value == 'no':
                return
            time.sleep(self.SHORT_DELAY)
            time_left = time_left - self.SHORT_DELAY
        raise error.TestFail("Failed setting volume_down to no")

    def arrow_up(self, press_secs='tab'):
        """Simulate arrow up key presses.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'.
        """
        # TODO: Remove this check after a lab update to include CL:1913684
        if not self.has_control('arrow_up'):
            logging.warning('Control arrow_up ignored. '
                            'Please update hdctools')
            return
        self.set_nocheck('arrow_up', press_secs)

    def arrow_down(self, press_secs='tab'):
        """Simulate arrow down key presses.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'.
        """
        # TODO: Remove this check after a lab update to include CL:1913684
        if not self.has_control('arrow_down'):
            logging.warning('Control arrow_down ignored. '
                            'Please update hdctools')
            return
        self.set_nocheck('arrow_down', press_secs)

    def ctrl_d(self, press_secs='tab'):
        """Simulate Ctrl-d simultaneous button presses.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('ctrl_d', press_secs)


    def ctrl_r(self, press_secs='tab'):
        """Simulate Ctrl-r simultaneous button presses.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('ctrl_r', press_secs)

    def ctrl_s(self, press_secs='tab'):
        """Simulate Ctrl-s simultaneous button presses.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('ctrl_s', press_secs)


    def ctrl_u(self, press_secs='tab'):
        """Simulate Ctrl-u simultaneous button presses.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('ctrl_u', press_secs)


    def ctrl_enter(self, press_secs='tab'):
        """Simulate Ctrl-enter simultaneous button presses.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('ctrl_enter', press_secs)


    def ctrl_key(self, press_secs='tab'):
        """Simulate Enter key button press.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('ctrl_key', press_secs)


    def enter_key(self, press_secs='tab'):
        """Simulate Enter key button press.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('enter_key', press_secs)


    def refresh_key(self, press_secs='tab'):
        """Simulate Refresh key (F3) button press.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('refresh_key', press_secs)


    def ctrl_refresh_key(self, press_secs='tab'):
        """Simulate Ctrl and Refresh (F3) simultaneous press.

        This key combination is an alternative of Space key.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('ctrl_refresh_key', press_secs)


    def imaginary_key(self, press_secs='tab'):
        """Simulate imaginary key button press.

        Maps to a key that doesn't physically exist.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('imaginary_key', press_secs)


    def sysrq_x(self, press_secs='tab'):
        """Simulate Alt VolumeUp X simulataneous press.

        This key combination is the kernel system request (sysrq) X.

        @param press_secs: int, float, str; time to press key in seconds or
                           known shorthand: 'tab' 'press' 'long_press'
        """
        self.set_nocheck('sysrq_x', press_secs)


    def toggle_recovery_switch(self):
        """Toggle recovery switch on and off."""
        self.enable_recovery_mode()
        time.sleep(self.REC_TOGGLE_DELAY)
        self.disable_recovery_mode()


    def enable_recovery_mode(self):
        """Enable recovery mode on device."""
        self.set('rec_mode', 'on')


    def disable_recovery_mode(self):
        """Disable recovery mode on device."""
        self.set('rec_mode', 'off')


    def toggle_development_switch(self):
        """Toggle development switch on and off."""
        self.enable_development_mode()
        time.sleep(self.DEV_TOGGLE_DELAY)
        self.disable_development_mode()


    def enable_development_mode(self):
        """Enable development mode on device."""
        self.set('dev_mode', 'on')


    def disable_development_mode(self):
        """Disable development mode on device."""
        self.set('dev_mode', 'off')

    def boot_devmode(self):
        """Boot a dev-mode device that is powered off."""
        self.power_short_press()
        self.pass_devmode()


    def pass_devmode(self):
        """Pass through boot screens in dev-mode."""
        time.sleep(Servo.BOOT_DELAY)
        self.ctrl_d()
        time.sleep(Servo.BOOT_DELAY)


    def get_board(self):
        """Get the board connected to servod."""
        with _WrapServoErrors(servo=self, description='get_board()'):
            return self._server.get_board()


    def get_base_board(self):
        """Get the board of the base connected to servod."""
        try:
            with _WrapServoErrors(servo=self, description='get_base_board()'):
                return self._server.get_base_board()
        except six.moves.xmlrpc_client.Fault as e:
            # TODO(waihong): Remove the following compatibility check when
            # the new versions of hdctools are deployed.
            if 'not supported' in str(e):
                logging.warning('The servod is too old that get_base_board '
                                'not supported.')
                return ''
            raise

    def can_set_active_device(self):
        """Returns True if the servo setup supports setting the active device

        Servo can only change the active device if there are multiple devices
        and servo has the active_dut_controller control.
        """
        return ('_and_' in self.get_servo_type()
                and self.has_control('active_dut_controller'))

    def get_active_device_prefix(self):
        """Return ccd_(gsc|cr50) or '' if the main device is active."""
        active_device = ''
        if self.can_set_active_device():
            # If servo v4 is allowing dual_v4 devices, then choose the
            # active device.
            active_device = self.get('active_dut_controller')
            if active_device == self.get_main_servo_device():
                active_device = ''
        return active_device

    def get_ec_board(self):
        """Get the board name from EC."""

        return self.get('ec_board', prefix=self.get_active_device_prefix())

    def get_ec_active_copy(self):
        """Get the active copy of the EC image."""
        return self.get('ec_active_copy')

    def has_control(self, ctrl_name, prefix=''):
        """Query servod server to determine if |ctrl_name| is a valid control.

        @param ctrl_name Name of the control.
        @param prefix: prefix to route control to correct servo device.

        @returns: true if |ctrl_name| is a known control, false otherwise.
        """
        ctrl_name = self._build_ctrl_name(ctrl_name, prefix)
        try:
            # If the control exists, doc() will work.
            with _WrapServoErrors(
                    servo=self,
                    description='has_control(%s)->doc()' % ctrl_name):
                self._server.doc(ctrl_name)
            return True
        except ControlUnavailableError:
            return False

    def _build_ctrl_name(self, ctrl_name, prefix):
        """Helper to build the control name if a prefix is used.

        @param ctrl_name Name of the control.
        @param prefix: prefix to route control to correct servo device.

        @returns: [|prefix|.]ctrl_name depending on whether prefix is non-empty.
        """
        assert ctrl_name
        if prefix:
            return '%s.%s' % (prefix, ctrl_name)
        return ctrl_name

    def get(self, ctrl_name, prefix=''):
        """Get the value of a gpio from Servod.

        @param ctrl_name Name of the control.
        @param prefix: prefix to route control to correct servo device.

        @returns: server response to |ctrl_name| request.

        @raise ControlUnavailableError: if |ctrl_name| not a known control.
        @raise error.TestFail: for all other failures doing get().
        """
        ctrl_name = self._build_ctrl_name(ctrl_name, prefix)
        with _WrapServoErrors(
                servo=self, description='Getting %s' % ctrl_name):
            return self._server.get(ctrl_name)

    def set(self, ctrl_name, ctrl_value, prefix=''):
        """Set and check the value of a gpio using Servod.

        @param ctrl_name: Name of the control.
        @param ctrl_value: New setting for the control.
        @param prefix: prefix to route control to correct servo device.
        @raise error.TestFail: if the control value fails to change.
        """
        ctrl_name = self._build_ctrl_name(ctrl_name, prefix)
        self.set_nocheck(ctrl_name, ctrl_value)
        retry_count = Servo.GET_RETRY_MAX
        actual_value = self.get(ctrl_name)
        while ctrl_value != actual_value and retry_count:
            logging.warning("%s != %s, retry %d", ctrl_name, ctrl_value,
                            retry_count)
            retry_count -= 1
            time.sleep(Servo.SHORT_DELAY)
            actual_value = self.get(ctrl_name)

        if ctrl_value != actual_value:
            raise error.TestFail(
                    'Servo failed to set %s to %s. Got %s.'
                    % (ctrl_name, ctrl_value, actual_value))

    def set_nocheck(self, ctrl_name, ctrl_value, prefix=''):
        """Set the value of a gpio using Servod.

        @param ctrl_name Name of the control.
        @param ctrl_value New setting for the control.
        @param prefix: prefix to route control to correct servo device.

        @raise ControlUnavailableError: if |ctrl_name| not a known control.
        @raise error.TestFail: for all other failures doing set().
        """
        ctrl_name = self._build_ctrl_name(ctrl_name, prefix)
        # The real danger here is to pass a None value through the xmlrpc.
        assert ctrl_value is not None
        description = 'Setting %s to %r' % (ctrl_name, ctrl_value)
        logging.debug('%s', description)
        with _WrapServoErrors(servo=self, description=description):
            self._server.set(ctrl_name, ctrl_value)

    def set_get_all(self, controls):
        """Set &| get one or more control values.

        @param controls: list of strings, controls to set &| get.

        @raise: error.TestError in case error occurs setting/getting values.
        """
        description = 'Set/get all: %s' % str(controls)
        logging.debug('%s', description)
        with _WrapServoErrors(servo=self, description=description):
            return self._server.set_get_all(controls)


    def probe_host_usb_dev(self):
        """Probe the USB disk device plugged-in the servo from the host side.

        It uses servod to discover if there is a usb device attached to it.

        @return: String of USB disk path (e.g. '/dev/sdb') or None.
        """
        # Set up Servo's usb mux.
        return self.get('image_usbkey_dev') or None


    def image_to_servo_usb(self, image_path=None,
                           make_image_noninteractive=False,
                           power_off_dut=True):
        """Install an image to the USB key plugged into the servo.

        This method may copy any image to the servo USB key including a
        recovery image or a test image.  These images are frequently used
        for test purposes such as restoring a corrupted image or conducting
        an upgrade of ec/fw/kernel as part of a test of a specific image part.

        @param image_path: Path on the host to the recovery image.
        @param make_image_noninteractive: Make the recovery image
                                   noninteractive, therefore the DUT
                                   will reboot automatically after
                                   installation.
        @param power_off_dut: To put the DUT in power off mode.
        """
        # We're about to start plugging/unplugging the USB key.  We
        # don't know the state of the DUT, or what it might choose
        # to do to the device after hotplug.  To avoid surprises,
        # force the DUT to be off.
        if power_off_dut:
            self.get_power_state_controller().power_off()

        if image_path:
            logging.info('Searching for usb device and copying image to it. '
                         'Please wait a few minutes...')
            # The servod control automatically sets up the host in the host
            # direction.
            try:
                self.set_nocheck('download_image_to_usb_dev', image_path)
            except error.TestFail as e:
                logging.error('Failed to transfer requested image to USB. %s.'
                              'Please take a look at Servo Logs.', str(e))
                raise error.AutotestError('Download image to usb failed.')
            if make_image_noninteractive:
                logging.info('Making image noninteractive')
                try:
                    dev = self.probe_host_usb_dev()
                    if not dev:
                        # This is fine but also should never happen: if we
                        # successfully download an image but somehow cannot
                        # find the stick again, it needs to be investigated.
                        raise error.TestFail('No image usb key detected '
                                             'after successful download. '
                                             'Please investigate.')
                    # The modification has to happen on partition 1.
                    dev_partition = '%s1' % dev
                    self.set_nocheck('make_usb_dev_image_noninteractive',
                                     dev_partition)
                except error.TestFail as e:
                    logging.error('Failed to make image noninteractive. %s.'
                                  'Please take a look at Servo Logs.',
                                  str(e))

    def boot_in_recovery_mode(self, snk_mode=False):
        """Boot host DUT in recovery mode.

        @param snk_mode: If True, switch servo_v4 role to 'snk' mode before
                         boot DUT into recovery mode.
        """
        # This call has a built-in delay to ensure that we wait a timeout
        # for the stick to enumerate and settle on the DUT side.
        self.switch_usbkey('dut')
        # Switch servo_v4 mode to snk as the DUT won't able to see usb drive
        # in recovery mode if the servo is in src mode(see crbug.com/1129165).
        if snk_mode:
            logging.info('Setting servo_v4 role to snk mode in order to make'
                         ' the DUT can see usb drive while in recovery mode.')
            self.set_servo_v4_role('snk')

        try:
            power_state = self.get_power_state_controller()
            power_state.power_on(rec_mode=power_state.REC_ON)
        except error.TestFail as e:
            self.set_servo_v4_role('src')
            logging.error('Failed to boot DUT in recovery mode. %s.', str(e))
            raise error.AutotestError('Failed to boot DUT in recovery mode.')

    def install_recovery_image(self,
                               image_path=None,
                               make_image_noninteractive=False,
                               snk_mode=False):
        """Install the recovery image specified by the path onto the DUT.

        This method uses google recovery mode to install a recovery image
        onto a DUT through the use of a USB stick that is mounted on a servo
        board specified by the usb_dev.  If no image path is specified
        we use the recovery image already on the usb image.

        This method will switch servo_v4 role to 'snk' mode in order to make
        the DUT can see the usb drive plugged on servo, the caller should
        set servo_v4 role back to 'src' mode one the DUT exit recovery mode.

        @param image_path: Path on the host to the recovery image.
        @param make_image_noninteractive: Make the recovery image
                noninteractive, therefore the DUT will reboot automatically
                after installation.
        @param snk_mode: If True, switch servo_v4 role to 'snk' mode before
                         boot DUT into recovery mode.
        """
        self.image_to_servo_usb(image_path, make_image_noninteractive)
        # Give the DUT some time to power_off if we skip
        # download image to usb. (crbug.com/982993)
        if not image_path:
            time.sleep(10)
        self.boot_in_recovery_mode(snk_mode=snk_mode)


    def _scp_image(self, image_path):
        """Copy image to the servo host.

        When programming a firmware image on the DUT, the image must be
        located on the host to which the servo device is connected. Sometimes
        servo is controlled by a remote host, in this case the image needs to
        be transferred to the remote host. This adds the servod port number, to
        make sure tests for different DUTs don't trample on each other's files.
        Along with the firmware image, any subsidiary files in the same
        directory shall be copied to the host as well.

        @param image_path: a string, name of the firmware image file to be
               transferred.
        @return: a string, full path name of the copied file on the remote.
        """
        src_path = os.path.dirname(image_path)
        dest_path = os.path.join('/tmp', 'dut_%d' % self._servo_host.servo_port)
        logging.info('Copying %s to %s', src_path, dest_path)
        # Copy a directory, src_path to dest_path. send_file() will create a
        # directory named basename(src_path) under dest_path, and copy all files
        # in src_path to the destination.
        self._servo_host.send_file(src_path, dest_path, delete_dest=True)

        # Make a image path of the destination.
        # e.g. /tmp/dut_9999/EC/ec.bin
        rv = os.path.join(dest_path, os.path.basename(src_path))
        return os.path.join(rv, os.path.basename(image_path))


    def system(self, command, timeout=3600):
        """Execute the passed in command on the servod host.

        @param command Command to be executed.
        @param timeout Maximum number of seconds of runtime allowed. Default to
                       1 hour.
        """
        logging.info('Will execute on servo host: %s', command)
        self._servo_host.run(command, timeout=timeout)


    def system_output(self, command, timeout=3600,
                      ignore_status=False, args=()):
        """Execute the passed in command on the servod host, return stdout.

        @param command a string, the command to execute
        @param timeout an int, max number of seconds to wait til command
               execution completes. Default to 1 hour.
        @param ignore_status a Boolean, if true - ignore command's nonzero exit
               status, otherwise an exception will be thrown
        @param args a tuple of strings, each becoming a separate command line
               parameter for the command
        @return command's stdout as a string.
        """
        return self._servo_host.run(command, timeout=timeout,
                                    ignore_status=ignore_status,
                                    args=args).stdout.strip()


    def get_servo_version(self, active=False):
        """Get the version of the servo, e.g., servo_v2 or servo_v3.

        @param active: Only return the servo type with the active device.
        @return: The version of the servo.

        """
        with _WrapServoErrors(
                servo=self, description='get_servo_version()->get_version()'):
            servo_type = self._server.get_version()
        if '_and_' not in servo_type or not active:
            return servo_type

        # If servo v4 is using ccd and servo micro, modify the servo type to
        # reflect the active device.
        active_device = self.get('active_dut_controller')
        if active_device in servo_type:
            logging.info('%s is active', active_device)
            return 'servo_v4_with_' + active_device

        logging.warning("%s is active even though it's not in servo type",
                     active_device)
        return servo_type


    def get_servo_type(self):
        if self._servo_type is None:
            self._servo_type = self.get_servo_version()
        return self._servo_type

    def get_servo_v4_type(self):
        """Return the servo_v4_type (such as 'type-c'), or None if not v4."""
        if not hasattr(self, '_servo_v4_type'):
            if 'servo_v4' in self.get_servo_type():
                self._servo_v4_type = self.get('root.dut_connection_type')
            else:
                self._servo_v4_type = None
        return self._servo_v4_type

    def is_servo_v4_type_a(self):
        """True if the servo is v4 and type-a, else False."""
        return self.get_servo_v4_type() == 'type-a'

    def is_servo_v4_type_c(self):
        """True if the servo is v4 and type-c, else False."""
        return self.get_servo_v4_type() == 'type-c'

    def get_main_servo_device(self):
        """Return the main servo device"""
        return self.get_servo_type().split('_with_')[-1].split('_and_')[0]


    def enable_main_servo_device(self):
        """Make sure the main device has control of the dut."""
        if not self.can_set_active_device():
            return
        self.set('active_dut_controller', self.get_main_servo_device())

    def get_ccd_servo_device(self):
        """Return the ccd servo device or '' if no ccd devices are connected."""
        servo_type = self.get_servo_type()
        if 'ccd' not in servo_type:
            return ''
        return servo_type.split('_with_')[-1].split('_and_')[-1]

    def active_device_is_ccd(self):
        """Returns True if a ccd device is active."""
        return 'ccd' in self.get_servo_version(active=True)

    def enable_ccd_servo_device(self):
        """Make sure the ccd device has control of the dut.

        Returns True if the ccd device is in control of the dut.
        """
        if self.active_device_is_ccd():
            return True
        ccd_device = self.get_ccd_servo_device()
        if not self.can_set_active_device() or not ccd_device:
            return False
        self.set('active_dut_controller', ccd_device)
        return True

    def main_device_is_ccd(self):
        """Whether the main servo device (no prefixes) is a ccd device."""
        servo = self.get_servo_type()
        return 'ccd' in servo and not self.main_device_is_flex()

    def main_device_is_flex(self):
        """Whether the main servo device (no prefixes) is a legacy device."""
        servo = self.get_servo_type()
        return any([flex in servo for flex in self.FLEX_SERVOS])

    def main_device_uses_gsc_drv(self):
        """Whether the main servo device uses gsc drivers.

        Servo may use gsc wp or console commands to control the dut. These
        get restricted with ccd capabilities. This returns true if some of
        the servo functionality will be disabled if ccd is restricted.
        """
        return self.get_main_servo_device() in self.GSC_DRV_SERVOS

    def _initialize_programmer(self, rw_only=False):
        """Initialize the firmware programmer.

        @param rw_only: True to initialize a programmer which only
                        programs the RW portions.
        """
        if self._programmer:
            return
        # Initialize firmware programmer
        servo_type = self.get_servo_type()
        if servo_type.startswith('servo_v2'):
            self._programmer = firmware_programmer.ProgrammerV2(self)
            self._programmer_rw = firmware_programmer.ProgrammerV2RwOnly(self)
        # Both servo v3 and v4 use the same programming methods so just leverage
        # ProgrammerV3 for servo v4 as well.
        elif (servo_type.startswith('servo_v3')
              or servo_type.startswith('servo_v4')):
            self._programmer = firmware_programmer.ProgrammerV3(self)
            self._programmer_rw = firmware_programmer.ProgrammerV3RwOnly(self)
        else:
            raise error.TestError(
                    'No firmware programmer for servo version: %s' %
                    self.get_servo_type())


    def program_bios(self, image, rw_only=False, copy_image=True):
        """Program bios on DUT with given image.

        @param image: a string, file name of the BIOS image to program
                      on the DUT.
        @param rw_only: True to only program the RW portion of BIOS.
        @param copy_image: True indicates we need scp the image to servohost
                           while False means the image file is already on
                           servohost.
        @return: a string, full path name of the copied file on the remote.
        """
        self._initialize_programmer()
        # We don't need scp if test runs locally.
        if copy_image and not self.is_localhost():
            image = self._scp_image(image)
        if rw_only:
            self._programmer_rw.program_bios(image)
        else:
            self._programmer.program_bios(image)
        return image


    def program_ec(self, image, rw_only=False, copy_image=True):
        """Program ec on DUT with given image.

        @param image: a string, file name of the EC image to program
                      on the DUT.
        @param rw_only: True to only program the RW portion of EC.
        @param copy_image: True indicates we need scp the image to servohost
                           while False means the image file is already on
                           servohost.
        @return: a string, full path name of the copied file on the remote.
        """
        self._initialize_programmer()
        # We don't need scp if test runs locally.
        if copy_image and not self.is_localhost():
            image = self._scp_image(image)
        if rw_only:
            self._programmer_rw.program_ec(image)
        else:
            self._programmer.program_ec(image)
        return image


    def extract_ec_image(self, board, model, tarball_path, fake_image=False):
        """Helper function to extract EC image from downloaded tarball.

        @param board: The DUT board name.
        @param model: The DUT model name.
        @param tarball_path: The path of the downloaded build tarball.
        @param fake_image: True to return a fake zero-filled image instead.

        @return: Path to extracted EC image.
        """

        # Ignore extracting EC image and re-programming if not a Chrome EC
        chrome_ec = FAFTConfig(board).chrome_ec
        if not chrome_ec:
            logging.warning('Not a Chrome EC, ignore re-programming it')
            return None

        # Most boards use the model name as the ec directory.
        ec_image_candidates = ['%s/ec.bin' % model]

        if model == "dragonair":
            ec_image_candidates.append('dratini/ec.bin')

        # If that isn't found try the name from the EC RO version.
        try:
            fw_target = self.get_ec_board().lower()
            ec_image_candidates.append('%s/ec.bin' % fw_target)
        except Exception as err:
            logging.warning('Failed to get ec_board value; ignoring')

        # Fallback to the name of the board, and then a bare ec.bin.
        ec_image_candidates.append('%s/ec.bin' % board)
        ec_image_candidates.append('ec.bin')

        # Extract EC image from tarball
        dest_dir = os.path.join(os.path.dirname(tarball_path), 'EC')
        ec_image = _extract_image_from_tarball(tarball_path,
                                               dest_dir,
                                               ec_image_candidates,
                                               self.EXTRACT_TIMEOUT_SECS)

        # Check if EC image was found and return path or raise error
        if ec_image:
            # Extract subsidiary binaries for EC
            # Find a monitor binary for NPCX_UUT chip type, if any.
            mon_candidates = [candidate.replace('ec.bin', 'npcx_monitor.bin')
                              for candidate in ec_image_candidates]
            _extract_image_from_tarball(tarball_path, dest_dir, mon_candidates,
                                        self.EXTRACT_TIMEOUT_SECS)

            if fake_image:
                # Create a small (25% of original size) zero-filled binary to
                # replace the real ec_image
                file_size = os.path.getsize(ec_image) / 4
                ec_image = os.path.join(os.path.dirname(ec_image),
                                        "zero_ec.bin")
                dump_cmd = 'dd if=/dev/zero of=%s bs=4096 count=%d' % (
                        os.path.join(dest_dir, ec_image), file_size / 4096)
                if server_utils.system(dump_cmd, ignore_status=True) != 0:
                    return None

            return os.path.join(dest_dir, ec_image)
        else:
            raise error.TestError('Failed to extract EC image from %s' %
                                  tarball_path)


    def extract_bios_image(self, board, model, tarball_path):
        """Helper function to extract BIOS image from downloaded tarball.

        @param board: The DUT board name.
        @param model: The DUT model name.
        @param tarball_path: The path of the downloaded build tarball.

        @return: Path to extracted BIOS image.
        """

        # Most boards use the model name as the image filename.
        bios_image_candidates = [
                'image-%s.bin' % model,
        ]

        if model == "dragonair":
            bios_image_candidates.append('image-dratini.bin')

        # If that isn't found try the name from the EC RO version.
        try:
            fw_target = self.get_ec_board().lower()
            bios_image_candidates.append('image-%s.bin' % fw_target)
        except Exception as err:
            logging.warning('Failed to get ec_board value; ignoring')

        # Fallback to the name of the board, and then a bare image.bin.
        bios_image_candidates.append('image-%s.bin' % board)
        bios_image_candidates.append('image.bin')

        # Extract BIOS image from tarball
        dest_dir = os.path.join(os.path.dirname(tarball_path), 'BIOS')
        bios_image = _extract_image_from_tarball(tarball_path,
                                                 dest_dir,
                                                 bios_image_candidates,
                                                 self.EXTRACT_TIMEOUT_SECS)

        # Check if BIOS image was found and return path or raise error
        if bios_image:
            return os.path.join(dest_dir, bios_image)
        else:
            raise error.TestError('Failed to extract BIOS image from %s' %
                                  tarball_path)


    def switch_usbkey(self, usb_state):
        """Connect USB flash stick to either host or DUT, or turn USB port off.

        This function switches the servo multiplexer to provide electrical
        connection between the USB port J3 and either host or DUT side. It
        can also be used to turn the USB port off.

        @param usb_state: A string, one of 'dut', 'host', or 'off'.
                          'dut' and 'host' indicate which side the
                          USB flash device is required to be connected to.
                          'off' indicates turning the USB port off.

        @raise: error.TestError in case the parameter is not 'dut'
                'host', or 'off'.
        """
        if self.get_usbkey_state() == usb_state:
            return

        if usb_state == 'off':
            self.set_nocheck('image_usbkey_pwr', 'off')
            return
        elif usb_state == 'host':
            mux_direction = 'servo_sees_usbkey'
        elif usb_state == 'dut':
            mux_direction = 'dut_sees_usbkey'
        else:
            raise error.TestError('Unknown USB state request: %s' % usb_state)
        # On the servod side, this control will ensure that
        # - the port is power cycled if it is changing directions
        # - the port ends up in a powered state after this call
        # - if facing the host side, the call only returns once a usb block
        #   device is detected, or after a generous timeout (10s)
        self.set('image_usbkey_direction', mux_direction)
        # As servod makes no guarantees when switching to the dut side,
        # add a detection delay here when facing the dut.
        if mux_direction == 'dut_sees_usbkey':
            time.sleep(self.USB_DETECTION_DELAY)

    def get_usbkey_state(self):
        """Get which side USB is connected to or 'off' if usb power is off.

        @return: A string, one of 'dut', 'host', or 'off'.
        """
        pwr = self.get('image_usbkey_pwr')
        if pwr == 'off':
            return pwr
        direction = self.get('image_usbkey_direction')
        if direction == 'servo_sees_usbkey':
            return 'host'
        if direction == 'dut_sees_usbkey':
            return 'dut'
        raise error.TestFail('image_usbkey_direction set an unknown mux '
                             'direction: %s' % direction)

    def set_servo_v4_role(self, role):
        """Set the power role of servo v4, either 'src' or 'snk'.

        It does nothing if not a servo v4.

        @param role: Power role for DUT port on servo v4, either 'src' or 'snk'.
        """
        if not self.get_servo_type().startswith('servo_v4'):
            logging.debug('Not a servo v4, unable to set role to %s.', role)
            return

        if not self.has_control('servo_pd_role'):
            logging.debug(
                    'Servo does not has servo_v4_role control, unable'
                    ' to set role to %s.', role)
            return

        value = self.get('servo_pd_role')
        if value != role:
            self.set_nocheck('servo_pd_role', role)
        else:
            logging.debug('Already in the role: %s.', role)

    def get_servo_v4_role(self):
        """Get the power role of servo v4, either 'src' or 'snk'.

        It returns None if not a servo v4.
        """
        if not self.get_servo_type().startswith('servo_v4'):
            logging.debug('Not a servo v4, unable to get role')
            return None

        if not self.has_control('servo_pd_role'):
            logging.debug(
                    'Servo does not has servo_v4_role control, unable'
                    ' to get the role.')
            return None

        return self.get('servo_pd_role')

    def set_servo_v4_pd_comm(self, en):
        """Set the PD communication of servo v4, either 'on' or 'off'.

        It does nothing if not a servo v4.

        @param en: a string of 'on' or 'off' for PD communication.
        """
        if self.get_servo_type().startswith('servo_v4'):
            self.set_nocheck('servo_pd_comm', en)
        else:
            logging.debug('Not a servo v4, unable to set PD comm to %s.', en)

    def supports_built_in_pd_control(self):
        """Return whether the servo type supports pd charging and control."""
        # Only servo v4 type-c supports this feature.
        if not self.is_servo_v4_type_c():
            logging.info('PD controls require a servo v4 type-c.')
            return False
        # Lastly, one cannot really do anything without a plugged in charger.
        chg_port_mv = self.get('ppchg5_mv')
        if chg_port_mv < V4_CHG_ATTACHED_MIN_VOLTAGE_MV:
            logging.info(
                    'It appears that no charger is plugged into servo v4. '
                    'Charger port voltage: %dmV', chg_port_mv)
            return False
        logging.info('Charger port voltage: %dmV', chg_port_mv)
        return True

    def dts_mode_is_valid(self):
        """Return whether servo setup supports dts mode control for cr50."""
        # Only servo v4 type-c supports this feature.
        return self.is_servo_v4_type_c()

    def dts_mode_is_safe(self):
        """Return whether servo setup supports dts mode without losing access.

        DTS mode control exists but the main device might go through ccd.
        In that case, it's only safe to control dts mode if the main device
        is legacy as otherwise the connection to the main device cuts out.
        """
        return self.dts_mode_is_valid() and self.main_device_is_flex()

    def get_dts_mode(self):
        """Return servo dts mode.

        @returns: on/off whether dts is on or off
        """
        if not self.dts_mode_is_valid():
            logging.info('Not a valid servo setup. Unable to get dts mode.')
            return
        return self.get('servo_dts_mode')

    def ccd_watchdog_enable(self, enable):
        """Control the ccd watchdog."""
        if 'ccd' not in self.get_servo_type():
            return
        if self._ccd_watchdog_disabled and enable:
            logging.info('CCD watchdog disabled for test')
            return
        control = 'watchdog_add' if enable else 'watchdog_remove'
        self.set_nocheck(control, 'ccd')

    def disable_ccd_watchdog_for_test(self):
        """Prevent servo from enabling the watchdog."""
        self._ccd_watchdog_disabled = True
        self.ccd_watchdog_enable(False)

    def allow_ccd_watchdog_for_test(self):
        """Allow servo to enable the ccd watchdog."""
        self._ccd_watchdog_disabled = False
        self.ccd_watchdog_enable(True)

    def set_dts_mode(self, state):
        """Set servo dts mode to off or on.

        It does nothing if not a servo v4. Disable the ccd watchdog if we're
        disabling dts mode. CCD will disconnect. The watchdog only allows CCD
        to disconnect for 10 seconds until it kills servod. Disable the
        watchdog, so CCD can stay disconnected indefinitely.

        @param state: Set servo v4 dts mode 'off' or 'on'.
        """
        if not self.dts_mode_is_valid():
            logging.info('Not a valid servo setup. Unable to set dts mode %s.',
                         state)
            return

        enable_watchdog = state == 'on'

        if not enable_watchdog:
            self.ccd_watchdog_enable(False)

        self.set_nocheck('servo_dts_mode', state)

        if enable_watchdog:
            self.ccd_watchdog_enable(True)


    def _get_servo_type_fw_version(self, servo_type, prefix=''):
        """Helper to handle fw retrieval for micro/v4 vs ccd.

        @param servo_type: one of 'servo_v4', 'servo_micro', 'c2d2',
                           'ccd_cr50', or 'ccd_gsc'
        @param prefix: whether the control has a prefix

        @returns: fw version for non-ccd devices, cr50 version for ccd device
        """
        # If it's a ccd device, remove the 'ccd_' prefix to find the firmware
        # name.
        if servo_type.startswith(self.CCD_PREFIX):
            servo_type = servo_type[len(self.CCD_PREFIX)::]
        cmd = '%s_version' % servo_type
        try:
            return self.get(cmd, prefix=prefix)
        except error.TestFail:
            # Do not fail here, simply report the version as unknown.
            logging.warning('Unable to query %r to get servo fw version.', cmd)
            return 'unknown'


    def get_servo_fw_versions(self):
        """Retrieve a summary of attached servos and their firmware.

        Note: that only the Google firmware owned servos supports this e.g.
        micro, v4, etc. For anything else, the dictionary will have no entry.
        If no device is has Google owned firmware (e.g. v3) then the result
        is an empty dictionary.

        @returns: dict, a collection of each attached servo & their firmware.
        """
        def get_fw_version_tag(tag, dev):
            return '%s_version.%s' % (dev, tag)

        fw_versions = {}
        # Note, this works because v4p1 starts with v4 as well.
        # TODO(coconutruben): make this more robust so that it can work on
        # a future v-whatever as well.
        if 'servo_v4' not in self.get_servo_type():
            return {}
        # v4 or v4p1
        v4_flavor = self.get_servo_type().split('_with_')[0]
        v4_tag = get_fw_version_tag('root', v4_flavor)
        fw_versions[v4_tag] = self._get_servo_type_fw_version('servo_fw',
                                                              prefix='root')
        if 'with' in self.get_servo_type():
            dut_devs = self.get_servo_type().split('_with_')[1].split('_and_')
            main_tag = get_fw_version_tag('main', dut_devs[0])
            fw_versions[main_tag] = self._get_servo_type_fw_version(dut_devs[0])
            if len(dut_devs) == 2:
                # Right now, the only way for this to happen is for a dual setup
                # to exist where ccd is attached on top of servo micro. Thus, we
                # know that the prefix is ccd_cr50 and the type is ccd_cr50.
                # TODO(coconutruben): If the new servod is not deployed by
                # the time that there are more cases of '_and_' devices,
                # this needs to be reworked.
                dual_tag = get_fw_version_tag('ccd_flex_secondary', dut_devs[1])
                fw = self._get_servo_type_fw_version(dut_devs[1], dut_devs[1])
                fw_versions[dual_tag] = fw
        return fw_versions

    @property
    def uart_logs_dir(self):
        """Return the directory to save UART logs."""
        return self._uart.logs_dir if self._uart else ""


    @uart_logs_dir.setter
    def uart_logs_dir(self, logs_dir):
        """Set directory to save UART logs.

        @param logs_dir  String of directory name."""
        self._uart.logs_dir = logs_dir

    def get_uart_logfile(self, uart):
        """Return the path to the uart log file."""
        return self._uart.get_logfile(uart)

    def record_uart_capture(self, outdir=None):
        """Save uart stream output."""
        if outdir and not self.uart_logs_dir:
            self.uart_logs_dir = outdir
        self._uart.dump()

    def close(self, outdir=None):
        """Close the servo object."""
        # We want to ensure that servo_v4 is in src mode to avoid DUTs
        # left in discharge state after a task.
        try:
            self.set_servo_v4_role('src')
        except Exception as e:
            logging.info(
                    'Unexpected error while setting servo_v4 role'
                    ' to src; %s', e)

        self._uart.stop_capture()
        self.record_uart_capture(outdir)

    def ec_reboot(self):
        """Reboot Just the embedded controller."""
        self.set_nocheck('ec_uart_flush', 'off')
        self.set_nocheck('ec_uart_cmd', 'reboot')
        self.set_nocheck('ec_uart_flush', 'on')

    def get_vbus_voltage(self):
        """Get the voltage of VBUS'.

        @returns The voltage of VBUS, if vbus_voltage is supported.
                 None               , if vbus_voltage is not supported.
        """
        if not self.has_control('vbus_voltage'):
            logging.debug('Servo does not have vbus_voltage control,'
                          'unable to get vbus voltage')
            return None

        return self.get('vbus_voltage')

    def supports_eth_power_control(self):
        """True if servo supports power management for ethernet dongle."""
        return self.has_control('dut_eth_pwr_en')

    def set_eth_power(self, state):
        """Set ethernet dongle power state, either 'on' or 'off'.

        Note: this functionality is supported only on servo v4p1.

        @param state: a string of 'on' or 'off'.
        """
        if state != 'off' and state != 'on':
            raise error.TestError('Unknown ethernet power state request: %s' %
                                  state)

        if not self.supports_eth_power_control():
            logging.info('Not a supported servo setup. Unable to set ethernet'
                         'dongle power state %s.', state)
            return

        self.set_nocheck('dut_eth_pwr_en', state)

    def eth_power_reset(self):
        """Reset ethernet dongle power state if supported'.

        It does nothing if servo setup does not support power management for
        the etherent dongle, only log information about this.
        """
        if self.supports_eth_power_control():
            logging.info("Resetting servo's Ethernet controller...")
            self.set_eth_power('off')
            time.sleep(1)
            self.set_eth_power('on')
        else:
            logging.info("Trying to reset servo's Ethernet controller, but"
                         "this feature is not supported on used servo setup.")
