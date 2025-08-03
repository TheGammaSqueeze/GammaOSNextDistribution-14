# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os
import six

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_FWupdate(FirmwareTest):
    """RO+RW firmware update using chromeos-firmware with various modes.
    If restore=false is given, the DUT is left running that firmware, so the
    test can be used to apply updates.

    Accepted --args names:
    old_bios=
    old_bios_ro=
    old_bios_rw=
    old_ec=
    old_pd=
    new_bios=
    new_bios_ro=
    new_bios_rw=
    new_ec=
    new_pd=
        apply the given image(s)
    restore=false|true (default true)
        if value is anything but 'false', once the test ends, the firmware will
        be restored to the backup that was made at the start of the test.
    """
    # Region to use for flashrom wp-region commands
    WP_REGION = 'WP_RO'

    MODE = 'recovery'

    def initialize(self, host, cmdline_args):

        self.flashed = False
        self._want_restore = None
        self._orig_sw_wp = {}
        self._orig_hw_wp = None

        dict_args = utils.args_to_dict(cmdline_args)

        if dict_args.get('restore', '').lower() == 'false':
            self._want_restore = False
        else:
            self._want_restore = True

        self.images = {}

        for old_or_new in ('old', 'new'):
            for target in ('bios', 'bios_ro', 'bios_rw', 'ec', 'pd'):
                arg_name = '%s_%s' % (old_or_new, target)
                arg_value = dict_args.get(arg_name)
                if arg_value:
                    logging.info('%s=%s', arg_name, arg_value)
                    image_path = os.path.expanduser(arg_value)
                    if not os.path.isabs(image_path):
                        raise error.TestError(
                            'Specified path must be absolute: %s=%s'
                            % (arg_name, arg_value))
                    if not os.path.isfile(image_path):
                        raise error.TestError(
                            'Specified file does not exist: %s=%s'
                            % (arg_name, arg_value))
                    self.images[arg_name] = image_path

        self.old_bios = self.images.get('old_bios')
        self.old_bios_ro = self.images.get('old_bios_ro')
        self.old_bios_rw = self.images.get('old_bios_rw')

        self.new_bios = self.images.get('new_bios')
        self.new_bios_ro = self.images.get('new_bios_ro')
        self.new_bios_rw = self.images.get('new_bios_rw')

        if not (self.new_bios or self.new_bios_rw):
            raise error.TestError('Must specify at least new_bios=<path>'
                                  ' or new_bios_rw=<path>')

        super(firmware_FWupdate, self).initialize(host, cmdline_args)

        self._orig_sw_wp = self.faft_client.bios.get_write_protect_status()

        self.backup_firmware()

        if self.faft_config.ap_access_ec_flash:
            self._setup_ec_write_protect(False)

        self._original_hw_wp = 'on' in self.servo.get('fw_wp_state')

        self.set_ap_write_protect_and_reboot(False)
        self.faft_client.bios.set_write_protect_region(self.WP_REGION, True)
        self.set_ap_write_protect_and_reboot(True)

    def cleanup(self):
        """Restore write protection, unless "restore" was false."""
        if not hasattr(self, 'run_id'):
            # Exited very early during initialize, so no cleanup needed
            return

        self.set_ap_write_protect_and_reboot(False)
        try:
            if self.flashed and self._want_restore and self.is_firmware_saved():
                self.restore_firmware()
        except (EnvironmentError, six.moves.xmlrpc_client.Fault,
                error.AutoservError, error.TestBaseException):
            logging.error("Problem restoring firmware:", exc_info=True)

        try:
            # Restore the old write-protection value at the end of the test.
            if self._orig_sw_wp:
                self.faft_client.bios.set_write_protect_range(
                        self._orig_sw_wp['start'],
                        self._orig_sw_wp['length'],
                        self._orig_sw_wp['enabled'])
        except (EnvironmentError, six.moves.xmlrpc_client.Fault,
                error.AutoservError, error.TestBaseException):
            logging.error("Problem restoring software write-protect:",
                          exc_info=True)

        if self._orig_hw_wp is not None:
            self.set_ap_write_protect_and_reboot(self._orig_hw_wp)
        elif hasattr(self, 'ec'):
            self.sync_and_ec_reboot()

        super(firmware_FWupdate, self).cleanup()

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

    def check_bios_specified(self,
            old_ro=False, old_rw=False, new_ro=False, new_rw=False):
        """Check if the required --args were specified.

        @raise error.TestError: if args required for the test were not given
        """
        missing = set()
        if old_ro and not (self.old_bios_ro or self.old_bios):
            missing.add('old_bios[_ro]=')
        if old_rw and not (self.old_bios_rw or self.old_bios):
            missing.add('old_bios[_rw]=')
        if new_ro and not (self.new_bios_ro or self.new_bios):
            missing.add('new_bios[_ro]=')
        if new_rw and not (self.new_bios_rw or self.new_bios):
            missing.add('new_bios[_rw]=')
        if missing:
            raise error.TestError('Must specify args: %s' % '; '.join(missing))

    def copy_cmdline_images(self, old_or_new, section=None):
        """Copy the specified command line images into the extracted shellball.

        @param old_or_new: 'old' or 'new', to select a set from self.images
        @param section: 'ro' or 'rw', to use bios_ro or bios_rw.
        """
        local_bios = (
            self.images.get('%s_bios_%s' % (old_or_new, section)) or
            self.images.get('%s_bios' % old_or_new)
        )
        local_ec = self.images.get('%s_ec' % old_or_new)
        local_pd = self.images.get('%s_pd' % old_or_new)

        extract_dir = self.faft_client.updater.get_work_path()

        if local_bios:
            bios_rel = self.faft_client.updater.get_bios_relative_path()
            remote_bios = os.path.join(extract_dir, bios_rel)
            self._client.send_file(local_bios, remote_bios)

        if local_ec:
            ec_rel = self.faft_client.updater.get_ec_relative_path()
            remote_ec = os.path.join(extract_dir, ec_rel)
            self._client.send_file(local_ec, remote_ec)

        if local_pd:
            # note: pd.bin might likewise need special path logic
            remote_pd = os.path.join(extract_dir, 'pd.bin')
            self._client.send_file(local_pd, remote_pd)

    def prepare_shellball(self, old_or_new, ro_or_rw=None):
        """Prepare a shellball with the given set of images (old or new).

        @param old_or_new: 'old' or 'new', to select a set from self.images
        @param section: 'ro' or 'rw', to use bios_ro or bios_rw.
        """
        self.faft_client.updater.reset_shellball()
        self.copy_cmdline_images(old_or_new, ro_or_rw)
        self.faft_client.updater.reload_images()
        self.faft_client.updater.repack_shellball(old_or_new)

    def run_shellball(self, append, wp, host_only=False):
        """Run chromeos-firmwareupdate with given sub-case

        @param append: additional piece to add to shellball name
        @param wp: is the flash write protected (--wp)?
        @return: a list of failure messages for the case
        """
        have_ec = bool(self.faft_config.chrome_ec)
        options = []

        if host_only:
            options += ['--host_only']
        options += ['--quirks=ec_partial_recovery=0']

        before_fwids = self.get_installed_versions()
        image_fwids = self.identify_shellball(include_ec=have_ec)

        # Unlock the protection of the wp-enable and wp-range registers
        self.set_ap_write_protect_and_reboot(False)

        if wp:
            self.faft_client.bios.set_write_protect_region(
                    self.WP_REGION, True)
            self.set_ap_write_protect_and_reboot(True)
        else:
            self.faft_client.bios.set_write_protect_region(
                    self.WP_REGION, False)

        cmd_desc = ['chromeos-firmwareupdate-%s' % append,
                    '--mode=%s' % self.MODE]
        cmd_desc += options
        cmd_desc += ['[wp=%s]' % wp]
        cmd_desc = ' '.join(cmd_desc)

        expected_written = {}

        if wp:
            bios_written = ['a', 'b']
            ec_written = []  # EC write is all-or-nothing

        elif host_only:
            bios_written = ['ro', 'a', 'b']
            ec_written = []

        else:
            bios_written = ['ro', 'a', 'b']
            ec_written = ['ro', 'rw']

        expected_written['bios'] = bios_written

        if self.faft_config.chrome_ec and ec_written:
            expected_written['ec'] = ec_written

        # remove quotes and braces: bios: [a, b], ec: [ro, rw]
        written_desc = repr(expected_written).replace("'", "")[1:-1]
        logging.debug('Before(%s): %s', append, before_fwids)
        logging.debug('Image(%s):  %s', append, image_fwids)
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
        logging.debug('After(%s):  %s', append, after_fwids)

        errors += self.check_fwids_written(
                before_fwids, image_fwids, after_fwids, expected_written)

        if errors:
            logging.debug('%s', '\n'.join(errors))
            return ["%s (should write %s)\n%s"
                    % (cmd_desc, written_desc, '\n'.join(errors))]
        return []

    def test_upgrade_rw(self, raise_error=True):
        """Test case: RO=old, RW=new"""
        logging.info('%s', self.test_upgrade_rw.__doc__)
        self.check_bios_specified(old_ro=True, new_rw=True)

        errors = []

        # wp=0: update RO+RW
        self.prepare_shellball('old', 'ro')
        errors += self.run_shellball('old', wp=0)

        # wp=1: update RW
        self.prepare_shellball('new', 'rw')
        errors += self.run_shellball('new', wp=1)

        self.reboot_and_reset_tpm()
        self.sync_and_ec_reboot()
        self.switcher.wait_for_client()

        if errors:
            fail_msg = "After flashing new RW over old RO, FWIDs were wrong."
            errors.insert(0, fail_msg)
            if raise_error:
                raise error.TestFail('\n'.join(errors))
            return ['\n'.join(errors)]
        return []

    def test_downgrade_rw(self, raise_error=True):
        """Test case: RO=old, RW=old->new->old (with reboots)"""
        logging.info('%s', self.test_downgrade_rw.__doc__)
        self.check_bios_specified(old_ro=True, old_rw=True, new_rw=True)
        errors = []

        # wp=0: update RO+RW
        self.prepare_shellball('old', 'ro')
        errors += self.run_shellball('old', wp=0)

        self.reboot_and_reset_tpm()
        self.sync_and_ec_reboot()
        self.switcher.wait_for_client()

        self.prepare_shellball('new', 'rw')
        errors += self.run_shellball('new', wp=1)

        self.sync_and_ec_reboot()
        self.switcher.wait_for_client()

        # Downgrade BIOS RW, but leave EC/PD at newer firmware
        self.prepare_shellball('old', 'rw')
        errors += self.run_shellball('old', wp=1, host_only=True)

        self.reboot_and_reset_tpm()
        self.sync_and_ec_reboot()
        self.switcher.wait_for_client()

        if errors:
            fail_msg = "After upgrading then downgrading RW, FWIDs were wrong."
            errors.insert(0, fail_msg)
            if raise_error:
                raise error.TestFail('\n'.join(errors))
            return ['\n'.join(errors)]
        return []

    def test_new(self, raise_error=True):
        """Test case: RO=new, RW=new"""
        logging.info('%s', self.test_new.__doc__)
        self.check_bios_specified(new_ro=True, old_rw=True)

        errors = []

        # wp=0: update RO+RW
        self.prepare_shellball('new', 'ro')
        errors += self.run_shellball('new', wp=0)

        self.reboot_and_reset_tpm()
        self.sync_and_ec_reboot()
        self.switcher.wait_for_client()

        if errors:
            fail_msg = "After flashing new RO+RW, FWIDs were wrong."
            errors.insert(0, fail_msg)
            if raise_error:
                raise error.TestFail('\n'.join(errors))
            return ['\n'.join(errors)]
        return []

    def test_old(self, raise_error=True):
        """Test case: RO=old, RW=old"""
        logging.info('%s', self.test_old.__doc__)
        self.check_bios_specified(old_ro=True, old_rw=True)

        errors = []

        # wp=0: update RO+RW
        self.prepare_shellball('old', 'ro')
        errors += self.run_shellball('old', wp=0)

        self.reboot_and_reset_tpm()
        self.sync_and_ec_reboot()
        self.switcher.wait_for_client()

        if errors:
            fail_msg = "After flashing old RO+RW, FWIDs were wrong."
            errors.insert(0, fail_msg)
            if raise_error:
                raise error.TestFail('\n'.join(errors))
            return ['\n'.join(errors)]
        return []

    def test(self):
        """Run all the test_* cases"""
        self.check_bios_specified(old_ro=True, old_rw=True, new_rw=True)

        errors = []
        errors += self.test_old(raise_error=False)
        errors += self.test_downgrade_rw(raise_error=False)
        errors += self.test_upgrade_rw(raise_error=False)
        if self.new_bios or (self.new_bios_ro and self.new_bios_rw):
            errors += self.test_new(raise_error=False)
        else:
            logging.warning("No 'new_bios_ro' given, skipping: %s",
                         self.test_new.__doc__)
        if errors:
            if len(errors) > 1:
                errors.insert(0, "%s RO+RW combinations failed." % len(errors))
            raise error.TestFail('\n'.join(errors))
