# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import re

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.servo import servo

class firmware_ECCbiEeprom(FirmwareTest):
    """Servo-based EC test for Cros Board Info EEPROM"""
    version = 1

    EEPROM_LOCATE_TYPE = 0
    EEPROM_LOCATE_INDEX = 0   # Only one EEPROM ever

    # Test data to attempt to write to EEPROM
    TEST_EEPROM_DATA = ('0xaa ' * 8).strip()
    TEST_EEPROM_DATA_2 = ('0x55 ' * 8).strip()

    # Size of read and write. Use 8-bytes as this will work with EEPROMs with
    # page size 8 or 16 bytes. We allow 8-bytes page size parts.
    PAGE_SIZE = 8
    NO_READ = 0

    # The number of bytes we verify are both writable and write protectable
    MAX_BYTES = 64

    def initialize(self, host, cmdline_args):
        super(firmware_ECCbiEeprom, self).initialize(host, cmdline_args)
        # Don't bother if CBI isn't on this device.
        if not self.check_ec_capability(['cbi']):
            raise error.TestNAError("Nothing needs to be tested on this device")
        self.host = host
        cmd = 'ectool locatechip %d %d' % (self.EEPROM_LOCATE_TYPE,
                                           self.EEPROM_LOCATE_INDEX)
        cmd_out = self.faft_client.system.run_shell_command_get_output(
                cmd, True)
        logging.debug('Ran %s on DUT, output was: %s', cmd, cmd_out)

        if len(cmd_out) > 0 and cmd_out[0].startswith('Usage'):
            raise error.TestNAError("I2C lookup not supported yet.")

        if len(cmd_out) < 1:
            cmd_out = ['']

        match = re.search('Bus: I2C; Port: (\w+); Address: (\w+)', cmd_out[0])
        if match is None:
            raise error.TestFail("I2C lookup for EEPROM CBI Failed.  Check "
                                 "debug log for output.")

        # Allow hex value parsing (i.e. base set to 0)
        self.i2c_port = int(match.group(1), 0)
        self.i2c_addr = int(match.group(2), 0)

        # Ensure that the i2c mux is disabled on the servo as the CBI EEPROM
        # i2c lines are shared with the servo lines on some HW designs. If the
        # control does not exist, ignore error
        try:
            self.servo.set('i2c_mux_en', 'off')
            logging.info("i2c_mux_en present and reset.")
        except servo.ControlUnavailableError:
            logging.info("i2c_mux_en does not exist. Ignoring.")

        # Check to see if the CBI WP is decoupled.  If it's decoupled, the EC
        # will have its own signal to control the CBI WP called `EC_CBI_WP`.
        cmd = 'ectool gpioget ec_cbi_wp'
        cmd_status = self.faft_client.system.run_shell_command_get_status(cmd)
        self._wp_is_decoupled = True if cmd_status == 0 else False

    def _gen_write_command(self, offset, data):
        return ('ectool i2cxfer %d %d %d %d %s' %
               (self.i2c_port, self.i2c_addr, self.NO_READ, offset, data))

    def _read_eeprom(self, offset):
        cmd_out = self.faft_client.system.run_shell_command_get_output(
                  'ectool i2cxfer %d %d %d %d' %
                  (self.i2c_port, self.i2c_addr, self.PAGE_SIZE, offset))
        if len(cmd_out) < 1:
            raise error.TestFail(
                "Could not read EEPROM data at offset %d" % (offset))
        data = re.search('Read bytes: (.+)', cmd_out[0]).group(1)
        if data == '':
            raise error.TestFail(
                "Empty EEPROM read at offset %d" % (offset))
        return data

    def _write_eeprom(self, offset, data):
        # Note we expect this call to fail in certain scenarios, so ignore
        # results
        self.faft_client.system.run_shell_command_get_output(
             self._gen_write_command(offset, data))

    def _read_write_data(self, offset):
        before = self._read_eeprom(offset)
        logging.info("To reset CBI that's in a bad state, run w/ WP off:\n%s",
                     self._gen_write_command(offset, before))

        if before == self.TEST_EEPROM_DATA:
            write_data = self.TEST_EEPROM_DATA_2
        else:
            write_data = self.TEST_EEPROM_DATA

        self._write_eeprom(offset, write_data)

        after = self._read_eeprom(offset)

        return before, write_data, after

    def _reset_ec_and_wait_up(self):
        self.servo.set('cold_reset', 'on')
        self.servo.set('cold_reset', 'off')
        self.host.wait_up(timeout=30)

    def check_eeprom_write_protected(self):
        """Checks that CBI EEPROM cannot be written to when WP is asserted"""
        self.set_hardware_write_protect(True)
        offset = 0

        if self._wp_is_decoupled:
            # When the CBI WP is decoupled from the main system write protect,
            # the EC drives a latch which sets the CBI WP.  This latch is only
            # reset when EC_RST_ODL is asserted.  Since the WP has changed
            # above, toggle EC_RST_ODL in order to clear this latch.
            logging.info(
                    "CBI WP is EC driven, resetting EC before continuing...")
            self._reset_ec_and_wait_up()

            # Additionally, EC SW WP must be set in order for the system to be
            # locked, which is the criteria that the EC uses to assert CBI
            # EEPROM WP or not.
            cmd = 'flashrom -p ec --wp-enable'
            self.faft_client.system.run_shell_command(cmd)

        for offset in range(0, self.MAX_BYTES, self.PAGE_SIZE):
            before, write_data, after = self._read_write_data(offset)

            if before != after:
                # Set the data back to the original value before failing
                self._write_eeprom(offset, before)

                raise error.TestFail('EEPROM data changed with write protect '
                                     'enabled. Offset %d' % (offset))

        return True

    def check_eeprom_without_write_protected(self):
        """Checks that CBI EEPROM can be written to when WP is de-asserted"""
        self.set_hardware_write_protect(False)
        offset = 0

        if self._wp_is_decoupled:
            # When the CBI WP is decoupled from the main system write protect,
            # the EC drives a latch which sets the CBI WP.  This latch is only
            # reset when EC_RST_ODL is asserted.  Since the WP has changed
            # above, toggle EC_RST_ODL in order to clear this latch.
            logging.info(
                    "CBI WP is EC driven, resetting EC before continuing...")
            self._reset_ec_and_wait_up()

        for offset in range(0, self.MAX_BYTES, self.PAGE_SIZE):
            before, write_data, after = self._read_write_data(offset)

            if write_data != after:
                raise error.TestFail('EEPROM did not update with write protect '
                                     'disabled. Offset %d' % (offset))

            # Set the data back to the original value
            self._write_eeprom(offset, before)

        return True

    def cleanup(self):
        # Make sure to remove EC SW WP since we enabled it when testing
        if self._wp_is_decoupled:
            logging.debug("Disabling EC HW & SW WP...")
            self.set_hardware_write_protect(False)
            self._reset_ec_and_wait_up()
            cmd = 'flashrom -p ec --wp-disable'
            self.faft_client.system.run_shell_command(cmd)
        return super(firmware_ECCbiEeprom, self).cleanup()

    def run_once(self):
        """Execute the main body of the test."""

        logging.info("Checking CBI EEPROM with write protect off...")
        self.check_eeprom_without_write_protected()

        logging.info("Checking CBI EEPROM with write protect on...")
        self.check_eeprom_write_protected()
