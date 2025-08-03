# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_FWupdateWP(FirmwareTest):
    """RO+RW firmware update using chromeos-firmware, with WP=1 and with WP=0.
    It modifies the FWIDs of the current firmware before flashing, and restores
    the firmware after the test.
    """

    # Region to use for flashrom wp-region commands
    WP_REGION = 'WP_RO'
    MODE = 'recovery'

    def initialize(self, host, cmdline_args):

        self.flashed = False

        super(firmware_FWupdateWP, self).initialize(host, cmdline_args)

        self._old_bios_wp = self.faft_client.bios.get_write_protect_status()

        stripped_bios = self.faft_client.bios.strip_modified_fwids()
        if stripped_bios:
            logging.warning(
                    "Fixed the previously modified BIOS FWID(s): %s",
                    stripped_bios)

        if self.faft_config.chrome_ec:
            stripped_ec = self.faft_client.ec.strip_modified_fwids()
            if stripped_ec:
                logging.warning(
                        "Fixed the previously modified EC FWID(s): %s",
                        stripped_ec)

        self.backup_firmware()

        self.set_ap_write_protect_and_reboot(False)
        self.faft_client.bios.set_write_protect_region(self.WP_REGION, True)
        self.set_ap_write_protect_and_reboot(True)

    def get_installed_versions(self):
        """Get the installed versions of BIOS and EC firmware.

        @return: A nested dict keyed by target ('bios' or 'ec') and then section
        @rtype: dict
        """
        vers = dict()
        vers['bios'] = self.faft_client.updater.get_device_fwids('bios')
        if self.faft_config.chrome_ec:
            vers['ec'] = self.faft_client.updater.get_device_fwids('ec')
        return vers

    def run_case(self, append, write_protected, before_fwids, image_fwids):
        """Run chromeos-firmwareupdate with given sub-case

        @param append: additional piece to add to shellball name
        @param write_protected: is the flash write protected (--wp)?
        @param before_fwids: fwids before flashing ('bios' and 'ec' as keys)
        @param image_fwids: fwids in image ('bios' and 'ec' as keys)
        @return: a list of failure messages for the case
        """

        cmd_desc = ('chromeos-firmwareupdate --mode=%s [wp=%s]'
                    % (self.MODE, write_protected))

        # Unlock the protection of the wp-enable and wp-range registers
        self.set_ap_write_protect_and_reboot(False)

        if write_protected:
            self.faft_client.bios.set_write_protect_region(self.WP_REGION, True)
            self.set_ap_write_protect_and_reboot(True)
        else:
            self.faft_client.bios.set_write_protect_region(
                    self.WP_REGION, False)

        expected_written = {}

        if write_protected:
            bios_written = ['a', 'b']
            ec_written = []  # EC write is all-or-nothing

        else:
            bios_written = ['ro', 'a', 'b']
            ec_written = ['ro', 'rw']

        expected_written['bios'] = bios_written

        if self.faft_config.chrome_ec and ec_written:
            expected_written['ec'] = ec_written

        # bios: [a, b], ec: [ro, rw]
        written_desc = repr(expected_written).replace("'", "")[1:-1]
        logging.debug('Before(%s): %s', append, before_fwids)
        logging.debug('Image(%s):  %s', append, image_fwids)
        logging.info("Run %s (should write %s)", cmd_desc, written_desc)

        # make sure we restore firmware after the test, if it tried to flash.
        self.flashed = True

        errors = []
        options = ['--quirks=ec_partial_recovery=0']
        result = self.run_chromeos_firmwareupdate(
                self.MODE, append, options, ignore_status=True)

        if result.exit_status == 255:
            logging.info("DUT network dropped during update.")
        elif result.exit_status != 0:
            if (image_fwids == before_fwids and
                    'Good. It seems nothing was changed.' in result.stdout):
                logging.info("DUT already matched the image; updater aborted.")
            else:
                errors.append('...updater: unexpectedly failed (rc=%s)' %
                              result.exit_status)

        after_fwids = self.get_installed_versions()
        logging.debug('After(%s):  %s', append, after_fwids)

        errors += self.check_fwids_written(
                before_fwids, image_fwids, after_fwids, expected_written)

        if errors:
            logging.debug('%s', '\n'.join(errors))
            return ["%s (should write %s)\n%s"
                    % (cmd_desc, written_desc, '\n'.join(errors))]
        else:
            return []

    def run_once(self, host):
        """Run chromeos-firmwareupdate with recovery or factory mode.

        @param host: host to run on
        """
        append = 'new'
        have_ec = bool(self.faft_config.chrome_ec)

        self.faft_client.updater.extract_shellball()

        before_fwids = self.get_installed_versions()

        # Modify the stock image
        logging.info(
                "Using the currently running firmware, with modified fwids")
        self.setup_firmwareupdate_shellball()
        self.faft_client.updater.reload_images()
        self.modify_shellball(append, modify_ro=True, modify_ec=have_ec)
        modded_fwids = self.identify_shellball(include_ec=have_ec)

        fail_msg = "Section contents didn't show the expected changes."

        errors = []
        # no args specified, so check both wp=1 and wp=0
        errors += self.run_case(append, 1, before_fwids, modded_fwids)
        errors += self.run_case(append, 0, before_fwids, modded_fwids)

        if errors:
            raise error.TestFail("%s\n%s" % (fail_msg, '\n'.join(errors)))

    def cleanup(self):
        """
        Restore firmware from the backup taken before flashing.
        No EC reboot is needed, because the test doesn't actually reboot the EC
        with the "new" firmware.
        """
        self.set_ap_write_protect_and_reboot(False)
        self.faft_client.bios.set_write_protect_range(0, 0, False)

        if self.flashed:
            logging.info("Restoring firmware")
            self.restore_firmware()

        # Restore the old write-protection value at the end of the test.
        self.faft_client.bios.set_write_protect_range(
                self._old_bios_wp['start'],
                self._old_bios_wp['length'],
                self._old_bios_wp['enabled'])

        super(firmware_FWupdateWP, self).cleanup()
