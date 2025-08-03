# Copyright (c) 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.utils import crc8
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_TPMNotCorruptedDevMode(FirmwareTest):
    """
    Checks the kernel anti-rollback info stored in the TPM NVRAM, and then boots
    to USB and checks the firmware version and kernel version via crossystem for
    corruption.

    This test requires a USB disk plugged-in, which contains a ChromeOS test
    image (built by "build_image test").
    """
    version = 1
    NEEDS_SERVO_USB = True

    # The "what" of KERNEL_ANTIROLLBACK_SPACE_BYTES, KERNEL_NV_INDEX, and
    # TPM_NVRAM_EXPECTED_VALUES can be understood from
    # platform/vboot_reference/firmware/lib/include/rollback_index.h.
    KERNEL_ANTIROLLBACK_SPACE_BYTES = 0xd
    KERNEL_ANTIROLLBACK_SPACE_BYTES_v10 = 0x28
    KERNEL_NV_INDEX = 0x1008
    # TODO(kmshelton): This test intends to check whether the kernel version
    # stored in the TPM's NVRAM is corrupted during a transition from normal
    # mode to dev mode (according to it's objective in an internal tool named
    # test tracker).  Figure out why a specific kernel version is checked for,
    # and take a look at not requiring a specific kernel version. Examine
    # transitioning to crossystem for the normal mode read of the kernel
    # version (e.g. look at things like whether a successful exit code from
    # a crossystem reading of the kernel version can establish whether the
    # kernel version starts in a valid state).
    TPM_NVRAM_EXPECTED_VALUES = set([
            '1 4c 57 52 47 1 0 1 0 0 0 0 0', '2 4c 57 52 47 1 0 1 0 0 0 0 55',
            '2 4c 57 52 47 0 0 0 0 0 0 0 e8'
    ])

    # TPM kernel data format version 1.0
    #   byte[0]: version. bit[7:4]=Major, bit[3:0]=Minor.
    #   byte[1]: byte size. 0x28, 40(0x28) bytes
    #   byte[2]: crc8
    #   byte[3]: flag
    #   byte[7-4]: kernel version. 0x00010001
    #   byte[39:8]: ECFW hash in sha256.
    TPM_NVRAM_V10_HEADER = '10 28 '
    TPM_NVRAM_V10_CRC_OFFSET = 2

    def initialize(self, host, cmdline_args, ec_wp=None):
        """Initialize the test"""
        dict_args = utils.args_to_dict(cmdline_args)
        super(firmware_TPMNotCorruptedDevMode, self).initialize(
                host, cmdline_args, ec_wp=ec_wp)

        self.switcher.setup_mode('dev')
        # Use the USB key for Ctrl-U dev boot, not recovery.
        self.setup_usbkey(usbkey=True, host=False, used_for_recovery=False)

        self.original_dev_boot_usb = self.faft_client.system.get_dev_boot_usb()
        logging.info('Original dev_boot_usb value: %s',
                     str(self.original_dev_boot_usb))

    def cleanup(self):
        """Cleanup the test"""
        try:
            self.ensure_dev_internal_boot(self.original_dev_boot_usb)
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_TPMNotCorruptedDevMode, self).cleanup()

    def ensure_usb_device_boot(self):
        """Ensure USB device boot and if not reboot into USB."""
        if not self.faft_client.system.is_removable_device_boot():
            logging.info('Reboot into USB...')
            self.faft_client.system.set_dev_boot_usb(1)
            self.switcher.simple_reboot()
            self.switcher.bypass_dev_boot_usb()
            self.switcher.wait_for_client()

        self.check_state((self.checkers.dev_boot_usb_checker, (True, True),
                          'Device not booted from USB image properly.'))

    def read_tmpc(self):
        """First checks if internal device boot and if not reboots into it.
        Then stops the TPM daemon, reads the anti-rollback kernel version data
        and compares it to expected values.
        """
        self.ensure_dev_internal_boot(self.original_dev_boot_usb)
        logging.info('Reading kernel anti-rollback data from the TPM.')
        self.faft_client.tpm.stop_daemon()
        kernel_rollback_space = (
                self.faft_client.system.run_shell_command_get_output(
                        'tpmc read %s %s' %
                        (hex(self.KERNEL_NV_INDEX),
                         hex(self.KERNEL_ANTIROLLBACK_SPACE_BYTES))))
        self.faft_client.tpm.restart_daemon()

        logging.info('===== TPMC OUTPUT: %s =====', kernel_rollback_space)
        if not self.check_tpmc(kernel_rollback_space):
            raise error.TestFail(
                    'Kernel anti-rollback info looks unexpected. Actual: %s '
                    'Expected one of: %s or r%r' %
                    (kernel_rollback_space, self.TPM_NVRAM_EXPECTED_VALUES,
                     self.TPM_NVRAM_V10_REGEXP))

    def read_kern_fw_ver(self):
        """First ensures that we are booted on a USB device. Then checks the
        firmware and kernel version reported by crossystem.
        """
        self.ensure_usb_device_boot()
        logging.info('Checking kernel and fw version via crossystem.')

        if self.checkers.crossystem_checker({
                'tpm_fwver': '0xffffffff',
                'tpm_kernver': '0xffffffff',
        }):
            raise error.TestFail(
                    'Invalid kernel and firmware versions stored in the TPM.')

    def check_tpmc_v10(self):
        self.faft_client.tpm.stop_daemon()
        try:
            kernel_data = (
                    self.faft_client.system.run_shell_command_get_output(
                            'tpmc read %s %s' %
                            (hex(self.KERNEL_NV_INDEX),
                             hex(self.KERNEL_ANTIROLLBACK_SPACE_BYTES_v10)))
                    )[0].split()
        finally:
            self.faft_client.tpm.restart_daemon()

        # Convert string list to int list
        kernel_data_i = [ int(i, 16) for i in kernel_data ]

        # For all the bytes from offset 3, calculate CRC8.
        crc8_calc = crc8(kernel_data_i[(self.TPM_NVRAM_V10_CRC_OFFSET + 1):])

        if kernel_data_i[self.TPM_NVRAM_V10_CRC_OFFSET] != crc8_calc:
            logging.error('Kernel Data CRC(%s) is not correct: should be %s.',
                          hex(kernel_data_i[self.TPM_NVRAM_V10_CRC_OFFSET]),
                          hex(crc8_calc))
            return False

        logging.info('Kernel data v1.0 CRC is checked.')
        return True

    def check_tpmc(self, tpmc_output):
        """Checks that the kernel anti-rollback data from the tpmc read command
        is one of the expected values.
        """
        if len(tpmc_output) != 1:
            return False

        # Check if TPM kernel data is in the format of version 1.0.
        if tpmc_output[0].startswith(self.TPM_NVRAM_V10_HEADER):
            return self.check_tpmc_v10()

        return (tpmc_output[0] in self.TPM_NVRAM_EXPECTED_VALUES)

    def run_once(self):
        """Runs a single iteration of the test."""
        self.read_tmpc()
        self.read_kern_fw_ver()
