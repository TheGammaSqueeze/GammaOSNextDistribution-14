# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.faft.firmware_test import ConnectionError


BIOS = 'bios'
EC = 'ec'


class firmware_WriteProtectFunc(FirmwareTest):
    """
    This test checks whether the SPI flash write-protection functionally works
    """
    version = 1

    def initialize(self, host, cmdline_args, dev_mode=False):
        """Initialize the test"""
        super(firmware_WriteProtectFunc, self).initialize(host, cmdline_args)
        self.switcher.setup_mode('dev' if dev_mode else 'normal',
                                 allow_gbb_force=True)
        if self.faft_config.chrome_ec:
            self._targets = (BIOS, EC)
        else:
            self._targets = (BIOS, )
        self._rpcs = {BIOS: self.faft_client.bios,
                EC: self.faft_client.ec}
        self._flashrom_targets = {BIOS: 'host', EC: 'ec'}
        self._original_sw_wps = {}
        for target in self._targets:
            sw_wp_dict = self._rpcs[target].get_write_protect_status()
            logging.debug("self._rpcs[%s].get_write_protect_status() = %s",
                          target, sw_wp_dict)
            self._original_sw_wps[target] = sw_wp_dict['enabled']
        self._original_hw_wp = 'on' in self.servo.get('fw_wp_state')
        self.backup_firmware()
        self.work_path = self.faft_client.system.create_temp_dir(
                'flashrom_', '/mnt/stateful_partition/')

    def cleanup(self):
        """Cleanup the test"""
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
        except ConnectionError:
            logging.error("ERROR: DUT did not come up after firmware restore!")

        try:
            # Recover SW WP status.
            if hasattr(self, '_original_sw_wps'):
                # If HW WP is enabled, we have to disable it first so that
                # SW WP can be changed.
                current_hw_wp = 'on' in self.servo.get('fw_wp_state')
                if current_hw_wp:
                    self.set_ap_write_protect_and_reboot(False)
                for target, original_sw_wp in self._original_sw_wps.items():
                    self._set_write_protect(target, original_sw_wp)
                self.set_ap_write_protect_and_reboot(current_hw_wp)
            # Recover HW WP status.
            if hasattr(self, '_original_hw_wp'):
                self.set_ap_write_protect_and_reboot(self._original_hw_wp)
        except Exception as e:
            logging.error('Caught exception: %s', str(e))

        self.faft_client.system.remove_dir(self.work_path)
        super(firmware_WriteProtectFunc, self).cleanup()

    def _set_write_protect(self, target, enable):
        """
        Set write_protect to `enable` for the specified target.

        @param target: Which firmware to toggle the write-protect for,
                       either 'bios' or 'ec'
        @type target: string
        @param enable: Whether to enable or disable write-protect
        @type enable: bool
        """
        assert target in (BIOS, EC)
        if target == BIOS:
            # Unlock registers to alter the region/range
            self.set_ap_write_protect_and_reboot(False)
            self.faft_client.bios.set_write_protect_region('WP_RO', enable)
            if enable:
                self.set_ap_write_protect_and_reboot(True)
        elif target == EC:
            self.switcher.mode_aware_reboot('custom',
                    lambda:self.set_ec_write_protect_and_reboot(enable))

    def _get_relative_path(self, target):
        """
        Send an RPC.updater call to get the relative path for the target.

        @param target: Which firmware to get the relative path to,
                       either 'bios' or 'ec'.
        @type target: string
        @return: The relative path of the bios/ec image in the shellball.
        """
        assert target in (BIOS, EC)
        if target == BIOS:
            return self.faft_client.updater.get_bios_relative_path()
        elif target == EC:
            return self.faft_client.updater.get_ec_relative_path()

    def run_cmd(self, command, checkfor=''):
        """
        Log and execute command and return the output.

        @param command: Command to execute on device.
        @param checkfor: If not empty, make the test fail when this param
            is not found in the command output.
        @returns the output of command.
        """
        command = command + ' 2>&1'
        logging.info('Execute %s', command)
        output = self.faft_client.system.run_shell_command_get_output(command)
        logging.info('Output >>> %s <<<', output)
        if checkfor and checkfor not in '\n'.join(output):
            raise error.TestFail('Expect %s in output of cmd <%s>:\n\t%s' %
                                 (checkfor, command, '\n\t'.join(output)))
        return output

    def get_wp_ro_firmware_section(self, firmware_file, wp_ro_firmware_file):
        """
        Read out WP_RO section from the firmware file.

        @param firmware_file: The AP or EC firmware binary to be parsed.
        @param wp_ro_firmware_file: The file path for the WP_RO section
            dumped from the firmware_file.
        @returns the output of the dd command.
        """
        cmd_output = self.run_cmd(
                'futility dump_fmap -p %s WP_RO'% firmware_file)
        if cmd_output:
            unused_name, offset, size = cmd_output[0].split()

        return self.run_cmd('dd bs=1 skip=%s count=%s if=%s of=%s' %
                            (offset, size, firmware_file, wp_ro_firmware_file))

    def run_once(self):
        """Runs a single iteration of the test."""
        # Enable WP
        for target in self._targets:
            self._set_write_protect(target, True)

        # Check WP is properly enabled at the start
        for target in self._targets:
            sw_wp_dict = self._rpcs[target].get_write_protect_status()
            logging.debug("self._rpcs[%s].get_write_protect_status() = %s",
                          target, sw_wp_dict)
            if not sw_wp_dict['enabled']:
                raise error.TestFail('Failed to enable %s SW WP at '
                                     'test start' % target.upper())

        reboots = (('shutdown cmd', lambda:self.run_shutdown_process(
                                        lambda:self.run_shutdown_cmd())),
                   ('reboot cmd', lambda:self.run_cmd('reboot')),
                   ('power button', lambda:self.full_power_off_and_on()))

        if self.faft_config.chrome_ec:
            reboots += (('ec reboot', lambda:self.sync_and_ec_reboot('hard')), )

        # Check if enabled SW WP can stay preserved across reboots.
        for (reboot_name, reboot_method) in reboots:
            self.switcher.mode_aware_reboot('custom', reboot_method)
            for target in self._targets:
                sw_wp_dict = self._rpcs[target].get_write_protect_status()
                if not sw_wp_dict['enabled']:
                    raise error.TestFail('%s SW WP can not stay preserved '
                                         'accross %s' %
                                         (target.upper(), reboot_name))

        work_path = self.work_path
        # Check if RO FW really can't be overwritten when WP is enabled.
        for target in self._targets:
            # Current firmware image as read from flash
            ro_before = os.path.join(work_path, '%s_ro_before.bin' % target)
            # Current firmware image with modification to test writing
            ro_test = os.path.join(work_path, '%s_ro_test.bin' % target)
            # Firmware as read after writing flash
            ro_after = os.path.join(work_path, '%s_ro_after.bin' % target)

            # Fetch firmware from flash. This serves as the base of ro_test
            self.run_cmd(
                    'flashrom -p %s -r -i WP_RO:%s ' %
                    (self._flashrom_targets[target], ro_before), 'SUCCESS')

            lines = self.run_cmd('dump_fmap -p %s' % ro_before)
            FMAP_AREA_NAMES = ['name', 'offset', 'size']

            modified = False
            wpro_offset = -1
            for line in lines:
                region = dict(zip(FMAP_AREA_NAMES, line.split()))
                if region['name'] == 'WP_RO':
                    wpro_offset = int(region['offset'])
            if wpro_offset == -1:
                raise error.TestFail('WP_RO not found in fmap')
            for line in lines:
                region = dict(zip(FMAP_AREA_NAMES, line.split()))
                if region['name'] == 'RO_FRID':
                    modified = True
                    self.run_cmd('cp %s %s' % (ro_before, ro_test))
                    self.run_cmd(
                            'dd if=%s bs=1 count=%d skip=%d '
                            '| tr "[a-zA-Z]" "[A-Za-z]" '
                            '| dd of=%s bs=1 count=%d seek=%d conv=notrunc' %
                            (ro_test, int(region['size']),
                             int(region['offset']) - wpro_offset, ro_test,
                             int(region['size']),
                             int(region['offset']) - wpro_offset))

            if not modified:
                raise error.TestFail('Could not find RO_FRID in %s' %
                                     target.upper())

            # Writing WP_RO section is expected to fail.
            self.run_cmd('flashrom -p %s -w -i WP_RO:%s' %
                    (self._flashrom_targets[target], ro_test),
                    'FAIL')
            self.run_cmd('flashrom -p %s -r -i WP_RO:%s' %
                    (self._flashrom_targets[target], ro_after),
                    'SUCCESS')

            self.switcher.mode_aware_reboot(reboot_type='cold')

            # The WP_RO section on the DUT should not change.
            cmp_output = self.run_cmd('cmp %s %s' % (ro_before, ro_after))
            if ''.join(cmp_output) != '':
                raise error.TestFail('%s RO changes when WP is on!' %
                        target.upper())

        # Disable WP
        for target in self._targets:
            self._set_write_protect(target, False)

        # Check if RO FW can be overwritten when WP is disabled.
        for target in self._targets:
            ro_after = os.path.join(work_path, '%s_ro_after.bin' % target)
            ro_test = os.path.join(work_path, '%s_ro_test.bin' % target)

            # Writing WP_RO section is expected to succeed.
            self.run_cmd('flashrom -p %s -w -i WP_RO:%s' %
                    (self._flashrom_targets[target], ro_test),
                    'SUCCESS')
            self.run_cmd('flashrom -p %s -r -i WP_RO:%s' %
                    (self._flashrom_targets[target], ro_after),
                    'SUCCESS')

            # The DUT's WP_RO section should be the same as the test firmware.
            cmp_output = self.run_cmd('cmp %s %s' % (ro_test, ro_after))
            if ''.join(cmp_output) != '':
                raise error.TestFail('%s RO is not flashed correctly'
                                     'when WP is off!' % target.upper())
