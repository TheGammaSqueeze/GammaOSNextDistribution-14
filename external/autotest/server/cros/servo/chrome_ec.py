# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import ast
import logging
import re
import time
from xml.parsers import expat

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros import ec
from autotest_lib.server.cros.servo import servo

# Hostevent codes, copied from:
#     ec/include/ec_commands.h
HOSTEVENT_LID_CLOSED        = 0x00000001
HOSTEVENT_LID_OPEN          = 0x00000002
HOSTEVENT_POWER_BUTTON      = 0x00000004
HOSTEVENT_AC_CONNECTED      = 0x00000008
HOSTEVENT_AC_DISCONNECTED   = 0x00000010
HOSTEVENT_BATTERY_LOW       = 0x00000020
HOSTEVENT_BATTERY_CRITICAL  = 0x00000040
HOSTEVENT_BATTERY           = 0x00000080
HOSTEVENT_THERMAL_THRESHOLD = 0x00000100
HOSTEVENT_THERMAL_OVERLOAD  = 0x00000200
HOSTEVENT_THERMAL           = 0x00000400
HOSTEVENT_USB_CHARGER       = 0x00000800
HOSTEVENT_KEY_PRESSED       = 0x00001000
HOSTEVENT_INTERFACE_READY   = 0x00002000
# Keyboard recovery combo has been pressed
HOSTEVENT_KEYBOARD_RECOVERY = 0x00004000
# Shutdown due to thermal overload
HOSTEVENT_THERMAL_SHUTDOWN  = 0x00008000
# Shutdown due to battery level too low
HOSTEVENT_BATTERY_SHUTDOWN  = 0x00010000
HOSTEVENT_INVALID           = 0x80000000

# Time to wait after sending keypress commands.
KEYPRESS_RECOVERY_TIME = 0.5

# Wakemask types, copied from:
#     ec/include/ec_commands.h
EC_HOST_EVENT_MAIN = 0
EC_HOST_EVENT_B = 1
EC_HOST_EVENT_SCI_MASK = 2
EC_HOST_EVENT_SMI_MASK = 3
EC_HOST_EVENT_ALWAYS_REPORT_MASK = 4
EC_HOST_EVENT_ACTIVE_WAKE_MASK = 5
EC_HOST_EVENT_LAZY_WAKE_MASK_S0IX = 6
EC_HOST_EVENT_LAZY_WAKE_MASK_S3 = 7
EC_HOST_EVENT_LAZY_WAKE_MASK_S5 = 8


