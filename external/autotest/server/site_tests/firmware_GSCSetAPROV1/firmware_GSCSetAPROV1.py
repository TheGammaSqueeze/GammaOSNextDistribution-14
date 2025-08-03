# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import cr50_utils
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_GSCSetAPROV1(Cr50Test):
    """
    Verify a dut can set the AP RO hash when board id type is erased.
    """
    version = 1

    TEST_AP_RO_VER = 1

    # gsctool errors.
    ERR_UNPROGRAMMED = 'AP RO hash unprogrammed'
    ERR_BID_PROGRAMMED = 'BID already programmed'

    # ap_ro_hash.py errors.
    AP_RO_ERR_ALREADY_PROGRAMMED = 'Already programmed'
    AP_RO_ERR_BID_PROGRAMMED = 'BID programmed'

    def initialize(self, host, cmdline_args, full_args={}):
        """Initialize servo"""
        super(firmware_GSCSetAPROV1,
              self).initialize(host,
                               cmdline_args,
                               full_args,
                               restore_cr50_image=True,
                               restore_cr50_board_id=True)

        if not self.cr50.ap_ro_version_is_supported(self.TEST_AP_RO_VER):
            raise error.TestNAError('GSC does not support AP RO v%s' %
                                    self.TEST_AP_RO_VER)

    def get_hash(self):
        """Get the hash."""
        time.sleep(10)
        result = cr50_utils.GSCTool(self.host, ['-a', '-A'])
        saved_hash = result.stdout.split(':')[-1].strip()
        logging.info('hash: %s', saved_hash)
        return None if self.ERR_UNPROGRAMMED in saved_hash else saved_hash

    def clear_hash(self, expect_error=False):
        """Clear the Hash."""
        result = cr50_utils.GSCTool(self.host, ['-a', '-H'],
                                    ignore_status=expect_error)
        if expect_error and (result.exit_status != 3
                             or self.ERR_BID_PROGRAMMED not in result.stderr):
            raise error.TestFail('Unexpected error clearing hash %r',
                                 result.stderr)
        self.get_hash()

    def set_hash(self, expected_error=None):
        """Set the Hash."""
        result = self.host.run('ap_ro_hash.py -v True GBB',
                               ignore_status=not not expected_error)
        if expected_error:
            if expected_error not in result.stderr:
                raise error.TestFail('Did not find %r in error' %
                                     expected_error)
        elif result.exit_status:
            raise error.TestFail('Error saving hash')
        return self.get_hash()

    def run_once(self):
        """Verify the AP RO hash can be updated when the BID type isn't set"""
        brand = self.get_device_brand()
        if not brand:
            raise error.TestNAError('Cannot run without brand')

        # Erase the board id if its set.
        if not self.cr50.get_board_id()[1]:
            logging.info('Erasing BID')
            self.eraseflashinfo_and_restore_image()
        bid = self.get_saved_cr50_original_version()[2]
        flags = int(bid.split(':')[-1] if bid else '0', 16)

        self.clear_hash()
        self.set_hash()
        self.set_hash(expected_error=self.AP_RO_ERR_ALREADY_PROGRAMMED)

        cr50_utils.SetChipBoardId(self.host, '0xffffffff', flags)

        self.clear_hash()
        self.set_hash()
        self.set_hash(expected_error=self.AP_RO_ERR_ALREADY_PROGRAMMED)
        self.clear_hash()

        cr50_utils.SetChipBoardId(self.host, brand, flags)

        self.clear_hash(expect_error=True)
        self.set_hash(expected_error=self.AP_RO_ERR_BID_PROGRAMMED)
