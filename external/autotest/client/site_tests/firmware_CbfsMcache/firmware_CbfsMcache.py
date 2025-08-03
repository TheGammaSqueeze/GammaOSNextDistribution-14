# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import subprocess

from autotest_lib.client.bin import test
from autotest_lib.client.bin import utils
from autotest_lib.client.common_lib import error


class firmware_CbfsMcache(test.test):
    """Validates that the CBFS metadata cache didn't overflow."""
    version = 1

    MCACHE_MAGIC_FULL = b'FULL'
    MCACHE_MAGIC_END = b'$END'

    CBMEM_RO_MCACHE = b'524d5346'
    CBMEM_RW_MCACHE = b'574d5346'

    def cbmem(self, *args):
        """Runs 'cbmem' utility with specified arguments and returns output."""
        # Cannot use utils.run because it force-decodes stdout as UTF-8.
        return subprocess.check_output(('cbmem', ) + args)

    def has_mcache(self):
        """Returns true iff there's an RO MCACHE section in CBMEM."""
        return self.CBMEM_RO_MCACHE in self.cbmem('-l')

    def check_mcache(self, cbmem_id, name):
        """Fail if the cbmem_id mcache wasn't terminated with an END token."""
        mcache = self.cbmem('-r', cbmem_id)
        if mcache[-4:] == self.MCACHE_MAGIC_FULL:
            raise error.TestFail("CBFS %s mcache overflowed!" % name)
        if mcache[-4:] != self.MCACHE_MAGIC_END:
            raise error.TestError(
                    "CBFS %s mcache ends with invalid token (%s)!" %
                    mcache[-4:].__repr__())

    def run_once(self):
        """Fail if mcaches exists and wasn't terminated with an END token."""
        if utils.get_board() == 'volteer':
            raise error.TestNAError("Skipped on Volteer, see b/187561710.")
        if not self.has_mcache():
            raise error.TestNAError("This platform doesn't use CBFS mcache.")
        self.check_mcache(self.CBMEM_RO_MCACHE, "RO")
        self.check_mcache(self.CBMEM_RW_MCACHE, "RW")
