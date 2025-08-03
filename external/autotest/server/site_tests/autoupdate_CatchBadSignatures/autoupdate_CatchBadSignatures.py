# Lint as: python2, python3
# Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.update_engine import update_engine_test


class autoupdate_CatchBadSignatures(update_engine_test.UpdateEngineTest):
    """Test to verify that update_engine correctly checks payload signatures."""
    version = 1

    # The test image to use and the values associated with it.
    _IMAGE_GS_URL='https://storage.googleapis.com/chromiumos-test-assets-public/autoupdate/autoupdate_CatchBadSignatures.bin'

    def _check_signature(self, expected_log_messages,
                         failure_message, public_key=None, tag=None):
        """
        Helper function for updating with a canned Omaha response.

        @param expected_log_messages: A list of strings that are expected to be
                                      in the update_engine log.
        @param failure_message: The message for exception to raise on error.
        @param public_key: The public key to be passed to the update_engine.
        @param tag: String to append to test name to identify it in the logs.

        """
        # Runs the update on the DUT and expect it to fail.
        self._run_client_test_and_check_result('autoupdate_CannedOmahaUpdate',
                                               payload_url=self._IMAGE_GS_URL,
                                               allow_failure=True,
                                               public_key=public_key,
                                               tag=tag)
        if not self._check_update_engine_log_for_entry(expected_log_messages):
            last_error = self._get_last_error_string()
            raise error.TestFail(
                '%s. Last update_engine.log error: %s' % (failure_message,
                                                          last_error))


    def _check_bad_metadata_signature(self):
        """Checks that update_engine rejects updates where the payload
        and Omaha response do not agree on the metadata signature."""
        expected_log_messages = [
                'Mandating payload signature checks since Omaha Response for '
                'unofficial build includes public RSA key',
                'Mandatory metadata signature validation failed'
        ]

        self._check_signature(expected_log_messages,
                              'Check for bad metadata signature failed.',
                              public_key=self._IMAGE_PUBLIC_KEY,
                              tag='metadata_signature')


    def _check_bad_payload_signature(self):
        """Checks that update_engine rejects updates where the payload
        signature does not match what is expected."""
        expected_log_messages = [
                'Mandating payload signature checks since Omaha Response for '
                'unofficial build includes public RSA key',
                'Metadata hash signature matches value in Omaha response.',
                'Public key verification failed, thus update failed'
        ]

        self._check_signature(expected_log_messages,
                              'Check for payload signature failed.',
                              tag='payload_signature')


    def run_once(self):
        """Runs the test on a DUT."""
        self._check_bad_metadata_signature()
        self._check_bad_payload_signature()
