# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import os

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.server import test

DEFAULT_AVAHI_SIZE_UPDATE_DELAY = 10

# P2P_PATH is the path where the p2p server expects the sharing files.
P2P_PATH = '/var/cache/p2p'

# Prefix all the test files with P2P_TEST_PREFIX.
P2P_TEST_PREFIX = 'p2p-test'

# Kilobyte.
KB = 1024

# File size of the shared file in MB.
P2P_FILE_SIZE_MB = 4 * KB * KB
P2P_FILE_SPLIT_SIZE_KB = P2P_FILE_SIZE_MB // (2 * KB)

# After a peer finishes the download we need it to keep serving the file for
# other peers. This peer will then wait up to P2P_SERVING_TIMEOUT_SECS seconds
# for the test to conclude.
P2P_SERVING_TIMEOUT_SECS = 300


class p2p_EndToEndTest(test.test):
    """Test to check that p2p works."""
    version = 1


    def run_once(self, dut, file_id, companions):
        self._dut = dut
        self._companion = companions[0]

        file_id = '%s-%s' % (P2P_TEST_PREFIX, file_id)
        file_temp_name = os.path.join(P2P_PATH, file_id + '.tmp')
        file_shared_name = os.path.join(P2P_PATH, file_id + '.p2p')

        logging.info('File ID: %s', file_id)

        # Setup dut and companion.
        for host in [self._dut, self._companion]:
            # Ensure that p2p is running.
            host.run('start p2p || true')
            host.run('status p2p | grep running')

        # Prepare an empty file to share and specify its final size via the
        # "user.cros-p2p-filesize" attribute.
        logging.info('All devices setup. Generating a file on main DUT')
        dut.run('touch %s' % file_temp_name)
        dut.run('setfattr -n user.cros-p2p-filesize -v %d %s' %
                (P2P_FILE_SIZE_MB, file_temp_name))
        dut.run('mv %s %s' % (file_temp_name, file_shared_name))

        # Generate part of the files total file fize.
        dut.run('dd if=/dev/zero of=%s bs=%d count=%d' %
                (file_shared_name, KB, P2P_FILE_SPLIT_SIZE_KB))

        def _wait_until_avahi_size_update():
            ret = ''
            try:
                ret = self._companion.run(
                        'p2p-client --get-url=%s --minimum-size=%d' %
                        (file_id, P2P_FILE_SPLIT_SIZE_KB * KB))
                ret = ret.stdout.strip()
            except:
                return False
            return ret != ''

        err = 'Shared file size did not update in time.'
        # The actual delay is 10 seconds, so triple that to account for flakes.
        utils.poll_for_condition(condition=_wait_until_avahi_size_update,
                                 timeout=DEFAULT_AVAHI_SIZE_UPDATE_DELAY * 3,
                                 exception=error.TestFail(err))

        # Now thhe companion can attempt a p2p file download.
        logging.info('Listing all p2p peers for the companion: ')
        logging.info(self._companion.run('p2p-client --list-all').stdout)
        ret = self._companion.run('p2p-client --get-url=%s' % file_id,
                                  ignore_status=True)
        url = ret.stdout.strip()

        if not url:
            raise error.TestFail(
                    'p2p-client on companion returned an empty URL.')
        else:
            logging.info('Companion using URL %s.', url)
            logging.info(
                    'Companion downloading the file from main DUT via p2p in the background.'
            )
            self._companion.run_background('curl %s -o %s' %
                                           (url, file_shared_name),
                                           verbose=True)

        logging.info(
                'While companion is downloading the file, we will expand it to its full size.'
        )
        dut.run('dd if=/dev/zero of=%s bs=%d count=%d'
                ' conv=notrunc oflag=append' %
                (file_shared_name, KB, P2P_FILE_SPLIT_SIZE_KB))

        # Calculate the SHA1 (160 bits -> 40 characters when
        # hexencoded) of the generated file.
        ret = dut.run('sha1sum %s' % file_shared_name)
        sha1_main = ret.stdout.strip()[0:40]
        logging.info('SHA1 of main is %s', sha1_main)
        sha1_companion = ''
        logging.info(
                'Waiting for companion to finish downloading file so we can compare SHA1 values'
        )

        def _shas_match():
            """Returns true when the SHA1 of the file matches on DUT and companion."""
            ret = self._companion.run('sha1sum %s' % file_shared_name)
            sha1_companion = ret.stdout.strip()[0:40]
            logging.debug(sha1_companion)
            return sha1_main == sha1_companion

        err = "Main DUT's SHA1 (%s) doesn't match companions's SHA1 (%s)." % (
                sha1_main, sha1_companion)
        utils.poll_for_condition(condition=_shas_match,
                                 timeout=P2P_SERVING_TIMEOUT_SECS,
                                 exception=error.TestFail(err))

    def cleanup(self):
        # Clean the test environment and stop sharing this file.
        for host in [self._dut, self._companion]:
            host.run('rm -f %s/%s-*.p2p' % (P2P_PATH, P2P_TEST_PREFIX))
            host.run('stop p2p')
