# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import os
import re
import logging

from autotest_lib.server.cros.faft.fingerprint_test import FingerprintTest
from autotest_lib.client.common_lib import error


class firmware_Fingerprint(FingerprintTest):
    """
    Common class for running fingerprint firmware tests. Initializes the
    firmware to a known state and then runs the test executable with
    specified arguments on the DUT.
    """
    version = 1

    def run_once(self, test_exe, test_exe_args=None,
                 use_dev_signed_fw=False,
                 enable_hardware_write_protect=True,
                 enable_software_write_protect=True,
                 force_firmware_flashing=False,
                 init_entropy=True):
        """Run the test."""
        test_dir = os.path.join(self.bindir, 'tests/')
        logging.info('test_dir: %s', test_dir)

        # Initialize DUT state and set up tmp working directory on device.
        self.setup_test(
            test_dir, use_dev_signed_fw, enable_hardware_write_protect,
            enable_software_write_protect, force_firmware_flashing,
            init_entropy)

        # Check if FPMCU firmware needs to be re-flashed during cleanup
        self._need_fw_restore = True
        self._test_exe = test_exe

        # Convert the arguments (test image names) to the actual filenames of
        # the test images.
        image_args = []
        if test_exe_args:
            for arg in test_exe_args:
                image_args.append(getattr(self, arg))
        self._test_exe_args = image_args

        if self.is_uart_device():
            # TODO(b/170770251): Move the rdp1 and rdp0 tests to separate files
            #
            # On devices with UART, RDP1 and RDP0 tests requires an AP reboot.
            if self._test_exe == 'rdp1.sh':
                self.test_rdp1()
            elif self._test_exe == 'rdp0.sh':
                self.test_rdp0()
        else:
            logging.info('Running test: %s', self._test_exe)
            self.run_test(self._test_exe, *self._test_exe_args)

    def test_rdp1(self):
        """
        Validate initial state for the RDP1 test. The test tries to read from
        flash while maintaining RDP level 1. Then it tries to read from flash
        while changing RDP level to 0.
        """
        if self.get_fp_board() == 'bloonchipper':
            _HW_WP_OFF_AND_SW_WP_ON = (
                    'Flash protect flags: 0x00000407 ro_at_boot ro_now rollback_now all_now\n'
                    'Valid flags:         0x0000083f wp_gpio_asserted ro_at_boot ro_now all_now STUCK INCONSISTENT UNKNOWN_ERROR\n'
                    'Writable flags:      0x00000000\n')
        else:
            _HW_WP_OFF_AND_SW_WP_ON = (
                    'Flash protect flags: 0x00000003 ro_at_boot ro_now\n'
                    'Valid flags:         0x0000083f wp_gpio_asserted ro_at_boot ro_now all_now STUCK INCONSISTENT UNKNOWN_ERROR\n'
                    'Writable flags:      0x00000000\n')

        logging.info('Running test to validate RDP level 1')
        original_fw_file = self._test_exe_args[0]
        self.check_file_exists(original_fw_file)

        logging.info('Making sure hardware write protect is DISABLED and '
                     'software write protect is ENABLED')
        flashprotect_result = self._run_ectool_cmd('flashprotect')
        if flashprotect_result.stdout != _HW_WP_OFF_AND_SW_WP_ON:
            raise error.TestFail('Incorrect flashprotect state')

        logging.info('Validating initial state')
        # TODO(yichengli): Check that we are running MP-signed RO and RW by
        # checking the key id.
        if self.get_running_firmware_type() != self._FIRMWARE_TYPE_RW:
            raise error.TestFail('Not running RW copy of firmware')
        if not self.is_rollback_set_to_initial_val():
            raise error.TestFail('Rollback is not set to initial value')

        self.test_rdp1_without_modifying_rdp_level()
        self.test_rdp1_while_setting_rdp_level_0()

    def test_rdp0(self):
        """
        Validate initial state for the RDP0 test. The test tries to read from
        flash while maintaining RDP level 0. Then it tries to read from flash
        while setting RDP level to 0.
        """
        _HW_AND_SW_WP_OFF = (
                'Flash protect flags: 0x00000000\n'
                'Valid flags:         0x0000083f wp_gpio_asserted ro_at_boot ro_now all_now STUCK INCONSISTENT UNKNOWN_ERROR\n'
                'Writable flags:      0x00000001 ro_at_boot\n')

        logging.info('Running test to validate RDP level 0')
        original_fw_file = self._test_exe_args[0]
        self.check_file_exists(original_fw_file)

        logging.info('Making sure all write protect is disabled')
        flashprotect_result = self._run_ectool_cmd('flashprotect')
        if flashprotect_result.stdout != _HW_AND_SW_WP_OFF:
            raise error.TestFail('Incorrect flashprotect state')

        logging.info('Validating initial state')
        # TODO(yichengli): Check that we are running MP-signed RO and RW by
        # checking the key id.
        if self.get_running_firmware_type() != self._FIRMWARE_TYPE_RW:
            raise error.TestFail('Not running RW copy of firmware')
        if not self.is_rollback_unset():
            raise error.TestFail('Rollback should be unset.')

        self.check_firmware_is_functional()

        self.test_rdp0_without_modifying_rdp_level()
        self.test_rdp0_while_setting_rdp_level_0()

    def test_rdp1_without_modifying_rdp_level(self):
        """
        Given:
           * Hardware write protect is disabled
               (so we can use bootloader to read and change RDP level)
           * Software write protect is enabled
           * RDP is at level 1

        Then:
           * Reading from flash without changing the RDP level should fail
             (and we should not have read any bytes from flash).
           * The firmware should still be functional because mass erase is NOT
             triggered since we are NOT changing the RDP level.
        """
        logging.info('Reading firmware without modifying RDP level')

        # This should fail and the file should be empty
        file_read_from_flash = os.path.join(self._dut_working_dir,
                                            'test_keep_rdp.bin')
        cmd = 'flash_fp_mcu --noservices --read' + \
            ' --noremove_flash_read_protect %s' % file_read_from_flash
        result = self.run_cmd(cmd)
        if result.exit_status == 0:
            raise error.TestFail('Should not be able to read from flash')

        logging.info('Checking file_read_from_flash is empty')
        if self.get_file_size(file_read_from_flash) != 0:
            raise error.TestFail('File read from flash is not empty')

        # On devices with UART, an AP reboot is needed after using flash_fp_mcu.
        if self.is_uart_device():
            self.host.reboot()

        self.check_firmware_is_functional()

    def test_rdp1_while_setting_rdp_level_0(self):
        """
        Given:
           * Hardware write protect is disabled
               (so we can use bootloader to read and change RDP level)
           * Software write protect is enabled
           * RDP is at level 1

        Then:
           * Setting the RDP level to 0 (after being at level 1) should trigger
             a mass erase.
           * A mass erase sets all flash bytes to 0xFF, so all bytes read from flash
             should have that value.
           * Since the flash was mass erased, the firmware should no longer function.
        """
        logging.info('Reading firmware after setting RDP to level 0')

        # This command partially fails (and returns an error) because it causes the
        # flash to be mass erased, but we should still have a file with the contents
        # that we can compare against.

        file_read_from_flash = os.path.join(self._dut_working_dir,
                                            'test_change_rdp.bin')
        cmd = 'flash_fp_mcu --noservices --read %s' % file_read_from_flash
        self.run_cmd(cmd)

        logging.info(
                'Checking that value read is made up entirely of OxFF bytes')
        original_fw_file = self._test_exe_args[0]
        if self.get_file_size(original_fw_file) != self.get_file_size(
                file_read_from_flash):
            raise error.TestFail(
                    'Flash read output size doesn\'t match original fw size')
        self.check_file_contains_all_0xFF_bytes(file_read_from_flash)

        # On devices with UART, an AP reboot is needed after using flash_fp_mcu.
        if self.is_uart_device():
            self.host.reboot()

        logging.info('Checking that firmware is non-functional')
        result = self._run_ectool_cmd('version')
        if result.exit_status == 0:
            raise error.TestFail(
                    'Firmware should not be responding to commands')

    def test_rdp0_without_modifying_rdp_level(self):
        """
        Given:
           * Hardware write protect is disabled
           * Software write protect is disabled
           * RDP is at level 0

        Then:
           * Reading from flash without changing the RDP level should succeed
             (we're already at level 0). Thus we should be able to read the
             entire firmware out of flash and it should exactly match the
             firmware that we flashed for testing.
        """
        logging.info('Reading firmware without modifying RDP level')

        file_read_from_flash = os.path.join(self._dut_working_dir,
                                            'test_keep_rdp.bin')
        cmd = 'flash_fp_mcu --noservices --read' + \
            ' --noremove_flash_read_protect %s' % file_read_from_flash
        result = self.run_cmd(cmd)
        if result.exit_status != 0:
            raise error.TestFail('Failed to read from flash')

        logging.info('Checking that value read matches the flashed version')
        original_fw_file = self._test_exe_args[0]
        if not self.files_match(file_read_from_flash, original_fw_file):
            raise error.TestFail(
                    'File read from flash does not match original fw file')

        # On devices with UART, an AP reboot is needed after using flash_fp_mcu.
        if self.is_uart_device():
            self.host.reboot()

        self.check_firmware_is_functional()

    def test_rdp0_while_setting_rdp_level_0(self):
        """
        Given:
           * Hardware write protect is disabled
           * Software write protect is disabled
           * RDP is at level 0

        Then:
           * Changing the RDP level to 0 should have no effect
             (we're already at level 0). Thus we should be able to read the
             entire firmware out of flash and it should exactly match the
             firmware that we flashed for testing.
        """
        logging.info('Reading firmware while setting RDP to level 0')

        file_read_from_flash = os.path.join(self._dut_working_dir,
                                            'test_change_rdp.bin')
        cmd = 'flash_fp_mcu --noservices --read %s' % file_read_from_flash
        result = self.run_cmd(cmd)
        if result.exit_status != 0:
            raise error.TestFail('Failed to read from flash')

        logging.info('Checking that value read matches the flashed version')
        original_fw_file = self._test_exe_args[0]
        if not self.files_match(file_read_from_flash, original_fw_file):
            raise error.TestFail(
                    'File read from flash does not match original fw file')

        # On devices with UART, an AP reboot is needed after using flash_fp_mcu.
        if self.is_uart_device():
            self.host.reboot()

        self.check_firmware_is_functional()

    def check_file_exists(self, filename):
        """Checks that |filename| exists on DUT. Fails the test otherwise."""
        if not self.host.is_file_exists(filename):
            raise error.TestFail('Cannot find file: %s' % filename)

    def get_file_size(self, filename):
        """Returns the size of |filename| on DUT. Fails the test on error."""
        cmd = 'stat --printf %%s %s' % filename
        result = self.run_cmd(cmd)
        if result.exit_status != 0 or not result.stdout.isdigit():
            raise error.TestFail('Cannot get the size of file: %s' % filename)
        return int(result.stdout)

    def files_match(self, filename1, filename2):
        """Returns True if two files are identical, False otherwise."""
        cmd = 'cmp %s %s' % (filename1, filename2)
        return self.run_cmd(cmd).exit_status == 0

    def check_file_contains_all_0xFF_bytes(self, file_to_check):
        """
        Checks that |file_to_check| is made of only 0xFF bytes.
        Fails the test otherwise.
        """
        regex = '0000000 ffff ffff ffff ffff ffff ffff ffff ffff\n\*\n[0-9]+\n$'
        cmd = 'hexdump %s' % file_to_check
        result = self.run_cmd(cmd)
        if not re.match(regex, result.stdout):
            raise error.TestFail('%s does not contain all 0xFF bytes' %
                                 file_to_check)

    def check_firmware_is_functional(self):
        """
        Returns true if AP can talk to FPMCU firmware. Fails the test otherwise
        """
        logging.info('Checking that firmware is functional')
        # Catch exception to show better error message.
        try:
            self.get_running_firmware_type()
        except error.TestFail:
            raise error.TestFail('Firmware is not functional')
