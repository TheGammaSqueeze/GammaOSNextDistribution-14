# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_ECUsbPorts(FirmwareTest):
    """
    Servo based EC USB port control test.
    """
    version = 1


    # Delay for remote shell command call to return
    RPC_DELAY = 1

    # Delay between turning off and on USB ports
    REBOOT_DELAY = 6

    # USB charge modes, copied from ec/include/usb_charge.h
    USB_CHARGE_MODE_DISABLED       = 0
    USB_CHARGE_MODE_SDP2           = 1
    USB_CHARGE_MODE_CDP            = 2
    USB_CHARGE_MODE_DCP_SHORT      = 3
    USB_CHARGE_MODE_ENABLED        = 4

    def initialize(self, host, cmdline_args):
        super(firmware_ECUsbPorts, self).initialize(host, cmdline_args)
        # Don't bother if there is no Chrome EC.
        if not self.check_ec_capability(['usb']):
            raise error.TestNAError("Nothing needs to be tested on this device")
        # Only run in normal mode
        self.switcher.setup_mode('normal')
        self.ec.send_command("chan 0")


    def cleanup(self):
        try:
            self.ec.send_command("chan 0xffffffff")
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_ECUsbPorts, self).cleanup()


    def fake_reboot_by_usb_mode_change(self):
        """
        Turn off USB ports and also kill FAFT client so that this acts like a
        reboot. If USB ports cannot be turned off or on, reboot step would
        fail.
        """
        for_all_ports_cmd = ('id=0; while [ $id -lt %d ];' +
                             'do ectool usbchargemode "$id" %d;' +
                             'id=$((id+1)); sleep 0.5; done')
        # Port disable - same for smart and dumb ports.
        ports_off_cmd = for_all_ports_cmd % (self._port_count,
                                             self.USB_CHARGE_MODE_DISABLED)
        # Port enable - different command based on smart/dumb port.
        port_enable_param = (self.USB_CHARGE_MODE_SDP2
            if self._smart_usb_charge else self.USB_CHARGE_MODE_ENABLED)
        ports_on_cmd = for_all_ports_cmd % (self._port_count, port_enable_param)
        cmd = ("sleep %d; %s; sleep %d; %s" %
               (self.RPC_DELAY, ports_off_cmd, self.REBOOT_DELAY,
                ports_on_cmd))
        block = False
        self.faft_client.system.run_shell_command(cmd, block)
        self.faft_client.disconnect()

    def __check_usb_enabled(self, idx):
        """Returns True if USB-A enable signal is high for a given index"""
        is_ioex = False
        gpio_name = 'USB%d_ENABLE' % (idx + 1)
        if self.faft_config.custom_usb_enable_pins:
            if idx >= len(self.faft_config.custom_usb_enable_pins):
                raise error.TestFail('No USB enable for index %d' % idx)
            is_ioex = self.faft_config.custom_usb_enable_pins[idx].get(
                    'ioex', False)
            gpio_name = self.faft_config.custom_usb_enable_pins[idx]['name']
            # change the unicode to ascii
            gpio_name = str(gpio_name)
        _, val = self.ec.send_command_get_output(
                '%sget %s' % (('gpio', 'ioex')[is_ioex], gpio_name),
                ['(?i)([01])[^\n\r]*\s%s' % gpio_name])[0]
        return val == '1'

    def probe_port_count(self):
        """Probe the EC's gpio pins to determine the number of USB-A ports"""
        for cnt in range(10):
            try:
                self.__check_usb_enabled(cnt)
            except error.TestFail:
                # Enforce that zero ports are specified explicitly. Without
                # this, the TEST_NA result tends to get ignored until final
                # FSI signoff, at which point it becomes an emergency when
                # someone notices this device does indeed have USB-A ports.
                if cnt == 0:
                    raise error.TestFail(
                            "No USB A ports could be found. If this device has "
                            "no USB-A ports, specify usb_a_port_count: 0 in your "
                            "fw-testing-configs")

                logging.info("Found %d USB ports via probe", cnt)
                return cnt
        # Limit reached. Probably something went wrong.
        raise error.TestFail("Unexpected error while trying to determine " +
                             "number of USB ports")

    def get_port_count(self):
        """Get the number of USB ports."""

        # Prefer an explicit count.
        count = self.faft_config.usb_a_port_count
        if count is not None:
            logging.info("%d USB ports specified via config", count)
            # Allow -1 as an escape hatch back to dynamic probing for
            # devices whose USB-A port counts may vary by SKU.
            if count == -1:
                try:
                    count = self.probe_port_count()
                except error.TestFail:
                    count = 0

            return count

        # Next use the custom enable as a proxy, if it's non-zero.
        if self.faft_config.custom_usb_enable_pins:
            count = len(self.faft_config.custom_usb_enable_pins)
            logging.info("%d USB ports counted by pins", count)
            return count

        # Finally, fall back to probing if unspecified.
        return self.probe_port_count()

    def check_power_off_mode(self):
        """Shutdown the system and check USB ports are disabled."""
        self.run_shutdown_cmd()
        self.wait_for('shutdown', 'Checking that all USB-A ports are disabled')
        # Check that all USB-A ports are disabled
        for idx in range(self._port_count):
            if self.__check_usb_enabled(idx):
                raise error.TestFail(
                        'Not all USB-A ports are disabled after shutdown')
        self.servo.power_short_press()


    def run_once(self):
        """Execute the main body of the test.
        """
        self._smart_usb_charge = (
            'smart_usb_charge' in self.faft_config.ec_capability)
        self._port_count = self.get_port_count()

        if self._port_count == 0:
            raise error.TestNAError("No USB-A port; nothing needs to be tested")

        if self.servo.main_device_is_ccd():
            logging.info("Using CCD, ignore checking USB port connection.")
        elif self.servo.is_servo_v4_type_c():
            logging.info("Using type-c servo, ignore checking USB port connection.")
        elif self.servo.get_servo_v4_type() is not None:
            # When only one USB-A port control is available, turning off the
            # USB-A port disconnects the network connection from the DUT.
            raise error.TestNAError("Only one USB-A port control; servo v4 type-C required")
        else:
            logging.info("Turn off all USB ports and then turn them on again.")
            self.switcher.mode_aware_reboot(
                    'custom', self.fake_reboot_by_usb_mode_change)

        logging.info("Check USB ports are disabled when powered off.")
        self.switcher.mode_aware_reboot('custom', self.check_power_off_mode)
