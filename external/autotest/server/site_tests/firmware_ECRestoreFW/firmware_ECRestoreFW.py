# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""The autotest performing FW update, both EC and AP in CCD mode."""
import logging
import re

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_ECRestoreFW(FirmwareTest):
    """A test that restores a machine from an incrrect FW to backup."""

    version = 1

    def initialize(self, host, cmdline_args, full_args):
        """Initialize the test and pick a fake board to use for corruption. """
        super(firmware_ECRestoreFW, self).initialize(host, cmdline_args,
                                                   full_args)

        # Don't bother if there is no Chrome EC.
        if not self.check_ec_capability():
            raise error.TestNAError('Nothing needs to be tested on this device')

        self.local_tarball = None
        self.build = None
        # find if "local_tarball" was given in the command line arguments.
        for arg in cmdline_args:
            match = re.search(r'^local_tarball=(.+)', arg)
            if match:
                self.local_tarball = match.group(1)
                logging.info('Use local tarball %s', self.local_tarball)
                break
        else:
            # Get the latest firmware release from the server.
            # Even this test uses a fake EC image, it needs to download
            # the release to get some subsidiary binary (like npcx_monitor.bin).
            platform = self.faft_config.platform

            # Get the parent (a.k.a. reference board or baseboard), and hand it
            # to get_latest_release_version so that it can use it in search as
            # secondary candidate. For example, bob doesn't have its own release
            # directory, but its parent, gru does.
            parent = getattr(self.faft_config, 'parent', None)

            self.build = host.get_latest_release_version(platform, parent)

            if not self.build:
                raise error.TestError(
                        'Cannot locate the latest release for %s' % platform)
            logging.info('Will use the build %s', self.build)
        self.backup_firmware()

    def cleanup(self):
        """The method called by the control file to start the test.

        Raises:
          TestFail: if the firmware restoration fails.
        """
        try:
            if self.is_firmware_saved():
                self.restore_firmware()
        except Exception as e:
            raise error.TestFail('FW Restoration failed: %s' % str(e))
        finally:
            super(firmware_ECRestoreFW, self).cleanup()

    def run_once(self, host):
        """The method called by the control file to start the test.

        Args:
          host:  a CrosHost object of the machine to update.
        """

        try:
            host.firmware_install(build=self.build,
                                  dest=self.resultsdir,
                                  local_tarball=self.local_tarball,
                                  install_ec=True,
                                  install_bios=False,
                                  corrupt_ec=True)
        except error.TestError as e:
            # It failed before the test attempts to install firmware.
            # It could be either devserver timeout or servo device error.
            # Let this test fail in those cases.
            raise e
        except Exception as e:
            # Nothing can be guaranteed with the firmware corruption with wrong
            # firmware. Let's not this test fail for that.
            logging.info('Caught an exception: %s', e)
