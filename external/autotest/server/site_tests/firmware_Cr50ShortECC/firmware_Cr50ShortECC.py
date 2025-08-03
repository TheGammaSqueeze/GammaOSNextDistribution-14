# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server import test

class firmware_Cr50ShortECC(test.test):
    """A test that verifies cr50 can handle short ECC keys."""

    version = 1
    CMD_TRUNKS_CLIENT = 'trunks_client %s'
    CMD_HELP = '--help'
    CMD_SHORT_ECC_TEST = '--key_test_short_ecc'
    KEY_BLOB = '/tmp/test.keyblob'
    CMD_KEY_CREATE = '--key_create --ecc --usage=all --key_blob=' + KEY_BLOB
    CMD_KEY_LOAD = '--key_load --key_blob=' + KEY_BLOB

    def run_trunks_client_cmd(self, cmd):
        """Run a trunks_client command on the DUT."""
        trunks_client_cmd = self.CMD_TRUNKS_CLIENT % cmd
        logging.info('Running %r', trunks_client_cmd)
        result = self.client.run(trunks_client_cmd).stdout
        logging.info('stdout: %s', result)
        return result

    def run_once(self, host=None):
        """Verify cr50 can handle short ECC keys."""
        self.client = host

        if (self.CMD_SHORT_ECC_TEST not in
            self.run_trunks_client_cmd(self.CMD_HELP)):
            raise error.TestNAError('OS trunks_client does not support %r' %
                                    self.CMD_SHORT_ECC_TEST)

        self.run_trunks_client_cmd(self.CMD_KEY_CREATE)

        load_result = self.run_trunks_client_cmd(self.CMD_KEY_LOAD)
        handle = load_result.split('handle:')[-1].strip()

        short_ecc_test_cmd = self.CMD_SHORT_ECC_TEST + ' --handle=' + handle
        self.run_trunks_client_cmd(short_ecc_test_cmd)
