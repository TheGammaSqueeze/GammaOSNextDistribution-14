# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import re
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_Cr50Keygen(FirmwareTest):
    """Verify cr50 can tell the state of the dev mode switch."""
    version = 1

    RUNS = 20
    TIME_RE = r'KeyPair took (\d+) '
    TRUNKS_BASE = (
            'trunks_client --key_create --key_blob=/tmp/key --print_time '
            '--usage=decrypt ')
    RSA_CMD_ARGS = '--rsa=2048'
    # TODO(mruthven): look at results to see if 5000 is a reasonable average and
    # 30s is a reasonable max across the test devices. Start a low threshold to
    # get an idea for how the lab devices are operating.
    # Raise an error if the average RSA key generation time takes longer than
    # this threshold in ms.
    RSA_AVG_THRESHOLD = 8000
    # Raise an error if the max RSA key generation time takes longer than this
    # threshold in ms.
    RSA_MAX_THRESHOLD = 30000
    ECC_CMD_ARGS = '--ecc'
    # TODO(mruthven): look at results to see if 150 is a reasonable average and
    # 500 is a reasonable max across the test devices. Start a low threshold to
    # get an idea for how the lab devices are operating.
    # Raise an error if the average ECC key generation time takes longer than
    # this threshold.
    ECC_AVG_THRESHOLD = 150
    # Raise an error if the max ECC key generation time takes longer than this
    # threshold in ms.
    ECC_MAX_THRESHOLD = 500

    def wait_for_client_after_changing_ccd(self, enable):
        """Change CCD and wait for client.

        @param enable: True to enable ccd. False to disable it.
        @raises TestError if the DUT isn't pingable after changing ccd.
        """
        if not hasattr(self, 'cr50') or not self.cr50:
            return

        if enable:
            self.cr50.ccd_enable()
        else:
            self.cr50.ccd_disable()

        time.sleep(5)

        if self.host.ping_wait_up(180):
            return
        msg = ('DUT is not pingable after %sabling ccd' %
               'en' if enable else 'dis')
        logging.info(msg)
        logging.info('Resetting DUT')
        self.host.reset_via_servo()
        if not self.host.ping_wait_up(180):
            raise error.TestError(msg)

    def get_key_attr(self, attr):
        """Get the attribute for the type of key the test is generating."""
        return getattr(self, self.key_type + '_' + attr)

    def get_keygen_cmd(self):
        """Generate the trunks_client key_create command."""
        return self.TRUNKS_BASE + self.get_key_attr('CMD_ARGS')

    def run_once(self, host, key_type='RSA'):
        """Check ECC and RSA Keygen times."""
        self.host = host
        self.key_type = key_type.upper()

        # TODO(b/218492933) : find better way to disable rddkeepalive
        # Disable rddkeepalive, so the test can disable ccd.
        self.cr50.send_command('ccd testlab open')
        self.cr50.send_command('rddkeepalive disable')
        # Lock cr50 so the console will be restricted
        self.cr50.set_ccd_level('lock')

        self.wait_for_client_after_changing_ccd(False)

        cmd = self.get_keygen_cmd()
        logging.info(cmd)
        full_cmd = ('for i in {1..%d} ; do echo $i ; %s || break; done' %
                    (self.RUNS, cmd))
        response = host.run(full_cmd)
        logging.debug(response.stdout)
        times = [int(t) for t in re.findall(self.TIME_RE, response.stdout)]
        logging.info(times)
        avg_time = sum(times) / len(times)
        max_time = max(times)
        logging.info('Average time: %s', avg_time)
        logging.info('Max time: %s', max_time)
        self.wait_for_client_after_changing_ccd(True)
        if len(times) != self.RUNS:
            raise error.TestFail('did not generate %d keys' % self.RUNS)
        max_threshold = self.get_key_attr('MAX_THRESHOLD')
        if max_time > max_threshold:
            raise error.TestFail('MAX time %r is over the acceptable '
                                 'threshold(%dms)' % (max_time, max_threshold))
        avg_threshold = self.get_key_attr('AVG_THRESHOLD')
        if avg_time > avg_threshold:
            raise error.TestFail('Average time %r is over the acceptable '
                                 'threshold(%dms)' % (avg_time, avg_threshold))
