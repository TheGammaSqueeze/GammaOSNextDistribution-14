# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import six

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.power import utils as PowerUtils


class firmware_FWupdateThenSleep(FirmwareTest):
    """Firmware update using chromeos-firmwareupdate --mode=recovery, then
    sleep and wake, then make sure the host responds and the flash contents
    don't change.
    """
    MODE = 'recovery'
    MIN_BATTERY_LEVEL = 20

    def initialize(self, host, cmdline_args, battery_only=False):

        self.flashed = False
        self._want_restore = None
        self._original_sw_wp = {}
        self._original_hw_wp = None

        super(firmware_FWupdateThenSleep, self).initialize(host, cmdline_args)

        ac_online = self._client.is_ac_connected()
        battery_percentage = self._client.get_battery_display_percentage()
        self.have_power_control = False
        if battery_only:
            if not self._client.has_battery():
                raise error.TestNAError("DUT type does not have a battery.")

            if self.servo.supports_built_in_pd_control():
                self.have_power_control = True
                PowerUtils.put_host_battery_in_range(self._client,
                                                     min_level=25,
                                                     max_level=100,
                                                     timeout=600)
            elif ac_online:
                raise error.TestNAError(
                    "For this version of the test, the DUT power supply must"
                    " be unplugged, or connected through Servo V4 Type-C.")
            elif battery_percentage < self.MIN_BATTERY_LEVEL:
                raise error.TestError(
                    "Battery level is too low (%s%%).  Please charge the DUT "
                    "to at least %s%%, then try again."
                    % (battery_percentage, self.MIN_BATTERY_LEVEL))
            else:
                logging.info("On battery: %s%% charge", battery_percentage)

        self._original_sw_wp = self.faft_client.bios.get_write_protect_status()
        self._original_hw_wp = 'on' in self.servo.get('fw_wp_state')

        self.backup_firmware()
        self.setup_firmwareupdate_shellball()

        if self.faft_config.ap_access_ec_flash:
            self._setup_ec_write_protect(False)
        self.set_ap_write_protect_and_reboot(False)
        self.faft_client.bios.set_write_protect_range(0, 0, False)

        if battery_only and self.have_power_control:
            self.set_servo_v4_role_to_snk()

    def cleanup(self):
        """Restore the original firmware and original write-protect."""
        self._restore_servo_v4_role()

        self.set_ap_write_protect_and_reboot(False)
        try:
            if self.flashed and self.is_firmware_saved():
                self.restore_firmware()
        except (EnvironmentError, six.moves.xmlrpc_client.Fault,
                error.AutoservError, error.TestBaseException):
            logging.error("Problem restoring firmware:", exc_info=True)

        try:
            # Restore the old write-protection value at the end of the test.
            if self._original_sw_wp:
                self.faft_client.bios.set_write_protect_range(
                        self._original_sw_wp['start'],
                        self._original_sw_wp['length'],
                        self._original_sw_wp['enabled'])
        except (EnvironmentError, six.moves.xmlrpc_client.Fault,
                error.AutoservError, error.TestBaseException):
            logging.error("Problem restoring SW write-protect:", exc_info=True)

        if self._original_hw_wp is not None:
            self.set_ap_write_protect_and_reboot(self._original_hw_wp)

        super(firmware_FWupdateThenSleep, self).cleanup()

    def get_installed_versions(self):
        """Get the installed versions of BIOS and EC firmware.

        @return: A nested dict keyed by target ('bios' or 'ec') and then section
        @rtype: dict
        """
        versions = dict()
        versions['bios'] = self.faft_client.updater.get_device_fwids('bios')
        if self.faft_config.chrome_ec:
            versions['ec'] = self.faft_client.updater.get_device_fwids('ec')
        return versions

    def apply_update(self, append, before_fwids, image_fwids, wp=0):
        """Run chromeos-firmwareupdate with given sub-case

        @param append: additional piece to add to shellball name
        @param wp: is the flash write protected (--wp)?
        @return: a list of failure messages for the case
        """
        options = ['--wp=%s' % wp, '--quirks=ec_partial_recovery=0']

        if append:
            cmd_desc = ['chromeos-firmwareupdate-%s' % append]
        else:
            cmd_desc = ['chromeos-firmwareupdate']
        cmd_desc += ['--mode=%s' % self.MODE]
        cmd_desc += options
        cmd_desc = ' '.join(cmd_desc)

        expected_written = {}

        if wp:
            bios_written = ['a', 'b']
            ec_written = []  # EC write is all-or-nothing
        else:
            bios_written = ['ro', 'a', 'b']
            ec_written = ['ro', 'rw']

        expected_written['bios'] = bios_written

        if self.faft_config.chrome_ec and ec_written:
            expected_written['ec'] = ec_written

        # remove quotes and braces: bios: [a, b], ec: [ro, rw]
        written_desc = repr(expected_written).replace("'", "")[1:-1]
        logging.debug('Before(%s): %s', append or '', before_fwids)
        logging.debug('Image(%s):  %s', append or '', image_fwids)
        logging.info("Run %s (should write %s)", cmd_desc, written_desc)

        # make sure we restore firmware after the test, if it tried to flash.
        self.flashed = True

        errors = []
        result = self.run_chromeos_firmwareupdate(
                self.MODE, append, options, ignore_status=True)

        if result.exit_status == 255:
            logging.warning("DUT network dropped during update.")
        elif result.exit_status != 0:
            if (image_fwids == before_fwids and
                    'Good. It seems nothing was changed.' in result.stdout):
                logging.info("DUT already matched the image; updater aborted.")
            else:
                errors.append('...updater: unexpectedly failed (rc=%s)' %
                              result.exit_status)

        after_fwids = self.get_installed_versions()
        logging.debug('After(%s):  %s', append or '', after_fwids)

        errors += self.check_fwids_written(
                before_fwids, image_fwids, after_fwids, expected_written)

        if errors:
            logging.debug('%s', '\n'.join(errors))
            return ["%s (should write %s)\n%s"
                    % (cmd_desc, written_desc, '\n'.join(errors))]
        return []

    def run_once(self, wp=0, battery_only=False):
        """Try a firmware update, then put the machine in sleep and wake it."""

        errors = []
        original_boot_id = self._client.get_boot_id()

        have_ec = bool(self.faft_config.chrome_ec)
        before_fwids = self.get_installed_versions()

        self.modify_shellball('mod', modify_ro=True, modify_ec=have_ec)
        modded_fwids = self.identify_shellball(include_ec=have_ec)

        # Check power again, in case it got reconnected during setup.
        if battery_only and self._client.is_ac_connected():
            if self.have_power_control:
                raise error.TestError(
                    "DUT is on AC power, even though Servo role was set to"
                    " sink.  Is a second power supply connected?")
            else:
                raise error.TestError(
                    "DUT is back on AC power, when it should be on battery."
                    "  Did it get plugged back in?")

        errors += self.apply_update('mod', before_fwids, modded_fwids, wp=wp)
        if errors:
            fail_msg = "Problem(s) occurred during flash, before sleep:"
            errors.insert(0, fail_msg)
            raise error.TestFail('\n'.join(errors))

        logging.info("Firmware flashed successfully; trying sleep.")
        before_sleep_fwids = self.get_installed_versions()
        self.suspend()
        if self.faft_config.chrome_ec:
            if not self.wait_power_state(
                    self.POWER_STATE_SUSPEND, self.DEFAULT_PWR_RETRIES):
                raise error.TestFail('Platform failed to reach a suspend state'
                                     ' after flashing firmware.')
        else:
            self.switcher.wait_for_client_offline(orig_boot_id=original_boot_id)
        logging.info("System should now be in sleep; trying to wake.")

        self.servo.power_normal_press()
        self.switcher.wait_for_client()

        after_sleep_fwids = self.get_installed_versions()
        logging.debug('After sleep+wake:  %s', after_sleep_fwids)

        if have_ec:
            expected_changes = {'bios': None, 'ec': None}
        else:
            expected_changes = {'bios': None}

        after_sleep_errors = self.check_fwids_written(
                before_sleep_fwids, None, after_sleep_fwids, expected_changes)
        if after_sleep_errors:
            errors += ['After sleep, FWIDs unexpectedly differ from'
                       ' what was written:'] + after_sleep_errors

        if errors:
            fail_msg = "Problem(s) occurred during flash + sleep + wake:"
            errors.insert(0, fail_msg)
            raise error.TestFail('\n'.join(errors))