class ChromeConsole(object):
    """Manages control of a Chrome console.

    We control the Chrome console via the UART of a Servo board. Chrome console
    provides many interfaces to set and get its behavior via console commands.
    This class is to abstract these interfaces.
    """

    CMD = "_cmd"
    REGEXP = "_regexp"
    MULTICMD = "_multicmd"

    # EC Features
    # Quoted from 'enum ec_feature_code' in platform/ec/include/ec_commands.h.
    EC_FEATURE = {
        'EC_FEATURE_LIMITED'                            : 0,
        'EC_FEATURE_FLASH'                              : 1,
        'EC_FEATURE_PWM_FAN'                            : 2,
        'EC_FEATURE_PWM_KEYB'                           : 3,
        'EC_FEATURE_LIGHTBAR'                           : 4,
        'EC_FEATURE_LED'                                : 5,
        'EC_FEATURE_MOTION_SENSE'                       : 6,
        'EC_FEATURE_KEYB'                               : 7,
        'EC_FEATURE_PSTORE'                             : 8,
        'EC_FEATURE_PORT80'                             : 9,
        'EC_FEATURE_THERMAL'                            : 10,
        'EC_FEATURE_BKLIGHT_SWITCH'                     : 11,
        'EC_FEATURE_WIFI_SWITCH'                        : 12,
        'EC_FEATURE_HOST_EVENTS'                        : 13,
        'EC_FEATURE_GPIO'                               : 14,
        'EC_FEATURE_I2C'                                : 15,
        'EC_FEATURE_CHARGER'                            : 16,
        'EC_FEATURE_BATTERY'                            : 17,
        'EC_FEATURE_SMART_BATTERY'                      : 18,
        'EC_FEATURE_HANG_DETECT'                        : 19,
        'EC_FEATURE_PMU'                                : 20,
        'EC_FEATURE_SUB_MCU'                            : 21,
        'EC_FEATURE_USB_PD'                             : 22,
        'EC_FEATURE_USB_MUX'                            : 23,
        'EC_FEATURE_MOTION_SENSE_FIFO'                  : 24,
        'EC_FEATURE_VSTORE'                             : 25,
        'EC_FEATURE_USBC_SS_MUX_VIRTUAL'                : 26,
        'EC_FEATURE_RTC'                                : 27,
        'EC_FEATURE_FINGERPRINT'                        : 28,
        'EC_FEATURE_TOUCHPAD'                           : 29,
        'EC_FEATURE_RWSIG'                              : 30,
        'EC_FEATURE_DEVICE_EVENT'                       : 31,
        'EC_FEATURE_UNIFIED_WAKE_MASKS'                 : 32,
        'EC_FEATURE_HOST_EVENT64'                       : 33,
        'EC_FEATURE_EXEC_IN_RAM'                        : 34,
        'EC_FEATURE_CEC'                                : 35,
        'EC_FEATURE_MOTION_SENSE_TIGHT_TIMESTAMPS'      : 36,
        'EC_FEATURE_REFINED_TABLET_MODE_HYSTERESIS'     : 37,
        'EC_FEATURE_EFS2'                               : 38,
        'EC_FEATURE_SCP'                                : 39,
        'EC_FEATURE_ISH'                                : 40,
    }

    def __init__(self, servo, name):
        """Initialize and keep the servo object.

        Args:
          servo: A Servo object.
          name: The console name.
        """
        self.name = name
        self.uart_cmd = self.name + self.CMD
        self.uart_regexp = self.name + self.REGEXP
        self.uart_multicmd = self.name + self.MULTICMD

        self._servo = servo

    def __repr__(self):
        """Return a string representation: <ChromeConsole 'foo_uart'>"""
        return "<%s %r>" % (self.__class__.__name__, self.name)

    def set_uart_regexp(self, regexp):
        self._servo.set(self.uart_regexp, regexp)

    def clear_uart_regex(self):
        """Clear uart_regexp"""
        self.set_uart_regexp('None')

    def send_command(self, commands):
        """Send command through UART.

        This function opens UART pty when called, and then command is sent
        through UART.

        Args:
          commands: The commands to send, either a list or a string.
        """
        self.clear_uart_regex()
        if isinstance(commands, list):
            try:
                self._servo.set_nocheck(self.uart_multicmd, ';'.join(commands))
            except servo.ControlUnavailableError:
                logging.warning('The servod is too old that uart_multicmd not '
                                'supported. Use uart_cmd instead.')
                for command in commands:
                    self._servo.set_nocheck(self.uart_cmd, command)
        else:
            self._servo.set_nocheck(self.uart_cmd, commands)
        self.clear_uart_regex()

    def has_command(self, command):
        """Check whether EC console supports |command|.

        Args:
          command: Command to look for.

        Returns:
          True: If the |command| exist on the EC image of the device.
          False: If the |command| does not exist on the EC image of the device.
        """
        result = None
        try:
            # Throws error.TestFail (on timeout) if it cannot find a line with
            # 'command' in the output. Thus return False in that case.
            result = self.send_command_get_output('help', [command])
        except error.TestFail:
            return False
        return result is not None

    def send_command_get_output(self, command, regexp_list, retries=1):
        """Send command through UART and wait for response.

        This function waits for response message matching regular expressions.

        Args:
          command: The command sent.
          regexp_list: List of regular expressions used to match response
            message. Note, list must be ordered.

        Returns:
          List of tuples, each of which contains the entire matched string and
          all the subgroups of the match. None if not matched.
          For example:
            response of the given command:
              High temp: 37.2
              Low temp: 36.4
            regexp_list:
              ['High temp: (\d+)\.(\d+)', 'Low temp: (\d+)\.(\d+)']
            returns:
              [('High temp: 37.2', '37', '2'), ('Low temp: 36.4', '36', '4')]

        Raises:
          error.TestError: An error when the given regexp_list is not valid.
        """
        if not isinstance(regexp_list, list):
            raise error.TestError('Arugment regexp_list is not a list: %s' %
                                  str(regexp_list))

        while retries > 0:
            retries -= 1
            try:
                self.set_uart_regexp(str(regexp_list))
                self._servo.set_nocheck(self.uart_cmd, command)
                return ast.literal_eval(self._servo.get(self.uart_cmd))
            except (servo.UnresponsiveConsoleError,
                    servo.ResponsiveConsoleError, expat.ExpatError) as e:
                if retries <= 0:
                    raise
                logging.warning('Failed to send EC cmd. %s', e)
            finally:
                self.clear_uart_regex()


    def is_dfp(self, port=0):
        """This function checks if EC is DFP

        Args:
          port: Port of EC to check

        Returns:
          True: if EC is DFP
          False: if EC is not DFP
        """
        is_dfp = None
        ret = None
        try:
            ret = self.send_command_get_output("pd %d state" % port,
                                               ["DFP.*Flag"])
            is_dfp = True
        except Exception as e:
            is_dfp = False

        # For TCPMv1, after disconnecting a device the data state remains
        # the same, so even when pd state shows DPF, make sure the device is
        # not disconnected
        if is_dfp:
            if "DRP_AUTO_TOGGLE" in ret[0] or "DISCONNECTED" in ret[0]:
                is_dfp = False

        return is_dfp


class ChromeEC(ChromeConsole):
    """Manages control of a Chrome EC.

    We control the Chrome EC via the UART of a Servo board. Chrome EC
    provides many interfaces to set and get its behavior via console commands.
    This class is to abstract these interfaces.
    """

    # The dict to cache the battery information
    BATTERY_INFO = {}

    def __init__(self, servo, name="ec_uart"):
        super(ChromeEC, self).__init__(servo, name)

    def __repr__(self):
        """Return a string representation of the object: <ChromeEC 'ec_uart'>"""
        return "<%s %r>" % (self.__class__.__name__, self.name)

    def key_down(self, keyname):
        """Simulate pressing a key.

        Args:
          keyname: Key name, one of the keys of KEYMATRIX.
        """
        self.send_command('kbpress %d %d 1' %
                (ec.KEYMATRIX[keyname][1], ec.KEYMATRIX[keyname][0]))


    def key_up(self, keyname):
        """Simulate releasing a key.

        Args:
          keyname: Key name, one of the keys of KEYMATRIX.
        """
        self.send_command('kbpress %d %d 0' %
                (ec.KEYMATRIX[keyname][1], ec.KEYMATRIX[keyname][0]))


    def key_press(self, keyname):
        """Press and then release a key.

        Args:
          keyname: Key name, one of the keys of KEYMATRIX.
        """
        self.send_command([
                'kbpress %d %d 1' %
                    (ec.KEYMATRIX[keyname][1], ec.KEYMATRIX[keyname][0]),
                'kbpress %d %d 0' %
                    (ec.KEYMATRIX[keyname][1], ec.KEYMATRIX[keyname][0]),
                ])
        # Don't spam the EC console as fast as we can; leave some recovery time
        # in between commands.
        time.sleep(KEYPRESS_RECOVERY_TIME)


    def send_key_string_raw(self, string):
        """Send key strokes consisting of only characters.

        Args:
          string: Raw string.
        """
        for c in string:
            self.key_press(c)


    def send_key_string(self, string):
        """Send key strokes including special keys.

        Args:
          string: Character string including special keys. An example
            is "this is an<tab>example<enter>".
        """
        for m in re.finditer("(<[^>]+>)|([^<>]+)", string):
            sp, raw = m.groups()
            if raw is not None:
                self.send_key_string_raw(raw)
            else:
                self.key_press(sp)


    def reboot(self, flags=''):
        """Reboot EC with given flags.

        Args:
          flags: Optional, a space-separated string of flags passed to the
                 reboot command, including:
                   default: EC soft reboot;
                   'hard': EC hard/cold reboot;
                   'ap-off': Leave AP off after EC reboot (by default, EC turns
                             AP on after reboot if lid is open).

        Raises:
          error.TestError: If the string of flags is invalid.
        """
        for flag in flags.split():
            if flag not in ('hard', 'ap-off'):
                raise error.TestError(
                        'The flag %s of EC reboot command is invalid.' % flag)
        self.send_command("reboot %s" % flags)


    def set_flash_write_protect(self, enable):
        """Set the software write protect of EC flash.

        Args:
          enable: True to enable write protect, False to disable.
        """
        if enable:
            self.send_command("flashwp enable")
        else:
            self.send_command("flashwp disable")


    def set_hostevent(self, codes):
        """Set the EC hostevent codes.

        Args:
          codes: Hostevent codes, HOSTEVENT_*
        """
        self.send_command("hostevent set %#x" % codes)
        # Allow enough time for EC to process input and set flag.
        # See chromium:371631 for details.
        # FIXME: Stop importing time module if this hack becomes obsolete.
        time.sleep(1)


    def enable_console_channel(self, channel):
        """Find console channel mask and enable that channel only

        @param channel: console channel name
        """
        # The 'chan' command returns a list of console channels,
        # their channel masks and channel numbers
        regexp = r'(\d+)\s+([\w]+)\s+\*?\s+{0}'.format(channel)
        l = self.send_command_get_output('chan', [regexp])
        # Use channel mask and append the 0x for proper hex input value
        cmd = 'chan 0x' + l[0][2]
        # Set console to only output the desired channel
        self.send_command(cmd)


    def get_version(self):
        """Get version information from the Chrome EC console.
           Additionally, can be used to verify if EC console is available.
        """
        self.send_command("chan 0")
        # Use "[ \t]" here and not \s because sometimes the version is blank,
        # i.e. 'RO:   \r\n' which matches RO:\s+
        expected_output = [
                "Chip:[ \t]+([^\r\n]*)\r\n", "RO:[ \t]+([^\r\n]*)\r\n",
                "RW_?[AB]?:[ \t]+([^\r\n]*)\r\n", "Build:[ \t]+([^\r\n]*)\r\n"
        ]
        l = self.send_command_get_output("version", expected_output)
        self.send_command("chan 0xffffffff")
        return l

    def check_ro_rw(self, img_exp):
        """Tell if the current EC image matches the given input, 'RW' or 'RO.

        Args:
            img_exp: Expected image type. It should be either 'RW' or 'RO'.
        Return:
            True if the active EC image matches to 'img_exp'.
            False otherwise.
        Raise:
            TestError if img_exp is neither 'RW' nor 'RO'.
        """
        if img_exp not in ['RW', 'RO']:
            raise error.TestError('Arugment img_exp is neither RW nor RO')

        result = self.send_command_get_output('sysinfo', [r'Copy:\s*(RO|RW)'])
        return result[0][1] == img_exp

    def check_feature(self, feature):
        """Return true if EC supports the given feature

        Args:
            feature: feature name as a string as in self.EC_FEATURE.

        Returns:
            True if 'feature' is in EC's feature set.
            False otherwise
        """
        feat_id = self.EC_FEATURE[feature]
        if feat_id < 32:
            feat_start = 0
        else:
            feat_start = 32

        regexp = r'%d-%d:\s*(0x[0-9a-fA-F]{8})' % (feat_start,
                                                   feat_start + 31)

        try:
            result = self.send_command_get_output('feat', [regexp])
        except servo.ResponsiveConsoleError as e:
            logging.warning("feat command is not available: %s", str(e))
            return False

        feat_bitmap = int(result[0][1], 16)

        return ((1 << (feat_id - feat_start)) & feat_bitmap) != 0

    def update_battery_info(self):
        """Get the battery info we care for this test."""
        # The battery parameters we care for this test. The order must match
        # the output of EC battery command.
        battery_params = [
                'V', 'V-desired', 'I', 'I-desired', 'Charging', 'Remaining'
        ]
        regex_str_list = []

        for p in battery_params:
            if p == 'Remaining':
                regex_str_list.append(p + ':\s+(\d+)\s+')
            elif p == 'Charging':
                regex_str_list.append(p + r':\s+(Not Allowed|Allowed)\s+')
            else:
                regex_str_list.append(p +
                                      r':\s+0x[0-9a-f]*\s+=\s+([0-9-]+)\s+')

        # For unknown reasons, servod doesn't always capture the ec
        # command output. It doesn't happen often, but retry if it does.
        retries = 3
        while retries > 0:
            retries -= 1
            try:
                battery_regex_match = self.send_command_get_output(
                        'battery', regex_str_list)
                break
            except (servo.UnresponsiveConsoleError,
                    servo.ResponsiveConsoleError) as e:
                if retries <= 0:
                    raise
                logging.warning('Failed to get battery status. %s', e)
        else:
            battery_regex_match = self.send_command_get_output(
                    'battery', regex_str_list)

        for i in range(len(battery_params)):
            if battery_params[i] == 'Charging':
                self.BATTERY_INFO[
                        battery_params[i]] = battery_regex_match[i][1]
            else:
                self.BATTERY_INFO[battery_params[i]] = int(
                        battery_regex_match[i][1])
        logging.debug('Battery info: %s', self.BATTERY_INFO)

    def get_battery_desired_voltage(self, print_result=True):
        """Get battery desired voltage value."""
        if not self.BATTERY_INFO:
            self.update_battery_info()
        if print_result:
            logging.info('Battery desired voltage = %d mV',
                         self.BATTERY_INFO['V-desired'])
        return self.BATTERY_INFO['V-desired']

    def get_battery_desired_current(self, print_result=True):
        """Get battery desired current value."""
        if not self.BATTERY_INFO:
            self.update_battery_info()
        if print_result:
            logging.info('Battery desired current = %d mA',
                         self.BATTERY_INFO['I-desired'])
        return self.BATTERY_INFO['I-desired']

    def get_battery_actual_voltage(self, print_result=True):
        """Get the actual voltage from charger to battery."""
        if not self.BATTERY_INFO:
            self.update_battery_info()
        if print_result:
            logging.info('Battery actual voltage = %d mV',
                         self.BATTERY_INFO['V'])
        return self.BATTERY_INFO['V']

    def get_battery_actual_current(self, print_result=True):
        """Get the actual current from charger to battery."""
        if not self.BATTERY_INFO:
            self.update_battery_info()
        if print_result:
            logging.info('Battery actual current = %d mA',
                         self.BATTERY_INFO['I'])
        return self.BATTERY_INFO['I']

    def get_battery_remaining(self, print_result=True):
        """Get battery charge remaining in mAh."""
        if not self.BATTERY_INFO:
            self.update_battery_info()
        if print_result:
            logging.info("Battery charge remaining = %d mAh",
                         self.BATTERY_INFO['Remaining'])
        return self.BATTERY_INFO['Remaining']

    def get_battery_charging_allowed(self, print_result=True):
        """Get the battery charging state.

        Returns True if charging is allowed.
        """
        if not self.BATTERY_INFO:
            self.update_battery_info()
        if print_result:
            logging.info("Battery charging = %s",
                         self.BATTERY_INFO['Charging'])
        if self.BATTERY_INFO['Charging'] == 'Allowed':
            return True
        return False


class ChromeUSBPD(ChromeEC):
    """Manages control of a Chrome USBPD.

    We control the Chrome EC via the UART of a Servo board. Chrome USBPD
    provides many interfaces to set and get its behavior via console commands.
    This class is to abstract these interfaces.
    """

    def __init__(self, servo):
        super(ChromeUSBPD, self).__init__(servo, "usbpd_uart")
